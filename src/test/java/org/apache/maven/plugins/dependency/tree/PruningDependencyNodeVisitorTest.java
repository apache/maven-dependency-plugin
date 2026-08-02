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

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.filter.DependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class PruningDependencyNodeVisitorTest {
    @Test
    void evaluatesFilterOnlyWhenStartingNodeVisit() {
        DefaultDependencyNode root = newNode(null);
        root.setChildren(Collections.emptyList());
        AtomicInteger filterInvocations = new AtomicInteger();
        AtomicInteger endVisits = new AtomicInteger();
        DependencyNodeVisitor visitor = new DependencyNodeVisitor() {
            @Override
            public boolean visit(DependencyNode node) {
                return true;
            }

            @Override
            public boolean endVisit(DependencyNode node) {
                endVisits.incrementAndGet();
                return true;
            }
        };

        root.accept(new PruningDependencyNodeVisitor(visitor, node -> filterInvocations.incrementAndGet() == 1));

        assertEquals(1, filterInvocations.get());
        assertEquals(1, endVisits.get());
    }

    @Test
    void prunesRejectedSubtreeAndContinuesWithSiblings() {
        DefaultDependencyNode root = newNode(null);
        DefaultDependencyNode rejected = newNode(root);
        DefaultDependencyNode rejectedChild = newNode(rejected);
        DefaultDependencyNode sibling = newNode(root);

        root.setChildren(Arrays.asList(rejected, sibling));
        rejected.setChildren(Collections.singletonList(rejectedChild));
        rejectedChild.setChildren(Collections.emptyList());
        sibling.setChildren(Collections.emptyList());

        CollectingDependencyNodeVisitor collectingVisitor = new CollectingDependencyNodeVisitor();
        DependencyNodeFilter filter = node -> node != rejected;

        root.accept(new PruningDependencyNodeVisitor(collectingVisitor, filter));

        assertEquals(Arrays.asList(root, sibling), collectingVisitor.getNodes());
    }

    private DefaultDependencyNode newNode(DependencyNode parent) {
        return new DefaultDependencyNode(parent, mock(Artifact.class), null, null, null);
    }
}
