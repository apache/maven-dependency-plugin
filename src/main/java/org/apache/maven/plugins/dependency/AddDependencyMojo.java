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
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.pom.DependencyCoordinates;
import org.apache.maven.plugins.dependency.pom.PomEditor;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.w3c.dom.Element;

/**
 * Adds a dependency to the project's {@code pom.xml}.
 * Supports adding to {@code <dependencies>} or {@code <dependencyManagement>},
 * with version inference from managed dependencies and BOM import shorthand.
 *
 * @since 3.11.0
 */
@Mojo(name = "add", requiresProject = true, threadSafe = true)
public class AddDependencyMojo extends AbstractDependencyMojo {

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
     * The dependency's version. Required when adding to {@code <dependencyManagement>}.
     * Optional when adding to {@code <dependencies>} if the version is managed by an ancestor.
     */
    @Parameter(property = "version")
    private String version;

    /**
     * Shorthand coordinates: {@code groupId:artifactId[:version[:scope[:type[:classifier]]]]}.
     */
    @Parameter(property = "gav")
    private String gav;

    /**
     * Dependency scope ({@code compile}, {@code provided}, {@code runtime}, {@code test}, {@code system}, {@code import}).
     */
    @Parameter(property = "scope")
    private String scope;

    /**
     * Dependency type/packaging (e.g., {@code jar}, {@code pom}, {@code war}).
     */
    @Parameter(property = "type")
    private String type;

    /**
     * Dependency classifier (e.g., {@code sources}, {@code javadoc}, {@code tests}).
     */
    @Parameter(property = "classifier")
    private String classifier;

    /**
     * Whether the dependency is optional.
     */
    @Parameter(property = "optional", defaultValue = "false")
    private boolean optional;

    /**
     * When {@code true}, insert into {@code <dependencyManagement>} instead of {@code <dependencies>}.
     */
    @Parameter(property = "managed", defaultValue = "false")
    private boolean managed;

    /**
     * Target a specific child module by artifactId when running from the root of a multi-module project.
     */
    @Parameter(property = "module")
    private String module;

    /**
     * When {@code true} and the dependency already exists, update it. Otherwise fail.
     */
    @Parameter(property = "updateExisting", defaultValue = "false")
    private boolean updateExisting;

    /**
     * When {@code true}, add as a BOM import ({@code type=pom}, {@code scope=import})
     * into {@code <dependencyManagement>}.
     */
    @Parameter(property = "bom", defaultValue = "false")
    private boolean bom;

    @Inject
    public AddDependencyMojo(MavenSession session, BuildContext buildContext, MavenProject project) {
        super(session, buildContext, project);
    }

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        DependencyCoordinates coords = resolveCoordinates();

        if (bom) {
            handleBomFlag(coords);
        }

        MavenProject targetProject = resolveTargetProject();
        boolean targetManaged = managed || bom;

        // Validate version requirements
        if (targetManaged && coords.getVersion() == null) {
            throw new MojoFailureException(
                    "Version is required when adding to <dependencyManagement>. Provide a version with -Dversion=...");
        }

        // Version inference for non-managed dependencies
        if (!targetManaged && coords.getVersion() == null) {
            String managedVersion = findManagedVersion(targetProject, coords.getGroupId(), coords.getArtifactId());
            if (managedVersion != null) {
                getLog().info("Version managed by parent: " + coords.getGroupId() + ":" + coords.getArtifactId() + ":"
                        + managedVersion);
            } else {
                throw new MojoFailureException("No version specified and no managed version found for "
                        + coords.getGroupId() + ":" + coords.getArtifactId()
                        + ". Provide a version with -Dversion=...");
            }
        }

        // Warn when adding to parent <dependencies> (not managed) in a multi-module project
        if (!targetManaged
                && targetProject.getModules() != null
                && !targetProject.getModules().isEmpty()
                && module == null) {
            getLog().warn("Adding dependency to parent POM — this will be inherited by all child modules. "
                    + "Use -Dmanaged to add to <dependencyManagement> instead.");
        }

        File pomFile = targetProject.getFile();
        try {
            PomEditor editor = PomEditor.load(pomFile);
            Element existing = editor.findDependency(
                    coords.getGroupId(),
                    coords.getArtifactId(),
                    coords.getType(),
                    coords.getClassifier(),
                    targetManaged);

            if (existing != null) {
                if (!updateExisting) {
                    throw new MojoFailureException("Dependency " + coords.getGroupId() + ":" + coords.getArtifactId()
                            + " already exists. Use -DupdateExisting to update it.");
                }
                editor.updateDependency(existing, coords);
                getLog().info("Updated dependency " + coords + " in " + pomFile.getName());
            } else if (existsInResolvedModel(targetProject, coords, targetManaged)) {
                // Dependency exists in the resolved model but not in raw XML — property interpolation
                throw new MojoFailureException("Dependency " + coords.getGroupId() + ":" + coords.getArtifactId()
                        + " already exists in the POM (using property references). "
                        + "Cannot safely add or update automatically. Please edit the POM manually.");
            } else {
                editor.addDependency(coords, targetManaged);
                getLog().info("Added dependency " + coords + " to " + pomFile.getName());
            }

            editor.save();
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
            throw new MojoFailureException("You must specify either -Dgav=groupId:artifactId[:version] "
                    + "or both -DgroupId=... and -DartifactId=...");
        }

        // Explicit parameters override GAV shorthand values
        if (gav != null) {
            if (version != null) {
                coords.setVersion(version);
            }
            if (scope != null) {
                coords.setScope(scope);
            }
            if (type != null) {
                coords.setType(type);
            }
            if (classifier != null) {
                coords.setClassifier(classifier);
            }
        } else {
            coords.setVersion(version);
            coords.setScope(scope);
            coords.setType(type);
            coords.setClassifier(classifier);
        }

        if (optional) {
            coords.setOptional(true);
        }

        try {
            coords.validate();
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage());
        }

        return coords;
    }

    private void handleBomFlag(DependencyCoordinates coords) {
        if (coords.getScope() != null || coords.getType() != null) {
            getLog().warn("The -Dbom flag overrides scope and type. Using scope=import and type=pom.");
        }
        coords.setScope("import");
        coords.setType("pom");
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
        throw new MojoFailureException("Module '" + module + "' not found in the reactor. " + "Available modules: "
                + getModuleNames(reactorProjects));
    }

    private String findManagedVersion(MavenProject project, String groupId, String artifactId) {
        MavenProject current = project;
        while (current != null) {
            DependencyManagement depMgmt = current.getDependencyManagement();
            if (depMgmt != null && depMgmt.getDependencies() != null) {
                for (Dependency dep : depMgmt.getDependencies()) {
                    if (groupId.equals(dep.getGroupId()) && artifactId.equals(dep.getArtifactId())) {
                        return dep.getVersion();
                    }
                }
            }
            current = current.getParent();
        }
        return null;
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

    /**
     * Checks whether the dependency exists in Maven's resolved (interpolated) model.
     * This catches dependencies declared with property references like {@code ${project.groupId}}.
     */
    private static boolean existsInResolvedModel(MavenProject project, DependencyCoordinates coords, boolean managed) {
        List<Dependency> deps;
        if (managed) {
            DependencyManagement depMgmt = project.getDependencyManagement();
            deps = depMgmt != null ? depMgmt.getDependencies() : null;
        } else {
            deps = project.getDependencies();
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
