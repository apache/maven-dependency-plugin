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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.model.Dependency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExplicitDependencyMatcherTest {
    private final ArtifactHandlerManager handlers = handlers();

    private static ArtifactHandlerManager handlers() {
        ArtifactHandlerManager manager = mock(ArtifactHandlerManager.class);
        when(manager.getArtifactHandler(anyString())).thenAnswer(invocation -> {
            String type = invocation.getArgument(0);
            if ("test-jar".equals(type)) {
                ArtifactHandler handler = mock(ArtifactHandler.class);
                when(handler.getExtension()).thenReturn("jar");
                when(handler.getClassifier()).thenReturn("tests");
                return handler;
            }
            return new DefaultArtifactHandler(type);
        });
        return manager;
    }

    private static Dependency dependency(String artifactId, String version, String type, String classifier) {
        Dependency dependency = new Dependency();
        dependency.setGroupId("g");
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        if (type != null) {
            dependency.setType(type);
        }
        dependency.setClassifier(classifier);
        return dependency;
    }

    private static ArtifactItem item(String artifactId, String version, String type, String classifier) {
        ArtifactItem item = new ArtifactItem();
        item.setGroupId("g");
        item.setArtifactId(artifactId);
        item.setVersion(version);
        if (type != null) {
            item.setType(type);
        }
        item.setClassifier(classifier);
        return item;
    }

    private List<String> check(List<Dependency> declared, ArtifactItem... items) {
        return new ExplicitDependencyMatcher(handlers, declared).findViolations(Arrays.asList(items));
    }

    @Test
    void declaredItemMatches() {
        assertTrue(check(Collections.singletonList(dependency("a", "1.0", null, null)), item("a", "1.0", null, null))
                .isEmpty());
    }

    @Test
    void missingItemIsReported() {
        List<String> violations =
                check(Collections.singletonList(dependency("a", "1.0", null, null)), item("b", "1.0", null, null));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("g:b:1.0:jar"), violations.get(0));
        assertTrue(violations.get(0).contains("not declared"), violations.get(0));
    }

    @Test
    void emptyTypeAndClassifierAreDefaults() {
        assertTrue(check(Collections.singletonList(dependency("a", "1.0", "jar", "")), item("a", "1.0", "", ""))
                .isEmpty());
    }

    @Test
    void typeMustMatch() {
        assertEquals(
                1,
                check(Collections.singletonList(dependency("a", "1.0", "jar", null)), item("a", "1.0", "zip", null))
                        .size());
    }

    @Test
    void classifierMustMatch() {
        assertEquals(
                1,
                check(
                                Collections.singletonList(dependency("a", "1.0", "jar", null)),
                                item("a", "1.0", "jar", "sources"))
                        .size());
        assertEquals(
                1,
                check(
                                Collections.singletonList(dependency("a", "1.0", "jar", "sources")),
                                item("a", "1.0", "jar", null))
                        .size());
    }

    @Test
    void testJarEqualsJarWithTestsClassifier() {
        assertTrue(check(
                        Collections.singletonList(dependency("a", "1.0", "test-jar", null)),
                        item("a", "1.0", "jar", "tests"))
                .isEmpty());
        assertTrue(check(
                        Collections.singletonList(dependency("a", "1.0", "jar", "tests")),
                        item("a", "1.0", "test-jar", null))
                .isEmpty());
        assertEquals(
                1,
                check(
                                Collections.singletonList(dependency("a", "1.0", "jar", null)),
                                item("a", "1.0", "test-jar", null))
                        .size());
    }

    @Test
    void versionMismatchIsReportedWithDeclaredVersion() {
        List<String> violations =
                check(Collections.singletonList(dependency("a", "1.0", null, null)), item("a", "2.0", null, null));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("version 1.0, not 2.0"), violations.get(0));
    }

    @Test
    void itemWithoutVersionMatchesAnyDeclaredVersion() {
        assertTrue(check(Collections.singletonList(dependency("a", "1.0", null, null)), item("a", null, null, null))
                .isEmpty());
    }

    @Test
    void anyDeclaredVariantWithTheVersionMatches() {
        assertTrue(check(
                        Arrays.asList(dependency("a", "1.0", null, null), dependency("a", "2.0", null, null)),
                        item("a", "2.0", null, null))
                .isEmpty());
    }

    @Test
    void everyViolationIsListed() {
        assertEquals(
                2,
                check(Collections.<Dependency>emptyList(), item("a", "1", null, null), item("b", "1", null, null))
                        .size());
    }
}
