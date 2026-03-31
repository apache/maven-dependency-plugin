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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.pom.DependencyCoordinates;
import org.apache.maven.plugins.dependency.pom.PomEditor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Removes a dependency from the project's {@code pom.xml}.
 * Supports removing from {@code <dependencies>} or {@code <dependencyManagement>}.
 *
 * <p>Matching uses groupId, artifactId, type, and classifier for precision.
 * If the dependency exists in Maven's resolved model but uses property references
 * in the raw POM, a clear error directs the user to edit manually.</p>
 * When removing a managed dependency from a parent POM, warns if child modules
 * reference it without an explicit version.
 *
 * @since 3.11.0
 */
@Mojo(name = "remove", requiresProject = true, threadSafe = true)
public class RemoveDependencyMojo extends AbstractDependencyMojo {

    /**
     * The dependency's groupId. Ignored if {@link #gav} is used.
     */
    @Parameter(property = "groupId")
    private String groupId;

    /**
     * The dependency's artifactId. Ignored if {@link #gav} is used.
     */
    @Parameter(property = "artifactId")
    private String artifactId;

    /**
     * Shorthand coordinates: {@code groupId:artifactId[:version[:scope[:type[:classifier]]]]}.
     * Only groupId and artifactId are required. Type and classifier, if provided,
     * are used for precise matching when multiple dependency variants exist
     * (e.g., jar vs test-jar).
     */
    @Parameter(property = "gav")
    private String gav;

    /**
     * When {@code true}, remove from {@code <dependencyManagement>} instead of {@code <dependencies>}.
     */
    @Parameter(property = "managed", defaultValue = "false")
    private boolean managed;

    /**
     * Target a specific child module by artifactId.
     */
    @Parameter(property = "module")
    private String module;

    @Inject
    public RemoveDependencyMojo(MavenSession session, BuildContext buildContext, MavenProject project) {
        super(session, buildContext, project);
    }

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        DependencyCoordinates coords = resolveCoordinates();
        MavenProject targetProject = resolveTargetProject();
        File pomFile = targetProject.getFile();

        // Safety check for managed dependency removal in parent POM
        if (managed
                && targetProject.getModules() != null
                && !targetProject.getModules().isEmpty()) {
            checkChildModuleDependencies(targetProject, coords.getGroupId(), coords.getArtifactId());
        }

        try {
            PomEditor editor = PomEditor.load(pomFile);
            boolean removed = editor.removeDependency(
                    coords.getGroupId(), coords.getArtifactId(), coords.getType(), coords.getClassifier(), managed);

            if (!removed) {
                // Cross-reference with resolved model to detect property-interpolated coords
                if (existsInResolvedModel(targetProject, coords, managed)) {
                    String section = managed ? "<dependencyManagement>" : "<dependencies>";
                    throw new MojoFailureException("Dependency " + coords.getGroupId() + ":"
                            + coords.getArtifactId()
                            + " exists in " + section + " but uses property references in the POM. "
                            + "Please remove it manually.");
                }
                String section = managed ? "<dependencyManagement>" : "<dependencies>";
                throw new MojoFailureException("Dependency " + coords.getGroupId() + ":" + coords.getArtifactId()
                        + " not found in " + section + ".");
            }

            editor.save();
            getLog().info("Removed dependency " + coords.getGroupId() + ":" + coords.getArtifactId() + " from "
                    + pomFile.getName());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to modify POM file: " + pomFile, e);
        }
    }

    private DependencyCoordinates resolveCoordinates() throws MojoFailureException {
        DependencyCoordinates coords;

        if (gav != null && !gav.isEmpty()) {
            try {
                coords = DependencyCoordinates.parse(gav);
            } catch (IllegalArgumentException e) {
                throw new MojoFailureException(e.getMessage());
            }
        } else if (groupId != null && artifactId != null) {
            coords = new DependencyCoordinates(groupId, artifactId);
        } else {
            throw new MojoFailureException(
                    "You must specify either -Dgav=groupId:artifactId " + "or both -DgroupId=... and -DartifactId=...");
        }

        try {
            coords.validate();
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage());
        }

        return coords;
    }

    private MavenProject resolveTargetProject() throws MojoFailureException {
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
        throw new MojoFailureException("Module '" + module + "' not found in the reactor.");
    }

    private void checkChildModuleDependencies(MavenProject parentProject, String depGroupId, String depArtifactId)
            throws MojoExecutionException {
        StringBuilder affected = new StringBuilder();
        MavenXpp3Reader pomReader = new MavenXpp3Reader();

        for (String moduleName : parentProject.getModules()) {
            File moduleDir = new File(parentProject.getBasedir(), moduleName);
            File modulePom = new File(moduleDir, "pom.xml");
            if (!modulePom.exists()) {
                continue;
            }
            try (Reader reader = new XmlStreamReader(modulePom)) {
                Model model = pomReader.read(reader);
                if (model.getDependencies() != null) {
                    for (Dependency dep : model.getDependencies()) {
                        if (depGroupId.equals(dep.getGroupId())
                                && depArtifactId.equals(dep.getArtifactId())
                                && (dep.getVersion() == null || dep.getVersion().isEmpty())) {
                            if (affected.length() > 0) {
                                affected.append(", ");
                            }
                            affected.append(moduleName);
                            break;
                        }
                    }
                }
            } catch (IOException | XmlPullParserException e) {
                getLog().debug("Could not read module POM: " + modulePom + " - " + e.getMessage());
            }
        }

        if (affected.length() > 0) {
            getLog().warn("The following child modules depend on " + depGroupId + ":" + depArtifactId
                    + " without an explicit version and will break: [" + affected + "]. Proceeding with removal.");
        }
    }

    /**
     * Checks whether the dependency exists in the project's declared (original) model
     * after property interpolation, but before inheritance merging.
     * This catches dependencies declared with property references like {@code ${project.groupId}}
     * without false-positiving on inherited dependencies from a parent POM.
     */
    private static boolean existsInResolvedModel(MavenProject project, DependencyCoordinates coords, boolean managed) {
        java.util.List<Dependency> deps;
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
}
