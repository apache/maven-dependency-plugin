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
package org.apache.maven.plugins.dependency.tree;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugins.dependency.tree.DependencyNode.VerboseInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDependencyNodeVisitorTest {

    @Test
    void nodeWithoutVerboseInfoHasNoVerboseFields() {
        JsonObject json = toJson(new DependencyNode(null, artifact("a", "1.0"), "a"));

        assertEquals(7, json.size());
        assertEquals("false", json.getString("optional"));
        assertFalse(json.containsKey("included"));
    }

    @Test
    void includedNodeReportsIncludedAndManagement() {
        DependencyNode node = new DependencyNode(null, artifact("a", "2.0"), "a");
        node.setVerboseInfo(new VerboseInfo(null, null, "1.0", "test", "compile"));

        JsonObject json = toJson(node);

        assertEquals("true", json.getString("included"));
        assertEquals("1.0", json.getString("versionManagedFrom"));
        assertEquals("test", json.getString("scopeManagedFrom"));
        assertEquals("compile", json.getString("scopeNotUpdatedTo"));
        assertFalse(json.containsKey("omittedReason"));
        assertFalse(json.containsKey("winnerVersion"));
    }

    @Test
    void omittedForDuplicateHasReasonButNoWinnerVersion() {
        DependencyNode node = new DependencyNode(null, artifact("a", "1.0"), "a");
        node.setVerboseInfo(new VerboseInfo(VerboseInfo.OMITTED_FOR_DUPLICATE, "1.0", null, null, null));

        JsonObject json = toJson(node);

        assertEquals("false", json.getString("included"));
        assertEquals("duplicate", json.getString("omittedReason"));
        assertFalse(json.containsKey("winnerVersion"));
    }

    @Test
    void omittedForConflictHasWinnerVersion() {
        DependencyNode node = new DependencyNode(null, artifact("a", "1.0"), "a");
        node.setVerboseInfo(new VerboseInfo(VerboseInfo.OMITTED_FOR_CONFLICT, "2.0", null, null, null));

        JsonObject json = toJson(node);

        assertEquals("false", json.getString("included"));
        assertEquals("conflict", json.getString("omittedReason"));
        assertEquals("2.0", json.getString("winnerVersion"));
    }

    @Test
    void verboseFieldsOfChildrenAreNestedAndOutputStaysValidJson() {
        DependencyNode root = new DependencyNode(null, artifact("root", "1.0"), "root");
        DependencyNode child = new DependencyNode(root, artifact("child", "1.0"), "child");
        child.setVerboseInfo(new VerboseInfo(VerboseInfo.OMITTED_FOR_DUPLICATE, "1.0", null, null, null));
        root.setChildren(Collections.singletonList(child));

        JsonObject json = toJson(root);

        assertTrue(json.getJsonArray("children").getJsonObject(0).containsKey("omittedReason"));
        assertFalse(json.containsKey("included"));
    }

    private static JsonObject toJson(DependencyNode node) {
        StringWriter writer = new StringWriter();
        node.accept(new JsonDependencyNodeVisitor(writer));
        System.setProperty("jakarta.json.provider", "org.glassfish.json.JsonProviderImpl");
        try (JsonReader reader = Json.createReader(new StringReader(writer.toString()))) {
            return reader.readObject();
        }
    }

    private static Artifact artifact(String artifactId, String version) {
        return new DefaultArtifact(
                "g", artifactId, version, Artifact.SCOPE_COMPILE, "jar", null, new DefaultArtifactHandler("jar"));
    }
}
