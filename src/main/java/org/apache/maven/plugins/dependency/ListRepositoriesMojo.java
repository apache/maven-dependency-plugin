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
package org.apache.maven.plugins.dependency;

import javax.inject.Inject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputSource;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Goal that collects all project dependencies and then lists the dependency repositories used by the build and
 * transitive dependencies, along with the plugin repositories used by the build.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 2.2
 */
@Mojo(name = "list-repositories", threadSafe = true)
public class ListRepositoriesMojo extends AbstractDependencyMojo {

    private final RepositorySystem repositorySystem;

    @Inject
    public ListRepositoriesMojo(
            MavenSession session, BuildContext buildContext, MavenProject project, RepositorySystem repositorySystem) {
        super(session, buildContext, project);
        this.repositorySystem = repositorySystem;
    }

    /**
     * Displays a list of the repositories used by this build.
     *
     * @throws MojoExecutionException with a message if an error occurs
     */
    @Override
    protected void doExecute() throws MojoExecutionException {

        CollectRequest request = new CollectRequest();
        request.setRepositories(getProject().getRemoteProjectRepositories());
        request.setRootArtifact(RepositoryUtils.toArtifact(getProject().getArtifact()));

        ArtifactTypeRegistry artifactTypeRegistry =
                session.getRepositorySession().getArtifactTypeRegistry();

        request.setDependencies(getProject().getDependencies().stream()
                .map(d -> RepositoryUtils.toDependency(d, artifactTypeRegistry))
                .collect(Collectors.toList()));

        request.setManagedDependencies(Optional.ofNullable(getProject().getDependencyManagement())
                .map(DependencyManagement::getDependencies)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(d -> RepositoryUtils.toDependency(d, artifactTypeRegistry))
                .collect(Collectors.toList()));

        try {
            CollectResult collectResult = repositorySystem.collectDependencies(session.getRepositorySession(), request);
            Set<RemoteRepository> repositories = new HashSet<>();
            collectResult.getRoot().accept(new TreeDependencyVisitor(new DependencyVisitor() {
                @Override
                public boolean visitEnter(DependencyNode node) {
                    repositories.addAll(node.getRepositories());
                    debugLogNodeRepo(node);
                    return true;
                }

                @Override
                public boolean visitLeave(DependencyNode node) {
                    return true;
                }
            }));

            Set<RemoteRepository> pluginRepositories =
                    new HashSet<>(Optional.ofNullable(getProject().getRemotePluginRepositories())
                            .orElseGet(Collections::emptyList));

            if (repositories.isEmpty() && pluginRepositories.isEmpty()) {
                getLog().info("No remote repository is used by this build." + System.lineSeparator());
                return;
            }

            StringBuilder message = new StringBuilder();
            prepareRemoteRepositoriesList(
                    message,
                    "Project remote repositories used by this build:",
                    repositories,
                    declaredLocations(getProject().getRepositories()));
            prepareRemoteRepositoriesList(
                    message,
                    "Plugin repositories used by this build:",
                    pluginRepositories,
                    declaredLocations(getProject().getPluginRepositories()));

            getLog().info(message);

        } catch (DependencyCollectionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void debugLogNodeRepo(DependencyNode node) {
        if (!getLog().isDebugEnabled()) {
            return;
        }

        getLog().debug("Node: " + node + " resolved from:");
        node.getRepositories().forEach(repo -> {
            if (repo.getMirroredRepositories().isEmpty()) {
                getLog().debug(" - " + repo);
            } else {
                getLog().debug(" - " + repo + " as mirror for:");
                repo.getMirroredRepositories().forEach(mrepo -> getLog().debug("    - " + mrepo));
            }
        });
    }

    /**
     * Maps each repository on the project model that carries an input location (the project, its parent POMs, and
     * the super POM) to a human readable description of where it was declared. Repositories that only come from the
     * POMs of transitive dependencies are not part of the project model and have no entry.
     */
    private static Map<String, String> declaredLocations(List<Repository> modelRepositories) {
        Map<String, String> locations = new HashMap<>();
        if (modelRepositories == null) {
            return locations;
        }
        for (Repository repository : modelRepositories) {
            String description = describeLocation(repository);
            if (description != null) {
                locations.putIfAbsent(locationKey(repository.getId(), repository.getUrl()), description);
            }
        }
        return locations;
    }

    private static String locationKey(String id, String url) {
        String normalizedUrl = url == null ? "" : url.trim();
        while (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }
        return id + '\n' + normalizedUrl;
    }

    private static String describeLocation(Repository repository) {
        InputLocation location = repository.getLocation("");
        if (location == null) {
            location = repository.getLocation("id");
        }
        if (location == null || location.getSource() == null) {
            return null;
        }
        InputSource source = location.getSource();
        if (source.getModelId() != null && source.getModelId().endsWith(":super-pom")) {
            return "the Maven super POM";
        }
        StringBuilder description = new StringBuilder();
        if (source.getModelId() != null && !source.getModelId().isEmpty()) {
            description.append(source.getModelId());
        }
        if (source.getLocation() != null) {
            if (description.length() > 0) {
                description.append(' ');
            }
            description.append(source.getLocation());
            if (location.getLineNumber() > 0) {
                description.append(':').append(location.getLineNumber());
            }
        }
        return description.length() == 0 ? null : description.toString();
    }

    /**
     * Returns an indented line saying where the repository was declared, or an empty string when that is unknown. It
     * goes on its own line so the repository lines keep their format.
     */
    private static String declaredLine(RemoteRepository repo, Map<String, String> locations) {
        String declared = locations.get(locationKey(repo.getId(), repo.getUrl()));
        return declared == null ? "" : "   declared in " + declared + System.lineSeparator();
    }

    private void prepareRemoteMirrorRepositoriesList(
            StringBuilder message,
            Collection<RemoteRepository> remoteProjectRepositories,
            Map<String, String> locations) {

        Map<RemoteRepository, RemoteRepository> mirrorMap = new HashMap<>();
        remoteProjectRepositories.forEach(
                repo -> repo.getMirroredRepositories().forEach(mrepo -> mirrorMap.put(mrepo, repo)));

        mirrorMap.forEach((repo, mirror) -> message.append(" * ")
                .append(repo)
                .append(" mirrored by ")
                .append(mirror)
                .append(System.lineSeparator())
                .append(declaredLine(repo, locations)));
    }

    private void prepareRemoteRepositoriesList(
            StringBuilder message,
            String heading,
            Collection<RemoteRepository> repositories,
            Map<String, String> locations) {
        if (repositories.isEmpty()) {
            return;
        }

        message.append(heading).append(System.lineSeparator());

        Map<Boolean, List<RemoteRepository>> repoGroupByMirrors = repositories.stream()
                .collect(Collectors.groupingBy(
                        repo -> repo.getMirroredRepositories().isEmpty()));

        repoGroupByMirrors
                .getOrDefault(Boolean.TRUE, Collections.emptyList())
                .forEach(repo -> message.append(" * ")
                        .append(repo)
                        .append(System.lineSeparator())
                        .append(declaredLine(repo, locations)));
        prepareRemoteMirrorRepositoriesList(
                message, repoGroupByMirrors.getOrDefault(Boolean.FALSE, Collections.emptyList()), locations);
    }
}
