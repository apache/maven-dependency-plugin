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
    void parseWithExtensionAndVersion() {
        // g:a:ext:v format
        DependencyCoordinates coords = DependencyCoordinates.parse("com.google.adk:google-adk:pom:1.0.0");
        assertEquals("com.google.adk", coords.getGroupId());
        assertEquals("google-adk", coords.getArtifactId());
        assertEquals("1.0.0", coords.getVersion());
        assertEquals("pom", coords.getType());
    }

    @Test
    void parseFullForm() {
        // g:a:ext:cls:v format
        DependencyCoordinates coords = DependencyCoordinates.parse("com.google.adk:google-adk:jar:sources:1.0.0");
        assertEquals("com.google.adk", coords.getGroupId());
        assertEquals("google-adk", coords.getArtifactId());
        assertEquals("1.0.0", coords.getVersion());
        assertNull(coords.getScope());
        assertEquals("jar", coords.getType());
        assertEquals("sources", coords.getClassifier());
    }

    @Test
    void parseEmptyOptionalFields() {
        // g:a:ext:v with empty extension
        DependencyCoordinates coords = DependencyCoordinates.parse("g:a::1.0.0");
        assertEquals("g", coords.getGroupId());
        assertEquals("a", coords.getArtifactId());
        assertEquals("1.0.0", coords.getVersion());
        assertNull(coords.getType());
    }

    @Test
    void parseInvalidTooFewTokens() {
        assertThrows(IllegalArgumentException.class, () -> DependencyCoordinates.parse("only-one"));
    }

    @Test
    void parseInvalidTooManyTokens() {
        assertThrows(IllegalArgumentException.class, () -> DependencyCoordinates.parse("a:b:c:d:e:f"));
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

    @Test
    void toStringIncludesScope() {
        DependencyCoordinates coords = DependencyCoordinates.parse("com.example:lib:1.0.0");
        coords.setScope("test");
        assertEquals("com.example:lib:1.0.0 [scope=test]", coords.toString());
    }

    @Test
    void toStringIncludesNonJarType() {
        DependencyCoordinates coords = DependencyCoordinates.parse("com.example:lib:1.0.0");
        coords.setType("pom");
        assertEquals("com.example:lib:1.0.0 [type=pom]", coords.toString());
    }

    @Test
    void toStringIncludesClassifier() {
        DependencyCoordinates coords = DependencyCoordinates.parse("com.example:lib:1.0.0");
        coords.setClassifier("sources");
        assertEquals("com.example:lib:1.0.0 [classifier=sources]", coords.toString());
    }

    @Test
    void toStringIncludesAllDetails() {
        DependencyCoordinates coords = DependencyCoordinates.parse("com.example:lib:1.0.0");
        coords.setType("pom");
        coords.setClassifier("linux");
        coords.setScope("provided");
        assertEquals("com.example:lib:1.0.0 [type=pom, classifier=linux, scope=provided]", coords.toString());
    }

    @Test
    void toStringOmitsDefaultJarType() {
        DependencyCoordinates coords = DependencyCoordinates.parse("com.example:lib:1.0.0");
        coords.setType("jar");
        assertEquals("com.example:lib:1.0.0", coords.toString());
    }

    @Test
    void validateAcceptsValidScopes() {
        String[] validScopes = {"compile", "provided", "runtime", "test", "system", "import"};
        for (String scope : validScopes) {
            DependencyCoordinates coords = new DependencyCoordinates("g", "a");
            coords.setScope(scope);
            coords.validate(); // should not throw
        }
    }

    @Test
    void validateRejectsInvalidScope() {
        DependencyCoordinates coords = new DependencyCoordinates("g", "a");
        coords.setScope("bananas");
        assertThrows(IllegalArgumentException.class, coords::validate);
    }

    @Test
    void validateAcceptsNullScope() {
        DependencyCoordinates coords = new DependencyCoordinates("g", "a");
        coords.validate(); // null scope is fine (defaults to compile)
    }

    @Test
    void validateAcceptsEmptyScopeForClearing() {
        DependencyCoordinates coords = new DependencyCoordinates("g", "a");
        coords.setScope("");
        coords.validate(); // empty scope is accepted
    }

    @Test
    void validateRejectsNoneScope() {
        DependencyCoordinates coords = new DependencyCoordinates("g", "a");
        coords.setScope("NONE");
        assertThrows(IllegalArgumentException.class, coords::validate);
    }
}
