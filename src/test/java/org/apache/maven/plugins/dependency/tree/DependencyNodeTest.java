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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class DependencyNodeTest {
    //   root
    //   +- a
    //   |  +- a1
    //   |  \- a2
    //   \- b
    //      \- b1
    private final DependencyNode root = newNode(null, "root");
    private final DependencyNode a = newNode(root, "a");
    private final DependencyNode a1 = newNode(a, "a1");
    private final DependencyNode a2 = newNode(a, "a2");
    private final DependencyNode b = newNode(root, "b");
    private final DependencyNode b1 = newNode(b, "b1");

    DependencyNodeTest() {
        root.setChildren(Arrays.asList(a, b));
        a.setChildren(Arrays.asList(a1, a2));
        b.setChildren(Arrays.asList(b1));
    }

    @Test
    void copiesWholeTreeWithoutFilters() {
        assertEquals(Arrays.asList("root", "a", "a1", "a2", "b", "b1"), labels(root.filter(null, null, null)));
    }

    @Test
    void prunesRejectedSubtreeAndKeepsSiblings() {
        assertEquals(Arrays.asList("root", "b", "b1"), labels(root.filter(null, node -> node != a, null)));
    }

    @Test
    void includesMatchesWithTheirAncestorsOnly() {
        DependencyNode copy = root.filter(null, null, node -> node == a2 || node == b);

        assertEquals(Arrays.asList("root", "a", "a2", "b"), labels(copy));
        DependencyNode copiedA2 = copy.getChildren().get(0).getChildren().get(0);
        assertSame(copy.getChildren().get(0), copiedA2.getParent());
        assertSame(copy, copiedA2.getParent().getParent());
    }

    @Test
    void doesNotIncludeMatchesInsideAPrunedSubtree() {
        assertEquals(
                Arrays.asList("root", "b", "b1"),
                labels(root.filter(null, node -> node != a, node -> node == a1 || node == b1)));
    }

    @Test
    void leavesOutEverythingWhenNothingMatches() {
        assertNull(root.filter(null, null, node -> false));
        assertNull(root.filter(null, node -> node != root, null));
    }

    private static List<String> labels(DependencyNode node) {
        List<String> labels = new ArrayList<>();
        node.accept(new DependencyNodeVisitor() {
            @Override
            public boolean visit(DependencyNode visited) {
                labels.add(visited.toNodeString());
                return true;
            }

            @Override
            public boolean endVisit(DependencyNode visited) {
                return true;
            }
        });
        return labels;
    }

    private static DependencyNode newNode(DependencyNode parent, String label) {
        return new DependencyNode(parent, mock(Artifact.class), label);
    }
}
