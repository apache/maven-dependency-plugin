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
 * Goal that collects all project dependencies and then lists the repositories used by the build and by the transitive
 * dependencies.
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
                    return true;
                }

                @Override
                public boolean visitLeave(DependencyNode node) {
                    return true;
                }
            }));

            if (repositories.isEmpty()) {
                getLog().info("No remote repository is used by this build." + System.lineSeparator());
                return;
            }

            StringBuilder message = new StringBuilder();

            Map<Boolean, List<RemoteRepository>> repoGroupByMirrors = repositories.stream()
                    .collect(Collectors.groupingBy(
                            repo -> repo.getMirroredRepositories().isEmpty()));

            prepareRemoteRepositoriesList(
                    message, repoGroupByMirrors.getOrDefault(Boolean.TRUE, Collections.emptyList()));
            prepareRemoteMirrorRepositoriesList(
                    message, repoGroupByMirrors.getOrDefault(Boolean.FALSE, Collections.emptyList()));

            getLog().info(message);

        } catch (DependencyCollectionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void prepareRemoteMirrorRepositoriesList(
            StringBuilder message, Collection<RemoteRepository> remoteProjectRepositories) {

        Map<RemoteRepository, RemoteRepository> mirrorMap = new HashMap<>();
        remoteProjectRepositories.forEach(
                repo -> repo.getMirroredRepositories().forEach(mrepo -> mirrorMap.put(mrepo, repo)));

        mirrorMap.forEach((repo, mirror) -> message.append(" * ")
                .append(repo)
                .append(" mirrored by ")
                .append(mirror)
                .append(System.lineSeparator()));
    }

    private void prepareRemoteRepositoriesList(
            StringBuilder message, Collection<RemoteRepository> remoteProjectRepositories) {

        message.append("Project remote repositories used by this build:").append(System.lineSeparator());

        remoteProjectRepositories.forEach(
                repo -> message.append(" * ").append(repo).append(System.lineSeparator()));
    }
}
