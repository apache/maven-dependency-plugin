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

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugins.dependency.tree.DependencyNode.VerboseInfo;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencySizesTest {
    // the verbose tree Maven collects; sizes in brackets
    //   root
    //   +- a:1.0 [1000]
    //   |  +- shared:1.0 [100]
    //   |  |  \- deep:1.0 [5]
    //   |  +- a2:1.0 [10]
    //   |  \- lib:1.0 [50]
    //   \- b:1.0 [2000]
    //      +- (shared:1.0, omitted for duplicate)
    //      \- (lib:2.0 [999], omitted for conflict with 1.0)
    private final DependencyNode root = node(null, "root", "1.0");
    private final DependencyNode a = node(root, "a", "1.0");
    private final DependencyNode shared = node(a, "shared", "1.0");
    private final DependencyNode deep = node(shared, "deep", "1.0");
    private final DependencyNode a2 = node(a, "a2", "1.0");
    private final DependencyNode lib = node(a, "lib", "1.0");
    private final DependencyNode b = node(root, "b", "1.0");
    private final DependencyNode sharedAgain = node(b, "shared", "1.0");
    private final DependencyNode libLoser = node(b, "lib", "2.0");

    private final Map<String, Long> sizes = new HashMap<>();

    DependencySizesTest() {
        root.setChildren(Arrays.asList(a, b));
        a.setChildren(Arrays.asList(shared, a2, lib));
        shared.setChildren(Arrays.asList(deep));
        b.setChildren(Arrays.asList(sharedAgain, libLoser));
        sharedAgain.setVerboseInfo(new VerboseInfo(VerboseInfo.OMITTED_FOR_DUPLICATE, "1.0", null, null, null));
        libLoser.setVerboseInfo(new VerboseInfo(VerboseInfo.OMITTED_FOR_CONFLICT, "1.0", null, null, null));
        size(a, 1000);
        size(shared, 100);
        size(deep, 5);
        size(a2, 10);
        size(lib, 50);
        size(b, 2000);
        size(libLoser, 999);
    }

    /** The tree as Maven resolves it, without the omitted nodes. */
    private DependencyNode plain() {
        return root.filter(
                null, n -> n.getVerboseInfo() == null || n.getVerboseInfo().isIncluded(), null);
    }

    @Test
    void totalCountsWhatTheDependencyBringsInAlsoWhereMavenKeptItElsewhere() {
        DependencyNode plain = plain();
        DependencySizes.annotate(plain, root, sizes);

        DependencyNode plainA = plain.getChildren().get(0);
        DependencyNode plainB = plain.getChildren().get(1);
        assertEquals(1000 + 100 + 5 + 10 + 50, plainA.getTotalSize());
        // b keeps no child in the plain tree, but depends on shared (with deep) and on lib 1.0
        assertTrue(plainB.getChildren().isEmpty());
        assertEquals(2000 + 100 + 5 + 50, plainB.getTotalSize());
        assertEquals(Long.valueOf(2000), plainB.getSize());
        // shared counts once for the project, the omitted lib 2.0 not at all
        assertEquals(1000 + 100 + 5 + 10 + 50 + 2000, plain.getTotalSize());
        assertNull(plain.getSize());
    }

    @Test
    void sameTotalsInTheVerboseTree() {
        DependencySizes.annotate(root, root, sizes);

        assertEquals(1165, a.getTotalSize());
        assertEquals(2155, b.getTotalSize());
        assertEquals(105, shared.getTotalSize());
        assertEquals(3165, root.getTotalSize());
        assertEquals(Long.valueOf(100), shared.getSize());
    }

    @Test
    void omittedNodeShowsNoSizeAndTheOmittedVersionDoesNotCount() {
        DependencySizes.annotate(root, root, sizes);

        assertFalse(sharedAgain.hasSizes());
        assertFalse(libLoser.hasSizes());
        assertEquals("", DependencySizes.describe(libLoser));
        assertFalse(DependencySizes.nodes(root, root).containsKey(DependencySizes.key(libLoser.getArtifact())));
    }

    @Test
    void unknownSizeIsReportedAndCountsAsZero() {
        sizes.remove(DependencySizes.key(a2.getArtifact()));
        sizes.remove(DependencySizes.key(b.getArtifact()));

        DependencyNode plain = plain();
        DependencySizes.annotate(plain, root, sizes);

        assertEquals(
                " (1000 B, total 1.1 KB)",
                DependencySizes.describe(plain.getChildren().get(0)));
        DependencyNode plainB = plain.getChildren().get(1);
        assertNull(plainB.getSize());
        assertEquals(155, plainB.getTotalSize());
        assertEquals(" (size unknown, total 155 B)", DependencySizes.describe(plainB));
    }

    @Test
    void leafShowsOnlyItsSizeAndAnArtifactThatBringsInOthersShowsTotal() {
        DependencyNode plain = plain();
        DependencySizes.annotate(plain, root, sizes);

        DependencyNode plainA = plain.getChildren().get(0);
        assertEquals(" (10 B)", DependencySizes.describe(plainA.getChildren().get(1)));
        assertEquals(
                " (100 B, total 105 B)",
                DependencySizes.describe(plainA.getChildren().get(0)));
        // b has no child to show in the plain tree, still it brings in others
        assertEquals(
                " (2 KB, total 2.1 KB)",
                DependencySizes.describe(plain.getChildren().get(1)));
        assertEquals(" (total 3.1 KB)", DependencySizes.describe(plain));
    }

    @Test
    void nodeWithoutSizesIsUnchanged() {
        assertEquals("", DependencySizes.describe(a));
        assertEquals("", DependencySizes.describe(root));
    }

    @Test
    void collectsDistinctArtifactsExceptRootAndOmitted() {
        Map<String, DependencyNode> nodes = DependencySizes.nodes(plain(), root);

        assertEquals(6, nodes.size());
        assertTrue(nodes.containsKey(DependencySizes.key(deep.getArtifact())));
        assertFalse(nodes.containsKey(DependencySizes.key(root.getArtifact())));
        assertFalse(nodes.containsKey(DependencySizes.key(libLoser.getArtifact())));
    }

    @Test
    void textOutputAppendsSizes() {
        DependencyNode plain = plain();
        DependencySizes.annotate(plain, root, sizes);
        StringWriter writer = new StringWriter();
        plain.accept(new SerializingDependencyNodeVisitor(writer, SerializingDependencyNodeVisitor.STANDARD_TOKENS));

        List<String> lines = Arrays.asList(writer.toString().split("\\R"));
        assertEquals("root (total 3.1 KB)", lines.get(0));
        assertEquals("+- a (1000 B, total 1.1 KB)", lines.get(1));
        assertEquals("|  +- shared (100 B, total 105 B)", lines.get(2));
        assertEquals("|  |  \\- deep (5 B)", lines.get(3));
        assertEquals("\\- b (2 KB, total 2.1 KB)", lines.get(6));
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
        DependencySizes.annotate(root, root, sizes);
        StringWriter withSizes = new StringWriter();
        root.accept(new JsonDependencyNodeVisitor(withSizes));
        String json = withSizes.toString();
        assertTrue(json.contains("\"size\": 1000,"));
        assertTrue(json.contains("\"totalSize\": 1155"));
        assertTrue(json.contains("\"totalSize\": 0"));
        assertFalse(json.contains("\"size\": 10,"));
    }

    @Test
    void systemScopeUsesTheSystemPathNotTheResolver(@TempDir File dir) throws IOException {
        File jar = new File(dir, "system.jar");
        Files.write(jar.toPath(), new byte[123]);
        DependencyNode system = node(root, "system", "1.0");
        system.setResolution(
                new org.eclipse.aether.artifact.DefaultArtifact("g:system:jar:1.0")
                        .setProperties(java.util.Collections.singletonMap(
                                ArtifactProperties.LOCAL_PATH, jar.getAbsolutePath())),
                null);

        assertEquals(jar, DependencySizes.localFile(system));
        assertNull(DependencySizes.localFile(a));
    }

    private void size(DependencyNode node, long bytes) {
        sizes.put(DependencySizes.key(node.getArtifact()), bytes);
    }

    private static DependencyNode node(DependencyNode parent, String artifactId, String version) {
        Artifact artifact = new DefaultArtifact(
                "g", artifactId, version, Artifact.SCOPE_COMPILE, "jar", null, new DefaultArtifactHandler("jar"));
        return new DependencyNode(parent, artifact, artifactId);
    }
}
