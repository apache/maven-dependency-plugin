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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.apache.maven.shared.artifact.filter.collection.ClassifierFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.GroupIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.sonatype.plexus.build.incremental.BuildContext;

import static java.util.Optional.ofNullable;

/**
 * Goal that resolves all project dependencies, including plugins and reports and their dependencies.
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
    // CHECKSTYLE_OFF: ParameterNumber
    public GoOfflineMojo(
            MavenSession session,
            BuildContext buildContext,
            MavenProject project,
            ResolverUtil resolverUtil,
            ProjectBuilder projectBuilder,
            ArtifactHandlerManager artifactHandlerManager) {
        super(session, buildContext, project, resolverUtil, projectBuilder, artifactHandlerManager);
    }
    // CHECKSTYLE_ON: ParameterNumber

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

                logMessage("Resolved plugin: "
                        + DependencyUtil.getFormattedFileName(RepositoryUtils.toArtifact(artifact), false));
                if (!excludeTransitive) {
                    logMessage("Resolved plugin dependency:");
                    List<org.eclipse.aether.artifact.Artifact> artifacts =
                            getResolverUtil().resolveDependencies(plugin);
                    for (org.eclipse.aether.artifact.Artifact a : artifacts) {
                        logMessage(
                                "      " + DependencyUtil.getFormattedFileName(RepositoryUtils.toArtifact(a), false));
                    }
                }
            }

            final List<org.eclipse.aether.artifact.Artifact> dependencies = resolveDependencyArtifacts();

            for (org.eclipse.aether.artifact.Artifact artifact : dependencies) {
                logMessage("Resolved dependency: "
                        + DependencyUtil.getFormattedFileName(RepositoryUtils.toArtifact(artifact), false));
            }

        } catch (ArtifactFilterException | ArtifactResolutionException | DependencyResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void logMessage(String message) {
        if (isSilent()) {
            getLog().debug(message);
        } else {
            getLog().info(message);
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
            excludeReactorProjectsDependencyFilter = new ExcludeReactorProjectsDependencyFilter(this.reactorProjects);
        }

        ArtifactTypeRegistry artifactTypeRegistry =
                session.getRepositorySession().getArtifactTypeRegistry();

        List<org.eclipse.aether.graph.Dependency> dependableCoordinates = dependencies.stream()
                .filter(excludeReactorProjectsDependencyFilter)
                .map(d -> RepositoryUtils.toDependency(d, artifactTypeRegistry))
                .collect(Collectors.toList());

        List<org.eclipse.aether.graph.Dependency> managedDependencies = ofNullable(
                        getProject().getDependencyManagement())
                .map(DependencyManagement::getDependencies)
                .map(list -> list.stream()
                        .map(d -> RepositoryUtils.toDependency(d, artifactTypeRegistry))
                        .collect(Collectors.toList()))
                .orElse(null);

        return getResolverUtil()
                .resolveDependenciesForArtifact(
                        RepositoryUtils.toArtifact(getProject().getArtifact()),
                        dependableCoordinates,
                        managedDependencies,
                        getProject().getRemoteProjectRepositories());
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
            filter.addFilter(new ExcludeReactorProjectsArtifactFilter(reactorProjects, getLog()));
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
                DependencyUtil.cleanToBeTokenizedString(this.excludeGroupIds)));

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
