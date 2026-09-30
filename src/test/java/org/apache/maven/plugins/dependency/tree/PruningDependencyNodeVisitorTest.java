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
import java.util.function.Predicate;

import org.apache.maven.artifact.Artifact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class PruningDependencyNodeVisitorTest {
    @Test
    void evaluatesFilterOnlyWhenStartingNodeVisit() {
        DependencyNode root = newNode(null);
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
        DependencyNode root = newNode(null);
        DependencyNode rejected = newNode(root);
        DependencyNode rejectedChild = newNode(rejected);
        DependencyNode sibling = newNode(root);

        root.setChildren(Arrays.asList(rejected, sibling));
        rejected.setChildren(Collections.singletonList(rejectedChild));
        rejectedChild.setChildren(Collections.emptyList());
        sibling.setChildren(Collections.emptyList());

        CollectingDependencyNodeVisitor collectingVisitor = new CollectingDependencyNodeVisitor();
        Predicate<DependencyNode> filter = node -> node != rejected;

        root.accept(new PruningDependencyNodeVisitor(collectingVisitor, filter));

        assertEquals(Arrays.asList(root, sibling), collectingVisitor.getNodes());
    }

    private DependencyNode newNode(DependencyNode parent) {
        return new DependencyNode(parent, mock(Artifact.class), "node");
    }
}
