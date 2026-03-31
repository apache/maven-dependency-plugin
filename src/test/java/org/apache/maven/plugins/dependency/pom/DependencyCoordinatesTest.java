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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DependencyCoordinatesTest {

    @Test
    void parseMinimalGav() {
        DependencyCoordinates coords = DependencyCoordinates.parse("com.google.adk:google-adk");
        assertEquals("com.google.adk", coords.getGroupId());
        assertEquals("google-adk", coords.getArtifactId());
        assertNull(coords.getVersion());
        assertNull(coords.getScope());
        assertNull(coords.getType());
        assertNull(coords.getClassifier());
    }

    @Test
    void parseWithVersion() {
        DependencyCoordinates coords = DependencyCoordinates.parse("com.google.adk:google-adk:1.0.0");
        assertEquals("com.google.adk", coords.getGroupId());
        assertEquals("google-adk", coords.getArtifactId());
        assertEquals("1.0.0", coords.getVersion());
    }

    @Test
    void parseWithVersionAndScope() {
        DependencyCoordinates coords = DependencyCoordinates.parse("com.google.adk:google-adk:1.0.0:test");
        assertEquals("com.google.adk", coords.getGroupId());
        assertEquals("google-adk", coords.getArtifactId());
        assertEquals("1.0.0", coords.getVersion());
        assertEquals("test", coords.getScope());
    }

    @Test
    void parseFullForm() {
        DependencyCoordinates coords =
                DependencyCoordinates.parse("com.google.adk:google-adk:1.0.0:compile:jar:sources");
        assertEquals("com.google.adk", coords.getGroupId());
        assertEquals("google-adk", coords.getArtifactId());
        assertEquals("1.0.0", coords.getVersion());
        assertEquals("compile", coords.getScope());
        assertEquals("jar", coords.getType());
        assertEquals("sources", coords.getClassifier());
    }

    @Test
    void parseEmptyOptionalFields() {
        DependencyCoordinates coords = DependencyCoordinates.parse("g:a::test");
        assertEquals("g", coords.getGroupId());
        assertEquals("a", coords.getArtifactId());
        assertNull(coords.getVersion());
        assertEquals("test", coords.getScope());
    }

    @Test
    void parseInvalidTooFewTokens() {
        assertThrows(IllegalArgumentException.class, () -> DependencyCoordinates.parse("only-one"));
    }

    @Test
    void parseInvalidTooManyTokens() {
        assertThrows(IllegalArgumentException.class, () -> DependencyCoordinates.parse("a:b:c:d:e:f:g"));
    }

    @Test
    void parseTrailingColonRejectsEmptyGroupId() {
        assertThrows(IllegalArgumentException.class, () -> DependencyCoordinates.parse(":artifactId"));
    }

    @Test
    void parseTrailingColonRejectsEmptyArtifactId() {
        assertThrows(IllegalArgumentException.class, () -> DependencyCoordinates.parse("groupId:"));
    }

    @Test
    void parseDoubleColonRejectsEmptyFields() {
        assertThrows(IllegalArgumentException.class, () -> DependencyCoordinates.parse("::"));
    }

    @Test
    void parseTrailingColonsAcceptedWhenOptionalFieldsEmpty() {
        // "g:a:" has 3 tokens with -1 split limit, 3rd is empty = valid (version empty)
        DependencyCoordinates coords = DependencyCoordinates.parse("g:a:");
        assertEquals("g", coords.getGroupId());
        assertEquals("a", coords.getArtifactId());
        assertNull(coords.getVersion());
    }

    @Test
    void parseNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> DependencyCoordinates.parse(null));
    }

    @Test
    void parseEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> DependencyCoordinates.parse(""));
    }

    @Test
    void validateSuccess() {
        DependencyCoordinates coords = new DependencyCoordinates("g", "a");
        coords.validate(); // should not throw
    }

    @Test
    void validateMissingGroupId() {
        DependencyCoordinates coords = new DependencyCoordinates(null, "a");
        assertThrows(IllegalArgumentException.class, coords::validate);
    }

    @Test
    void validateMissingArtifactId() {
        DependencyCoordinates coords = new DependencyCoordinates("g", null);
        assertThrows(IllegalArgumentException.class, coords::validate);
    }

    @Test
    void managementKeyDefaultType() {
        DependencyCoordinates coords = new DependencyCoordinates("com.example", "my-lib");
        assertEquals("com.example:my-lib:jar", coords.getManagementKey());
    }

    @Test
    void managementKeyWithTypeAndClassifier() {
        DependencyCoordinates coords = new DependencyCoordinates("com.example", "my-lib");
        coords.setType("pom");
        coords.setClassifier("sources");
        assertEquals("com.example:my-lib:pom:sources", coords.getManagementKey());
    }

    @Test
    void toStringWithVersion() {
        DependencyCoordinates coords = DependencyCoordinates.parse("com.example:my-lib:2.0.0");
        assertEquals("com.example:my-lib:2.0.0", coords.toString());
    }

    @Test
    void toStringWithoutVersion() {
        DependencyCoordinates coords = new DependencyCoordinates("com.example", "my-lib");
        assertEquals("com.example:my-lib", coords.toString());
    }

    @Test
    void settersWork() {
        DependencyCoordinates coords = new DependencyCoordinates();
        coords.setGroupId("g");
        coords.setArtifactId("a");
        coords.setVersion("v");
        coords.setScope("test");
        coords.setType("pom");
        coords.setClassifier("cls");
        coords.setOptional(true);

        assertEquals("g", coords.getGroupId());
        assertEquals("a", coords.getArtifactId());
        assertEquals("v", coords.getVersion());
        assertEquals("test", coords.getScope());
        assertEquals("pom", coords.getType());
        assertEquals("cls", coords.getClassifier());
        assertEquals(true, coords.getOptional());
    }

    @Test
    void parseTrimsWhitespace() {
        DependencyCoordinates coords = DependencyCoordinates.parse(" com.example : my-lib : 1.0.0 ");
        assertEquals("com.example", coords.getGroupId());
        assertEquals("my-lib", coords.getArtifactId());
        assertEquals("1.0.0", coords.getVersion());
    }
}
