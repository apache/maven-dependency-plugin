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
package org.apache.maven.plugins.dependency.fromConfiguration;

import java.util.Objects;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * Selects dependencies by their effective Maven classpath scope while the graph is being collected. Filtering after
 * conflict resolution is too late because dependencies excluded from one classpath can otherwise affect mediation in
 * another classpath.
 */
final class ClasspathDependencySelector implements DependencySelector {
    private final ClasspathScope classpathScope;

    private final String parentScope;

    ClasspathDependencySelector(ClasspathScope classpathScope) {
        this(classpathScope, null);
    }

    private ClasspathDependencySelector(ClasspathScope classpathScope, String parentScope) {
        this.classpathScope = Objects.requireNonNull(classpathScope);
        this.parentScope = parentScope;
    }

    @Override
    public boolean selectDependency(Dependency dependency) {
        return classpathScope.includes(deriveScope(parentScope, dependency.getScope()));
    }

    @Override
    public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
        Dependency dependency = context.getDependency();
        if (dependency == null) {
            return this;
        }
        return new ClasspathDependencySelector(classpathScope, deriveScope(parentScope, dependency.getScope()));
    }

    // Keep this in sync with Resolver's legacy JavaScopeDeriver. Its callable context is not binary compatible
    // between Resolver 1.x and 2.x, while this plugin must run with both Maven 3 and Maven 4.
    static String deriveScope(String parentScope, String childScope) {
        String child = childScope == null || childScope.isEmpty() ? JavaScopes.COMPILE : childScope;
        if (JavaScopes.SYSTEM.equals(child) || JavaScopes.TEST.equals(child)) {
            return child;
        } else if (parentScope == null || parentScope.isEmpty() || JavaScopes.COMPILE.equals(parentScope)) {
            return child;
        } else if (JavaScopes.TEST.equals(parentScope) || JavaScopes.RUNTIME.equals(parentScope)) {
            return parentScope;
        } else if (JavaScopes.SYSTEM.equals(parentScope) || JavaScopes.PROVIDED.equals(parentScope)) {
            return JavaScopes.PROVIDED;
        } else {
            return JavaScopes.RUNTIME;
        }
    }
}
