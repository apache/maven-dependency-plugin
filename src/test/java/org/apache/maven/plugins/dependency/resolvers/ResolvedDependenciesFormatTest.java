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
package org.apache.maven.plugins.dependency.resolvers;

import java.io.File;
import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedDependenciesFormatTest {

    private static final String NL = System.lineSeparator();

    private static Artifact artifact(
            String artifactId, String version, String scope, String type, String classifier, boolean optional) {
        DefaultArtifactHandler handler = new DefaultArtifactHandler(type) {
            @Override
            public String getClassifier() {
                return "test-jar".equals(type) ? "tests" : null;
            }
        };
        DefaultArtifact artifact = new DefaultArtifact("g", artifactId, version, scope, type, classifier, handler);
        artifact.setOptional(optional);
        return artifact;
    }

    private static Set<Artifact> set(Artifact... artifacts) {
        return new LinkedHashSet<>(Arrays.asList(artifacts));
    }

    private static JsonObject parse(String json) {
        System.setProperty("jakarta.json.provider", "org.glassfish.json.JsonProviderImpl");
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }

    @Test
    void pomOmitsTypeJarClassifierNoneAndScopeCompile() {
        DependencyStatusSets sets =
                new DependencyStatusSets(set(artifact("a", "1.0", "compile", "jar", null, false)), null, null);

        assertEquals(
                "<dependencies>" + NL
                        + "  <dependency>" + NL
                        + "    <groupId>g</groupId>" + NL
                        + "    <artifactId>a</artifactId>" + NL
                        + "    <version>1.0</version>" + NL
                        + "  </dependency>" + NL
                        + "</dependencies>" + NL,
                ResolvedDependenciesFormat.pom(sets, false));
    }

    @Test
    void pomWritesTypeClassifierAndScopeWhenTheyAreNotTheDefaults() {
        DependencyStatusSets sets = new DependencyStatusSets(
                set(
                        artifact("b", "2.0", "test", "test-jar", "tests", false),
                        artifact("c", "3.0", "runtime", "jar", "client", false),
                        artifact("d", "4.0", "provided", "pom", null, false)),
                null,
                null);

        String pom = ResolvedDependenciesFormat.pom(sets, false);

        assertTrue(
                pom.contains("<type>test-jar</type>" + NL + "    <classifier>tests</classifier>" + NL
                        + "    <scope>test</scope>"),
                pom);
        assertTrue(pom.contains("<classifier>client</classifier>" + NL + "    <scope>runtime</scope>"), pom);
        assertTrue(pom.contains("<type>pom</type>" + NL + "    <scope>provided</scope>"), pom);
    }

    @Test
    void pomUsesTheBaseVersionOfSnapshotsAndOnlyListsResolvedArtifacts() {
        Artifact snapshot = artifact("s", "1.0-20240101.120000-1", "compile", "jar", null, false);
        snapshot.setBaseVersion("1.0-SNAPSHOT");
        DependencyStatusSets sets = new DependencyStatusSets(
                set(snapshot),
                set(artifact("missing", "1.0", "compile", "jar", null, false)),
                set(artifact("skipped", "1.0", "compile", "jar", null, false)));

        String pom = ResolvedDependenciesFormat.pom(sets, false);

        assertTrue(pom.contains("<version>1.0-SNAPSHOT</version>"), pom);
        assertFalse(pom.contains("missing"), pom);
        assertFalse(pom.contains("skipped"), pom);
    }

    @Test
    void pomWritesOptionalAndSkipsArtifactsWithoutScope() {
        Artifact optional = artifact("opt", "1.0", "compile", "jar", null, true);
        Artifact parent = artifact("parent", "1.0", null, "pom", null, false);
        DependencyStatusSets sets = new DependencyStatusSets(set(optional, parent), null, null);

        String pom = ResolvedDependenciesFormat.pom(sets, false);

        assertTrue(pom.contains("<version>1.0</version>" + NL + "    <optional>true</optional>"), pom);
        assertFalse(pom.contains("parent"), pom);
    }

    @Test
    void pomIsEmptyElementWithoutArtifactsAndSorts() {
        assertEquals(
                "<dependencies>" + NL + "</dependencies>" + NL,
                ResolvedDependenciesFormat.pom(new DependencyStatusSets(), false));

        DependencyStatusSets sets = new DependencyStatusSets(
                set(
                        artifact("z", "1.0", "compile", "jar", null, false),
                        artifact("a", "1.0", "compile", "jar", null, false)),
                null,
                null);
        String pom = ResolvedDependenciesFormat.pom(sets, true);
        assertTrue(pom.indexOf("<artifactId>a<") < pom.indexOf("<artifactId>z<"), pom);
        pom = ResolvedDependenciesFormat.pom(sets, false);
        assertTrue(pom.indexOf("<artifactId>z<") < pom.indexOf("<artifactId>a<"), pom);
    }

    @Test
    void jsonHasThreeArraysWithTheTreeMembers() {
        DependencyStatusSets sets = new DependencyStatusSets(
                set(artifact("a", "1.0", "test", "jar", "client", true)),
                set(artifact("missing", "2.0", "compile", "jar", null, false)),
                null);

        JsonObject json = parse(ResolvedDependenciesFormat.json(sets, false, false));

        JsonArray resolved = json.getJsonArray("resolved");
        assertEquals(1, resolved.size());
        JsonObject a = resolved.getJsonObject(0);
        assertEquals("g", a.getString("groupId"));
        assertEquals("a", a.getString("artifactId"));
        assertEquals("1.0", a.getString("version"));
        assertEquals("jar", a.getString("type"));
        assertEquals("test", a.getString("scope"));
        assertEquals("client", a.getString("classifier"));
        assertEquals("true", a.getString("optional"));
        assertFalse(a.containsKey("file"));
        assertEquals(0, json.getJsonArray("skipped").size());
        assertEquals("missing", json.getJsonArray("unresolved").getJsonObject(0).getString("artifactId"));
        assertEquals("", json.getJsonArray("unresolved").getJsonObject(0).getString("classifier"));
    }

    @Test
    void jsonAddsTheFileAndEscapesIt() {
        Artifact artifact = artifact("a", "1.0", "compile", "jar", null, false);
        artifact.setFile(new File("target/with \"quote\".jar"));

        JsonObject json = parse(
                ResolvedDependenciesFormat.json(new DependencyStatusSets(set(artifact), null, null), true, false));

        assertEquals(
                artifact.getFile().getAbsolutePath(),
                json.getJsonArray("resolved").getJsonObject(0).getString("file"));
    }

    @Test
    void jsonOfNothingIsThreeEmptyArrays() {
        JsonObject json = parse(ResolvedDependenciesFormat.json(new DependencyStatusSets(), false, true));

        assertEquals(3, json.size());
        assertTrue(json.getJsonArray("resolved").isEmpty());
    }

    @Test
    void unsupportedOutputTypeFailsTheMojo() {
        ResolveDependenciesMojo mojo = new ResolveDependenciesMojo(null, null, null, null, null, null);
        mojo.outputType = "yaml";

        assertThrows(MojoExecutionException.class, mojo::doExecute);
    }

    @Test
    void mojoFormatsByOutputType() {
        ResolveDependenciesMojo mojo = new ResolveDependenciesMojo(null, null, null, null, null, null);
        mojo.results = new DependencyStatusSets(set(artifact("a", "1.0", "compile", "jar", null, false)), null, null);

        mojo.outputType = "pom";
        assertTrue(mojo.formatOutput().startsWith("<dependencies>"));
        mojo.outputType = "json";
        assertTrue(mojo.formatOutput().startsWith("{"));
        mojo.outputType = "text";
        assertTrue(mojo.formatOutput().contains("The following files have been resolved:"));
        mojo.outputType = null;
        assertTrue(mojo.formatOutput().contains("The following files have been resolved:"));
    }
}
