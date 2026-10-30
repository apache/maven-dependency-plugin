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
package org.apache.maven.plugins.dependency.utils.filters;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestGroupIdFilter {

    private static Artifact groupId(final String groupId) {
        final ArtifactStub artifact = new ArtifactStub();
        artifact.setGroupId(groupId);
        artifact.setArtifactId("artifact-" + groupId);
        artifact.setVersion("1.0");
        return artifact;
    }

    private static Set<Artifact> artifacts(final String... groupIds) {
        final Set<Artifact> result = new LinkedHashSet<>();
        for (final String groupId : groupIds) {
            result.add(groupId(groupId));
        }
        return result;
    }

    private static Set<String> groupIdsOf(final Set<Artifact> artifacts) {
        final Set<String> result = new TreeSet<>();
        for (final Artifact artifact : artifacts) {
            result.add(artifact.getGroupId());
        }
        return result;
    }

    @Test
    void includeDoesNotMatchUnrelatedGroupIdSharingStringPrefix() {
        final GroupIdFilter filter = new GroupIdFilter("org.example", "");

        final Set<Artifact> result = filter.filter(artifacts("org.example", "org.example.foo", "org.example2"));

        assertEquals(new TreeSet<>(Arrays.asList("org.example", "org.example.foo")), groupIdsOf(result));
    }

    @Test
    void excludeDoesNotMatchUnrelatedGroupIdSharingStringPrefix() {
        final GroupIdFilter filter = new GroupIdFilter("", "org.example");

        final Set<Artifact> result = filter.filter(artifacts("org.example", "org.example.foo", "org.example2"));

        assertEquals(new TreeSet<>(Collections.singleton("org.example2")), groupIdsOf(result));
    }

    @Test
    void exactMatchOnlyMatchesTheListedGroupId() {
        final GroupIdFilter filter = new GroupIdFilter("org.example", "", true);

        final Set<Artifact> result = filter.filter(artifacts("org.example", "org.example.foo", "org.example2"));

        assertEquals(new TreeSet<>(Collections.singleton("org.example")), groupIdsOf(result));
    }
}
