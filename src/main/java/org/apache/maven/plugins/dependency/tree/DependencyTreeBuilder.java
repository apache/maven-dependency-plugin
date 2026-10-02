/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.dependency.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.JavaScopeDeriver;
import org.eclipse.aether.util.graph.transformer.NearestVersionSelector;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;

/**
 * Builds the {@link DependencyNode} tree of a project from Maven Resolver's dependency graph.
 * <p>
 * The plain tree is what Maven resolves for the project, so it is consistent with the Maven version running. The
 * verbose tree is collected again with the conflict resolver keeping the omitted nodes.
 */
class DependencyTreeBuilder {
    private final RepositorySystem repositorySystem;

    private final ProjectDependenciesResolver projectDependenciesResolver;

    DependencyTreeBuilder(RepositorySystem repositorySystem, ProjectDependenciesResolver projectDependenciesResolver) {
        this.repositorySystem = repositorySystem;
        this.projectDependenciesResolver = projectDependenciesResolver;
    }

    /**
     * Builds the tree resolved by Maven, without omitted nodes.
     */
    DependencyNode build(MavenProject project, RepositorySystemSession session, ArtifactFilter filter)
            throws DependencyResolutionException {
        DependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
        request.setMavenProject(project);
        request.setRepositorySession(session);
        // only collect the graph, do not download the artifacts
        request.setResolutionFilter((node, parents) -> false);

        org.eclipse.aether.graph.DependencyNode root =
                projectDependenciesResolver.resolve(request).getDependencyGraph();

        return buildNode(null, root, project.getArtifact(), filter, false);
    }

    /**
     * Builds the tree including the nodes omitted for duplicates and conflicts, and the managed versions and scopes.
     */
    DependencyNode buildVerbose(MavenProject project, RepositorySystemSession repositorySession, ArtifactFilter filter)
            throws DependencyCollectionException {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(repositorySession);
        session.setDependencyGraphTransformer(new ConflictResolver(
                new NearestVersionSelector(),
                new VerboseJavaScopeSelector(),
                new SimpleOptionalitySelector(),
                new JavaScopeDeriver()));
        session.setDependencySelector(new AndDependencySelector(
                new DirectScopeDependencySelector(JavaScopes.TEST),
                new DirectScopeDependencySelector(JavaScopes.PROVIDED),
                new OptionalDependencySelector(),
                new ExclusionDependencySelector()));
        session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
        session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);

        ArtifactTypeRegistry stereotypes = session.getArtifactTypeRegistry();
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRootArtifact(RepositoryUtils.toArtifact(project.getArtifact()));
        collectRequest.setRepositories(RepositoryUtils.toRepos(project.getRemoteArtifactRepositories()));
        for (Dependency dependency : project.getDependencies()) {
            collectRequest.addDependency(RepositoryUtils.toDependency(dependency, stereotypes));
        }
        if (project.getDependencyManagement() != null) {
            for (Dependency dependency : project.getDependencyManagement().getDependencies()) {
                collectRequest.addManagedDependency(RepositoryUtils.toDependency(dependency, stereotypes));
            }
        }

        org.eclipse.aether.graph.DependencyNode root =
                repositorySystem.collectDependencies(session, collectRequest).getRoot();

        return buildNode(null, root, project.getArtifact(), filter, true);
    }

    private DependencyNode buildNode(
            DependencyNode parent,
            org.eclipse.aether.graph.DependencyNode node,
            Artifact artifact,
            ArtifactFilter filter,
            boolean verbose) {
        DependencyNode current = new DependencyNode(
                parent, artifact, verbose ? verboseNodeString(node, artifact) : nodeString(artifact));

        List<DependencyNode> children = new ArrayList<>(node.getChildren().size());
        for (org.eclipse.aether.graph.DependencyNode child : node.getChildren()) {
            Artifact childArtifact = toArtifact(child.getDependency());

            if (filter == null || filter.include(childArtifact)) {
                children.add(buildNode(current, child, childArtifact, filter, verbose));
            }
        }
        current.setChildren(Collections.unmodifiableList(children));

        return current;
    }

    private static Artifact toArtifact(org.eclipse.aether.graph.Dependency dependency) {
        Artifact artifact = RepositoryUtils.toArtifact(dependency.getArtifact());
        artifact.setScope(dependency.getScope());
        artifact.setOptional(dependency.isOptional());
        return artifact;
    }

    private static String nodeString(Artifact artifact) {
        return artifact + (artifact.isOptional() ? " (optional)" : "");
    }

    private static String verboseNodeString(org.eclipse.aether.graph.DependencyNode node, Artifact artifact) {
        org.eclipse.aether.graph.DependencyNode winner =
                (org.eclipse.aether.graph.DependencyNode) node.getData().get(ConflictResolver.NODE_DATA_WINNER);
        String winnerVersion = null;
        String ignoredScope = null;
        if (winner != null) {
            winnerVersion = winner.getArtifact().getBaseVersion();
        } else {
            ignoredScope = (String) node.getData().get(VerboseJavaScopeSelector.REDUCED_SCOPE);
        }

        boolean included = winnerVersion == null;

        StringBuilder buffer = new StringBuilder();
        if (!included) {
            buffer.append('(');
        }
        buffer.append(artifact);

        List<String> items = new ArrayList<>();
        String premanagedVersion = DependencyManagerUtils.getPremanagedVersion(node);
        if (premanagedVersion != null) {
            items.add("version managed from " + premanagedVersion);
        }
        String premanagedScope = DependencyManagerUtils.getPremanagedScope(node);
        if (premanagedScope != null) {
            items.add("scope managed from " + premanagedScope);
        }
        if (ignoredScope != null) {
            items.add("scope not updated to " + ignoredScope);
        }
        if (!included) {
            items.add(
                    winnerVersion.equals(artifact.getVersion())
                            ? "omitted for duplicate"
                            : "omitted for conflict with " + winnerVersion);
        }

        if (!items.isEmpty()) {
            buffer.append(included ? " (" : " - ")
                    .append(String.join("; ", items))
                    .append(included ? ")" : "");
        }
        if (!included) {
            buffer.append(')');
        }

        return buffer.toString();
    }
}
