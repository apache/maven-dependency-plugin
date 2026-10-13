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
package org.apache.maven.plugins.dependency.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArtifactCoordinatesTest {

    private static ArtifactCoordinates parse(String artifact) {
        return ArtifactCoordinates.parse(artifact.split(":"), artifact);
    }

    private static void assertCoordinates(String artifact, String version, String packaging, String classifier) {
        ArtifactCoordinates c = parse(artifact);
        assertEquals("g", c.getGroupId(), artifact);
        assertEquals("a", c.getArtifactId(), artifact);
        assertEquals(version, c.getVersion(), artifact);
        assertEquals(packaging, c.getPackaging(), artifact);
        assertEquals(classifier, c.getClassifier(), artifact);
    }

    @Test
    void legacyShapes() {
        assertCoordinates("g:a:1.0", "1.0", null, null);
        assertCoordinates("g:a:1.0:war", "1.0", "war", null);
        assertCoordinates("g:a:1.0:jar:sources", "1.0", "jar", "sources");
        assertCoordinates("g:a:1.0-SNAPSHOT:test-jar:tests", "1.0-SNAPSHOT", "test-jar", "tests");
    }

    @Test
    void anyThreePartStringIsLegacy() {
        assertCoordinates("g:a:v1", "v1", null, null);
        assertCoordinates("g:a:SNAPSHOT", "SNAPSHOT", null, null);
    }

    @Test
    void mavenShapes() {
        // as printed by Artifact.toString() and dependency:list
        assertCoordinates("g:a:jar:1.0", "1.0", "jar", null);
        assertCoordinates("g:a:jar:1.0:compile", "1.0", "jar", null);
        assertCoordinates("g:a:test-jar:tests:1.0", "1.0", "test-jar", "tests");
        assertCoordinates("g:a:jar:sources:1.0:test", "1.0", "jar", "sources");
        assertCoordinates("g:a:jar:1.0-SNAPSHOT", "1.0-SNAPSHOT", "jar", null);
    }

    @ParameterizedTest
    @CsvSource({"LATEST", "RELEASE", "'[1.0,2.0)'", "'(,2.0]'"})
    void versionKeywordsAndRangesAreVersions(String version) {
        assertCoordinates("g:a:jar:" + version, version, "jar", null);
        assertCoordinates("g:a:" + version + ":jar", version, "jar", null);
    }

    @Test
    void withoutAVersionAtTheMavenPositionTheLegacyFormIsRead() {
        assertCoordinates("g:a:c:d", "c", "d", null);
        assertCoordinates("g:a:c:d:e", "c", "d", "e");
        assertCoordinates("g:a:master-SNAPSHOT:jar", "master-SNAPSHOT", "jar", null);
        // 1.0 is in the classifier position of the legacy form
        assertCoordinates("g:a:1.0:jar:test", "1.0", "jar", "test");
        assertCoordinates("g:a:c:tests:test", "c", "tests", "test");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // the third and the Maven version position both look like versions
                "g:a:1.0:2.0",
                "g:a:1.0:jar:2.0",
                "g:a:1.0:2.0:test",
                // too few or too many parts
                "g:a",
                "g",
                "g:a:jar:sources:1.0:test:extra",
                // sixth part is not a scope, and without destFileName there is no other reading
                "g:a:jar:sources:1.0:extra",
                "g:a:1.0:jar:cls:file.jar"
            })
    void ambiguousOrMalformedStringsAreRejected(String artifact) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(artifact));
        assertEquals(true, e.getMessage().startsWith("Invalid artifact format: " + artifact), e.getMessage());
    }

    @Test
    void theMessageNamesBothForms() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("g:a:1.0:2.0"));

        assertEquals(
                "Invalid artifact format: g:a:1.0:2.0, both the third and the last part look like the version,"
                        + " expected groupId:artifactId:version[:packaging[:classifier]]"
                        + " or groupId:artifactId:type[:classifier]:version[:scope]",
                e.getMessage());
    }

    @Test
    void emptyPartsAreKept() {
        // a caller that keeps empty parts passes them on, as before
        ArtifactCoordinates c = ArtifactCoordinates.parse(new String[] {"g", "a", "1.0", "", "x"}, "g:a:1.0::x");
        assertEquals("", c.getPackaging());
        assertEquals("x", c.getClassifier());
        assertNull(ArtifactCoordinates.parse(new String[] {"g", "a", "1.0"}, "g:a:1.0")
                .getClassifier());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "jar",
                "pom",
                "war",
                "ear",
                "test-jar",
                "maven-plugin",
                "ejb",
                "ejb-client",
                "rar",
                "zip",
                "java-source",
                "javadoc"
            })
    void aWellKnownTypeAsThirdPartMakesItTheMavenFormWhateverTheVersionLooksLike(String type) {
        assertCoordinates("g:a:" + type + ":v1.2", "v1.2", type, null);
        assertCoordinates("g:a:" + type + ":v1.2:test", "v1.2", type, null);
        assertCoordinates("g:a:" + type + ":cls:v1.2", "v1.2", type, "cls");
    }

    @Test
    void aThirdPartThatIsNotAWellKnownTypeStaysLegacy() {
        assertCoordinates("g:a:jars:v1.2", "jars", "v1.2", null);
    }

    @Test
    void theMavenFormRejectsEmptyTypeAndVersion() {
        for (String artifact : new String[] {"g:a::1.0", "g:a::cls:1.0", "g:a::1.0:test", "g:a::cls:1.0:test"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(artifact), artifact);
            assertEquals(true, e.getMessage().startsWith("Invalid artifact format: " + artifact), e.getMessage());
        }
    }

    @Test
    void sixPartsAreTheMavenFormIfTheLastIsAScopeAndTheFifthLooksLikeAVersion() {
        assertCoordinates("g:a:jar:cls:1.0:compile", "1.0", "jar", "cls");
        assertCoordinates("g:a:test-jar:tests:1.2:test", "1.2", "test-jar", "tests");
    }

    private static ArtifactCoordinates parseWithDestFileName(String artifact) {
        return ArtifactCoordinates.parse(artifact.split(":", -1), artifact, true);
    }

    @Test
    void sixPartsAreLegacyWithDestFileNameIfTheThirdLooksLikeAVersion() {
        ArtifactCoordinates c = parseWithDestFileName("g:a:1.0:jar:cls:renamed.jar");
        assertEquals("1.0", c.getVersion());
        assertEquals("jar", c.getPackaging());
        assertEquals("cls", c.getClassifier());
        assertEquals("renamed.jar", c.getDestFileName());

        c = parseWithDestFileName("g:a:1.0:jar::renamed.jar");
        assertEquals("", c.getClassifier());
        assertEquals("renamed.jar", c.getDestFileName());
    }

    @Test
    void sixPartsAreTheMavenFormBeforeTheyAreLegacyWithDestFileName() {
        ArtifactCoordinates c = parseWithDestFileName("g:a:jar:cls:1.0:test");
        assertEquals("1.0", c.getVersion());
        assertEquals("jar", c.getPackaging());
        assertEquals("cls", c.getClassifier());
        assertNull(c.getDestFileName());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"g:a:c:d:e:f.jar", "g:a:jar:cls:v1.2:f.jar", "g:a:jar:cls::test", "g:a:1.0:jar:cls:f.jar:x"})
    void sixPartsThatFitNeitherFormAreRejected(String artifact) {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> parseWithDestFileName(artifact));
        assertEquals(true, e.getMessage().contains("[:classifier[:destFileName]]"), e.getMessage());
    }

    @Test
    void theLegacyFormWithoutDestFileNameKeepsItsOwnMessage() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> parse("g:a:1.0:jar:cls:renamed.jar"));
        assertEquals(false, e.getMessage().contains("destFileName"), e.getMessage());
    }

    @Test
    void anEmptyVersionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parseWithDestFileName("g:a:"));
        assertThrows(IllegalArgumentException.class, () -> parseWithDestFileName("g:a::jar"));
    }
}
