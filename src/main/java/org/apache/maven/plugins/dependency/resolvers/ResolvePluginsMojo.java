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

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.AbstractDependencyMojo;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Goal that resolves all project plugins and reports and their dependencies.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 2.0
 */
@Mojo(name = "resolve-plugins", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class ResolvePluginsMojo extends AbstractDependencyMojo {

    /**
     * If specified, this parameter causes the dependencies to be written to the path specified instead of
     * the console.
     *
     * @since 2.0
     */
    @Parameter(property = "outputFile")
    protected File outputFile;

    /**
     * If we should exclude transitive dependencies.
     * This means only the plugin artifacts itself will be resolved not plugin dependencies.
     *
     * @since 2.0
     */
    @Parameter(property = "excludeTransitive", defaultValue = "false")
    protected boolean excludeTransitive;

    /**
     * List of artifact IDs to exclude.
     *
     * @since 2.0
     */
    @Parameter(property = "excludeArtifactIds", defaultValue = "")
    protected List<String> excludeArtifactIds;

    /**
     * List of artifact IDs to include. Empty list indicates include everything (default).
     *
     * @since 2.0
     */
    @Parameter(property = "includeArtifactIds", defaultValue = "")
    protected List<String> includeArtifactIds;

    /**
     * List of group IDs to exclude.
     *
     * @since 2.0
     */
    @Parameter(property = "excludeGroupIds", defaultValue = "")
    protected List<String> excludeGroupIds;

    /**
     * List of group IDs to include. Empty list indicates include everything (default).
     *
     * @since 2.0
     */
    @Parameter(property = "includeGroupIds", defaultValue = "")
    protected List<String> includeGroupIds;

    /**
     * Whether to append outputs into the output file or overwrite it.
     *
     * @since 2.2
     */
    @Parameter(property = "appendOutput", defaultValue = "false")
    protected boolean appendOutput;

    /**
     * Don't resolve plugins that are in the current reactor.
     *
     * @since 2.7
     */
    @Parameter(property = "excludeReactor", defaultValue = "true")
    protected boolean excludeReactor;

    /**
     * The encoding of the output file.
     *
     * @since 3.2.0
     */
    @Parameter(property = "outputEncoding", defaultValue = "${project.reporting.outputEncoding}")
    private String outputEncoding;

    /**
     * Output absolute filename for resolved artifacts.
     *
     * @since 2.0
     */
    @Parameter(property = "outputAbsoluteArtifactFilename", defaultValue = "false")
    private boolean outputAbsoluteArtifactFilename;

    /**
     * Include parent poms of the plugins and of their dependencies in the resolution list.
     *
     * @since 3.12.0
     */
    @Parameter(property = "includeParents", defaultValue = "false")
    boolean includeParents;

    /**
     * Same value as {@code ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL}: only the parent chain is needed, so
     * invalid but readable POMs must not fail the build.
     */
    private static final int VALIDATION_LEVEL_MINIMAL = 0;

    private final ResolverUtil resolverUtil;

    private final ProjectBuilder projectBuilder;

    @Inject
    public ResolvePluginsMojo(
            MavenSession session,
            BuildContext buildContext,
            MavenProject project,
            ResolverUtil resolverUtil,
            ProjectBuilder projectBuilder) {
        super(session, buildContext, project);
        this.resolverUtil = resolverUtil;
        this.projectBuilder = projectBuilder;
    }

    /**
     * Main entry into mojo. Gets the list of dependencies and iterates through displaying the resolved version.
     *
     * @throws MojoExecutionException with a message if an error occurs
     */
    @Override
    protected void doExecute() throws MojoExecutionException {
        try {
            final Set<Plugin> plugins = getProjectPlugins();

            StringBuilder sb = new StringBuilder();
            sb.append(System.lineSeparator());
            sb.append("The following plugins have been resolved:");
            sb.append(System.lineSeparator());
            if (plugins.isEmpty()) {
                sb.append("   none");
                sb.append(System.lineSeparator());
            } else {
                for (Plugin plugin : plugins) {
                    Artifact pluginArtifact = resolverUtil.resolvePlugin(plugin);
                    String artifactFilename = null;
                    if (outputAbsoluteArtifactFilename) {
                        // we want to print the absolute file name here
                        artifactFilename = Optional.ofNullable(pluginArtifact.getFile())
                                .map(File::getAbsoluteFile)
                                .map(File::getPath)
                                .orElse(null);
                    }

                    String id = pluginArtifact.toString();
                    sb.append("   ")
                            .append(id)
                            .append(outputAbsoluteArtifactFilename ? ":" + artifactFilename : "")
                            .append(System.lineSeparator());

                    Set<Artifact> parents = new LinkedHashSet<>();
                    if (includeParents) {
                        addParentArtifacts(pluginArtifact, getProject().getPluginArtifactRepositories(), parents);
                    }

                    if (!excludeTransitive) {
                        List<Artifact> pluginDependencies =
                                resolverUtil.resolveDependencies(plugin, getDependencyFilter());
                        if (includeParents) {
                            for (Artifact artifact : pluginDependencies) {
                                addParentArtifacts(artifact, getProject().getRemoteArtifactRepositories(), parents);
                            }
                        }
                        for (Artifact artifact : pluginDependencies) {
                            artifactFilename = null;
                            if (outputAbsoluteArtifactFilename) {
                                // we want to print the absolute file name here
                                artifactFilename = Optional.ofNullable(artifact.getFile())
                                        .map(File::getAbsoluteFile)
                                        .map(File::getPath)
                                        .orElse(null);
                            }

                            id = artifact.toString();
                            sb.append("      ")
                                    .append(id)
                                    .append(outputAbsoluteArtifactFilename ? ":" + artifactFilename : "")
                                    .append(System.lineSeparator());
                        }
                    }

                    for (Artifact parent : parents) {
                        String parentFilename = null;
                        if (outputAbsoluteArtifactFilename) {
                            parentFilename = Optional.ofNullable(parent.getFile())
                                    .map(File::getAbsoluteFile)
                                    .map(File::getPath)
                                    .orElse(null);
                        }
                        sb.append("      ")
                                .append(parent)
                                .append(outputAbsoluteArtifactFilename ? ":" + parentFilename : "")
                                .append(System.lineSeparator());
                    }
                }
                sb.append(System.lineSeparator());

                String output = sb.toString();
                if (outputFile == null) {
                    DependencyUtil.log(output, getLog());
                } else {
                    String encoding = Objects.toString(outputEncoding, "UTF-8");
                    DependencyUtil.write(output, outputFile, appendOutput, encoding);
                }
            }
        } catch (IOException
                | ArtifactResolutionException
                | DependencyResolutionException
                | ArtifactDescriptorException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * Resolves the chain of parent poms of the given artifact and adds them to {@code parents}.
     */
    private void addParentArtifacts(Artifact artifact, List<ArtifactRepository> repositories, Set<Artifact> parents)
            throws MojoExecutionException, ArtifactResolutionException, ArtifactDescriptorException {
        MavenProject current;
        try {
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setProcessPlugins(false);
            buildingRequest.setValidationLevel(VALIDATION_LEVEL_MINIMAL);
            buildingRequest.setRemoteRepositories(repositories);
            current = projectBuilder
                    .build(RepositoryUtils.toArtifact(artifact), buildingRequest)
                    .getProject();
        } catch (ProjectBuildingException e) {
            throw new MojoExecutionException("Could not build project for " + artifact, e);
        }

        while (current.hasParent()) {
            current = current.getParent();
            Artifact parent = resolverUtil.resolveArtifact(
                    RepositoryUtils.toArtifact(current.getArtifact()), current.getRemoteProjectRepositories());
            if (!parents.add(parent)) {
                // the rest of the chain is already listed
                break;
            }
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
     * This return plugin list of the project after applying the include/exclude filters.
     *
     * @return set of project plugin
     */
    private Set<Plugin> getProjectPlugins() {

        Predicate<Plugin> pluginsFilter = new PluginsIncludeExcludeFilter(
                includeGroupIds, excludeGroupIds, includeArtifactIds, excludeArtifactIds);

        Predicate<Plugin> reactorExclusionFilter = plugin -> true;
        if (excludeReactor) {
            reactorExclusionFilter = new PluginsReactorExcludeFilter(session.getProjects());
        }

        return resolverUtil.getProjectPlugins(getProject()).stream()
                .filter(reactorExclusionFilter)
                .filter(pluginsFilter)
                .collect(Collectors.toSet());
    }
}
