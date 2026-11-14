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

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.fromDependencies.AbstractDependencyFilterMojo;
import org.apache.maven.plugins.dependency.utils.AnnotationProcessorPaths;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.plugins.dependency.utils.filters.GroupIdFilter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.apache.maven.shared.artifact.filter.collection.ClassifierFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.sonatype.plexus.build.incremental.BuildContext;

import static java.util.Optional.ofNullable;

/**
 * Goal that resolves all project dependencies, including plugins and reports and their dependencies. It also
 * resolves the {@code annotationProcessorPaths} configured for the {@code maven-compiler-plugin}, in the plugin
 * configuration and in each execution, together with their transitive dependencies. The scope filters of this goal
 * treat these paths as compile scope dependencies.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @author Maarten Mulders
 * @author Lisa Hardy
 * @since 2.0
 */
@Mojo(name = "go-offline", threadSafe = true)
public class GoOfflineMojo extends AbstractDependencyFilterMojo {

    /**
     * Don't resolve plugins and artifacts that are in the current reactor.
     *
     * @since 2.7
     */
    @Parameter(property = "excludeReactor", defaultValue = "true")
    protected boolean excludeReactor;

    @Inject
    public GoOfflineMojo(
            MavenSession session,
            BuildContext buildContext,
            MavenProject project,
            ResolverUtil resolverUtil,
            ProjectBuilder projectBuilder,
            ArtifactHandlerManager artifactHandlerManager) {
        super(session, buildContext, project, resolverUtil, projectBuilder, artifactHandlerManager);
    }

    /**
     * Main entry into mojo. Gets the list of dependencies, resolves all that are not in the Reactor, and iterates
     * through displaying the resolved versions.
     *
     * @throws MojoExecutionException with a message if an error occurs
     */
    @Override
    protected void doExecute() throws MojoExecutionException {

        try {
            final Set<Plugin> plugins = getProjectPlugins();

            for (Plugin plugin : plugins) {
                org.eclipse.aether.artifact.Artifact artifact =
                        getResolverUtil().resolvePlugin(plugin);

                getLog().debug("Resolved plugin: "
                        + DependencyUtil.getFormattedFileName(RepositoryUtils.toArtifact(artifact), false));
                if (!excludeTransitive) {
                    getLog().debug("Resolved plugin dependency:");
                    List<org.eclipse.aether.artifact.Artifact> artifacts =
                            getResolverUtil().resolveDependencies(plugin, getDependencyFilter());
                    for (org.eclipse.aether.artifact.Artifact a : artifacts) {
                        getLog().debug("      "
                                + DependencyUtil.getFormattedFileName(RepositoryUtils.toArtifact(a), false));
                    }
                }
            }

            final List<org.eclipse.aether.artifact.Artifact> dependencies = resolveDependencyArtifacts();

            for (org.eclipse.aether.artifact.Artifact artifact : dependencies) {
                getLog().debug("Resolved dependency: "
                        + DependencyUtil.getFormattedFileName(RepositoryUtils.toArtifact(artifact), false));
            }

            for (org.eclipse.aether.artifact.Artifact artifact : resolveAnnotationProcessorPaths()) {
                getLog().debug("Resolved annotation processor path: "
                        + DependencyUtil.getFormattedFileName(RepositoryUtils.toArtifact(artifact), false));
            }

        } catch (ArtifactFilterException
                | ArtifactResolutionException
                | DependencyResolutionException
                | ArtifactDescriptorException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private Predicate<Dependency> getDependencyFilter() {
        if (excludeReactor) {
            return new ExcludeReactorProjectsDependencyFilter(session.getProjects());
        } else {
            return __ -> true;
        }
    }

    /**
     * This method resolves the dependency artifacts from the project.
     *
     * @return lis of resolved dependency artifacts
     * @throws ArtifactFilterException in case of an error while filtering the artifacts
     * @throws DependencyResolutionException in case of an error while resolving the artifacts
     */
    protected List<org.eclipse.aether.artifact.Artifact> resolveDependencyArtifacts()
            throws ArtifactFilterException, DependencyResolutionException {
        Collection<Dependency> dependencies = getProject().getDependencies();

        dependencies = filterDependencies(dependencies);

        Predicate<Dependency> excludeReactorProjectsDependencyFilter = d -> true;
        if (this.excludeReactor) {
            excludeReactorProjectsDependencyFilter = new ExcludeReactorProjectsDependencyFilter(session.getProjects());
        }

        ArtifactTypeRegistry artifactTypeRegistry =
                session.getRepositorySession().getArtifactTypeRegistry();

        List<org.eclipse.aether.graph.Dependency> dependableCoordinates = dependencies.stream()
                .filter(excludeReactorProjectsDependencyFilter)
                .map(d -> RepositoryUtils.toDependency(d, artifactTypeRegistry))
                .collect(Collectors.toList());

        List<org.eclipse.aether.graph.Dependency> managedDependencies = managedDependencies(artifactTypeRegistry);

        return getResolverUtil()
                .resolveDependenciesForArtifact(
                        RepositoryUtils.toArtifact(getProject().getArtifact()),
                        dependableCoordinates,
                        managedDependencies,
                        getProject().getRemoteProjectRepositories());
    }

    /**
     * Resolves the {@code annotationProcessorPaths} of the {@code maven-compiler-plugin}, which Maven 3 resolves
     * only when it compiles. Each configuration of the compiler plugin is resolved on its own, as the compiler does,
     * and the results are merged. The artifact filters of this goal apply to the paths; the transitive dependencies
     * of a path are resolved unless {@code excludeTransitive} is set.
     */
    private List<org.eclipse.aether.artifact.Artifact> resolveAnnotationProcessorPaths()
            throws ArtifactFilterException, ArtifactResolutionException, DependencyResolutionException {
        Set<String> warnings = new LinkedHashSet<>();
        List<AnnotationProcessorPaths.Group> groups = AnnotationProcessorPaths.collect(
                getProject().getBuild().getPlugins(), getProject().getDependencyManagement(), warnings::add);
        warnings.forEach(getLog()::warn);

        ArtifactTypeRegistry artifactTypeRegistry =
                session.getRepositorySession().getArtifactTypeRegistry();
        Map<String, org.eclipse.aether.artifact.Artifact> result = new LinkedHashMap<>();
        for (AnnotationProcessorPaths.Group group : groups) {
            List<org.eclipse.aether.graph.Dependency> coordinates = new ArrayList<>();
            for (Dependency path : group.getPaths()) {
                if (!filterDependencies(Collections.singletonList(path)).isEmpty()) {
                    coordinates.add(RepositoryUtils.toDependency(path, artifactTypeRegistry));
                }
            }
            if (coordinates.isEmpty()) {
                continue;
            }
            List<org.eclipse.aether.artifact.Artifact> resolved;
            if (excludeTransitive) {
                resolved = getResolverUtil()
                        .resolveArtifacts(
                                coordinates.stream()
                                        .map(org.eclipse.aether.graph.Dependency::getArtifact)
                                        .collect(Collectors.toList()),
                                getProject().getRemoteProjectRepositories())
                        .stream()
                        .map(ArtifactResult::getArtifact)
                        .collect(Collectors.toList());
            } else {
                resolved = getResolverUtil()
                        .resolveDependenciesForArtifact(
                                RepositoryUtils.toArtifact(getProject().getArtifact()),
                                coordinates,
                                group.isUseDependencyManagement() ? managedDependencies(artifactTypeRegistry) : null,
                                getProject().getRemoteProjectRepositories());
            }
            for (org.eclipse.aether.artifact.Artifact artifact : resolved) {
                result.putIfAbsent(artifact.toString(), artifact);
            }
        }
        return new ArrayList<>(result.values());
    }

    private List<org.eclipse.aether.graph.Dependency> managedDependencies(ArtifactTypeRegistry artifactTypeRegistry) {
        return ofNullable(getProject().getDependencyManagement())
                .map(DependencyManagement::getDependencies)
                .map(list -> list.stream()
                        .map(d -> RepositoryUtils.toDependency(d, artifactTypeRegistry))
                        .collect(Collectors.toList()))
                .orElse(null);
    }

    /**
     * This method retrieve plugins list from the project.
     *
     * @return set of plugin used in project
     */
    private Set<Plugin> getProjectPlugins() {
        Predicate<Plugin> pluginsFilter = new PluginsIncludeExcludeFilter(
                toList(includeGroupIds),
                toList(excludeGroupIds),
                toList(includeArtifactIds),
                toList(excludeArtifactIds));

        Predicate<Plugin> reactorExclusionFilter = plugin -> true;
        if (excludeReactor) {
            reactorExclusionFilter = new PluginsReactorExcludeFilter(session.getProjects());
        }

        return getResolverUtil().getProjectPlugins(getProject()).stream()
                .filter(reactorExclusionFilter)
                .filter(pluginsFilter)
                .collect(Collectors.toSet());
    }

    private List<String> toList(String list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(DependencyUtil.cleanToBeTokenizedString(list).split(","));
    }

    private Collection<Dependency> filterDependencies(Collection<Dependency> deps) throws ArtifactFilterException {

        Set<Artifact> artifacts = createArtifactSetFromDependencies(deps);

        final FilterArtifacts filter = getArtifactsFilter();
        artifacts = filter.filter(artifacts);

        return createDependencySetFromArtifacts(artifacts);
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

    /**
     * @return {@link FilterArtifacts}
     */
    // TODO: refactor this to use Resolver API filters
    protected FilterArtifacts getArtifactsFilter() {
        final FilterArtifacts filter = new FilterArtifacts();

        if (excludeReactor) {
            filter.addFilter(new ExcludeReactorProjectsArtifactFilter(session.getProjects(), getLog()));
        }

        filter.addFilter(new ScopeFilter(
                DependencyUtil.cleanToBeTokenizedString(this.includeScope),
                DependencyUtil.cleanToBeTokenizedString(this.excludeScope)));

        filter.addFilter(new TypeFilter(
                DependencyUtil.cleanToBeTokenizedString(this.includeTypes),
                DependencyUtil.cleanToBeTokenizedString(this.excludeTypes)));

        filter.addFilter(new ClassifierFilter(
                DependencyUtil.cleanToBeTokenizedString(this.includeClassifiers),
                DependencyUtil.cleanToBeTokenizedString(this.excludeClassifiers)));

        filter.addFilter(new GroupIdFilter(
                DependencyUtil.cleanToBeTokenizedString(this.includeGroupIds),
                DependencyUtil.cleanToBeTokenizedString(this.excludeGroupIds),
                this.groupIdsExactMatch));

        filter.addFilter(new ArtifactIdFilter(
                DependencyUtil.cleanToBeTokenizedString(this.includeArtifactIds),
                DependencyUtil.cleanToBeTokenizedString(this.excludeArtifactIds)));

        return filter;
    }

    @Override
    protected ArtifactsFilter getMarkedArtifactFilter() {
        return null;
    }
}
