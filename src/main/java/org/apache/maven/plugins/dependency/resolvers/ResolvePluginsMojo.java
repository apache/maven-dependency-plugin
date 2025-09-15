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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.ModelBase;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginContainer;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.AbstractDependencyMojo;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
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

    private final ResolverUtil resolverUtil;

    @Inject
    public ResolvePluginsMojo(
            MavenSession session, BuildContext buildContext, MavenProject project, ResolverUtil resolverUtil) {
        super(session, buildContext, project);
        this.resolverUtil = resolverUtil;
    }

    /**
     * Main entry into mojo. Gets the list of dependencies and iterates through displaying the resolved version.
     *
     * @throws MojoExecutionException with a message if an error occurs
     */
    @Override
    protected void doExecute() throws MojoExecutionException {
        try {
            // ideally this should either be DependencyCoordinates or DependencyNode
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

                    if (!excludeTransitive) {
                        for (Artifact artifact : resolverUtil.resolveDependencies(plugin)) {
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
        } catch (IOException | ArtifactResolutionException | DependencyResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
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

        List<Plugin> reportPlugins = Optional.ofNullable(getProject().getModel())
                .map(ModelBase::getReporting)
                .map(Reporting::getPlugins)
                .orElse(Collections.emptyList())
                .stream()
                .map(this::toPlugin)
                .collect(Collectors.toList());

        List<Plugin> projectPlugins = getProject().getBuild().getPlugins();

        return new LinkedHashSet<Plugin>(reportPlugins.size() + projectPlugins.size()) {
            {
                addAll(reportPlugins);
                addAll(projectPlugins);
            }
        }.stream().filter(reactorExclusionFilter).filter(pluginsFilter).collect(Collectors.toSet());
    }

    private Plugin toPlugin(ReportPlugin reportPlugin) {
        // first look in the pluginManagement section
        Plugin plugin = Optional.ofNullable(getProject().getBuild().getPluginManagement())
                .map(PluginContainer::getPluginsAsMap)
                .orElseGet(Collections::emptyMap)
                .get(reportPlugin.getKey());

        if (plugin == null) {
            plugin = getProject().getBuild().getPluginsAsMap().get(reportPlugin.getKey());
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
