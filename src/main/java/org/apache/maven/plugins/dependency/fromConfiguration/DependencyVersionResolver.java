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
package org.apache.maven.plugins.dependency.fromConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyCollectorRequest;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.JavaScopeDeriver;
import org.eclipse.aether.util.graph.transformer.JavaScopeSelector;
import org.eclipse.aether.util.graph.transformer.NearestVersionSelector;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;

final class DependencyVersionResolver {
    private final MavenSession session;

    private final MavenProject project;

    private final DependencyCollectorBuilder dependencyCollectorBuilder;

    private final Map<ClasspathScope, Map<GroupArtifactKey, List<Artifact>>> artifactIndexByScope =
            new EnumMap<>(ClasspathScope.class);

    DependencyVersionResolver(
            MavenSession session, MavenProject project, DependencyCollectorBuilder dependencyCollectorBuilder) {
        this.session = session;
        this.project = project;
        this.dependencyCollectorBuilder = dependencyCollectorBuilder;
    }

    String resolveVersion(ArtifactItem artifactItem) throws MojoExecutionException {
        List<Dependency> directDependencies = project.getDependencies();
        List<Dependency> managedDependencies = project.getDependencyManagement() == null
                ? Collections.emptyList()
                : project.getDependencyManagement().getDependencies();

        String modelVersion = findModelVersion(artifactItem, directDependencies, false);
        if (modelVersion == null) {
            modelVersion = findModelVersion(artifactItem, managedDependencies, false);
        }
        if (modelVersion == null) {
            modelVersion = findModelVersion(artifactItem, directDependencies, true);
        }
        if (modelVersion == null) {
            modelVersion = findModelVersion(artifactItem, managedDependencies, true);
        }
        if (modelVersion != null) {
            return modelVersion;
        }

        List<ClasspathScope> requestedScopes = requestedScopes(artifactItem);
        Map<ClasspathScope, Set<String>> versions = findGraphVersions(artifactItem, requestedScopes, false);
        if (versions.isEmpty()) {
            versions = findGraphVersions(artifactItem, requestedScopes, true);
        }
        if (!versions.isEmpty()) {
            return uniqueVersion(artifactItem, versions);
        }

        String scopeDescription = artifactItem.getDependencyScope() == null
                ? "compile, runtime, or test dependency graph"
                : artifactItem.getDependencyScope() + " dependency graph";
        throw new MojoExecutionException("Unable to find artifact version of " + artifactItem.getGroupId() + ":"
                + artifactItem.getArtifactId() + " in direct dependencies, dependency management, or the project's "
                + scopeDescription + ".");
    }

    private List<ClasspathScope> requestedScopes(ArtifactItem artifactItem) throws MojoExecutionException {
        String dependencyScope = artifactItem.getDependencyScope();
        if (dependencyScope == null || dependencyScope.isEmpty()) {
            List<ClasspathScope> scopes = new ArrayList<>(3);
            Collections.addAll(scopes, ClasspathScope.COMPILE, ClasspathScope.RUNTIME, ClasspathScope.TEST);
            return scopes;
        }
        try {
            return Collections.singletonList(ClasspathScope.fromString(dependencyScope));
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private Map<ClasspathScope, Set<String>> findGraphVersions(
            ArtifactItem artifactItem, List<ClasspathScope> scopes, boolean looseMatch) throws MojoExecutionException {
        Map<ClasspathScope, Set<String>> result = new LinkedHashMap<>();
        GroupArtifactKey key = GroupArtifactKey.from(artifactItem);
        for (ClasspathScope scope : scopes) {
            List<Artifact> artifacts = artifactIndex(scope).get(key);
            Set<String> versions =
                    artifacts == null ? Collections.emptySet() : matchingVersions(artifactItem, artifacts, looseMatch);
            if (!versions.isEmpty()) {
                result.put(scope, versions);
            }
        }
        return result;
    }

    private String uniqueVersion(ArtifactItem artifactItem, Map<ClasspathScope, Set<String>> versions)
            throws MojoExecutionException {
        Set<String> uniqueVersions = versions.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueVersions.size() == 1) {
            return uniqueVersions.iterator().next();
        }

        String selections = versions.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining(", "));
        throw new MojoExecutionException("Dependency graphs select different versions of " + artifactItem.getGroupId()
                + ":" + artifactItem.getArtifactId() + " (" + selections
                + "). Set dependencyScope to compile, runtime, or test, or specify version explicitly.");
    }

    private Map<GroupArtifactKey, List<Artifact>> artifactIndex(ClasspathScope scope) throws MojoExecutionException {
        Map<GroupArtifactKey, List<Artifact>> cached = artifactIndexByScope.get(scope);
        if (cached != null) {
            return cached;
        }

        ProjectBuildingRequest buildingRequest;
        if (session.getProjectBuildingRequest() == null) {
            buildingRequest = new DefaultProjectBuildingRequest();
            buildingRequest.setRepositorySession(session.getRepositorySession());
        } else {
            buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        }
        buildingRequest.setProject(project);

        DependencyCollectorRequest request = new DependencyCollectorRequest(buildingRequest);
        request.dependencySelector(dependencySelector(scope));
        request.dependencyGraphTransformer(new ConflictResolver(
                new NearestVersionSelector(),
                new JavaScopeSelector(),
                new SimpleOptionalitySelector(),
                new JavaScopeDeriver()));
        request.addConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, false);
        request.addConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, false);

        try {
            DependencyNode root = dependencyCollectorBuilder.collectDependencyGraph(request);
            List<Artifact> collected = new ArrayList<>();
            collectArtifacts(root, collected, true);
            Map<GroupArtifactKey, List<Artifact>> index = indexArtifacts(collected);
            artifactIndexByScope.put(scope, index);
            return index;
        } catch (DependencyCollectorBuilderException e) {
            throw new MojoExecutionException("Unable to collect the project's " + scope + " dependency graph.", e);
        }
    }

    private DependencySelector dependencySelector(ClasspathScope scope) {
        return new AndDependencySelector(
                new ClasspathDependencySelector(scope),
                new ScopeDependencySelector(JavaScopes.TEST, JavaScopes.PROVIDED),
                new OptionalDependencySelector(),
                new ExclusionDependencySelector());
    }

    private void collectArtifacts(DependencyNode node, List<Artifact> artifacts, boolean root) {
        if (!root && node.getArtifact() != null) {
            artifacts.add(node.getArtifact());
        }
        for (DependencyNode child : node.getChildren()) {
            collectArtifacts(child, artifacts, false);
        }
    }

    static Map<GroupArtifactKey, List<Artifact>> indexArtifacts(Collection<Artifact> artifacts) {
        Map<GroupArtifactKey, List<Artifact>> index = new LinkedHashMap<>();
        for (Artifact artifact : artifacts) {
            index.computeIfAbsent(GroupArtifactKey.from(artifact), key -> new ArrayList<>())
                    .add(artifact);
        }
        for (Map.Entry<GroupArtifactKey, List<Artifact>> entry : index.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(index);
    }

    static Set<String> matchingVersions(ArtifactItem artifactItem, List<Artifact> artifacts, boolean looseMatch) {
        return artifacts.stream()
                .filter(artifact -> looseMatch
                        || (Objects.equals(artifact.getClassifier(), artifactItem.getClassifier())
                                && Objects.equals(artifact.getType(), artifactItem.getType())))
                .map(Artifact::getVersion)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String findModelVersion(ArtifactItem artifactItem, List<Dependency> dependencies, boolean looseMatch) {
        for (Dependency dependency : dependencies) {
            if (Objects.equals(dependency.getArtifactId(), artifactItem.getArtifactId())
                    && Objects.equals(dependency.getGroupId(), artifactItem.getGroupId())
                    && (looseMatch || Objects.equals(dependency.getClassifier(), artifactItem.getClassifier()))
                    && (looseMatch || Objects.equals(dependency.getType(), artifactItem.getType()))) {
                return dependency.getVersion();
            }
        }
        return null;
    }

    static final class GroupArtifactKey {
        private final String groupId;

        private final String artifactId;

        private GroupArtifactKey(String groupId, String artifactId) {
            this.groupId = groupId;
            this.artifactId = artifactId;
        }

        static GroupArtifactKey from(ArtifactItem artifactItem) {
            return new GroupArtifactKey(artifactItem.getGroupId(), artifactItem.getArtifactId());
        }

        static GroupArtifactKey from(Artifact artifact) {
            return new GroupArtifactKey(artifact.getGroupId(), artifact.getArtifactId());
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof GroupArtifactKey)) {
                return false;
            }
            GroupArtifactKey that = (GroupArtifactKey) object;
            return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, artifactId);
        }
    }
}
