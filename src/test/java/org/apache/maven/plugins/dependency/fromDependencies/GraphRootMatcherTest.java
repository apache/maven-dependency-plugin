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
package org.apache.maven.plugins.dependency.fromDependencies;

import java.util.stream.Stream;

import org.apache.maven.model.Dependency;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphRootMatcherTest {

    @ParameterizedTest
    @NullSource
    void illegalArgument(GraphRoot root) {
        assertThatThrownBy(() -> new GraphRootMatcher(root)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @MethodSource
    void match(GraphRoot graphRoot, Dependency dependency, boolean result) {
        assertThat(new GraphRootMatcher(graphRoot).matches(dependency)).isEqualTo(result);
    }

    static Stream<Arguments> match() {
        return Stream.of(
                Arguments.arguments(newGraphRoot("g", "a"), newDependency("g", "a"), true),
                Arguments.arguments(newGraphRoot("x", "a"), newDependency("g", "a"), false),
                Arguments.arguments(newGraphRoot("g", "x"), newDependency("g", "a"), false),
                Arguments.arguments(newGraphRoot("g", "a"), newDependency("x", "a"), false),
                Arguments.arguments(newGraphRoot("g", "a"), newDependency("g", "x"), false));
    }

    private static GraphRoot newGraphRoot(String groupId, String artifactId) {
        GraphRoot graphRoot = new GraphRoot();
        graphRoot.setGroupId(groupId);
        graphRoot.setArtifactId(artifactId);
        return graphRoot;
    }

    private static Dependency newDependency(String groupId, String artifactId) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        return dependency;
    }
}
