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
package org.apache.maven.plugins.dependency.pom;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import eu.maveniverse.domtrip.Document;
import eu.maveniverse.domtrip.Editor;
import eu.maveniverse.domtrip.Element;

/**
 * Formatting-preserving POM editor built on
 * <a href="https://github.com/maveniverse/domtrip">DomTrip</a>.
 * Preserves comments, whitespace, indentation style, XML namespaces,
 * BOM markers, and XML declarations when modifying {@code pom.xml} files.
 *
 * @since 3.11.0
 */
public class PomEditor {

    private final Editor editor;
    private final File pomFile;
    private String profileId;

    private PomEditor(Editor editor, File pomFile) {
        this.editor = editor;
        this.pomFile = pomFile;
    }

    /**
     * Loads a {@code pom.xml} file, preserving its structure.
     *
     * @param pomFile the POM file to load
     * @return a new PomEditor instance
     * @throws IOException if the file cannot be read or parsed
     */
    public static PomEditor load(File pomFile) throws IOException {
        try {
            // Reject DOCTYPE declarations to prevent XXE attacks
            String content = new String(Files.readAllBytes(pomFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            String upper = content.toUpperCase(java.util.Locale.ROOT);
            if (upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY")) {
                throw new IOException("DOCTYPE/ENTITY declarations are not allowed in POM files (security risk)");
            }

            Document doc = Document.of(pomFile.toPath());
            Editor ed = new Editor(doc);

            String rootName = ed.root().name();
            if (!"project".equals(rootName)) {
                throw new IOException(
                        "Not a valid POM file: expected <project> root element but found <" + rootName + ">");
            }

            return new PomEditor(ed, pomFile);
        } catch (RuntimeException e) {
            throw new IOException("Failed to parse POM file: " + pomFile, e);
        }
    }

    /**
     * Sets the target profile for subsequent operations. When set, dependency operations
     * target the specified profile's {@code <dependencies>} or {@code <dependencyManagement>}
     * section instead of the top-level project sections.
     *
     * @param profileId the profile {@code <id>} to target, or {@code null} for top-level
     */
    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    /**
     * Finds an existing dependency matching the given groupId and artifactId
     * in the specified section.
     *
     * @param groupId    the groupId to match
     * @param artifactId the artifactId to match
     * @param managed    if {@code true}, search in {@code <dependencyManagement>}
     * @return the matching {@code <dependency>} element, or {@code null} if not found
     */
    public Element findDependency(String groupId, String artifactId, boolean managed) {
        return findDependency(groupId, artifactId, null, null, managed);
    }

    /**
     * Finds a dependency element matching groupId, artifactId, type, and classifier.
     *
     * <p>Type matching: {@code null} and {@code "jar"} are treated as equivalent.
     * Classifier matching: {@code null} matches only dependencies without a classifier.
     *
     * @param groupId    the groupId to match
     * @param artifactId the artifactId to match
     * @param type       the type to match ({@code null} matches "jar" or absent)
     * @param classifier the classifier to match ({@code null} matches absent)
     * @param managed    if {@code true}, search in {@code <dependencyManagement>}
     * @return the matching {@code <dependency>} element, or {@code null} if not found
     */
    public Element findDependency(String groupId, String artifactId, String type, String classifier, boolean managed) {
        if (groupId == null || artifactId == null) {
            throw new IllegalArgumentException("groupId and artifactId must not be null");
        }
        Element depsElement = getDependenciesElement(managed, false);
        if (depsElement == null) {
            return null;
        }

        return dependencyStream(depsElement)
                .filter(dep -> groupId.equals(childText(dep, "groupId"))
                        && artifactId.equals(childText(dep, "artifactId"))
                        && typeMatches(childText(dep, "type"), type)
                        && classifierMatches(childText(dep, "classifier"), classifier))
                .findFirst()
                .orElse(null);
    }

    /**
     * Adds a new dependency to the POM.
     *
     * @param coords the dependency coordinates
     * @param managed if {@code true}, add to {@code <dependencyManagement>}
     */
    public void addDependency(DependencyEntry coords, boolean managed) {
        Element depsElement = getDependenciesElement(managed, true);

        Element dep = editor.addElement(depsElement, "dependency");
        editor.addElement(dep, "groupId", coords.getGroupId());
        editor.addElement(dep, "artifactId", coords.getArtifactId());

        if (coords.getVersion() != null && !coords.getVersion().isEmpty()) {
            editor.addElement(dep, "version", coords.getVersion());
        }
        if (coords.getScope() != null && !coords.getScope().isEmpty()) {
            editor.addElement(dep, "scope", coords.getScope());
        }
        if (coords.getType() != null && !coords.getType().isEmpty()) {
            editor.addElement(dep, "type", coords.getType());
        }
        if (coords.getClassifier() != null && !coords.getClassifier().isEmpty()) {
            editor.addElement(dep, "classifier", coords.getClassifier());
        }
        if (coords.getOptional() != null && coords.getOptional()) {
            editor.addElement(dep, "optional", "true");
        }
    }

    /**
     * Removes a dependency matching groupId and artifactId (any type/classifier).
     *
     * @param groupId    the groupId to match
     * @param artifactId the artifactId to match
     * @param managed    if {@code true}, remove from {@code <dependencyManagement>}
     * @return {@code true} if the dependency was found and removed
     */
    public boolean removeDependency(String groupId, String artifactId, boolean managed) {
        return removeDependency(groupId, artifactId, null, null, managed);
    }

    /**
     * Removes a dependency from the POM matching on groupId, artifactId, type, and classifier.
     *
     * @param groupId    the groupId to match
     * @param artifactId the artifactId to match
     * @param type       the type to match ({@code null} matches "jar" or absent)
     * @param classifier the classifier to match ({@code null} matches absent)
     * @param managed    if {@code true}, remove from {@code <dependencyManagement>}
     * @return {@code true} if the dependency was found and removed
     */
    public boolean removeDependency(
            String groupId, String artifactId, String type, String classifier, boolean managed) {
        Element dep = findDependency(groupId, artifactId, type, classifier, managed);
        if (dep == null) {
            return false;
        }
        editor.removeElement(dep);
        return true;
    }

    /**
     * Writes the modified POM back to disk using an atomic write (temp file + rename).
     *
     * @throws IOException if the file cannot be written
     */
    public void save() throws IOException {
        Path target = pomFile.toPath();
        File tempFile = File.createTempFile("pom", ".xml.tmp", pomFile.getParentFile());
        boolean success = false;
        try {
            try (OutputStream os = Files.newOutputStream(tempFile.toPath())) {
                editor.document().toXml(os);
            }
            Files.move(tempFile.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            success = true;
        } finally {
            if (!success) {
                Files.deleteIfExists(tempFile.toPath());
            }
        }
    }

    /**
     * Finds a {@code <profile>} element with the given {@code <id>}.
     *
     * @param id the profile id to find
     * @return the matching profile element, or {@code null} if not found
     */
    public Element findProfile(String id) {
        if (id == null) {
            return null;
        }
        return editor.root()
                .childElement("profiles")
                .map(profiles -> profiles.childElements("profile")
                        .filter(p -> p.childElement("id")
                                .map(idEl -> id.equals(idEl.textContent()))
                                .orElse(false))
                        .findFirst()
                        .orElse(null))
                .orElse(null);
    }

    /**
     * Adds or updates a property in the {@code <properties>} section.
     *
     * @param name  the property name
     * @param value the property value
     */
    public void addProperty(String name, String value) {
        Element properties = getOrCreateChild(editor.root(), "properties", true);
        Optional<Element> existing = properties.childElement(name);
        if (existing.isPresent()) {
            editor.setTextContent(existing.get(), value);
        } else {
            editor.addElement(properties, name, value);
        }
    }

    /**
     * Returns the version strings from all {@code <dependency>} elements in the given section.
     * Only returns dependencies that have an explicit {@code <version>} child element.
     *
     * @param managed if {@code true}, scan {@code <dependencyManagement><dependencies>}
     * @return a list of version strings (raw text, may include {@code ${...}} references)
     */
    public List<String> getDependencyVersions(boolean managed) {
        List<String> versions = new ArrayList<>();
        Element depsElement = getDependenciesElement(managed, false);
        if (depsElement == null) {
            return versions;
        }
        dependencyStream(depsElement).forEach(dep -> {
            String version = childText(dep, "version");
            if (version != null && !version.isEmpty()) {
                versions.add(version);
            }
        });
        return versions;
    }

    /**
     * Returns the total count of {@code <dependency>} elements in the given section.
     *
     * @param managed if {@code true}, count in {@code <dependencyManagement><dependencies>}
     * @return the number of dependency elements
     */
    public long getDependencyCount(boolean managed) {
        Element depsElement = getDependenciesElement(managed, false);
        if (depsElement == null) {
            return 0;
        }
        return dependencyStream(depsElement).count();
    }

    /**
     * Returns the text content of a child element, or {@code null} if not present.
     */
    static String getChildText(Element parent, String tagName) {
        return parent.childElement(tagName).map(el -> el.textContent().trim()).orElse(null);
    }

    // --- Private helpers ---

    private Element getDependenciesElement(boolean managed, boolean create) {
        Element context = editor.root();

        if (profileId != null) {
            context = findProfile(profileId);
            if (context == null) {
                return null;
            }
        }

        if (managed) {
            Element depMgmt = getOrCreateChild(context, "dependencyManagement", create);
            if (depMgmt == null) {
                return null;
            }
            return getOrCreateChild(depMgmt, "dependencies", create);
        } else {
            return getOrCreateChild(context, "dependencies", create);
        }
    }

    private Element getOrCreateChild(Element parent, String name, boolean create) {
        Optional<Element> existing = parent.childElement(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!create) {
            return null;
        }
        return editor.addElement(parent, name);
    }

    private Stream<Element> dependencyStream(Element depsElement) {
        return depsElement.childElements("dependency");
    }

    private static String childText(Element parent, String name) {
        return parent.childElement(name)
                .map(Element::textContent)
                .map(String::trim)
                .orElse(null);
    }

    private static boolean typeMatches(String depType, String searchType) {
        String effective = (depType == null || depType.isEmpty()) ? "jar" : depType;
        String target = (searchType == null || searchType.isEmpty()) ? "jar" : searchType;
        return effective.equals(target);
    }

    private static boolean classifierMatches(String depClassifier, String searchClassifier) {
        String effective = (depClassifier == null) ? "" : depClassifier;
        String target = (searchClassifier == null) ? "" : searchClassifier;
        return effective.equals(target);
    }
}
