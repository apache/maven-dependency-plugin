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
package org.apache.maven.plugins.dependency.exclusion;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static org.apache.maven.plugins.dependency.exclusion.Coordinates.coordinates;
import static org.assertj.core.api.Assertions.assertThat;

public class ExclusionCheckerTest {

    private ExclusionChecker checker;

    @Before
    public void setUp() throws Exception {
        checker = new ExclusionChecker();
    }

    @Test
    public void shall_report_invalid_exclusions() {
        Coordinates artifact = coordinates("com.current", "artifact");
        Set<Coordinates> excludes = newHashSet(
                coordinates("com.example", "one"),
                coordinates("com.example", "two"),
                coordinates("com.example", "three"),
                coordinates("com.example", "four"));

        Set<Coordinates> actualDependencies =
                newHashSet(coordinates("com.example", "one"), coordinates("com.example", "four"));

        checker.check(artifact, excludes, actualDependencies);

        assertThat(checker.getViolations())
                .containsEntry(
                        artifact, newArrayList(coordinates("com.example", "two"), coordinates("com.example", "three")));
    }

    @Test
    public void no_violations_when_empty_exclusions() {
        checker.check(coordinates("a", "b"), new HashSet<>(), new HashSet<>());
        assertThat(checker.getViolations()).isEmpty();
    }

    @Test
    public void shall_report_invalid_exclusions_when_no_dependencies() {
        Coordinates artifact = coordinates("a", "b");
        HashSet<Coordinates> actualDependencies = new HashSet<>();
        checker.check(artifact, newHashSet(coordinates("p", "m")), actualDependencies);
        assertThat(checker.getViolations()).containsEntry(artifact, newArrayList(coordinates("p", "m")));
    }

    @Test
    public void shall_handle_wildcard_exclusions() {
        Coordinates artifact = coordinates("com.current", "artifact");
        Set<Coordinates> excludes = newHashSet(coordinates("*", "*"));

        Set<Coordinates> actualDependencies =
                newHashSet(coordinates("com.example", "one"), coordinates("com.example", "four"));

        checker.check(artifact, excludes, actualDependencies);

        assertThat(checker.getViolations()).isEmpty();
    }

    @Test
    public void shall_handle_wildcard_groupId_exclusion() {
        Coordinates artifact = coordinates("com.current", "artifact");
        Set<Coordinates> excludes = newHashSet(coordinates("javax", "*"));

        Set<Coordinates> actualDependencies = newHashSet(
                coordinates("com.example", "one"),
                coordinates("com.example", "four"),
                coordinates("javax", "whatever"));

        checker.check(artifact, excludes, actualDependencies);

        assertThat(checker.getViolations()).isEmpty();
    }
}
