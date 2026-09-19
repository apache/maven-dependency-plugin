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

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClasspathDependencySelectorTest {
    @Test
    void providedDependenciesAreOnCompileAndTestClasspaths() {
        Dependency provided = dependency(JavaScopes.PROVIDED);

        assertTrue(new ClasspathDependencySelector(ClasspathScope.COMPILE).selectDependency(provided));
        assertFalse(new ClasspathDependencySelector(ClasspathScope.RUNTIME).selectDependency(provided));
        assertTrue(new ClasspathDependencySelector(ClasspathScope.TEST).selectDependency(provided));
    }

    @Test
    void derivesTransitiveScopesLikeResolver() {
        assertEquals(
                JavaScopes.PROVIDED, ClasspathDependencySelector.deriveScope(JavaScopes.PROVIDED, JavaScopes.COMPILE));
        assertEquals(
                JavaScopes.RUNTIME, ClasspathDependencySelector.deriveScope(JavaScopes.RUNTIME, JavaScopes.COMPILE));
        assertEquals(JavaScopes.TEST, ClasspathDependencySelector.deriveScope(JavaScopes.COMPILE, JavaScopes.TEST));
        assertEquals(
                JavaScopes.RUNTIME, ClasspathDependencySelector.deriveScope(JavaScopes.COMPILE, JavaScopes.RUNTIME));
    }

    private Dependency dependency(String scope) {
        return new Dependency(new DefaultArtifact("groupId:artifactId:jar:1.0"), scope);
    }
}
