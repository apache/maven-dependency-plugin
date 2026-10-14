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
import java.util.Collections;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MermaidDependencyNodeVisitorTest {

    @Test
    void writesNodesOnceAndEdgesWithScope() {
        // root -> a -> c, root -> b(test) -> c
        DependencyNode root = node(null, "root", Artifact.SCOPE_COMPILE);
        DependencyNode a = node(root, "a", Artifact.SCOPE_COMPILE);
        DependencyNode b = node(root, "b", Artifact.SCOPE_TEST);
        DependencyNode ac = node(a, "c", Artifact.SCOPE_COMPILE);
        DependencyNode bc = node(b, "c", Artifact.SCOPE_TEST);
        root.setChildren(Arrays.asList(a, b));
        a.setChildren(Collections.singletonList(ac));
        b.setChildren(Collections.singletonList(bc));

        String nl = System.lineSeparator();
        assertEquals(
                "graph LR" + nl
                        + "\tn0[\"root\"]" + nl
                        + "\tn1[\"a\"]" + nl
                        + "\tn0 -->|compile| n1" + nl
                        + "\tn2[\"b\"]" + nl
                        + "\tn0 -->|test| n2" + nl
                        + "\tn3[\"c\"]" + nl
                        + "\tn1 -->|compile| n3" + nl
                        + "\tn2 -->|test| n3" + nl,
                render(root));
    }

    @Test
    void writesALoneRoot() {
        assertEquals(
                "graph LR" + System.lineSeparator() + "\tn0[\"root\"]" + System.lineSeparator(),
                render(node(null, "root", Artifact.SCOPE_COMPILE)));
    }

    @Test
    void escapesQuotesInLabelsAndLeavesOutEmptyScope() {
        DependencyNode root = new DependencyNode(null, artifact("root", ""), "say \"hi\"");
        DependencyNode child = new DependencyNode(root, artifact("child", ""), "(child - omitted for duplicate)");
        root.setChildren(Collections.singletonList(child));

        String nl = System.lineSeparator();
        assertEquals(
                "graph LR" + nl
                        + "\tn0[\"say #quot;hi#quot;\"]" + nl
                        + "\tn1[\"(child - omitted for duplicate)\"]" + nl
                        + "\tn0 --> n1" + nl,
                render(root));
    }

    private static String render(DependencyNode root) {
        StringWriter writer = new StringWriter();
        root.accept(new MermaidDependencyNodeVisitor(writer));
        return writer.toString();
    }

    private static DependencyNode node(DependencyNode parent, String artifactId, String scope) {
        return new DependencyNode(parent, artifact(artifactId, scope), artifactId);
    }

    private static Artifact artifact(String artifactId, String scope) {
        return new DefaultArtifact("g", artifactId, "1.0", scope, "jar", null, new DefaultArtifactHandler("jar"));
    }
}
