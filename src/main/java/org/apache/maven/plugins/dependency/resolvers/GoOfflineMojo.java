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
package org.apache.maven.plugins.dependency.resolvers;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.apache.maven.shared.artifact.filter.collection.ClassifierFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.GroupIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.apache.maven.shared.artifact.filter.resolve.TransformableFilter;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;

/**
 * Goal that resolves all project dependencies, including plugins and reports and their dependencies.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @author Maarten Mulders
 * @author Lisa Hardy
 * @since 2.0
 */
@Mojo(name = "go-offline", threadSafe = true)
public class GoOfflineMojo extends AbstractResolveMojo {
    /**
     * Main entry into mojo. Gets the list of dependencies, resolves all that are not in the Reactor, and iterates
     * through displaying the resolved versions.
     *
     * @throws MojoExecutionException with a message if an error occurs.
     */
    @Override
    protected void doExecute() throws MojoExecutionException {

        try {
            final Set<Artifact> plugins = resolvePluginArtifacts();

            final Set<Artifact> dependencies = resolveDependencyArtifacts();

            if (!isSilent()) {
                for (Artifact artifact : plugins) {
                    this.getLog().info("Resolved plugin: " + DependencyUtil.getFormattedFileName(artifact, false));
                }

                for (Artifact artifact : dependencies) {
                    this.getLog().info("Resolved dependency: " + DependencyUtil.getFormattedFileName(artifact, false));
                }
            }

        } catch (DependencyResolverException | ArtifactFilterException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * This method resolves the dependency artifacts from the project.
     *
     * @return set of resolved dependency artifacts.
     * @throws DependencyResolverException in case of an error while resolving the artifacts.
     * @throws ArtifactFilterException
     */
    protected Set<Artifact> resolveDependencyArtifacts() throws DependencyResolverException, ArtifactFilterException {
        Collection<Dependency> dependencies = getProject().getDependencies();

        dependencies = filterDependencies(dependencies);

        Set<DependableCoordinate> dependableCoordinates = dependencies.stream()
                .map(this::createDependendableCoordinateFromDependency)
                .collect(Collectors.toSet());

        ProjectBuildingRequest buildingRequest = newResolveArtifactProjectBuildingRequest();

        return resolveDependableCoordinate(buildingRequest, dependableCoordinates, "dependencies");
    }

    private Set<Artifact> resolveDependableCoordinate(
            final ProjectBuildingRequest buildingRequest,
            final Collection<DependableCoordinate> dependableCoordinates,
            final String type)
            throws DependencyResolverException {
        final TransformableFilter filter = getTransformableFilter();

        this.getLog().debug("Resolving '" + type + "' with following repositories:");
        for (ArtifactRepository repo : buildingRequest.getRemoteRepositories()) {
            getLog().debug(repo.getId() + " (" + repo.getUrl() + ")");
        }

        final Set<Artifact> results = new HashSet<>();

        for (DependableCoordinate dependableCoordinate : dependableCoordinates) {
            final Iterable<ArtifactResult> artifactResults =
                    getDependencyResolver().resolveDependencies(buildingRequest, dependableCoordinate, filter);

            for (final ArtifactResult artifactResult : artifactResults) {
                results.add(artifactResult.getArtifact());
            }
        }

        return results;
    }

    private TransformableFilter getTransformableFilter() {
        if (this.excludeReactor) {
            return new ExcludeReactorProjectsDependencyFilter(this.reactorProjects, getLog());
        } else {
            return null;
        }
    }

    /**
     * This method resolves the plugin artifacts from the project.
     *
     * @return set of resolved plugin artifacts.
     * @throws DependencyResolverException in case of an error while resolving the artifacts.
     * @throws ArtifactFilterException
     */
    protected Set<Artifact> resolvePluginArtifacts() throws DependencyResolverException, ArtifactFilterException {

        Set<Artifact> plugins = getProject().getPluginArtifacts();
        Set<Artifact> reports = getProject().getReportArtifacts();

        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.addAll(reports);
        artifacts.addAll(plugins);

        final FilterArtifacts filter = getArtifactsFilter();
        artifacts = filter.filter(artifacts);

        Set<DependableCoordinate> dependableCoordinates = artifacts.stream()
                .map(this::createDependendableCoordinateFromArtifact)
                .collect(Collectors.toSet());

        ProjectBuildingRequest buildingRequest = newResolvePluginProjectBuildingRequest();

        return resolveDependableCoordinate(buildingRequest, dependableCoordinates, "plugins");
    }

    private Collection<Dependency> filterDependencies(Collection<Dependency> deps) throws ArtifactFilterException {

        Set<Artifact> artifacts = createArtifactSetFromDependencies(deps);

        final FilterArtifacts filter = getArtifactsFilter();
        artifacts = filter.filter(artifacts);

        return createDependencySetFromArtifacts(artifacts);
    }

    private DependableCoordinate createDependendableCoordinateFromArtifact(final Artifact artifact) {
        final DefaultDependableCoordinate result = new DefaultDependableCoordinate();
        result.setGroupId(artifact.getGroupId());
        result.setArtifactId(artifact.getArtifactId());
        result.setVersion(artifact.getVersion());
        result.setType(artifact.getType());
        result.setClassifier(artifact.getClassifier());

        return result;
    }

    private DependableCoordinate createDependendableCoordinateFromDependency(final Dependency dependency) {
        final DefaultDependableCoordinate result = new DefaultDependableCoordinate();
        result.setGroupId(dependency.getGroupId());
        result.setArtifactId(dependency.getArtifactId());
        result.setVersion(dependency.getVersion());
        result.setType(dependency.getType());
        result.setClassifier(dependency.getClassifier());

        return result;
    }

    private Set<Artifact> createArtifactSetFromDependencies(Collection<Dependency> deps) {
        Set<Artifact> artifacts = new HashSet<>();
        for (Dependency dep : deps) {
            DefaultArtifactHandler handler = new DefaultArtifactHandler(dep.getType());
            artifacts.add(new DefaultArtifact(
                    dep.getGroupId(),
                    dep.getArtifactId(),
                    dep.getVersion(),
                    dep.getScope(),
                    dep.getType(),
                    dep.getClassifier(),
                    handler));
        }
        return artifacts;
    }

    private Collection<Dependency> createDependencySetFromArtifacts(Set<Artifact> artifacts) {
        Set<Dependency> dependencies = new HashSet<>();

        for (Artifact artifact : artifacts) {
            Dependency d = new Dependency();
            d.setGroupId(artifact.getGroupId());
            d.setArtifactId(artifact.getArtifactId());
            d.setVersion(artifact.getVersion());
            d.setType(artifact.getType());
            d.setClassifier(artifact.getClassifier());
            d.setScope(artifact.getScope());
            dependencies.add(d);
        }

        return dependencies;
    }

    @Override
    protected ArtifactsFilter getMarkedArtifactFilter() {
        return null;
    }
}
