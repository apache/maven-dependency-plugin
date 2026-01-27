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
package org.apache.maven.plugins.dependency.utils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.ModelBase;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginContainer;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

/**
 * Helper class for using Resolver API.
 */
@Named
@Singleton
public class ResolverUtil {

    private final RepositorySystem repositorySystem;

    private final Provider<MavenSession> mavenSessionProvider;

    @Inject
    public ResolverUtil(RepositorySystem repositorySystem, Provider<MavenSession> mavenSessionProvider) {
        this.repositorySystem = repositorySystem;
        this.mavenSessionProvider = mavenSessionProvider;
    }

    /**
     * Collects the transitive dependencies.
     *
     * @param root a root dependency for collections
     * @return a resolved dependencies collections
     */
    public Collection<Dependency> collectDependencies(Dependency root) throws DependencyCollectionException {

        MavenSession session = mavenSessionProvider.get();

        CollectRequest request =
                new CollectRequest(root, session.getCurrentProject().getRemoteProjectRepositories());
        CollectResult result = repositorySystem.collectDependencies(session.getRepositorySession(), request);

        PreorderNodeListGenerator nodeListGenerator = new PreorderNodeListGenerator();
        result.getRoot().accept(nodeListGenerator);
        return nodeListGenerator.getDependencies(true);
    }

    /**
     * Resolve given artifact.
     *
     * @param artifact     an artifact to resolve
     * @param repositories remote repositories list
     * @return resolved artifact
     * @throws ArtifactResolutionException if the artifact could not be resolved
     */
    public Artifact resolveArtifact(Artifact artifact, List<RemoteRepository> repositories)
            throws ArtifactResolutionException {
        MavenSession session = mavenSessionProvider.get();
        ArtifactRequest request = new ArtifactRequest(artifact, repositories, null);
        ArtifactResult result = repositorySystem.resolveArtifact(session.getRepositorySession(), request);
        return result.getArtifact();
    }

    /**
     * Resolve given plugin artifact.
     *
     * @param plugin a plugin to resolve
     * @return resolved artifact
     * @throws ArtifactResolutionException if the artifact could not be resolved
     */
    public Artifact resolvePlugin(Plugin plugin) throws ArtifactResolutionException {
        MavenSession session = mavenSessionProvider.get();
        Artifact artifact = toArtifact(plugin);
        return resolveArtifact(artifact, session.getCurrentProject().getRemotePluginRepositories());
    }

    /**
     * Resolve transitive dependencies for artifact.
     *
     * @param artifact     an artifact to resolve
     * @param repositories remote repositories list
     * @return list of transitive dependencies for artifact
     * @throws DependencyResolutionException if the dependency tree could not be built or any dependency artifact could
     *                                       not be resolved
     */
    public List<Artifact> resolveDependencies(Artifact artifact, List<RemoteRepository> repositories)
            throws DependencyResolutionException {
        return resolveDependencies(artifact, null, repositories);
    }

    /**
     * Resolve transitive dependencies for artifact.
     *
     * @param artifact an artifact to resolve
     * @param dependencies a list of additional dependencies for artifact
     * @param repositories remote repositories list
     * @return list of transitive dependencies for artifact
     * @throws DependencyResolutionException if the dependency tree could not be built or any dependency artifact could
     *                                       not be resolved
     */
    public List<Artifact> resolveDependencies(
            Artifact artifact, List<Dependency> dependencies, List<RemoteRepository> repositories)
            throws DependencyResolutionException {
        MavenSession session = mavenSessionProvider.get();

        CollectRequest collectRequest = new CollectRequest(new Dependency(artifact, null), dependencies, repositories);
        DependencyRequest request = new DependencyRequest(collectRequest, null);

        DependencyResult result = repositorySystem.resolveDependencies(session.getRepositorySession(), request);
        return result.getArtifactResults().stream()
                .map(ArtifactResult::getArtifact)
                .collect(Collectors.toList());
    }

    /**
     * Resolve transitive dependencies for artifact with managed dependencies.
     *
     * @param rootArtifact a root artifact to resolve
     * @param dependencies a list of dependencies for artifact
     * @param managedDependencies  a list of managed dependencies for artifact
     * @param remoteProjectRepositories remote repositories list
     * @return Resolved dependencies
     * @throws DependencyResolutionException if the dependency tree could not be built or any dependency artifact could
     *                                       not be resolved
     */
    public List<Artifact> resolveDependenciesForArtifact(
            Artifact rootArtifact,
            List<Dependency> dependencies,
            List<Dependency> managedDependencies,
            List<RemoteRepository> remoteProjectRepositories)
            throws DependencyResolutionException {
        MavenSession session = mavenSessionProvider.get();

        CollectRequest collectRequest =
                new CollectRequest(dependencies, managedDependencies, remoteProjectRepositories);
        collectRequest.setRootArtifact(rootArtifact);
        DependencyRequest request = new DependencyRequest(collectRequest, null);
        DependencyResult result = repositorySystem.resolveDependencies(session.getRepositorySession(), request);
        return result.getArtifactResults().stream()
                .map(ArtifactResult::getArtifact)
                .collect(Collectors.toList());
    }

    /**
     * Resolve transitive dependencies for plugin.
     *
     * @param plugin a plugin to resolve
     * @param dependencyFilter a filter to apply to plugin dependencies
     * @return list of transitive dependencies for plugin
     * @throws DependencyResolutionException if the dependency tree could not be built or any dependency artifact could
     *                                       not be resolved
     */
    public List<Artifact> resolveDependencies(
            final Plugin plugin, Predicate<org.apache.maven.model.Dependency> dependencyFilter)
            throws DependencyResolutionException {

        MavenSession session = mavenSessionProvider.get();

        org.eclipse.aether.artifact.Artifact artifact = toArtifact(plugin);
        List<Dependency> pluginDependencies = plugin.getDependencies().stream()
                .filter(dependencyFilter)
                .map(d -> RepositoryUtils.toDependency(
                        d, session.getRepositorySession().getArtifactTypeRegistry()))
                .collect(Collectors.toList());

        return resolveDependencies(
                artifact, pluginDependencies, session.getCurrentProject().getRemoteProjectRepositories());
    }

    private Artifact toArtifact(Plugin plugin) {
        MavenSession session = mavenSessionProvider.get();
        return new DefaultArtifact(
                plugin.getGroupId(),
                plugin.getArtifactId(),
                null,
                "jar",
                plugin.getVersion(),
                session.getRepositorySession().getArtifactTypeRegistry().get("maven-plugin"));
    }

    /**
     * Prepare a remote repositories list for given descriptions.
     *
     * @param repositories remote repositories descriptions
     * @return a list of remote repositories
     */
    public List<RemoteRepository> remoteRepositories(List<String> repositories) {
        MavenSession mavenSession = mavenSessionProvider.get();
        List<RemoteRepository> projectRepositories =
                mavenSession.getCurrentProject().getRemoteProjectRepositories();
        if (repositories == null || repositories.isEmpty()) {
            return projectRepositories;
        }

        List<RemoteRepository> repositoriesList =
                repositories.stream().map(this::prepareRemoteRepository).collect(Collectors.toList());
        repositoriesList =
                repositorySystem.newResolutionRepositories(mavenSession.getRepositorySession(), repositoriesList);

        List<RemoteRepository> result = new ArrayList<>(projectRepositories);
        result.addAll(repositoriesList);
        return result;
    }

    // protected for testing purpose
    protected RemoteRepository prepareRemoteRepository(String repository) {
        String[] items = Objects.requireNonNull(repository, "repository must be not null")
                .split("::");
        String id = "temp";
        String type = null;
        String url;
        switch (items.length) {
            case 3:
                id = items[0];
                type = items[1];
                url = items[2];
                break;
            case 2:
                id = items[0];
                url = items[1];
                break;
            case 1:
                url = items[0];
                break;
            default:
                throw new IllegalArgumentException("Invalid repository: " + repository);
        }

        if (type == null || type.isEmpty()) {
            type = "default";
        }

        MavenSession mavenSession = mavenSessionProvider.get();
        RepositorySystemSession repositorySession = mavenSession.getRepositorySession();

        String checksumPolicy = repositorySession.getChecksumPolicy();
        if (checksumPolicy == null) {
            checksumPolicy = RepositoryPolicy.CHECKSUM_POLICY_WARN;
        }
        String updatePolicy =
                mavenSession.getRequest().isUpdateSnapshots() ? RepositoryPolicy.UPDATE_POLICY_ALWAYS : null;
        RepositoryPolicy repositoryPolicy = new RepositoryPolicy(true, updatePolicy, checksumPolicy);

        RemoteRepository.Builder builder = new RemoteRepository.Builder(id, type, url);
        builder.setReleasePolicy(repositoryPolicy);
        builder.setSnapshotPolicy(repositoryPolicy);

        return builder.build();
    }

    /**
     * Create an artifact based on configuration from Mojo.
     *
     * @param paramArtifact an artifact configuration
     * @return new artifact
     */
    public Artifact createArtifactFromParams(ParamArtifact paramArtifact) {
        Objects.requireNonNull(paramArtifact);
        if (paramArtifact.getArtifact() != null) {
            return createArtifactFromString(paramArtifact.getArtifact());
        } else {
            ArtifactType artifactType = getArtifactType(paramArtifact.getPackaging());
            return new DefaultArtifact(
                    paramArtifact.getGroupId(),
                    paramArtifact.getArtifactId(),
                    paramArtifact.getClassifier(),
                    artifactType.getExtension(),
                    paramArtifact.getVersion(),
                    artifactType);
        }
    }

    private Artifact createArtifactFromString(String artifact) {
        // groupId:artifactId:version[:packaging[:classifier]].
        String[] items = artifact.split(":");
        if (items.length < 3) {
            throw new IllegalArgumentException("Invalid artifact format: " + artifact);
        }

        ArtifactType artifactType = getArtifactType(items.length > 3 ? items[3] : null);
        String classifier = items.length > 4 ? items[4] : null;

        return new DefaultArtifact(items[0], items[1], classifier, artifactType.getExtension(), items[2], artifactType);
    }

    private ArtifactType getArtifactType(String packaging) {
        ArtifactTypeRegistry artifactTypeRegistry =
                mavenSessionProvider.get().getRepositorySession().getArtifactTypeRegistry();
        return artifactTypeRegistry.get(packaging != null ? packaging : "jar");
    }

    /**
     * Retrieve all plugins used in project either in build or reporting section.
     *
     * @param project a maven project
     * @return a collection of plugins
     */
    public Collection<Plugin> getProjectPlugins(MavenProject project) {
        List<Plugin> reportPlugins = Optional.ofNullable(project.getModel())
                .map(ModelBase::getReporting)
                .map(Reporting::getPlugins)
                .orElse(Collections.emptyList())
                .stream()
                .map(p -> toPlugin(p, project))
                .collect(Collectors.toList());

        List<Plugin> projectPlugins = project.getBuild().getPlugins();

        LinkedHashSet<Plugin> result = new LinkedHashSet<>(reportPlugins.size() + projectPlugins.size());
        result.addAll(reportPlugins);
        result.addAll(projectPlugins);
        return result;
    }

    private Plugin toPlugin(ReportPlugin reportPlugin, MavenProject project) {
        // first look in the pluginManagement section
        Plugin plugin = Optional.ofNullable(project.getBuild().getPluginManagement())
                .map(PluginContainer::getPluginsAsMap)
                .orElseGet(Collections::emptyMap)
                .get(reportPlugin.getKey());

        if (plugin == null) {
            plugin = project.getBuild().getPluginsAsMap().get(reportPlugin.getKey());
        }

        if (plugin == null) {
            plugin = new Plugin();
            plugin.setGroupId(reportPlugin.getGroupId());
            plugin.setArtifactId(reportPlugin.getArtifactId());
            plugin.setVersion(reportPlugin.getVersion());
        } else {
            // override the version with the one from the report plugin if specified
            if (reportPlugin.getVersion() != null) {
                plugin.setVersion(reportPlugin.getVersion());
            }
        }

        if (plugin.getVersion() == null) {
            plugin.setVersion("RELEASE");
        }

        return plugin;
    }
}
