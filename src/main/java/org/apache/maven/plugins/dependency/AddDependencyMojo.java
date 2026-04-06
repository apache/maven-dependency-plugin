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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.maveniverse.domtrip.Element;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.pom.DependencyEntry;
import org.apache.maven.plugins.dependency.pom.PomEditor;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Adds a dependency to the project's {@code pom.xml}.
 * Supports adding to {@code <dependencies>} or {@code <dependencyManagement>},
 * with version inference from managed dependencies.
 *
 * <p>If the dependency already exists, the goal fails with a descriptive error
 * directing the user to remove it first.</p>
 *
 * <p>The goal uses formatting-preserving DOM manipulation to maintain the POM's
 * existing structure (comments, indentation, encoding). Duplicate detection uses
 * type and classifier-aware matching, and cross-references Maven's resolved model
 * to catch dependencies declared via property references.</p>
 *
 * <p>Scope values are validated against Maven's known scopes:
 * {@code compile}, {@code provided}, {@code runtime}, {@code test}, {@code system}, {@code import}.</p>
 *
 * @since 3.11.0
 */
@Mojo(name = "add", requiresProject = true, threadSafe = true)
public class AddDependencyMojo extends AbstractDependencyMojo {

    /**
     * Dependency coordinates: {@code groupId:artifactId[:version]}
     * or {@code groupId:artifactId[:extension[:classifier]]:version}.
     * Scope, type, classifier, and optional can be overridden via separate parameters.
     */
    @Parameter(property = "gav")
    private String gav;

    /**
     * Dependency scope. Validated against Maven's known scope values:
     * {@code compile}, {@code provided}, {@code runtime}, {@code test}, {@code system}, {@code import}.
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
     * Whether the dependency is optional.
     */
    @Parameter(property = "optional")
    private Boolean optional;

    /**
     * When {@code true}, insert into {@code <dependencyManagement>} instead of {@code <dependencies>}.
     */
    @Parameter(property = "managed", defaultValue = "false")
    private boolean managed;

    /**
     * When {@code true} (the default), automatically detect and follow the project's existing
     * dependency management conventions:
     * <ul>
     *   <li>If most existing dependencies are version-less, add managed dependency to parent POM</li>
     *   <li>If versions use {@code ${...}} property references, create a version property</li>
     *   <li>Property naming follows the detected pattern (e.g., {@code .version}, {@code -version})</li>
     * </ul>
     * Explicit parameters ({@code managed}, {@code useProperty}, {@code propertyName}) override
     * detected conventions.
     *
     * @since 3.11.0
     */
    @Parameter(property = "align", defaultValue = "true")
    private boolean align;

    /**
     * When set, controls whether a version property is created in {@code <properties>}.
     * When {@code null} (the default), the behavior is auto-detected from existing conventions
     * if {@code align=true}.
     *
     * @since 3.11.0
     */
    @Parameter(property = "useProperty")
    private Boolean useProperty;

    /**
     * Explicit property name for the version (e.g., {@code guava.version}).
     * When not set, the name is derived from the detected naming convention.
     *
     * @since 3.11.0
     */
    @Parameter(property = "propertyName")
    private String propertyName;

    /**
     * Target a specific Maven profile by its {@code <id>}. When set, the dependency is added
     * to the profile's {@code <dependencies>} or {@code <dependencyManagement>} section.
     * The profile must already exist in the POM.
     */
    @Parameter(property = "profile")
    private String profile;

    @Inject
    public AddDependencyMojo(MavenSession session, BuildContext buildContext, MavenProject project) {
        super(session, buildContext, project);
    }

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        DependencyEntry coords = resolveCoordinates();

        MavenProject targetProject = getProject();
        boolean effectiveManaged = managed;

        // Convention auto-detection when align=true
        Conventions conventions = null;
        if (align && coords.getVersion() != null) {
            conventions = detectConventions(targetProject);
            if (!managed) {
                effectiveManaged = conventions.useManaged;
            }
        }

        // Validate version requirements
        if (effectiveManaged && coords.getVersion() == null) {
            throw new MojoFailureException("Version is required when adding to <dependencyManagement>."
                    + " Include version in -Dgav (e.g. groupId:artifactId:version)");
        }

        // Version inference for non-managed dependencies
        if (!effectiveManaged && coords.getVersion() == null) {
            String managedVersion = findManagedVersion(targetProject, coords.getGroupId(), coords.getArtifactId());
            if (managedVersion != null) {
                getLog().info("Version managed by parent: " + coords.getGroupId() + ":" + coords.getArtifactId() + ":"
                        + managedVersion);
            } else {
                throw new MojoFailureException("No version specified and no managed version found for "
                        + coords.getGroupId() + ":" + coords.getArtifactId()
                        + ". Include version in -Dgav (e.g. groupId:artifactId:version)");
            }
        }

        // Determine effective property usage
        boolean effectiveUseProperty = false;
        String effectivePropertyName = null;
        if (conventions != null && coords.getVersion() != null) {
            effectiveUseProperty = useProperty != null ? useProperty : conventions.useProperty;
            if (effectiveUseProperty) {
                effectivePropertyName = propertyName != null ? propertyName : conventions.derivePropertyName(coords);
            }
        }

        // Determine the target POM for managed deps
        File managedPomFile = null;
        if (effectiveManaged && conventions != null && conventions.managedPomFile != null) {
            managedPomFile = conventions.managedPomFile;
        }

        // Cross-POM mode: add managed dep + property to parent, version-less dep to child
        File pomFile = targetProject.getFile();
        if (pomFile == null) {
            throw new MojoExecutionException("Cannot add dependency: project has no POM file to modify.");
        }

        boolean crossPom = effectiveManaged
                && managedPomFile != null
                && !managedPomFile.getAbsolutePath().equals(pomFile.getAbsolutePath());

        try {
            if (crossPom) {
                addCrossPom(coords, pomFile, managedPomFile, effectiveUseProperty, effectivePropertyName);
            } else {
                addSinglePom(
                        coords, targetProject, pomFile, effectiveManaged, effectiveUseProperty, effectivePropertyName);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to modify POM file: " + pomFile, e);
        }
    }

    /**
     * Cross-POM mode: adds a managed dependency (with optional property) to the parent POM,
     * and a version-less dependency reference to the child POM.
     */
    private void addCrossPom(
            DependencyEntry coords,
            File childPomFile,
            File parentPomFile,
            boolean effectiveUseProperty,
            String effectivePropertyName)
            throws IOException, MojoFailureException {
        // 1. Modify parent POM: add property + managed dependency
        PomEditor parentEditor = PomEditor.load(parentPomFile);
        Element existingManaged = parentEditor.findDependency(
                coords.getGroupId(), coords.getArtifactId(), coords.getType(), coords.getClassifier(), true);
        if (existingManaged != null) {
            throw new MojoFailureException("Dependency " + coords.getGroupId() + ":" + coords.getArtifactId()
                    + " already exists in " + parentPomFile.getName() + " <dependencyManagement>."
                    + " Remove it first with dependency:remove, then re-add.");
        }

        String versionRef = coords.getVersion();
        if (effectiveUseProperty && effectivePropertyName != null) {
            parentEditor.addProperty(effectivePropertyName, coords.getVersion());
            versionRef = "${" + effectivePropertyName + "}";
            getLog().info("Added property " + effectivePropertyName + "=" + coords.getVersion() + " to "
                    + parentPomFile.getName());
        }

        DependencyEntry managedCoords = new DependencyEntry(coords.getGroupId(), coords.getArtifactId());
        managedCoords.setVersion(versionRef);
        if (coords.getType() != null && !coords.getType().isEmpty()) {
            managedCoords.setType(coords.getType());
        }
        if (coords.getClassifier() != null && !coords.getClassifier().isEmpty()) {
            managedCoords.setClassifier(coords.getClassifier());
        }
        parentEditor.addDependency(managedCoords, true);
        parentEditor.save();
        getLog().info("Added managed dependency " + coords.getGroupId() + ":" + coords.getArtifactId() + ":"
                + versionRef + " to " + parentPomFile.getName());

        // 2. Modify child POM: add version-less dependency
        PomEditor childEditor = PomEditor.load(childPomFile);
        if (profile != null && !profile.isEmpty()) {
            if (childEditor.findProfile(profile) == null) {
                throw new MojoFailureException(
                        "Profile '" + profile + "' not found in " + childPomFile.getName() + ".");
            }
            childEditor.setProfileId(profile);
        }
        Element existingChild = childEditor.findDependency(
                coords.getGroupId(), coords.getArtifactId(), coords.getType(), coords.getClassifier(), false);
        if (existingChild != null) {
            throw new MojoFailureException("Dependency " + coords.getGroupId() + ":" + coords.getArtifactId()
                    + " already exists in " + childPomFile.getName()
                    + ". Remove it first with dependency:remove, then re-add.");
        } else if (existsInResolvedModel(getProject(), coords, false)) {
            throw new MojoFailureException("Dependency " + coords.getGroupId() + ":" + coords.getArtifactId()
                    + " already exists in the POM (using property references). "
                    + "Cannot safely add or update automatically. Please edit the POM manually.");
        }

        DependencyEntry childCoords = new DependencyEntry(coords.getGroupId(), coords.getArtifactId());
        if (coords.getScope() != null && !coords.getScope().isEmpty()) {
            childCoords.setScope(coords.getScope());
        }
        if (coords.getType() != null && !coords.getType().isEmpty()) {
            childCoords.setType(coords.getType());
        }
        if (coords.getClassifier() != null && !coords.getClassifier().isEmpty()) {
            childCoords.setClassifier(coords.getClassifier());
        }
        if (coords.getOptional() != null) {
            childCoords.setOptional(coords.getOptional());
        }
        childEditor.addDependency(childCoords, false);
        childEditor.save();
        getLog().info("Added version-less dependency " + coords.getGroupId() + ":" + coords.getArtifactId() + " to "
                + childPomFile.getName());

        // Sync in-memory model
        syncInMemoryModel(getProject(), coords, false, true);
    }

    /**
     * Single-POM mode: adds the dependency (optionally with a version property) to the current POM.
     */
    private void addSinglePom(
            DependencyEntry coords,
            MavenProject targetProject,
            File pomFile,
            boolean targetManaged,
            boolean effectiveUseProperty,
            String effectivePropertyName)
            throws IOException, MojoFailureException {

        // Warn when adding to parent <dependencies> (not managed) in a multi-module project
        if (!targetManaged
                && targetProject.getModules() != null
                && !targetProject.getModules().isEmpty()) {
            getLog().warn("Adding dependency to parent POM — this will be inherited by all child modules. "
                    + "Use -Dmanaged to add to <dependencyManagement> instead.");
        }

        PomEditor editor = PomEditor.load(pomFile);
        if (profile != null && !profile.isEmpty()) {
            if (editor.findProfile(profile) == null) {
                throw new MojoFailureException("Profile '" + profile + "' not found in " + pomFile.getName() + ".");
            }
            editor.setProfileId(profile);
        }
        Element existing = editor.findDependency(
                coords.getGroupId(), coords.getArtifactId(), coords.getType(), coords.getClassifier(), targetManaged);

        if (existing != null) {
            throw new MojoFailureException("Dependency " + coords.getGroupId() + ":" + coords.getArtifactId()
                    + " already exists in " + pomFile.getName()
                    + ". Remove it first with dependency:remove, then re-add.");
        } else if (existsInResolvedModel(targetProject, coords, targetManaged)) {
            throw new MojoFailureException("Dependency " + coords.getGroupId() + ":" + coords.getArtifactId()
                    + " already exists in the POM (using property references). "
                    + "Cannot safely add or update automatically. Please edit the POM manually.");
        } else {
            // Handle version property
            if (effectiveUseProperty && effectivePropertyName != null && coords.getVersion() != null) {
                editor.addProperty(effectivePropertyName, coords.getVersion());
                getLog().info("Added property " + effectivePropertyName + "=" + coords.getVersion());
                coords.setVersion("${" + effectivePropertyName + "}");
            }
            editor.addDependency(coords, targetManaged);
            getLog().info("Added dependency " + coords + " to " + pomFile.getName());
        }

        editor.save();

        syncInMemoryModel(targetProject, coords, targetManaged, false);
    }

    /**
     * Syncs the in-memory Maven model after POM modifications.
     */
    private void syncInMemoryModel(
            MavenProject targetProject, DependencyEntry coords, boolean targetManaged, boolean versionless) {
        Model model = targetProject.getModel();
        if (model != null) {
            Dependency modelDep = new Dependency();
            modelDep.setGroupId(coords.getGroupId());
            modelDep.setArtifactId(coords.getArtifactId());
            if (!versionless && coords.getVersion() != null) {
                modelDep.setVersion(coords.getVersion());
            }
            if (coords.getScope() != null && !coords.getScope().isEmpty()) {
                modelDep.setScope(coords.getScope());
            }
            if (coords.getType() != null && !coords.getType().isEmpty()) {
                modelDep.setType(coords.getType());
            }
            if (coords.getClassifier() != null && !coords.getClassifier().isEmpty()) {
                modelDep.setClassifier(coords.getClassifier());
            }
            if (coords.getOptional() != null) {
                modelDep.setOptional(String.valueOf(coords.getOptional()));
            }
            if (targetManaged) {
                DependencyManagement dm = model.getDependencyManagement();
                if (dm == null) {
                    dm = new DependencyManagement();
                    model.setDependencyManagement(dm);
                }
                dm.addDependency(modelDep);
            } else {
                model.addDependency(modelDep);
            }
        }
    }

    private DependencyEntry resolveCoordinates() throws MojoFailureException {
        if (gav == null || gav.isEmpty()) {
            throw new MojoFailureException("You must specify -Dgav=groupId:artifactId[:version]");
        }

        DependencyEntry coords;
        try {
            coords = DependencyEntry.parse(gav);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage());
        }

        // Explicit parameters override GAV shorthand values
        if (scope != null) {
            coords.setScope(scope);
        }
        if (type != null) {
            coords.setType(type);
        }
        if (classifier != null) {
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

        return coords;
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

    // --- Convention detection ---

    /**
     * Detects the project's dependency management conventions by analyzing existing dependencies.
     */
    private Conventions detectConventions(MavenProject project) throws MojoExecutionException {
        Conventions conv = new Conventions();

        File pomFile = project.getFile();
        if (pomFile == null) {
            return conv;
        }

        try {
            PomEditor editor = PomEditor.load(pomFile);

            // Analyze managed dependency usage: count deps with/without version
            long totalDeps = editor.getDependencyCount(false);
            List<String> depVersions = editor.getDependencyVersions(false);
            long depsWithVersion = depVersions.size();

            if (totalDeps > 0 && depsWithVersion < totalDeps / 2.0) {
                // Majority of deps are version-less → use managed deps
                conv.useManaged = true;
                getLog().debug("Convention detected: majority of dependencies are version-less (useManaged=true)");
            }

            // Find the POM that has <dependencyManagement>
            conv.managedPomFile = findManagedDepsPom(project);

            // Analyze property patterns from child POM versions
            List<String> allVersions = new java.util.ArrayList<>(depVersions);
            allVersions.addAll(editor.getDependencyVersions(true));

            // If child has mostly version-less deps, also scan the parent POM for property patterns
            if (conv.managedPomFile != null
                    && !conv.managedPomFile.getAbsolutePath().equals(pomFile.getAbsolutePath())) {
                PomEditor parentEditor = PomEditor.load(conv.managedPomFile);
                allVersions.addAll(parentEditor.getDependencyVersions(true));
                allVersions.addAll(parentEditor.getDependencyVersions(false));
            }

            // Count property references vs literal versions
            long propertyRefs = allVersions.stream()
                    .filter(v -> v.startsWith("${") && v.endsWith("}"))
                    .count();
            if (!allVersions.isEmpty() && propertyRefs > allVersions.size() / 2.0) {
                conv.useProperty = true;
                getLog().debug("Convention detected: majority of versions use property references (useProperty=true)");

                // Detect property naming pattern
                conv.pattern = detectPropertyPattern(allVersions);
                if (conv.pattern != null) {
                    getLog().debug("Convention detected: property naming pattern=" + conv.pattern);
                }
            }
        } catch (IOException e) {
            getLog().debug("Could not analyze POM conventions: " + e.getMessage());
        }

        return conv;
    }

    /**
     * Walks the parent chain to find the nearest POM that declares {@code <dependencyManagement>}.
     */
    private File findManagedDepsPom(MavenProject project) {
        MavenProject current = project.getParent();
        while (current != null) {
            File pf = current.getFile();
            if (pf != null) {
                DependencyManagement dm = current.getOriginalModel().getDependencyManagement();
                if (dm != null
                        && dm.getDependencies() != null
                        && !dm.getDependencies().isEmpty()) {
                    return pf;
                }
            }
            current = current.getParent();
        }
        return null;
    }

    private static final Pattern PROPERTY_REF = Pattern.compile("^\\$\\{(.+)}$");

    /**
     * Property naming conventions detected in existing POM files.
     */
    enum PropertyPattern {
        /** {@code artifactId.version} (e.g., {@code guava.version}) */
        DOT_VERSION {
            @Override
            String toPropertyName(String artifactId) {
                return artifactId + ".version";
            }

            @Override
            boolean matches(String propName) {
                return propName.endsWith(".version");
            }
        },
        /** {@code artifactId-version} (e.g., {@code guava-version}) */
        DASH_VERSION {
            @Override
            String toPropertyName(String artifactId) {
                return artifactId + "-version";
            }

            @Override
            boolean matches(String propName) {
                return propName.endsWith("-version");
            }
        },
        /** {@code artifactIdVersion} (camelCase, e.g., {@code guavaVersion}) */
        CAMEL_VERSION {
            @Override
            String toPropertyName(String artifactId) {
                return artifactId + "Version";
            }

            @Override
            boolean matches(String propName) {
                return propName.endsWith("Version");
            }
        },
        /** {@code version.artifactId} (prefix, e.g., {@code version.guava}) */
        VERSION_PREFIX {
            @Override
            String toPropertyName(String artifactId) {
                return "version." + artifactId;
            }

            @Override
            boolean matches(String propName) {
                return propName.startsWith("version.");
            }
        };

        abstract String toPropertyName(String artifactId);

        abstract boolean matches(String propName);
    }

    /**
     * Analyzes property reference names to detect the naming convention.
     *
     * @return the most common {@link PropertyPattern}, or {@code null} if no clear pattern is found
     */
    static PropertyPattern detectPropertyPattern(List<String> versions) {
        Map<PropertyPattern, Integer> patternCounts = new HashMap<>();
        for (String version : versions) {
            Matcher m = PROPERTY_REF.matcher(version);
            if (m.matches()) {
                String propName = m.group(1);
                for (PropertyPattern pp : PropertyPattern.values()) {
                    if (pp.matches(propName)) {
                        patternCounts.merge(pp, 1, Integer::sum);
                        break;
                    }
                }
            }
        }
        if (patternCounts.isEmpty()) {
            return null;
        }
        return patternCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Holds the detected conventions for the project.
     */
    static class Conventions {
        boolean useManaged;
        boolean useProperty;
        PropertyPattern pattern;
        File managedPomFile;

        /**
         * Derives a property name for the given dependency based on the detected pattern.
         */
        String derivePropertyName(DependencyEntry coords) {
            PropertyPattern effective = pattern != null ? pattern : PropertyPattern.DOT_VERSION;
            return effective.toPropertyName(coords.getArtifactId());
        }
    }
}
