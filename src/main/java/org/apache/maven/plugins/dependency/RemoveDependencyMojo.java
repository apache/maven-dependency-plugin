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
     * Shorthand coordinates: {@code groupId:artifactId[:version]}
     * or {@code groupId:artifactId[:extension[:classifier]]:version}.
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
     * Dependency type for precise matching (e.g., {@code pom}, {@code war}, {@code test-jar}).
     * When not specified, defaults to {@code "jar"}.
     */
    @Parameter(property = "type")
    private String type;

    /**
     * Dependency classifier for precise matching (e.g., {@code sources}, {@code javadoc}, {@code tests}).
     */
    @Parameter(property = "classifier")
    private String classifier;

    /**
     * When {@code true}, remove a BOM import ({@code type=pom}) from {@code <dependencyManagement>}.
     * Equivalent to {@code -Dmanaged -Dtype=pom}.
     */
    @Parameter(property = "bom", defaultValue = "false")
    private boolean bom;

    /**
     * Target a specific Maven profile by its {@code <id>}. When set, the dependency is removed
     * from the profile's {@code <dependencies>} or {@code <dependencyManagement>} section.
     * The profile must already exist in the POM.
     */
    @Parameter(property = "profile")
    private String profile;

    @Inject
    public RemoveDependencyMojo(MavenSession session, BuildContext buildContext, MavenProject project) {
        super(session, buildContext, project);
    }

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        DependencyCoordinates coords = resolveCoordinates();

        if (bom) {
            handleBomFlag(coords);
        }

        boolean targetManaged = managed || bom;
        MavenProject targetProject = getProject();
        File pomFile = targetProject.getFile();

        // Safety check for managed dependency removal in parent POM
        if (targetManaged
                && targetProject.getModules() != null
                && !targetProject.getModules().isEmpty()) {
            checkChildModuleDependencies(targetProject, coords.getGroupId(), coords.getArtifactId());
        }

        try {
            PomEditor editor = PomEditor.load(pomFile);
            if (profile != null && !profile.isEmpty()) {
                if (editor.findProfile(profile) == null) {
                    throw new MojoFailureException("Profile '" + profile + "' not found in " + pomFile.getName() + ".");
                }
                editor.setProfileId(profile);
            }
            boolean removed = editor.removeDependency(
                    coords.getGroupId(),
                    coords.getArtifactId(),
                    coords.getType(),
                    coords.getClassifier(),
                    targetManaged);

            if (!removed) {
                // Cross-reference with resolved model to detect property-interpolated coords
                if (existsInResolvedModel(targetProject, coords, targetManaged)) {
                    String section = targetManaged ? "<dependencyManagement>" : "<dependencies>";
                    throw new MojoFailureException("Dependency " + coords.getGroupId() + ":"
                            + coords.getArtifactId()
                            + " exists in " + section + " but uses property references in the POM. "
                            + "Please remove it manually.");
                }
                String section = targetManaged ? "<dependencyManagement>" : "<dependencies>";
                throw new MojoFailureException("Dependency " + coords.getGroupId() + ":" + coords.getArtifactId()
                        + " not found in " + section + ".");
            }

            editor.save();

            // Sync in-memory model so chained goals see the change
            Model model = targetProject.getModel();
            if (model != null) {
                if (targetManaged) {
                    DependencyManagement dm = model.getDependencyManagement();
                    if (dm != null && dm.getDependencies() != null) {
                        dm.getDependencies()
                                .removeIf(d -> coords.getGroupId().equals(d.getGroupId())
                                        && coords.getArtifactId().equals(d.getArtifactId()));
                    }
                } else if (model.getDependencies() != null) {
                    model.getDependencies()
                            .removeIf(d -> coords.getGroupId().equals(d.getGroupId())
                                    && coords.getArtifactId().equals(d.getArtifactId()));
                }
            }

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
            // Explicit parameters override GAV shorthand values
            if (type != null) {
                coords.setType(type);
            }
            if (classifier != null) {
                coords.setClassifier(classifier);
            }
        } else if (groupId != null && artifactId != null) {
            coords = new DependencyCoordinates(groupId, artifactId);
            coords.setType(type);
            coords.setClassifier(classifier);
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

    private void handleBomFlag(DependencyCoordinates coords) {
        if (coords.getType() != null && !"pom".equals(coords.getType())) {
            getLog().warn("The -Dbom flag overrides type. Using type=pom.");
        }
        coords.setType("pom");
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
}
