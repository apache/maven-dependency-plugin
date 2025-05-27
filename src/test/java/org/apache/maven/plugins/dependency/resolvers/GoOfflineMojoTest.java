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

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.plugins.dependency.testUtils.stubs.DependencyProjectStub;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.junit.Before;
import org.junit.jupiter.api.Disabled;

public class GoOfflineMojoTest extends AbstractDependencyMojoTestCase {
    private GoOfflineMojo subject;

    @Before
    public void setUp() throws Exception {
        // required for mojo lookups to work
        super.setUp();
        customizeSetUp("go-offline", true, true);

        MavenProject project = new DependencyProjectStub();
        getContainer().addComponent(project, MavenProject.class.getName());

        MavenSession session = newMavenSession(project);
        getContainer().addComponent(session, MavenSession.class.getName());
    }

    private static final String GROUP_EXCLUDE_PREFIX = "skip.this.groupid";

    private static final String ARTIFACT_EXCLUDE_PREFIX = "skip-this-artifact";

    private static final String CLASSIFIER_EXCLUDE_PREFIX = "skipThisClassifier";

    private static final String DUMMY_ARTIFACT_NAME = "dummy-artifact";

    private static final String STUB_ARTIFACT_VERSION = "3.14";

    private static final String VALID_GROUP = "org.junit.jupiter";

    public void testExcludeGroupIds() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/go-offline-test/exclude-plugin-config.xml");

        subject = (GoOfflineMojo) lookupMojo("go-offline", testPom);
        assertNotNull(subject);

        Artifact artifact1 = new ArtifactStub();
        artifact1.setGroupId(GROUP_EXCLUDE_PREFIX);
        artifact1.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact1.setVersion(STUB_ARTIFACT_VERSION);

        Artifact artifact2 = new ArtifactStub();
        artifact2.setGroupId(GROUP_EXCLUDE_PREFIX + ".too");
        artifact2.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact2.setVersion(STUB_ARTIFACT_VERSION + "-SNAPSHOT");

        Artifact artifact3 = new ArtifactStub();
        artifact3.setGroupId("dont.skip.me");
        artifact3.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact3.setVersion("1.0");

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

    public void testExcludeArtifactIds() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/go-offline-test/exclude-plugin-config.xml");

        subject = (GoOfflineMojo) lookupMojo("go-offline", testPom);
        assertNotNull(subject);

        Artifact artifact1 = new ArtifactStub();
        artifact1.setGroupId(VALID_GROUP);
        artifact1.setArtifactId(ARTIFACT_EXCLUDE_PREFIX);
        artifact1.setVersion(STUB_ARTIFACT_VERSION + "-SNAPSHOT");

        Artifact artifact2 = new ArtifactStub();
        artifact2.setGroupId(VALID_GROUP);
        artifact2.setArtifactId(ARTIFACT_EXCLUDE_PREFIX + "-too");
        artifact2.setVersion(STUB_ARTIFACT_VERSION);

        Artifact artifact3 = new ArtifactStub();
        artifact3.setGroupId("dont.skip.me");
        artifact3.setArtifactId("dummy-artifact");
        artifact3.setVersion("1.0");

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

    public void testExcludeScope() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/go-offline-test/exclude-plugin-config.xml");

        subject = (GoOfflineMojo) lookupMojo("go-offline", testPom);
        assertNotNull(subject);

        Artifact artifact1 = new ArtifactStub();
        artifact1.setGroupId(VALID_GROUP);
        artifact1.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact1.setVersion(STUB_ARTIFACT_VERSION + "-SNAPSHOT");

        Artifact artifact2 = new ArtifactStub();
        artifact2.setGroupId(VALID_GROUP);
        artifact2.setArtifactId(DUMMY_ARTIFACT_NAME + "-too");
        artifact2.setVersion(STUB_ARTIFACT_VERSION);
        artifact2.setScope("system");

        Artifact artifact3 = new ArtifactStub();
        artifact3.setGroupId(VALID_GROUP);
        artifact3.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact3.setVersion(STUB_ARTIFACT_VERSION);

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

    public void testExcludeTypes() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/go-offline-test/exclude-plugin-config.xml");

        subject = (GoOfflineMojo) lookupMojo("go-offline", testPom);
        assertNotNull(subject);

        ArtifactStub artifact1 = new ArtifactStub();
        artifact1.setGroupId(VALID_GROUP);
        artifact1.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact1.setVersion(STUB_ARTIFACT_VERSION + "-SNAPSHOT");
        artifact1.setType("ear");

        ArtifactStub artifact2 = new ArtifactStub();
        artifact2.setGroupId(VALID_GROUP);
        artifact2.setArtifactId(DUMMY_ARTIFACT_NAME + "-too");
        artifact2.setVersion(STUB_ARTIFACT_VERSION);
        artifact2.setType("war");

        ArtifactStub artifact3 = new ArtifactStub();
        artifact3.setGroupId(VALID_GROUP);
        artifact3.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact3.setVersion(STUB_ARTIFACT_VERSION);
        artifact3.setType("pom");

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
    @Disabled("Requires update to maven-plugin-test-harness to support this test")
    public void xtestExcludeClassifiers() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/go-offline-test/exclude-plugin-config.xml");

        subject = (GoOfflineMojo) lookupMojo("go-offline", testPom);
        assertNotNull(subject);

        ArtifactStub artifact1 = new ArtifactStub();
        artifact1.setGroupId(VALID_GROUP);
        artifact1.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact1.setVersion(STUB_ARTIFACT_VERSION + "-SNAPSHOT");
        // artifact1.setClassifier(CLASSIFIER_EXCLUDE_PREFIX);

        ArtifactStub artifact2 = new ArtifactStub();
        artifact2.setGroupId(VALID_GROUP);
        artifact2.setArtifactId(DUMMY_ARTIFACT_NAME + "-too");
        artifact2.setVersion(STUB_ARTIFACT_VERSION);
        // artifact2.setClassifier(CLASSIFIER_EXCLUDE_PREFIX + "Too");

        ArtifactStub artifact3 = new ArtifactStub();
        artifact3.setGroupId(VALID_GROUP);
        artifact3.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact3.setVersion(STUB_ARTIFACT_VERSION);

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

    public void testIncludeGroupIds() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/go-offline-test/include-gid-plugin-config.xml");

        subject = (GoOfflineMojo) lookupMojo("go-offline", testPom);
        assertNotNull(subject);

        Artifact artifact1 = new ArtifactStub();
        artifact1.setGroupId(GROUP_INCLUDE_PREFIX);
        artifact1.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact1.setVersion(STUB_ARTIFACT_VERSION);

        Artifact artifact2 = new ArtifactStub();
        artifact2.setGroupId(GROUP_INCLUDE_PREFIX + ".too");
        artifact2.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact2.setVersion(STUB_ARTIFACT_VERSION + "-SNAPSHOT");

        Artifact artifact3 = new ArtifactStub();
        artifact3.setGroupId("skip.me");
        artifact3.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact3.setVersion("1.0");

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

    public void testIncludeArtifactIds() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/go-offline-test/include-aid-plugin-config.xml");

        subject = (GoOfflineMojo) lookupMojo("go-offline", testPom);
        assertNotNull(subject);

        Artifact artifact1 = new ArtifactStub();
        artifact1.setGroupId(VALID_GROUP);
        artifact1.setArtifactId(ARTIFACT_INCLUDE_PREFIX);
        artifact1.setVersion(STUB_ARTIFACT_VERSION);

        Artifact artifact2 = new ArtifactStub();
        artifact2.setGroupId(VALID_GROUP);
        artifact2.setArtifactId(ARTIFACT_INCLUDE_PREFIX + "-too");
        artifact2.setVersion(STUB_ARTIFACT_VERSION + "-SNAPSHOT");

        Artifact artifact3 = new ArtifactStub();
        artifact3.setGroupId(VALID_GROUP);
        artifact3.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact3.setVersion("1.0");

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

    public void testIncludeScope() throws Exception {
        File testPom =
                new File(getBasedir(), "target/test-classes/unit/go-offline-test/include-scope-plugin-config.xml");

        subject = (GoOfflineMojo) lookupMojo("go-offline", testPom);
        assertNotNull(subject);

        Artifact artifact1 = new ArtifactStub();
        artifact1.setGroupId(VALID_GROUP);
        artifact1.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact1.setVersion(STUB_ARTIFACT_VERSION + "-SNAPSHOT");
        artifact1.setScope("provided");

        Artifact artifact2 = new ArtifactStub();
        artifact2.setGroupId(VALID_GROUP);
        artifact2.setArtifactId(DUMMY_ARTIFACT_NAME + "-too");
        artifact2.setVersion(STUB_ARTIFACT_VERSION);
        artifact2.setScope("system");

        Artifact artifact3 = new ArtifactStub();
        artifact3.setGroupId(VALID_GROUP);
        artifact3.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact3.setVersion(STUB_ARTIFACT_VERSION);
        artifact3.setScope("test");

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

    public void testIncludeTypes() throws Exception {
        File testPom =
                new File(getBasedir(), "target/test-classes/unit/go-offline-test/include-types-plugin-config.xml");

        subject = (GoOfflineMojo) lookupMojo("go-offline", testPom);
        assertNotNull(subject);

        ArtifactStub artifact1 = new ArtifactStub();
        artifact1.setGroupId(VALID_GROUP);
        artifact1.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact1.setVersion(STUB_ARTIFACT_VERSION + "-SNAPSHOT");
        artifact1.setType("ear");

        ArtifactStub artifact2 = new ArtifactStub();
        artifact2.setGroupId(VALID_GROUP);
        artifact2.setArtifactId(DUMMY_ARTIFACT_NAME + "-too");
        artifact2.setVersion(STUB_ARTIFACT_VERSION);
        artifact2.setType("pom");

        ArtifactStub artifact3 = new ArtifactStub();
        artifact3.setGroupId(VALID_GROUP);
        artifact3.setArtifactId(DUMMY_ARTIFACT_NAME);
        artifact3.setVersion(STUB_ARTIFACT_VERSION + "-SNAPSHOT");
        artifact3.setType("war");

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
}
