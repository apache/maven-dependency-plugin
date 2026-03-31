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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PomEditorTest {

    @TempDir
    File tempDir;

    private File createTempPom(String content) throws IOException {
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return pomFile;
    }

    @Test
    void addDependencyToEmptyProject() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>test</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "</project>\n";

        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        DependencyCoordinates coords = DependencyCoordinates.parse("com.google.adk:google-adk:1.0.0");
        editor.addDependency(coords, false);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<dependencies>"), "Should contain <dependencies>");
        assertTrue(result.contains("<groupId>com.google.adk</groupId>"), "Should contain groupId");
        assertTrue(result.contains("<artifactId>google-adk</artifactId>"), "Should contain artifactId");
        assertTrue(result.contains("<version>1.0.0</version>"), "Should contain version");
    }

    @Test
    void addDependencyToExistingDependencies() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>test</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "    <dependencies>\n"
                + "        <dependency>\n"
                + "            <groupId>junit</groupId>\n"
                + "            <artifactId>junit</artifactId>\n"
                + "            <version>4.13.2</version>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n"
                + "</project>\n";

        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        DependencyCoordinates coords = DependencyCoordinates.parse("com.google.adk:google-adk:1.0.0");
        coords.setScope("test");
        editor.addDependency(coords, false);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<groupId>com.google.adk</groupId>"));
        assertTrue(result.contains("<scope>test</scope>"));
        // Original dependency still present
        assertTrue(result.contains("<groupId>junit</groupId>"));
    }

    @Test
    void addDependencyToManagedSection() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>test</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "</project>\n";

        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        DependencyCoordinates coords = DependencyCoordinates.parse("com.google.adk:google-adk:1.0.0");
        editor.addDependency(coords, true);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<dependencyManagement>"), "Should contain <dependencyManagement>");
        assertTrue(result.contains("<groupId>com.google.adk</groupId>"));
    }

    @Test
    void addDependencyWithoutVersion() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>test</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "</project>\n";

        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        DependencyCoordinates coords = new DependencyCoordinates("com.google.adk", "google-adk");
        editor.addDependency(coords, false);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<groupId>com.google.adk</groupId>"));
        // Verify the dependency block doesn't contain <version> (project's own <version> may exist)
        int depStart = result.indexOf("<groupId>com.google.adk</groupId>");
        int depBlockEnd = result.indexOf("</dependency>", depStart);
        String depBlock = result.substring(depStart, depBlockEnd);
        assertFalse(depBlock.contains("<version>"), "Should not contain <version> when not specified");
    }

    @Test
    void findExistingDependency() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>test</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "    <dependencies>\n"
                + "        <dependency>\n"
                + "            <groupId>junit</groupId>\n"
                + "            <artifactId>junit</artifactId>\n"
                + "            <version>4.13.2</version>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n"
                + "</project>\n";

        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        Element found = editor.findDependency("junit", "junit", false);
        assertNotNull(found, "Should find existing dependency");

        Element notFound = editor.findDependency("com.example", "nonexistent", false);
        assertNull(notFound, "Should not find nonexistent dependency");
    }

    @Test
    void removeDependency() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>test</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "    <dependencies>\n"
                + "        <dependency>\n"
                + "            <groupId>junit</groupId>\n"
                + "            <artifactId>junit</artifactId>\n"
                + "            <version>4.13.2</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>com.google.guava</groupId>\n"
                + "            <artifactId>guava</artifactId>\n"
                + "            <version>31.0-jre</version>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n"
                + "</project>\n";

        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        boolean removed = editor.removeDependency("com.google.guava", "guava", false);
        assertTrue(removed, "Should successfully remove dependency");
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertFalse(result.contains("<groupId>com.google.guava</groupId>"), "Guava should be removed");
        assertTrue(result.contains("<groupId>junit</groupId>"), "JUnit should still be present");
    }

    @Test
    void removeNonexistentDependencyReturnsFalse() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>test</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "    <dependencies>\n"
                + "        <dependency>\n"
                + "            <groupId>junit</groupId>\n"
                + "            <artifactId>junit</artifactId>\n"
                + "            <version>4.13.2</version>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n"
                + "</project>\n";

        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        boolean removed = editor.removeDependency("com.nonexistent", "lib", false);
        assertFalse(removed, "Should return false for nonexistent dependency");
    }

    @Test
    void updateExistingDependency() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>test</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "    <dependencies>\n"
                + "        <dependency>\n"
                + "            <groupId>junit</groupId>\n"
                + "            <artifactId>junit</artifactId>\n"
                + "            <version>4.13.2</version>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n"
                + "</project>\n";

        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        Element existing = editor.findDependency("junit", "junit", false);
        assertNotNull(existing);

        DependencyCoordinates newCoords = new DependencyCoordinates("junit", "junit");
        newCoords.setVersion("5.0.0");
        newCoords.setScope("test");
        editor.updateDependency(existing, newCoords);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<version>5.0.0</version>"), "Version should be updated");
        assertTrue(result.contains("<scope>test</scope>"), "Scope should be added");
    }

    @Test
    void preservesXmlComments() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>test</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "    <!-- This is a comment -->\n"
                + "    <dependencies>\n"
                + "        <dependency>\n"
                + "            <groupId>junit</groupId>\n"
                + "            <artifactId>junit</artifactId>\n"
                + "            <version>4.13.2</version>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n"
                + "</project>\n";

        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        DependencyCoordinates coords = DependencyCoordinates.parse("com.example:new-lib:1.0.0");
        editor.addDependency(coords, false);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<!-- This is a comment -->"), "XML comment should be preserved");
    }

    @Test
    void addBomDependency() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>test</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "</project>\n";

        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        DependencyCoordinates coords =
                DependencyCoordinates.parse("org.springframework.boot:spring-boot-dependencies:3.2.0");
        coords.setScope("import");
        coords.setType("pom");
        editor.addDependency(coords, true);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<dependencyManagement>"));
        assertTrue(result.contains("<scope>import</scope>"));
        assertTrue(result.contains("<type>pom</type>"));
    }
}
