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

import eu.maveniverse.domtrip.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        DependencyEntry coords = DependencyEntry.parse("com.google.adk:google-adk:1.0.0");
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

        DependencyEntry coords = DependencyEntry.parse("com.google.adk:google-adk:1.0.0");
        coords.setScope("test");
        editor.addDependency(coords, false);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<groupId>com.google.adk</groupId>"), result);
        assertTrue(result.contains("<scope>test</scope>"), result);
        // Original dependency still present
        assertTrue(result.contains("<groupId>junit</groupId>"), result);
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

        DependencyEntry coords = DependencyEntry.parse("com.google.adk:google-adk:1.0.0");
        editor.addDependency(coords, true);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<dependencyManagement>"), "Should contain <dependencyManagement>");
        assertTrue(result.contains("<groupId>com.google.adk</groupId>"), result);
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

        DependencyEntry coords = new DependencyEntry("com.google.adk", "google-adk");
        editor.addDependency(coords, false);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<groupId>com.google.adk</groupId>"), result);
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

        DependencyEntry newCoords = new DependencyEntry("junit", "junit");
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

        DependencyEntry coords = DependencyEntry.parse("com.example:new-lib:1.0.0");
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

        DependencyEntry coords = DependencyEntry.parse("org.springframework.boot:spring-boot-dependencies:3.2.0");
        coords.setScope("import");
        coords.setType("pom");
        editor.addDependency(coords, true);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<dependencyManagement>"), result);
        assertTrue(result.contains("<scope>import</scope>"), result);
        assertTrue(result.contains("<type>pom</type>"), result);
    }

    @Test
    void addDependencyWithNamespacedPom() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 "
                + "http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
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

        // Should find existing dependency in namespaced POM
        Element found = editor.findDependency("junit", "junit", false);
        assertNotNull(found, "Should find dependency in namespaced POM");

        // Should add new dependency to namespaced POM
        DependencyEntry coords = DependencyEntry.parse("com.google.adk:google-adk:1.0.0");
        editor.addDependency(coords, false);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<groupId>com.google.adk</groupId>"), result);
        assertTrue(result.contains("xmlns=\"http://maven.apache.org/POM/4.0.0\""), "Namespace should be preserved");
    }

    @Test
    void addOptionalDependency() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>test</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "</project>\n";

        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        DependencyEntry coords = DependencyEntry.parse("com.example:optional-lib:1.0.0");
        coords.setOptional(true);
        editor.addDependency(coords, false);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<optional>true</optional>"), "Should contain <optional>true</optional>");
    }

    @Test
    void pomWithoutXmlDeclaration() throws IOException {
        String pom = "<project>\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>test</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "</project>\n";

        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        DependencyEntry coords = DependencyEntry.parse("com.example:lib:1.0.0");
        editor.addDependency(coords, false);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertFalse(result.startsWith("<?xml"), "Should not add XML declaration if original didn't have one");
        assertTrue(result.contains("<groupId>com.example</groupId>"), result);
    }

    @Test
    void removeWithPrecedingComment() throws IOException {
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
                + "        <!-- Guava for collections -->\n"
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
        assertTrue(removed);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertFalse(result.contains("com.google.guava"), "Guava should be removed");
        assertTrue(result.contains("<groupId>junit</groupId>"), "JUnit should remain");
    }

    @Test
    void preservesBom() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>test</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "</project>\n";

        // Write with BOM
        byte[] bomBytes = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] contentBytes = pom.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bomBytes.length + contentBytes.length];
        System.arraycopy(bomBytes, 0, withBom, 0, bomBytes.length);
        System.arraycopy(contentBytes, 0, withBom, bomBytes.length, contentBytes.length);

        File pomFile = new File(tempDir, "bom-pom.xml");
        Files.write(pomFile.toPath(), withBom);

        PomEditor editor = PomEditor.load(pomFile);
        DependencyEntry coords = DependencyEntry.parse("com.example:lib:1.0.0");
        editor.addDependency(coords, false);
        editor.save();

        byte[] result = Files.readAllBytes(pomFile.toPath());
        assertTrue(
                result.length >= 3 && result[0] == (byte) 0xEF && result[1] == (byte) 0xBB && result[2] == (byte) 0xBF,
                "BOM should be preserved");
    }

    @Test
    void findDependencyByTypeAndClassifier() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>junit</groupId>\n"
                + "      <artifactId>junit</artifactId>\n"
                + "      <version>4.13</version>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>junit</groupId>\n"
                + "      <artifactId>junit</artifactId>\n"
                + "      <version>4.13</version>\n"
                + "      <type>test-jar</type>\n"
                + "      <scope>test</scope>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        // Default (no type/classifier) matches the first (jar) entry
        Element defaultMatch = editor.findDependency("junit", "junit", null, null, false);
        assertNotNull(defaultMatch);
        assertNull(PomEditor.getChildText(defaultMatch, "type"));

        // Explicit test-jar type matches the second entry
        Element testJarMatch = editor.findDependency("junit", "junit", "test-jar", null, false);
        assertNotNull(testJarMatch);
        assertEquals("test-jar", PomEditor.getChildText(testJarMatch, "type"));

        // Non-existent classifier returns null
        Element noMatch = editor.findDependency("junit", "junit", null, "sources", false);
        assertNull(noMatch);
    }

    @Test
    void removeDependencyByTypeAndClassifier() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>junit</groupId>\n"
                + "      <artifactId>junit</artifactId>\n"
                + "      <version>4.13</version>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>junit</groupId>\n"
                + "      <artifactId>junit</artifactId>\n"
                + "      <version>4.13</version>\n"
                + "      <type>test-jar</type>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        // Remove only the test-jar variant
        assertTrue(editor.removeDependency("junit", "junit", "test-jar", null, false));
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        // The default jar variant should still be there
        assertTrue(result.contains("<groupId>junit</groupId>"), result);
        assertFalse(result.contains("<type>test-jar</type>"), result);
    }

    @Test
    void findDependencyWithClassifier() throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>com.example</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "      <version>1.0</version>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>com.example</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "      <version>1.0</version>\n"
                + "      <classifier>sources</classifier>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        PomEditor editor = PomEditor.load(pomFile);

        // Match by classifier
        Element sourcesMatch = editor.findDependency("com.example", "lib", null, "sources", false);
        assertNotNull(sourcesMatch);
        assertEquals("sources", PomEditor.getChildText(sourcesMatch, "classifier"));

        // Default (no classifier) matches the one without classifier
        Element defaultMatch = editor.findDependency("com.example", "lib", null, null, false);
        assertNotNull(defaultMatch);
        assertNull(PomEditor.getChildText(defaultMatch, "classifier"));
    }

    @Test
    void rejectsNonProjectRootElement() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<settings>\n"
                + "  <localRepository>/tmp/repo</localRepository>\n"
                + "</settings>\n";
        File pomFile = createTempPom(xml);
        IOException ex = assertThrows(IOException.class, () -> PomEditor.load(pomFile));
        assertTrue(ex.getMessage().contains("<settings>"));
    }

    @Test
    void rejectsDoctypeDeclaration() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE project [\n"
                + "  <!ENTITY xxe \"malicious\">\n"
                + "]>\n"
                + "<project>\n"
                + "  <groupId>&xxe;</groupId>\n"
                + "</project>\n";
        File pomFile = createTempPom(xml);
        assertThrows(IOException.class, () -> PomEditor.load(pomFile));
    }

    @Test
    void bomPrefixedFilePreservesXmlDeclaration() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <groupId>com.example</groupId>\n"
                + "</project>\n";
        // Write with UTF-8 BOM prefix
        byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] bomPrefixed = new byte[bom.length + xmlBytes.length];
        System.arraycopy(bom, 0, bomPrefixed, 0, bom.length);
        System.arraycopy(xmlBytes, 0, bomPrefixed, bom.length, xmlBytes.length);

        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), bomPrefixed);

        PomEditor editor = PomEditor.load(pomFile);
        DependencyEntry coords = new DependencyEntry("junit", "junit");
        coords.setVersion("4.13");
        editor.addDependency(coords, false);
        editor.save();

        byte[] resultBytes = Files.readAllBytes(pomFile.toPath());
        // BOM should still be present
        assertEquals((byte) 0xEF, resultBytes[0]);
        assertEquals((byte) 0xBB, resultBytes[1]);
        assertEquals((byte) 0xBF, resultBytes[2]);
        // XML declaration should be preserved
        String result = new String(resultBytes, StandardCharsets.UTF_8);
        assertTrue(result.contains("<?xml"), result);
    }

    @Test
    void updateDependencyClearsFieldsWithEmptyString() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>com.example</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "      <version>1.0</version>\n"
                + "      <scope>test</scope>\n"
                + "      <optional>true</optional>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        PomEditor editor = PomEditor.load(pomFile);
        Element dep = editor.findDependency("com.example", "lib", false);
        assertNotNull(dep);

        // Empty string signals removal
        DependencyEntry coords = new DependencyEntry("com.example", "lib");
        coords.setScope(""); // clear scope
        coords.setOptional(false); // clear optional

        editor.updateDependency(dep, coords);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(!result.contains("<scope>"), "scope element should be removed");
        assertTrue(!result.contains("<optional>"), "optional element should be removed");
        assertTrue(result.contains("<version>1.0</version>"), "version should remain");
    }

    @Test
    void updateDependencyPreservesFieldsWhenNull() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>com.example</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "      <version>1.0</version>\n"
                + "      <scope>test</scope>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        PomEditor editor = PomEditor.load(pomFile);
        Element dep = editor.findDependency("com.example", "lib", false);
        assertNotNull(dep);

        // Only update version, leave scope (null means don't touch)
        DependencyEntry coords = new DependencyEntry("com.example", "lib");
        coords.setVersion("2.0");

        editor.updateDependency(dep, coords);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<version>2.0</version>"), "version should be updated");
        assertTrue(result.contains("<scope>test</scope>"), "scope should be preserved");
    }

    @Test
    void addDependencyIgnoresEmptyStringFields() throws IOException {
        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<project>\n" + "  <dependencies/>\n" + "</project>\n";
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        PomEditor editor = PomEditor.load(pomFile);
        DependencyEntry coords = new DependencyEntry("com.example", "lib");
        coords.setVersion("1.0");
        coords.setScope(""); // empty = NONE sentinel, should not create element
        coords.setType(""); // same
        coords.setClassifier(""); // same
        editor.addDependency(coords, false);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<version>1.0</version>"), "version should be present");
        assertTrue(!result.contains("<scope>"), "empty scope should not create element");
        assertTrue(!result.contains("<type>"), "empty type should not create element");
        assertTrue(!result.contains("<classifier>"), "empty classifier should not create element");
    }

    @Test
    void addDependencyToExistingProfileWithNoDeps() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>existing</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "  <profiles>\n"
                + "    <profile>\n"
                + "      <id>test-profile</id>\n"
                + "    </profile>\n"
                + "  </profiles>\n"
                + "</project>\n";
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        PomEditor editor = PomEditor.load(pomFile);
        editor.setProfileId("test-profile");
        DependencyEntry coords = new DependencyEntry("com.example", "test-lib");
        coords.setVersion("1.0");
        coords.setScope("test");
        editor.addDependency(coords, false);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<id>test-profile</id>"), "profile id should exist");
        assertTrue(result.contains("<groupId>com.example</groupId>"), "dependency should be added");
        assertTrue(result.contains("<scope>test</scope>"), "scope should be present");
        // Original top-level dependency should still exist
        assertTrue(result.contains("<groupId>existing</groupId>"), "existing dependency should remain");
    }

    @Test
    void addDependencyToExistingProfile() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <profiles>\n"
                + "    <profile>\n"
                + "      <id>dev</id>\n"
                + "      <dependencies>\n"
                + "        <dependency>\n"
                + "          <groupId>existing</groupId>\n"
                + "          <artifactId>lib</artifactId>\n"
                + "        </dependency>\n"
                + "      </dependencies>\n"
                + "    </profile>\n"
                + "  </profiles>\n"
                + "</project>\n";
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        PomEditor editor = PomEditor.load(pomFile);
        editor.setProfileId("dev");
        DependencyEntry coords = new DependencyEntry("com.example", "new-lib");
        coords.setVersion("2.0");
        editor.addDependency(coords, false);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<groupId>existing</groupId>"), "existing profile dep should remain");
        assertTrue(result.contains("<groupId>com.example</groupId>"), "new dep should be added");
        assertTrue(result.contains("<version>2.0</version>"), "version should be present");
        // Should not create a second profile
        assertEquals(1, countOccurrences(result, "<profile>"), "should reuse existing profile");
    }

    @Test
    void removeDependencyFromProfile() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>com.example</groupId>\n"
                + "      <artifactId>top-level</artifactId>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "  <profiles>\n"
                + "    <profile>\n"
                + "      <id>dev</id>\n"
                + "      <dependencies>\n"
                + "        <dependency>\n"
                + "          <groupId>com.example</groupId>\n"
                + "          <artifactId>profile-lib</artifactId>\n"
                + "          <version>1.0</version>\n"
                + "        </dependency>\n"
                + "      </dependencies>\n"
                + "    </profile>\n"
                + "  </profiles>\n"
                + "</project>\n";
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        PomEditor editor = PomEditor.load(pomFile);
        editor.setProfileId("dev");
        boolean removed = editor.removeDependency("com.example", "profile-lib", false);
        editor.save();

        assertTrue(removed, "should find and remove the profile dependency");
        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(!result.contains("profile-lib"), "profile dep should be removed");
        assertTrue(result.contains("top-level"), "top-level dep should remain");
    }

    @Test
    void findDependencyInProfileDoesNotFindTopLevel() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>com.example</groupId>\n"
                + "      <artifactId>top-level</artifactId>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "  <profiles>\n"
                + "    <profile>\n"
                + "      <id>dev</id>\n"
                + "    </profile>\n"
                + "  </profiles>\n"
                + "</project>\n";
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        PomEditor editor = PomEditor.load(pomFile);
        editor.setProfileId("dev");
        Element found = editor.findDependency("com.example", "top-level", false);
        assertNull(found, "should not find top-level dep when targeting a profile");
    }

    @Test
    void findProfileReturnsNullForNonexistent() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <profiles>\n"
                + "    <profile>\n"
                + "      <id>dev</id>\n"
                + "    </profile>\n"
                + "  </profiles>\n"
                + "</project>\n";
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        PomEditor editor = PomEditor.load(pomFile);
        assertNull(editor.findProfile("nonexistent"), "should return null for non-existent profile");
        assertNotNull(editor.findProfile("dev"), "should find existing profile");
    }

    @Test
    void findProfileReturnsNullWhenNoProfilesSection() throws IOException {
        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<project>\n" + "  <dependencies/>\n" + "</project>\n";
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        PomEditor editor = PomEditor.load(pomFile);
        assertNull(editor.findProfile("any"), "should return null when no profiles section exists");
    }

    @Test
    void addManagedDependencyToProfile() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <profiles>\n"
                + "    <profile>\n"
                + "      <id>dev</id>\n"
                + "    </profile>\n"
                + "  </profiles>\n"
                + "</project>\n";
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        PomEditor editor = PomEditor.load(pomFile);
        editor.setProfileId("dev");
        DependencyEntry coords = new DependencyEntry("com.example", "lib");
        coords.setVersion("1.0");
        editor.addDependency(coords, true);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<dependencyManagement>"), "dependencyManagement should be created in profile");
        assertTrue(result.contains("<groupId>com.example</groupId>"), "dependency should be added");
        assertTrue(result.contains("<id>dev</id>"), "profile id should remain");
    }

    @Test
    void removeDependencyWhenSectionAbsentReturnsFalse() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "</project>\n";
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        PomEditor editor = PomEditor.load(pomFile);
        assertFalse(
                editor.removeDependency("com.example", "lib", false),
                "should return false when no dependencies section exists");
        assertFalse(
                editor.removeDependency("com.example", "lib", true),
                "should return false when no dependencyManagement section exists");
    }

    @Test
    void updateDependencyTypeAndClassifierAndOptional() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>com.example</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "      <version>1.0</version>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        PomEditor editor = PomEditor.load(pomFile);
        Element dep = editor.findDependency("com.example", "lib", false);
        assertNotNull(dep);

        DependencyEntry coords = new DependencyEntry("com.example", "lib");
        coords.setVersion("2.0");
        coords.setType("pom");
        coords.setClassifier("sources");
        coords.setOptional(true);
        editor.updateDependency(dep, coords);
        editor.save();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<version>2.0</version>"), "version should be updated");
        assertTrue(result.contains("<type>pom</type>"), "type should be set");
        assertTrue(result.contains("<classifier>sources</classifier>"), "classifier should be set");
        assertTrue(result.contains("<optional>true</optional>"), "optional should be set");
    }

    private static int countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
