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

import java.util.List;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public abstract class AbstractDependencyMojo extends AbstractMojo {

    /**
     * Remote repositories which will be searched for artifacts.
     */
    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true)
    private List<ArtifactRepository> remoteRepositories;

    /**
     * Remote repositories which will be searched for plugins.
     */
    @Parameter(defaultValue = "${project.pluginArtifactRepositories}", readonly = true, required = true)
    private List<ArtifactRepository> remotePluginRepositories;

    /**
     * Contains the full list of projects in the reactor.
     */
    @Parameter(defaultValue = "${reactorProjects}", readonly = true)
    protected List<MavenProject> reactorProjects;

    /**
     * The Maven session.
     */
    protected final MavenSession session;

    /**
     * Skip plugin execution completely.
     *
     * @since 2.7
     */
    @Parameter(property = "mdep.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Skip plugin execution only during incremental builds (e.g. triggered from M2E).
     *
     * @see #skip
     * @since 3.4.0
     */
    @Parameter(defaultValue = "false")
    private boolean skipDuringIncrementalBuild;

    /**
     * For IDE build support.
     */
    private final BuildContext buildContext;

    /**
     * POM.
     */
    private final MavenProject project;

    protected AbstractDependencyMojo(MavenSession session, BuildContext buildContext, MavenProject project) {
        this.session = session;
        this.buildContext = buildContext;
        this.project = project;
    }

    // Mojo methods -----------------------------------------------------------

    /*
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (isSkip()) {
            getLog().info("Skipping plugin execution");
            return;
        }

        doExecute();
    }

    /**
     * @throws MojoExecutionException {@link MojoExecutionException}
     * @throws MojoFailureException {@link MojoFailureException}
     */
    protected abstract void doExecute() throws MojoExecutionException, MojoFailureException;

    /**
     * @return returns a new ProjectBuildingRequest populated from the current session and the current project remote
     *         repositories, used to resolve artifacts
     */
    public ProjectBuildingRequest newResolveArtifactProjectBuildingRequest() {
        return newProjectBuildingRequest(remoteRepositories);
    }

    /**
     * @return returns a new ProjectBuildingRequest populated from the current session and the current project remote
     *         repositories, used to resolve plugins
     */
    protected ProjectBuildingRequest newResolvePluginProjectBuildingRequest() {
        return newProjectBuildingRequest(remotePluginRepositories);
    }

    private ProjectBuildingRequest newProjectBuildingRequest(List<ArtifactRepository> repositories) {
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

        buildingRequest.setRemoteRepositories(repositories);

        return buildingRequest;
    }

    /**
     * @return returns the project
     */
    public MavenProject getProject() {
        return this.project;
    }

    /**
     * @return {@link #skip}
     */
    public boolean isSkip() {
        if (skipDuringIncrementalBuild && buildContext.isIncremental()) {
            return true;
        }
        return skip;
    }

    /**
     * @param skip {@link #skip}
     */
    public void setSkip(boolean skip) {
        this.skip = skip;
    }
}
