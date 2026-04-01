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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.pom.DependencyCoordinates;
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

    /**
     * Resolves the target project for dependency operations. If {@code module} is non-null,
     * searches the reactor projects for a matching artifactId.
     *
     * @param module the module artifactId to target, or {@code null} for the current project
     * @return the resolved target project
     * @throws MojoFailureException if the module is not found in the reactor
     */
    protected MavenProject resolveTargetProject(String module) throws MojoFailureException {
        if (module == null || module.isEmpty()) {
            return getProject();
        }
        List<MavenProject> reactorProjects = session.getProjects();
        if (reactorProjects == null || reactorProjects.isEmpty()) {
            throw new MojoFailureException(
                    "Module '" + module + "' cannot be resolved: no reactor projects available.");
        }
        for (MavenProject p : reactorProjects) {
            if (module.equals(p.getArtifactId())) {
                return p;
            }
        }
        throw new MojoFailureException("Module '" + module + "' not found in the reactor. Available modules: "
                + getModuleNames(reactorProjects));
    }

    /**
     * Checks whether the dependency exists in the project's declared (original) model
     * after property interpolation, but before inheritance merging.
     * This catches dependencies declared with property references like {@code ${project.groupId}}
     * without false-positiving on inherited dependencies from a parent POM.
     */
    protected static boolean existsInResolvedModel(
            MavenProject project, DependencyCoordinates coords, boolean managed) {
        List<Dependency> deps;
        org.apache.maven.model.Model originalModel = project.getOriginalModel();
        if (managed) {
            DependencyManagement depMgmt = originalModel != null ? originalModel.getDependencyManagement() : null;
            deps = depMgmt != null ? depMgmt.getDependencies() : null;
        } else {
            deps = originalModel != null ? originalModel.getDependencies() : null;
        }
        if (deps == null) {
            return false;
        }
        String searchType = coords.getType() != null ? coords.getType() : "jar";
        String searchClassifier = coords.getClassifier() != null ? coords.getClassifier() : "";
        for (Dependency dep : deps) {
            if (coords.getGroupId().equals(dep.getGroupId())
                    && coords.getArtifactId().equals(dep.getArtifactId())) {
                String depType = dep.getType() != null ? dep.getType() : "jar";
                String depClassifier = dep.getClassifier() != null ? dep.getClassifier() : "";
                if (searchType.equals(depType) && searchClassifier.equals(depClassifier)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getModuleNames(List<MavenProject> projects) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < projects.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(projects.get(i).getArtifactId());
        }
        sb.append("]");
        return sb.toString();
    }
}
