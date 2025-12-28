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
package org.apache.maven.plugins.dependency.resolvers;

import java.util.HashSet;
import java.util.Set;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MojoTest
class GoOfflineMojoTest {

    private static final String GROUP_EXCLUDE_PREFIX = "skip.this.groupid";

    private static final String ARTIFACT_EXCLUDE_PREFIX = "skip-this-artifact";

    private static final String CLASSIFIER_EXCLUDE_PREFIX = "skipThisClassifier";

    private static final String DUMMY_ARTIFACT_NAME = "dummy-artifact";

    private static final String STUB_ARTIFACT_VERSION = "3.14";

    private static final String VALID_GROUP = "org.junit.jupiter";

    @Test
    @Basedir("/unit/go-offline-test")
    @InjectMojo(goal = "go-offline", pom = "exclude-plugin-config.xml")
    void testExcludeGroupIds(GoOfflineMojo subject) throws Exception {
        Artifact artifact1 = anArtifact(GROUP_EXCLUDE_PREFIX, DUMMY_ARTIFACT_NAME, STUB_ARTIFACT_VERSION);
        Artifact artifact2 =
                anArtifact(GROUP_EXCLUDE_PREFIX + ".too", DUMMY_ARTIFACT_NAME, STUB_ARTIFACT_VERSION + "-SNAPSHOT");
        Artifact artifact3 = anArtifact("dont.skip.me", DUMMY_ARTIFACT_NAME, "1.0");

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(artifact1);
        artifacts.add(artifact2);
        artifacts.add(artifact3);

        assertEquals(3, artifacts.size());
        FilterArtifacts filter = subject.getArtifactsFilter();
        artifacts = filter.filter(artifacts);
        assertEquals(1, artifacts.size());
        assertTrue(artifacts.contains(artifact3));
        assertFalse(artifacts.contains(artifact1));
        assertFalse(artifacts.contains(artifact2));
    }

    @Test
    @Basedir("/unit/go-offline-test")
    @InjectMojo(goal = "go-offline", pom = "exclude-plugin-config.xml")
    void testExcludeArtifactIds(GoOfflineMojo subject) throws Exception {
        Artifact artifact1 = anArtifact(VALID_GROUP, ARTIFACT_EXCLUDE_PREFIX, STUB_ARTIFACT_VERSION + "-SNAPSHOT");
        Artifact artifact2 = anArtifact(VALID_GROUP, ARTIFACT_EXCLUDE_PREFIX + "-too", STUB_ARTIFACT_VERSION);
        Artifact artifact3 = anArtifact("dont.skip.me", "dummy-artifact", "1.0");

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(artifact1);
        artifacts.add(artifact2);
        artifacts.add(artifact3);

        assertEquals(3, artifacts.size());
        FilterArtifacts filter = subject.getArtifactsFilter();
        artifacts = filter.filter(artifacts);
        assertEquals(1, artifacts.size());
        assertTrue(artifacts.contains(artifact3));
        assertFalse(artifacts.contains(artifact1));
        assertFalse(artifacts.contains(artifact2));
    }

    @Test
    @Basedir("/unit/go-offline-test")
    @InjectMojo(goal = "go-offline", pom = "exclude-plugin-config.xml")
    void testExcludeScope(GoOfflineMojo subject) throws Exception {
        Artifact artifact1 = anArtifact(VALID_GROUP, DUMMY_ARTIFACT_NAME, STUB_ARTIFACT_VERSION + "-SNAPSHOT");
        Artifact artifact2 = anArtifactScope(DUMMY_ARTIFACT_NAME + "-too", STUB_ARTIFACT_VERSION, "system");
        Artifact artifact3 = anArtifact(VALID_GROUP, DUMMY_ARTIFACT_NAME, STUB_ARTIFACT_VERSION);

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(artifact1);
        artifacts.add(artifact2);
        artifacts.add(artifact3);

        assertEquals(3, artifacts.size());
        FilterArtifacts filter = subject.getArtifactsFilter();
        artifacts = filter.filter(artifacts);
        assertEquals(2, artifacts.size());
        assertTrue(artifacts.contains(artifact3));
        assertTrue(artifacts.contains(artifact1));
        assertFalse(artifacts.contains(artifact2));
    }

    @Test
    @Basedir("/unit/go-offline-test")
    @InjectMojo(goal = "go-offline", pom = "exclude-plugin-config.xml")
    void testExcludeTypes(GoOfflineMojo subject) throws Exception {
        Artifact artifact1 = anArtifactType(DUMMY_ARTIFACT_NAME, STUB_ARTIFACT_VERSION + "-SNAPSHOT", "ear");
        Artifact artifact2 = anArtifactType(DUMMY_ARTIFACT_NAME + "-too", STUB_ARTIFACT_VERSION, "war");
        Artifact artifact3 = anArtifactType(DUMMY_ARTIFACT_NAME, STUB_ARTIFACT_VERSION, "pom");

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(artifact1);
        artifacts.add(artifact2);
        artifacts.add(artifact3);

        assertEquals(3, artifacts.size());
        FilterArtifacts filter = subject.getArtifactsFilter();
        artifacts = filter.filter(artifacts);
        assertEquals(1, artifacts.size());
        assertFalse(artifacts.contains(artifact3));
        assertTrue(artifacts.contains(artifact2));
        assertFalse(artifacts.contains(artifact1));
    }

    /**
     * Can't set a classifier on the ArtifactStub as of maven-plugin-testing-harness-3.3.0, there is a getter but no
     * setter. If that ever gets implemented, comment in these two lines to support unit testing for this case, rename
     * xtest to test and remove the Junit 5 Disabled annotation
     *
     * @throws Exception
     */
    @Test
    @Basedir("/unit/go-offline-test")
    @InjectMojo(goal = "go-offline", pom = "exclude-plugin-config.xml")
    void testExcludeClassifiers(GoOfflineMojo subject) throws Exception {
        Artifact artifact1 = anArtifactClassifier(
                DUMMY_ARTIFACT_NAME, STUB_ARTIFACT_VERSION + "-SNAPSHOT", CLASSIFIER_EXCLUDE_PREFIX);
        Artifact artifact2 = anArtifactClassifier(
                DUMMY_ARTIFACT_NAME + "-too", STUB_ARTIFACT_VERSION, CLASSIFIER_EXCLUDE_PREFIX + "Too");
        Artifact artifact3 = anArtifact(VALID_GROUP, DUMMY_ARTIFACT_NAME, STUB_ARTIFACT_VERSION);

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(artifact1);
        artifacts.add(artifact2);
        artifacts.add(artifact3);

        assertEquals(3, artifacts.size());
        FilterArtifacts filter = subject.getArtifactsFilter();
        artifacts = filter.filter(artifacts);
        assertEquals(1, artifacts.size());
        assertFalse(artifacts.contains(artifact1));
        assertFalse(artifacts.contains(artifact2));
        assertTrue(artifacts.contains(artifact3));
    }

    private static final String GROUP_INCLUDE_PREFIX = "include.this.groupid";

    private static final String ARTIFACT_INCLUDE_PREFIX = "include-this-artifact";

    private static final String CLASSIFIER_INCLUDE_PREFIX = "includeThisClassifier";

    @Test
    @Basedir("/unit/go-offline-test")
    @InjectMojo(goal = "go-offline", pom = "include-gid-plugin-config.xml")
    void testIncludeGroupIds(GoOfflineMojo subject) throws Exception {
        Artifact artifact1 = anArtifact(GROUP_INCLUDE_PREFIX, DUMMY_ARTIFACT_NAME, STUB_ARTIFACT_VERSION);
        Artifact artifact2 =
                anArtifact(GROUP_INCLUDE_PREFIX + ".too", DUMMY_ARTIFACT_NAME, STUB_ARTIFACT_VERSION + "-SNAPSHOT");
        Artifact artifact3 = anArtifact("skip.me", DUMMY_ARTIFACT_NAME, "1.0");

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(artifact1);
        artifacts.add(artifact2);
        artifacts.add(artifact3);

        assertEquals(3, artifacts.size());
        FilterArtifacts filter = subject.getArtifactsFilter();
        artifacts = filter.filter(artifacts);

        assertEquals(2, artifacts.size());
        assertFalse(artifacts.contains(artifact3));
        assertTrue(artifacts.contains(artifact1));
        assertTrue(artifacts.contains(artifact2));
    }

    @Test
    @Basedir("/unit/go-offline-test")
    @InjectMojo(goal = "go-offline", pom = "include-aid-plugin-config.xml")
    void testIncludeArtifactIds(GoOfflineMojo subject) throws Exception {
        Artifact artifact1 = anArtifact(VALID_GROUP, ARTIFACT_INCLUDE_PREFIX, STUB_ARTIFACT_VERSION);
        Artifact artifact2 =
                anArtifact(VALID_GROUP, ARTIFACT_INCLUDE_PREFIX + "-too", STUB_ARTIFACT_VERSION + "-SNAPSHOT");
        Artifact artifact3 = anArtifact(VALID_GROUP, DUMMY_ARTIFACT_NAME, "1.0");

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(artifact1);
        artifacts.add(artifact2);
        artifacts.add(artifact3);

        assertEquals(3, artifacts.size());
        FilterArtifacts filter = subject.getArtifactsFilter();
        artifacts = filter.filter(artifacts);

        assertEquals(2, artifacts.size());
        assertFalse(artifacts.contains(artifact3));
        assertTrue(artifacts.contains(artifact1));
        assertTrue(artifacts.contains(artifact2));
    }

    @Test
    @Basedir("/unit/go-offline-test")
    @InjectMojo(goal = "go-offline", pom = "include-scope-plugin-config.xml")
    void testIncludeScope(GoOfflineMojo subject) throws Exception {
        Artifact artifact1 = anArtifactScope(DUMMY_ARTIFACT_NAME, STUB_ARTIFACT_VERSION + "-SNAPSHOT", "provided");
        Artifact artifact2 = anArtifactScope(DUMMY_ARTIFACT_NAME + "-too", STUB_ARTIFACT_VERSION, "system");
        Artifact artifact3 = anArtifactScope(DUMMY_ARTIFACT_NAME, STUB_ARTIFACT_VERSION, "test");

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(artifact1);
        artifacts.add(artifact2);
        artifacts.add(artifact3);

        assertEquals(3, artifacts.size());
        FilterArtifacts filter = subject.getArtifactsFilter();
        artifacts = filter.filter(artifacts);
        assertEquals(1, artifacts.size());

        assertTrue(artifacts.contains(artifact1));
        assertFalse(artifacts.contains(artifact2));
        assertFalse(artifacts.contains(artifact3));
    }

    @Test
    @Basedir("/unit/go-offline-test")
    @InjectMojo(goal = "go-offline", pom = "include-types-plugin-config.xml")
    void testIncludeTypes(GoOfflineMojo subject) throws Exception {
        Artifact artifact1 = anArtifactType(DUMMY_ARTIFACT_NAME, STUB_ARTIFACT_VERSION + "-SNAPSHOT", "ear");
        Artifact artifact2 = anArtifactType(DUMMY_ARTIFACT_NAME + "-too", STUB_ARTIFACT_VERSION, "pom");
        Artifact artifact3 = anArtifactType(DUMMY_ARTIFACT_NAME, STUB_ARTIFACT_VERSION + "-SNAPSHOT", "war");

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(artifact1);
        artifacts.add(artifact2);
        artifacts.add(artifact3);

        assertEquals(3, artifacts.size());
        FilterArtifacts filter = subject.getArtifactsFilter();
        artifacts = filter.filter(artifacts);
        assertEquals(2, artifacts.size());

        assertTrue(artifacts.contains(artifact1));
        assertTrue(artifacts.contains(artifact2));
        assertFalse(artifacts.contains(artifact3));
    }

    private Artifact anArtifact(String groupId, String artifactId, String version) {
        return new DefaultArtifact(groupId, artifactId, version, null, "jar", "", null);
    }

    private Artifact anArtifactScope(String version, String artifactId, String scope) {
        return new DefaultArtifact(VALID_GROUP, artifactId, version, scope, "jar", "", null);
    }

    private Artifact anArtifactType(String artifactId, String version, String type) {
        return new DefaultArtifact(VALID_GROUP, artifactId, version, null, type, "", null);
    }

    private Artifact anArtifactClassifier(String artifactId, String version, String classifier) {
        return new DefaultArtifact(VALID_GROUP, artifactId, version, null, "jar", classifier, null);
    }
}
