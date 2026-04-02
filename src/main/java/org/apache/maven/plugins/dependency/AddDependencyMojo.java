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
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import eu.maveniverse.domtrip.Document;
import eu.maveniverse.domtrip.Element;
import eu.maveniverse.domtrip.maven.Coordinates;
import eu.maveniverse.domtrip.maven.PomEditor;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Adds or updates a dependency in the project's {@code pom.xml}.
 * Uses DomTrip for lossless XML editing that preserves formatting, comments, and whitespace.
 *
 * <p>By default ({@code align=true}), this mojo auto-detects the project's dependency management
 * conventions by analyzing existing dependencies:</p>
 * <ul>
 *   <li>If the project uses {@code <dependencyManagement>} (most deps are version-less),
 *       the version is added to the managed section and a version-less entry to {@code <dependencies>}.</li>
 *   <li>If existing versions use property references ({@code ${...}}), a property is created
 *       following the detected naming convention.</li>
 *   <li>The managed dependency POM is discovered by walking the parent chain.</li>
 * </ul>
 *
 * <p>Explicit flags ({@code -Dmanaged}, {@code -DuseProperty}, {@code -DpropertyName}) override
 * auto-detected conventions.</p>
 *
 * <p>Examples:</p>
 * <pre>
 * mvn dependency:add -Dgav=com.google.guava:guava:33.0.0-jre
 * mvn dependency:add -Dgav=com.google.guava:guava:33.0.0-jre -Dscope=compile
 * mvn dependency:add -Dgav=com.google.guava:guava:33.0.0-jre -Dalign=false -Dmanaged
 * mvn dependency:add -Dgav=com.google.guava:guava:33.0.0-jre -DpropertyName=guava.version
 * </pre>
 *
 * @since 3.11.0
 */
@Mojo(name = "add", requiresProject = true, threadSafe = true)
public class AddDependencyMojo extends AbstractDependencyMojo {

    /**
     * The dependency coordinates: {@code groupId:artifactId[:version[:classifier[:type]]]}.
     */
    @Parameter(property = "gav", required = true)
    private String gav;

    /**
     * When {@code true}, add to {@code <dependencyManagement>} instead of {@code <dependencies>}.
     * Overrides auto-detection from {@code align}.
     */
    @Parameter(property = "managed")
    private Boolean managed;

    /**
     * The dependency scope (e.g., {@code compile}, {@code test}, {@code provided}, {@code runtime}, {@code system}).
     * If not specified, no {@code <scope>} element is added (Maven defaults to {@code compile}).
     */
    @Parameter(property = "scope")
    private String scope;

    /**
     * When {@code true}, store the version in a property and reference it from the dependency's
     * {@code <version>} element. Overrides auto-detection from {@code align}.
     */
    @Parameter(property = "useProperty")
    private Boolean useProperty;

    /**
     * Explicit property name to use for the version (implies {@code useProperty=true}).
     * Overrides auto-detection from {@code align}.
     */
    @Parameter(property = "propertyName")
    private String propertyName;

    /**
     * When {@code true} (the default), auto-detect the project's dependency management conventions
     * by analyzing existing dependencies. Detected conventions are:
     * <ul>
     *   <li>Whether to use managed dependencies (based on whether existing deps are version-less)</li>
     *   <li>Whether to use version properties (based on existing {@code ${...}} references)</li>
     *   <li>Property naming convention (e.g., {@code artifactId.version} vs {@code version.artifactId})</li>
     *   <li>Which POM to add managed dependencies to (walks parent chain)</li>
     * </ul>
     */
    @Parameter(property = "align", defaultValue = "true")
    private boolean align;

    @Inject
    public AddDependencyMojo(MavenSession session, BuildContext buildContext, MavenProject project) {
        super(session, buildContext, project);
    }

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        Coordinates coords = parseCoordinates(gav);

        Conventions conventions;
        if (align) {
            conventions = detectConventions(coords);
        } else {
            conventions = new Conventions();
        }

        // Explicit flags override detected conventions
        boolean effectiveManaged = managed != null ? managed : conventions.useManaged;
        boolean effectiveUseProperty =
                propertyName != null || (useProperty != null ? useProperty : conventions.useProperty);
        String effectivePropertyName = propertyName != null ? propertyName : conventions.propertyName;
        File managedPomFile = conventions.managedPomFile;

        if (effectiveUseProperty
                && (coords.version() == null || coords.version().isEmpty())) {
            throw new MojoFailureException("Version is required when using property-based versioning");
        }

        try {
            if (effectiveManaged && managedPomFile != null) {
                addManagedDependency(coords, managedPomFile, effectiveUseProperty, effectivePropertyName);
                addVersionlessDependency(coords);
            } else {
                addDependencyToCurrentPom(coords, effectiveManaged, effectiveUseProperty, effectivePropertyName);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to modify POM file", e);
        }
    }

    private void addManagedDependency(
            Coordinates coords, File managedPomFile, boolean effectiveUseProperty, String effectivePropertyName)
            throws IOException {
        Document managedDoc = Document.of(managedPomFile.toPath());
        PomEditor managedEditor = new PomEditor(managedDoc);

        Coordinates managedCoords = coords;
        if (effectiveUseProperty && coords.version() != null) {
            String propName = effectivePropertyName != null ? effectivePropertyName : coords.artifactId() + ".version";
            managedCoords = coords.withVersion("${" + propName + "}");
            managedEditor.properties().updateProperty(true, propName, coords.version());
        }

        boolean changed = managedEditor.dependencies().updateManagedDependency(true, managedCoords);
        if (changed) {
            try (OutputStream os = Files.newOutputStream(managedPomFile.toPath())) {
                managedDoc.toXml(os);
            }
            getLog().info("Added/updated " + coords.toGAV() + " in <dependencyManagement> of " + managedPomFile);
        }
    }

    private void addVersionlessDependency(Coordinates coords) throws IOException {
        File pomFile = getProject().getFile();
        Document doc = Document.of(pomFile.toPath());
        PomEditor editor = new PomEditor(doc);

        // Add dependency without version (version comes from managed deps)
        Coordinates versionless =
                Coordinates.of(coords.groupId(), coords.artifactId(), null, coords.classifier(), coords.type());
        boolean changed = editor.dependencies().updateDependency(true, versionless);

        if (scope != null && !scope.isEmpty()) {
            setDependencyScope(editor, versionless);
            changed = true;
        }

        if (changed) {
            try (OutputStream os = Files.newOutputStream(pomFile.toPath())) {
                doc.toXml(os);
            }
            getLog().info("Added " + coords.toGA() + " (version-less) in <dependencies> of " + pomFile);
        }
    }

    private void addDependencyToCurrentPom(
            Coordinates coords, boolean effectiveManaged, boolean effectiveUseProperty, String effectivePropertyName)
            throws IOException {
        File pomFile = getProject().getFile();
        Document doc = Document.of(pomFile.toPath());
        PomEditor editor = new PomEditor(doc);

        Coordinates effectiveCoords = coords;
        if (effectiveUseProperty && coords.version() != null) {
            String propName = effectivePropertyName != null ? effectivePropertyName : coords.artifactId() + ".version";
            effectiveCoords = coords.withVersion("${" + propName + "}");
            editor.properties().updateProperty(true, propName, coords.version());
        }

        boolean changed;
        if (effectiveManaged) {
            changed = editor.dependencies().updateManagedDependency(true, effectiveCoords);
        } else {
            changed = editor.dependencies().updateDependency(true, effectiveCoords);
        }

        if (scope != null && !scope.isEmpty()) {
            setDependencyScope(editor, effectiveCoords);
            changed = true;
        }

        if (changed || effectiveUseProperty) {
            try (OutputStream os = Files.newOutputStream(pomFile.toPath())) {
                doc.toXml(os);
            }
            String section = effectiveManaged ? "<dependencyManagement>" : "<dependencies>";
            getLog().info("Added/updated " + coords.toGAV() + " in " + section);
        } else {
            getLog().info("No changes needed for " + coords.toGAV());
        }
    }

    private void setDependencyScope(PomEditor editor, Coordinates coords) {
        Element root = editor.document().root();
        Element depsContainer;
        if (managed != null && managed) {
            Element dm = editor.findChildElement(root, "dependencyManagement");
            depsContainer = dm != null ? editor.findChildElement(dm, "dependencies") : null;
        } else {
            depsContainer = editor.findChildElement(root, "dependencies");
        }
        if (depsContainer != null) {
            depsContainer
                    .childElements("dependency")
                    .filter(coords.predicateGATC())
                    .findFirst()
                    .ifPresent(dep -> {
                        editor.updateOrCreateChildElement(dep, "scope", scope);
                    });
        }
    }

    // --- Convention detection ---

    static class Conventions {
        boolean useManaged;
        boolean useProperty;
        String propertyName; // null means use detected pattern to generate
        String propertyPattern; // e.g., "suffix:.version" or "prefix:version."
        File managedPomFile;

        String derivePropertyName(String artifactId) {
            if (propertyName != null) {
                return propertyName;
            }
            if (propertyPattern != null) {
                if (propertyPattern.startsWith("suffix:")) {
                    return artifactId + propertyPattern.substring("suffix:".length());
                } else if (propertyPattern.startsWith("prefix:")) {
                    return propertyPattern.substring("prefix:".length()) + artifactId;
                }
            }
            return artifactId + ".version";
        }
    }

    private Conventions detectConventions(Coordinates coords) {
        Conventions conventions = new Conventions();
        MavenProject project = getProject();

        // 1. Analyze the current POM's dependencies for property and managed dep patterns
        File pomFile = project.getFile();
        if (pomFile != null && pomFile.exists()) {
            try {
                Document doc = Document.of(pomFile.toPath());
                analyzePropertyPatterns(doc, conventions);
                analyzeManagedDependencyUsage(doc, conventions);
            } catch (Exception e) {
                getLog().debug("Could not analyze POM conventions: " + e.getMessage());
            }
        }

        // 2. Find the POM that owns <dependencyManagement> by walking the parent chain
        if (conventions.useManaged) {
            conventions.managedPomFile = findManagedDependenciesPom(project);
            if (conventions.managedPomFile == null) {
                // No parent with managed deps found on disk — fall back to current POM
                conventions.managedPomFile = pomFile;
            }

            // 3. Also scan the managed deps POM for property patterns (the child POM
            //    may have only version-less deps, so patterns live in the parent)
            if (!conventions.useProperty
                    && conventions.managedPomFile != null
                    && !conventions.managedPomFile.equals(pomFile)) {
                try {
                    Document managedDoc = Document.of(conventions.managedPomFile.toPath());
                    analyzePropertyPatterns(managedDoc, conventions);
                } catch (Exception e) {
                    getLog().debug("Could not analyze managed POM conventions: " + e.getMessage());
                }
            }
        }

        // Use detected pattern for property name
        if (conventions.useProperty) {
            conventions.propertyName = conventions.derivePropertyName(coords.artifactId());
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("Detected conventions: useManaged=" + conventions.useManaged + ", useProperty="
                    + conventions.useProperty + ", propertyPattern=" + conventions.propertyPattern
                    + ", managedPomFile=" + conventions.managedPomFile);
        }

        return conventions;
    }

    private void analyzePropertyPatterns(Document doc, Conventions conventions) {
        Element root = doc.root();
        Map<String, Integer> patternCounts = new HashMap<>();
        int propertyVersions = 0;
        int totalVersions = 0;

        // Scan <dependencies>
        int[] directCounts = {0, 0};
        scanVersionPatterns(root, "dependencies", patternCounts, directCounts);
        propertyVersions += directCounts[0];
        totalVersions += directCounts[1];

        // Scan <dependencyManagement><dependencies>
        Element dm = root.childElement("dependencyManagement").orElse(null);
        if (dm != null) {
            int[] counts = {0, 0};
            scanVersionPatterns(dm, "dependencies", patternCounts, counts);
            propertyVersions += counts[0];
            totalVersions += counts[1];
        }

        // If majority of versioned deps use properties, adopt that convention
        if (totalVersions > 0 && propertyVersions * 2 >= totalVersions) {
            conventions.useProperty = true;
            // Find dominant pattern
            String dominantPattern = null;
            int maxCount = 0;
            for (Map.Entry<String, Integer> entry : patternCounts.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    dominantPattern = entry.getKey();
                }
            }
            conventions.propertyPattern = dominantPattern;
        }
    }

    private void scanVersionPatterns(
            Element parent, String containerName, Map<String, Integer> patternCounts, int[] counts) {
        Element container = parent.childElement(containerName).orElse(null);
        if (container == null) {
            return;
        }
        container.childElements("dependency").forEach(dep -> {
            String artifactId = dep.childTextTrimmed("artifactId");
            Element versionEl = dep.childElement("version").orElse(null);
            if (versionEl != null) {
                String version = versionEl.textContentTrimmed();
                counts[1]++; // totalVersions
                if (version != null && version.startsWith("${") && version.endsWith("}")) {
                    counts[0]++; // propertyVersions
                    String propName = version.substring(2, version.length() - 1);
                    String pattern = detectPattern(propName, artifactId);
                    if (pattern != null) {
                        patternCounts.merge(pattern, 1, Integer::sum);
                    }
                }
            }
        });
    }

    static String detectPattern(String propertyName, String artifactId) {
        if (artifactId == null) {
            return null;
        }
        // Check exact suffix patterns: artifactId.version, artifactId-version
        if (propertyName.equals(artifactId + ".version")) {
            return "suffix:.version";
        }
        if (propertyName.equals(artifactId + "-version")) {
            return "suffix:-version";
        }
        if (propertyName.equals("version." + artifactId)) {
            return "prefix:version.";
        }
        // Check with simplified artifactId (strip common prefixes/suffixes)
        String simplified = simplifyArtifactId(artifactId);
        if (!simplified.equals(artifactId)) {
            if (propertyName.equals(simplified + ".version")) {
                return "suffix:.version";
            }
            if (propertyName.equals(simplified + "-version")) {
                return "suffix:-version";
            }
            if (propertyName.equals("version." + simplified)) {
                return "prefix:version.";
            }
        }
        // Check if ends with .version, -version, or Version regardless
        if (propertyName.endsWith(".version")) {
            return "suffix:.version";
        }
        if (propertyName.endsWith("-version")) {
            return "suffix:-version";
        }
        if (propertyName.endsWith("Version")) {
            return "suffix:Version";
        }
        if (propertyName.startsWith("version.")) {
            return "prefix:version.";
        }
        return null;
    }

    private static String simplifyArtifactId(String artifactId) {
        // Remove common prefixes/suffixes that are often dropped in property names
        String result = artifactId;
        for (String prefix : new String[] {"maven-", "jakarta.", "javax."}) {
            if (result.startsWith(prefix)) {
                result = result.substring(prefix.length());
            }
        }
        for (String suffix : new String[] {"-api", "-core", "-impl"}) {
            if (result.endsWith(suffix)) {
                result = result.substring(0, result.length() - suffix.length());
            }
        }
        return result;
    }

    private void analyzeManagedDependencyUsage(Document doc, Conventions conventions) {
        Element root = doc.root();
        Element deps = root.childElement("dependencies").orElse(null);
        if (deps == null) {
            return;
        }
        int withVersion = 0;
        int withoutVersion = 0;
        for (Element dep :
                (Iterable<Element>) () -> deps.childElements("dependency").iterator()) {
            Element versionEl = dep.childElement("version").orElse(null);
            if (versionEl != null) {
                withVersion++;
            } else {
                withoutVersion++;
            }
        }
        // If there are dependencies and majority are version-less, project uses managed deps
        int total = withVersion + withoutVersion;
        if (total > 0 && withoutVersion * 2 >= total) {
            conventions.useManaged = true;
        }
    }

    private File findManagedDependenciesPom(MavenProject project) {
        MavenProject parent = project.getParent();
        while (parent != null) {
            File parentPom = parent.getFile();
            if (parentPom != null && parentPom.exists()) {
                try {
                    Document parentDoc = Document.of(parentPom.toPath());
                    Element root = parentDoc.root();
                    Element dm = root.childElement("dependencyManagement").orElse(null);
                    if (dm != null) {
                        return parentPom;
                    }
                } catch (Exception e) {
                    getLog().debug("Could not read parent POM: " + parentPom);
                }
            }
            parent = parent.getParent();
        }
        // If no parent has dependencyManagement, use current POM
        return project.getFile();
    }

    static Coordinates parseCoordinates(String gav) throws MojoFailureException {
        if (gav == null || gav.trim().isEmpty()) {
            throw new MojoFailureException("GAV must not be empty. Use -Dgav=groupId:artifactId[:version]");
        }
        String[] parts = gav.split(":");
        if (parts.length < 2 || parts.length > 5) {
            throw new MojoFailureException(
                    "Invalid GAV format: '" + gav + "'. Expected groupId:artifactId[:version[:classifier[:type]]]");
        }
        String groupId = parts[0].trim();
        String artifactId = parts[1].trim();
        String version = parts.length >= 3 ? parts[2].trim() : null;
        String classifier = parts.length >= 4 ? parts[3].trim() : null;
        String type = parts.length >= 5 ? parts[4].trim() : null;
        if (groupId.isEmpty() || artifactId.isEmpty()) {
            throw new MojoFailureException("groupId and artifactId must not be empty");
        }
        if (version != null && version.isEmpty()) {
            version = null;
        }
        if (classifier != null && classifier.isEmpty()) {
            classifier = null;
        }
        if (type != null && type.isEmpty()) {
            type = null;
        }
        return Coordinates.of(groupId, artifactId, version, classifier, type != null ? type : "jar");
    }
}
