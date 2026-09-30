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

import java.util.function.Predicate;

/**
 * Passes only the accepted nodes to the delegate, while still traversing the children of the rejected ones.
 */
class FilteringDependencyNodeVisitor implements DependencyNodeVisitor {
    private final DependencyNodeVisitor visitor;

    private final Predicate<DependencyNode> filter;

    FilteringDependencyNodeVisitor(DependencyNodeVisitor visitor, Predicate<DependencyNode> filter) {
        this.visitor = visitor;
        this.filter = filter;
    }

    @Override
    public boolean visit(DependencyNode node) {
        return !filter.test(node) || visitor.visit(node);
    }

    @Override
    public boolean endVisit(DependencyNode node) {
        return !filter.test(node) || visitor.endVisit(node);
    }
}
