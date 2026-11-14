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

import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugins.dependency.tree.DependencyNode.VerboseInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencySizesTest {
    //   root
    //   +- a (1000)
    //   |  +- shared (100)
    //   |  \- a2 (10)
    //   \- b (2000)
    //      +- shared (100)
    //      \- (shared2 omitted for duplicate, 5000)
    private final DependencyNode root = node(null, "root");
    private final DependencyNode a = node(root, "a");
    private final DependencyNode aShared = node(a, "shared");
    private final DependencyNode a2 = node(a, "a2");
    private final DependencyNode b = node(root, "b");
    private final DependencyNode bShared = node(b, "shared");
    private final DependencyNode omitted = node(b, "omitted");

    private final Map<String, Long> sizes = new HashMap<>();

    DependencySizesTest() {
        root.setChildren(Arrays.asList(a, b));
        a.setChildren(Arrays.asList(aShared, a2));
        b.setChildren(Arrays.asList(bShared, omitted));
        omitted.setVerboseInfo(new VerboseInfo(VerboseInfo.OMITTED_FOR_DUPLICATE, "1.0", null, null, null));
        sizes.put(DependencySizes.key(a.getArtifact()), 1000L);
        sizes.put(DependencySizes.key(aShared.getArtifact()), 100L);
        sizes.put(DependencySizes.key(a2.getArtifact()), 10L);
        sizes.put(DependencySizes.key(b.getArtifact()), 2000L);
        sizes.put(DependencySizes.key(omitted.getArtifact()), 5000L);
    }

    @Test
    void totalIsOwnSizePlusDescendants() {
        DependencySizes.annotate(root, sizes);

        assertEquals(Long.valueOf(1000), a.getSize());
        assertEquals(1110, a.getTotalSize());
        assertEquals(Long.valueOf(10), a2.getSize());
        assertEquals(10, a2.getTotalSize());
        assertEquals(2100, b.getTotalSize());
    }

    @Test
    void artifactOccurringTwiceInASubtreeCountsOnce() {
        // the same artifact below a and below b, counted once in the total of the root and once in each branch
        DependencySizes.annotate(root, sizes);

        assertEquals(1000 + 100 + 10 + 2000, root.getTotalSize());
        assertNull(root.getSize());
    }

    @Test
    void omittedNodeContributesNothingAndGetsNoSizes() {
        DependencySizes.annotate(root, sizes);

        assertFalse(omitted.hasSizes());
        assertEquals("", DependencySizes.describe(omitted));
        assertFalse(DependencySizes.artifacts(root).containsKey(DependencySizes.key(omitted.getArtifact())));
        assertEquals(2100, b.getTotalSize());
    }

    @Test
    void unknownSizeIsReportedAndCountsAsZero() {
        sizes.remove(DependencySizes.key(a.getArtifact()));

        DependencySizes.annotate(root, sizes);

        assertNull(a.getSize());
        assertEquals(110, a.getTotalSize());
        assertEquals(" (size unknown, total 110 B)", DependencySizes.describe(a));
        assertEquals(" (10 B)", DependencySizes.describe(a2));
    }

    @Test
    void leafShowsOnlyItsSizeAndParentsShowTotal() {
        DependencySizes.annotate(root, sizes);

        assertEquals(" (10 B)", DependencySizes.describe(a2));
        assertEquals(" (1000 B, total 1.1 KB)", DependencySizes.describe(a));
        assertEquals(" (total 3 KB)", DependencySizes.describe(root));
    }

    @Test
    void nodeWithoutSizesIsUnchanged() {
        assertEquals("", DependencySizes.describe(a));
        assertEquals("", DependencySizes.describe(root));
    }

    @Test
    void collectsDistinctArtifactsExceptRootAndOmitted() {
        Map<String, Artifact> artifacts = DependencySizes.artifacts(root);

        assertEquals(4, artifacts.size());
        assertTrue(artifacts.containsKey(DependencySizes.key(aShared.getArtifact())));
        assertFalse(artifacts.containsKey(DependencySizes.key(root.getArtifact())));
    }

    @Test
    void textOutputAppendsSizes() {
        DependencySizes.annotate(root, sizes);
        StringWriter writer = new StringWriter();
        root.accept(new SerializingDependencyNodeVisitor(writer, SerializingDependencyNodeVisitor.STANDARD_TOKENS));

        List<String> lines = Arrays.asList(writer.toString().split("\\R"));
        assertEquals("root (total 3 KB)", lines.get(0));
        assertEquals("+- a (1000 B, total 1.1 KB)", lines.get(1));
        assertEquals("|  +- shared (100 B)", lines.get(2));
        assertEquals("\\- b (2 KB, total 2.1 KB)", lines.get(4));
        assertEquals("   \\- omitted", lines.get(6));
    }

    @Test
    void formatsUnits() {
        assertEquals("0 B", DependencySizes.format(0));
        assertEquals("1023 B", DependencySizes.format(1023));
        assertEquals("1 KB", DependencySizes.format(1024));
        assertEquals("200 KB", DependencySizes.format(200 * 1024));
        assertEquals("1.5 KB", DependencySizes.format(1536));
        assertEquals("1.5 MB", DependencySizes.format(1536 * 1024));
        assertEquals("1 MB", DependencySizes.format(1024 * 1024 - 1));
        assertEquals("2.3 GB", DependencySizes.format(2469606195L));
        assertEquals("2048 TB", DependencySizes.format(2048L * 1024 * 1024 * 1024 * 1024));
    }

    @Test
    void jsonHasNumericSizesOnlyWhenComputed() {
        StringWriter plain = new StringWriter();
        root.accept(new JsonDependencyNodeVisitor(plain));
        assertFalse(plain.toString().contains("totalSize"));
        assertFalse(plain.toString().contains("\"size\""));

        // a2 has an unknown size: totalSize only
        sizes.remove(DependencySizes.key(a2.getArtifact()));
        DependencySizes.annotate(root, sizes);
        StringWriter withSizes = new StringWriter();
        root.accept(new JsonDependencyNodeVisitor(withSizes));
        String json = withSizes.toString();
        assertTrue(json.contains("\"size\": 1000,"));
        assertTrue(json.contains("\"totalSize\": 1100"));
        assertTrue(json.contains("\"totalSize\": 0"));
        assertFalse(json.contains("\"size\": 10,"));
        assertFalse(json.contains("\"size\": 10\n"));
    }

    private static DependencyNode node(DependencyNode parent, String artifactId) {
        Artifact artifact = new DefaultArtifact(
                "g", artifactId, "1.0", Artifact.SCOPE_COMPILE, "jar", null, new DefaultArtifactHandler("jar"));
        return new DependencyNode(parent, artifact, artifactId);
    }
}
