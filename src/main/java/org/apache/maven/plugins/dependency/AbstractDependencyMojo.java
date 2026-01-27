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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.utils.DependencySilentLog;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public abstract class AbstractDependencyMojo extends AbstractMojo {

    /**
     * The Maven session.
     */
    protected final MavenSession session;

    /**
     * If the plugin should be silent.
     *
     * @since 2.0
     * @deprecated to be removed in 4.0; use -q command line option instead
     */
    @Deprecated
    @Parameter(property = "silent", defaultValue = "false")
    private boolean silent;

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

    /**
     * @return {@link #silent}
     * @deprecated to be removed in 4.0
     */
    @Deprecated
    protected final boolean isSilent() {
        return silent;
    }

    /**
     * @param silent {@link #silent}
     * @deprecated to be removed in 4.0; no API replacement, use -q command line option instead
     */
    @Deprecated
    public void setSilent(boolean silent) {
        this.silent = silent;
        if (silent) {
            setLog(new DependencySilentLog());
        } else if (getLog() instanceof DependencySilentLog) {
            setLog(new SystemStreamLog());
        }
    }
}
