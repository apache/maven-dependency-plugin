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
 * <p>If the dependency already exists, it is updated automatically (version, scope, etc.)
 * and the change is logged. This matches the behavior of {@code cargo add}, {@code npm install},
 * and other package managers.</p>
 *
 * <p>The goal uses formatting-preserving DOM manipulation to maintain the POM's
 * existing structure (comments, indentation, encoding). Duplicate detection uses
 * type and classifier-aware matching, and cross-references Maven's resolved model
 * to catch dependencies declared via property references. When a dependency is found in
 * the effective model but not in the raw XML (i.e., declared using property interpolation
 * such as {@code ${my.group}}), the goal fails with a descriptive error rather than
 * risk creating a conflicting duplicate entry.</p>
 *
 * <p>Scope values are validated against Maven's known scopes:
 * {@code compile}, {@code provided}, {@code runtime}, {@code test}, {@code system}, {@code import}.</p>
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
     * Dependency scope. Validated against Maven's known scope values:
     * {@code compile}, {@code provided}, {@code runtime}, {@code test}, {@code system}, {@code import}.
     * Use {@code NONE} to remove an existing scope element when updating.
     * Invalid values are rejected with a {@link org.apache.maven.plugin.MojoFailureException}.
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
     * Whether the dependency is optional. Setting {@code -Doptional=false} explicitly
     * removes the {@code <optional>} element when updating an existing dependency.
     */
    @Parameter(property = "optional")
    private Boolean optional;

    /**
     * When {@code true}, insert into {@code <dependencyManagement>} instead of {@code <dependencies>}.
     */
    @Parameter(property = "managed", defaultValue = "false")
    private boolean managed;

    /**
     * Target a specific Maven profile by its {@code <id>}. When set, the dependency is added
     * to the profile's {@code <dependencies>} or {@code <dependencyManagement>} section.
     * The profile must already exist in the POM.
     */
    @Parameter(property = "profile")
    private String profile;

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

        MavenProject targetProject = getProject();
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
                && !targetProject.getModules().isEmpty()) {
            getLog().warn("Adding dependency to parent POM — this will be inherited by all child modules. "
                    + "Use -Dmanaged to add to <dependencyManagement> instead.");
        }

        File pomFile = targetProject.getFile();
        try {
            PomEditor editor = PomEditor.load(pomFile);
            if (profile != null && !profile.isEmpty()) {
                if (editor.findProfile(profile) == null) {
                    throw new MojoFailureException("Profile '" + profile + "' not found in " + pomFile.getName() + ".");
                }
                editor.setProfileId(profile);
            }
            Element existing = editor.findDependency(
                    coords.getGroupId(),
                    coords.getArtifactId(),
                    coords.getType(),
                    coords.getClassifier(),
                    targetManaged);

            if (existing != null) {
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

        if (optional != null) {
            coords.setOptional(optional);
        }

        try {
            coords.validate();
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage());
        }

        // Convert NONE sentinels to empty strings (signals field removal during update)
        if ("NONE".equals(coords.getScope())) {
            coords.setScope("");
        }
        if ("NONE".equals(coords.getType())) {
            coords.setType("");
        }
        if ("NONE".equals(coords.getClassifier())) {
            coords.setClassifier("");
        }

        return coords;
    }

    private void handleBomFlag(DependencyCoordinates coords) {
        if (coords.getScope() != null || coords.getType() != null) {
            getLog().warn("The -Dbom flag overrides scope and type. Using scope=import and type=pom.");
        }
        if (coords.getClassifier() != null && !coords.getClassifier().isEmpty()) {
            getLog().warn("The -Dbom flag clears classifier. BOM imports must not have a classifier.");
        }
        coords.setScope("import");
        coords.setType("pom");
        coords.setClassifier(null);
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
}
