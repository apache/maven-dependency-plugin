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

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.filter.DependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;

/**
 * A dependency node visitor that delegates accepted nodes and prunes rejected nodes and their descendants.
 */
final class PruningDependencyNodeVisitor implements DependencyNodeVisitor {
    private final DependencyNodeVisitor visitor;

    private final DependencyNodeFilter filter;

    private final Deque<Boolean> acceptedNodes = new ArrayDeque<>();

    PruningDependencyNodeVisitor(DependencyNodeVisitor visitor, DependencyNodeFilter filter) {
        this.visitor = visitor;
        this.filter = filter;
    }

    @Override
    public boolean visit(DependencyNode node) {
        boolean accepted = filter.accept(node);
        acceptedNodes.push(accepted);
        return accepted && visitor.visit(node);
    }

    @Override
    public boolean endVisit(DependencyNode node) {
        return !acceptedNodes.pop() || visitor.endVisit(node);
    }
}
