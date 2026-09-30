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

import java.util.Objects;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

/**
 * Excludes dependencies of a scope, unless they are direct dependencies of the project or its dependencies' first
 * level, as the verbose tree shows them.
 */
class DirectScopeDependencySelector implements DependencySelector {

    private final String scope;

    private final int depth;

    DirectScopeDependencySelector(String scope) {
        this(scope, 0);
    }

    private DirectScopeDependencySelector(String scope, int depth) {
        this.scope = Objects.requireNonNull(scope, "scope is null!");
        this.depth = depth;
    }

    @Override
    public boolean selectDependency(Dependency dependency) {
        return depth < 2 || !scope.equals(dependency.getScope());
    }

    @Override
    public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
        if (depth >= 2) {
            return this;
        }

        return new DirectScopeDependencySelector(scope, depth + 1);
    }

    @Override
    public int hashCode() {
        return 31 * (31 + depth) + scope.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        DirectScopeDependencySelector other = (DirectScopeDependencySelector) obj;
        return depth == other.depth && scope.equals(other.scope);
    }
}
