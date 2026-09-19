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
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DependencyVersionResolverTest {
    @Test
    void indexesAllVariantsUnderTheirGroupAndArtifactId() {
        Artifact main = artifact("group", "artifact", "1.0", "jar", null);
        Artifact sources = artifact("group", "artifact", "2.0", "jar", "sources");
        Artifact other = artifact("group", "other", "3.0", "jar", null);

        Map<DependencyVersionResolver.GroupArtifactKey, List<Artifact>> index =
                DependencyVersionResolver.indexArtifacts(Arrays.asList(main, sources, other));

        assertEquals(Arrays.asList(main, sources), index.get(DependencyVersionResolver.GroupArtifactKey.from(main)));
        assertEquals(Arrays.asList(other), index.get(DependencyVersionResolver.GroupArtifactKey.from(other)));
        assertThrows(UnsupportedOperationException.class, () -> index.clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> index.get(DependencyVersionResolver.GroupArtifactKey.from(main))
                        .clear());
    }

    @Test
    void matchesExactVariantBeforeUsingAllGaVariants() {
        Artifact main = artifact("group", "artifact", "1.0", "jar", null);
        Artifact sources = artifact("group", "artifact", "2.0", "jar", "sources");
        List<Artifact> variants = Arrays.asList(main, sources);

        ArtifactItem item = new ArtifactItem();
        item.setGroupId("group");
        item.setArtifactId("artifact");
        item.setType("jar");
        item.setClassifier("sources");

        assertIterableEquals(Arrays.asList("2.0"), DependencyVersionResolver.matchingVersions(item, variants, false));
        assertIterableEquals(
                Arrays.asList("1.0", "2.0"), DependencyVersionResolver.matchingVersions(item, variants, true));
    }

    private Artifact artifact(String groupId, String artifactId, String version, String type, String classifier) {
        return new DefaultArtifact(
                groupId, artifactId, version, null, type, classifier, new DefaultArtifactHandler(type));
    }
}
