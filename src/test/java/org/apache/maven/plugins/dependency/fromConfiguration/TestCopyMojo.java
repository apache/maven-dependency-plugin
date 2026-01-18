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

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.AssertionsKt.assertNull;

@MojoTest(realRepositorySession = true)
class TestCopyMojo {

    @TempDir
    private File tempDir;

    private DependencyArtifactStubFactory stubFactory;

    @Inject
    private MavenSession session;

    @Inject
    private MavenProject project;

    @BeforeEach
    void setUp() {
        stubFactory = new DependencyArtifactStubFactory(tempDir, true, false);
        session.getRequest().setLocalRepositoryPath(new File(tempDir, "localTestRepo"));

        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());
    }

    private ArtifactItem getSingleArtifactItem(CopyMojo mojo) throws MojoExecutionException {
        List<ArtifactItem> list =
                mojo.getProcessedArtifactItems(new ProcessArtifactItemsRequest(false, false, false, false));
        return list.get(0);
    }

    @Test
    @InjectMojo(goal = "copy")
    @MojoParameter(name = "artifact", value = "a:b:c")
    void testSetArtifactWithoutPackaging(CopyMojo mojo) {
        ArtifactItem item = mojo.getArtifactItems().get(0);
        assertEquals("a", item.getGroupId());
        assertEquals("b", item.getArtifactId());
        assertEquals("c", item.getVersion());
        assertEquals("jar", item.getType());
        assertNull(item.getClassifier());
    }

    @Test
    @InjectMojo(goal = "copy")
    @MojoParameter(name = "artifact", value = "a:b:c:d")
    void testSetArtifactWithoutClassifier(CopyMojo mojo) {
        ArtifactItem item = mojo.getArtifactItems().get(0);
        assertEquals("a", item.getGroupId());
        assertEquals("b", item.getArtifactId());
        assertEquals("c", item.getVersion());
        assertEquals("d", item.getType());
        assertNull(item.getClassifier());
    }

    @Test
    @InjectMojo(goal = "copy")
    @MojoParameter(name = "artifact", value = "a:b:c:d:e")
    void testSetArtifact(CopyMojo mojo) {
        ArtifactItem item = mojo.getArtifactItems().get(0);
        assertEquals("a", item.getGroupId());
        assertEquals("b", item.getArtifactId());
        assertEquals("c", item.getVersion());
        assertEquals("d", item.getType());
        assertEquals("e", item.getClassifier());
    }

    @Test
    @InjectMojo(goal = "copy")
    void testGetArtifactItems(CopyMojo mojo) throws Exception {

        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifact");
        item.setGroupId("groupId");
        item.setVersion("1.0");

        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(createArtifact(item));

        mojo.setArtifactItems(createArtifactItemArtifacts(list));

        ArtifactItem result = getSingleArtifactItem(mojo);
        assertEquals(mojo.getOutputDirectory(), result.getOutputDirectory());

        File output = new File(mojo.getOutputDirectory(), "override");
        item.setOutputDirectory(output);
        result = getSingleArtifactItem(mojo);
        assertEquals(output, result.getOutputDirectory());
    }

    private void assertFilesExist(Collection<ArtifactItem> items) {
        for (ArtifactItem item : items) {
            assertFileExists(item);
        }
    }

    private void assertFileExists(ArtifactItem item) {
        File file = new File(item.getOutputDirectory(), item.getDestFileName());
        assertTrue(file.exists());
    }

    @Test
    @InjectMojo(goal = "copy")
    void testMojoDefaults(CopyMojo mojo) {
        assertFalse(mojo.isStripVersion());
        assertFalse(mojo.isSkip());
        assertFalse(mojo.isStripClassifier());
    }

    @Test
    @InjectMojo(goal = "copy")
    void testCopyFile(CopyMojo mojo) throws Exception {
        List<ArtifactItem> list = stubFactory.getArtifactItems(stubFactory.getClassifiedArtifacts());

        mojo.setArtifactItems(createArtifactItemArtifacts(list));

        mojo.execute();

        assertFilesExist(list);
    }

    /**
     * TODO move to an integration test ...
     */
    @Test
    @Disabled("New version of resolver on classpath does not support timestamp version lookups in local repository.")
    void skipTestCopyFileWithBaseVersion(CopyMojo mojo) throws Exception {
        List<ArtifactItem> list = stubFactory.getArtifactItems(stubFactory.getClassifiedArtifacts());
        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifact");
        item.setGroupId("groupId");
        item.setVersion("1.0-20130210.213424-191");
        list.add(item);

        mojo.setArtifactItems(createArtifactItemArtifacts(list));
        mojo.setUseBaseVersion(true);

        mojo.execute();

        assertFilesExist(list);
    }

    @Test
    @InjectMojo(goal = "copy")
    @MojoParameter(name = "skip", value = "true")
    @MojoParameter(name = "artifact", value = "a:b:c")
    void testSkip(CopyMojo mojo) throws Exception {

        mojo.execute();

        assertFalse(mojo.getArtifactItems().isEmpty());

        for (ArtifactItem item : mojo.getArtifactItems()) {
            // these will be null because no processing has occured only when everything is skipped
            assertNull(item.getOutputDirectory());
            assertNull(item.getDestFileName());
        }
    }

    @Test
    @InjectMojo(goal = "copy")
    void testCopyFileNoOverwrite(CopyMojo mojo) throws Exception {
        List<ArtifactItem> list = stubFactory.getArtifactItems(stubFactory.getClassifiedArtifacts());

        for (ArtifactItem item : list) {
            // make sure that we copy even if false is set - MDEP-80
            item.setOverWrite("false");
        }

        mojo.setArtifactItems(createArtifactItemArtifacts(list));
        mojo.execute();

        assertFilesExist(list);
    }

    @Test
    @InjectMojo(goal = "copy")
    void testCopyToLocation(CopyMojo mojo) throws Exception {
        List<ArtifactItem> list = stubFactory.getArtifactItems(stubFactory.getClassifiedArtifacts());
        ArtifactItem item = list.get(0);
        item.setOutputDirectory(new File(mojo.getOutputDirectory(), "testOverride"));

        mojo.setArtifactItems(createArtifactItemArtifacts(list));

        mojo.execute();

        assertFilesExist(list);
    }

    @Test
    @InjectMojo(goal = "copy")
    void testCopyStripVersionSetInMojo(CopyMojo mojo) throws Exception {
        List<ArtifactItem> list = stubFactory.getArtifactItems(stubFactory.getClassifiedArtifacts());

        ArtifactItem item = list.get(0);
        item.setOutputDirectory(new File(mojo.getOutputDirectory(), "testOverride"));
        mojo.setStripVersion(true);

        mojo.setArtifactItems(createArtifactItemArtifacts(list));

        mojo.execute();
        assertEquals(DependencyUtil.getFormattedFileName(item.getArtifact(), true), item.getDestFileName());

        assertFilesExist(list);
    }

    @Test
    @InjectMojo(goal = "copy")
    void testCopyStripClassifierSetInMojo(CopyMojo mojo) throws Exception {
        List<ArtifactItem> list = stubFactory.getArtifactItems(stubFactory.getClassifiedArtifacts());

        ArtifactItem item = list.get(0);
        item.setOutputDirectory(new File(mojo.getOutputDirectory(), "testOverride"));
        mojo.setStripClassifier(true);

        mojo.setArtifactItems(createArtifactItemArtifacts(list));

        mojo.execute();
        assertEquals(
                DependencyUtil.getFormattedFileName(item.getArtifact(), false, false, false, true),
                item.getDestFileName());

        assertFilesExist(list);
    }

    @Test
    @InjectMojo(goal = "copy")
    void testNonClassifierStrip(CopyMojo mojo) throws Exception {
        List<ArtifactItem> list = stubFactory.getArtifactItems(stubFactory.getReleaseAndSnapshotArtifacts());
        mojo.setStripVersion(true);
        mojo.setArtifactItems(createArtifactItemArtifacts(list));

        mojo.execute();

        assertFilesExist(list);
    }

    @Test
    @InjectMojo(goal = "copy")
    void testNonClassifierNoStrip(CopyMojo mojo) throws Exception {
        List<ArtifactItem> list = stubFactory.getArtifactItems(stubFactory.getReleaseAndSnapshotArtifacts());

        mojo.setArtifactItems(createArtifactItemArtifacts(list));

        mojo.execute();

        assertFilesExist(list);
    }

    @Test
    @InjectMojo(goal = "copy")
    void testMissingVersionNotFound(CopyMojo mojo) throws Exception {
        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifactId");
        item.setClassifier("");
        item.setGroupId("groupId");
        item.setType("type");

        List<ArtifactItem> list = new ArrayList<>();
        list.add(item);
        mojo.setArtifactItems(list);

        try {
            mojo.execute();
            fail("Expected Exception Here.");
        } catch (MojoExecutionException e) {
            // caught the expected exception.
        }
    }

    private List<Dependency> getDependencyList(ArtifactItem item) {
        Dependency dep = new Dependency();
        dep.setArtifactId(item.getArtifactId());
        dep.setClassifier(item.getClassifier());
        dep.setGroupId(item.getGroupId());
        dep.setType(item.getType());
        dep.setVersion("2.0-SNAPSHOT");

        Dependency dep2 = new Dependency();
        dep2.setArtifactId(item.getArtifactId());
        dep2.setClassifier("classifier");
        dep2.setGroupId(item.getGroupId());
        dep2.setType(item.getType());
        dep2.setVersion("2.1");

        List<Dependency> list = new ArrayList<>(2);
        list.add(dep2);
        list.add(dep);

        return list;
    }

    @Test
    @InjectMojo(goal = "copy")
    void testMissingVersionFromDependencies(CopyMojo mojo) throws Exception {
        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifactId");
        item.setClassifier("");
        item.setGroupId("groupId");
        item.setType("type");

        List<ArtifactItem> list = new ArrayList<>();
        list.add(item);
        mojo.setArtifactItems(list);

        MavenProject project = mojo.getProject();
        project.setDependencies(createDependencyArtifacts(getDependencyList(item)));

        mojo.execute();
        this.assertFileExists(item);
        assertEquals("2.0-SNAPSHOT", item.getVersion());
    }

    @Test
    @InjectMojo(goal = "copy")
    void testMissingVersionFromDependenciesLooseMatch(CopyMojo mojo) throws Exception {
        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifactId");
        item.setClassifier("");
        item.setGroupId("groupId");
        item.setType("type");

        MavenProject project = mojo.getProject();
        project.setDependencies(createDependencyArtifacts(getDependencyList(item)));

        // ensure dependency exists
        item.setClassifier("sources");
        item.setType("jar");

        // pre-create item
        item.setVersion("2.1");
        createArtifact(item);
        item.setVersion(null);

        List<ArtifactItem> list = new ArrayList<>();
        list.add(item);
        mojo.setArtifactItems(list);

        mojo.execute();
        this.assertFileExists(item);
        assertEquals("2.1", item.getVersion());
    }

    @Test
    @InjectMojo(goal = "copy")
    void testMissingVersionFromDependenciesWithClassifier(CopyMojo mojo) throws Exception {
        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifactId");
        item.setClassifier("classifier");
        item.setGroupId("groupId");
        item.setType("type");

        List<ArtifactItem> list = new ArrayList<>();
        list.add(item);
        mojo.setArtifactItems(list);

        MavenProject project = mojo.getProject();
        project.setDependencies(createDependencyArtifacts(getDependencyList(item)));

        mojo.execute();
        this.assertFileExists(item);
        assertEquals("2.1", item.getVersion());
    }

    private List<Dependency> getDependencyMgtList(ArtifactItem item) {
        Dependency dep = new Dependency();
        dep.setArtifactId(item.getArtifactId());
        dep.setClassifier(item.getClassifier());
        dep.setGroupId(item.getGroupId());
        dep.setType(item.getType());
        dep.setVersion("3.0-SNAPSHOT");

        Dependency dep2 = new Dependency();
        dep2.setArtifactId(item.getArtifactId());
        dep2.setClassifier("classifier");
        dep2.setGroupId(item.getGroupId());
        dep2.setType(item.getType());
        dep2.setVersion("3.1");

        List<Dependency> list = new ArrayList<>(2);
        list.add(dep2);
        list.add(dep);

        return list;
    }

    @Test
    @InjectMojo(goal = "copy")
    void testMissingVersionFromDependencyMgt(CopyMojo mojo) throws Exception {
        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifactId");
        item.setClassifier("");
        item.setGroupId("groupId");
        item.setType("type");

        MavenProject project = mojo.getProject();
        project.setDependencies(getDependencyList(item));

        item = new ArtifactItem();

        item.setArtifactId("artifactId-2");
        item.setClassifier("");
        item.setGroupId("groupId");
        item.setType("type");

        List<ArtifactItem> list = new ArrayList<>();
        list.add(item);

        mojo.setArtifactItems(list);

        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setDependencies(createDependencyArtifacts(getDependencyMgtList(item)));
        project.getModel().setDependencyManagement(dependencyManagement);

        mojo.execute();

        this.assertFileExists(item);
        assertEquals("3.0-SNAPSHOT", item.getVersion());
    }

    @Test
    @InjectMojo(goal = "copy")
    void testMissingVersionFromDependencyMgtLooseMatch(CopyMojo mojo) throws Exception {
        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifactId");
        item.setClassifier("");
        item.setGroupId("groupId");
        item.setType("type");

        MavenProject project = mojo.getProject();
        project.setDependencies(getDependencyList(item));

        item = new ArtifactItem();

        item.setArtifactId("artifactId-2");
        item.setClassifier("");
        item.setGroupId("groupId");
        item.setType("type");

        List<ArtifactItem> list = new ArrayList<>();
        list.add(item);

        mojo.setArtifactItems(list);

        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setDependencies(createDependencyArtifacts(getDependencyMgtList(item)));
        project.getModel().setDependencyManagement(dependencyManagement);

        item.setType("jar");

        // pre-create item
        item.setVersion("3.1");
        createArtifact(item);
        item.setVersion(null);

        mojo.execute();

        this.assertFileExists(item);
        assertEquals("3.1", item.getVersion());
    }

    @Test
    @InjectMojo(goal = "copy")
    void testMissingVersionFromDependencyMgtWithClassifier(CopyMojo mojo) throws Exception {
        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifactId");
        item.setClassifier("classifier");
        item.setGroupId("groupId");
        item.setType("type");

        MavenProject project = mojo.getProject();
        project.setDependencies(getDependencyList(item));

        item = new ArtifactItem();

        item.setArtifactId("artifactId-2");
        item.setClassifier("classifier");
        item.setGroupId("groupId");
        item.setType("type");

        List<ArtifactItem> list = new ArrayList<>();
        list.add(item);

        mojo.setArtifactItems(list);

        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setDependencies(createDependencyArtifacts(getDependencyMgtList(item)));
        project.getModel().setDependencyManagement(dependencyManagement);

        mojo.execute();

        this.assertFileExists(item);
        assertEquals("3.1", item.getVersion());
    }

    @Test
    @InjectMojo(goal = "copy")
    void testArtifactNotFound(CopyMojo mojo) throws Exception {
        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifactId");
        item.setClassifier("");
        item.setGroupId("groupId");
        item.setType("type");
        item.setVersion("1.0");

        List<ArtifactItem> list = new ArrayList<>();
        list.add(item);
        mojo.setArtifactItems(list);

        try {
            mojo.execute();
            fail("ExpectedException");
        } catch (MojoExecutionException e) {
            assertEquals("Unable to find/resolve artifact.", e.getMessage());
        }
    }

    @Test
    @InjectMojo(goal = "copy")
    void testNoArtifactItems(CopyMojo mojo) {
        try {
            mojo.getProcessedArtifactItems(new ProcessArtifactItemsRequest(false, false, false, false));
            fail("Expected Exception");
        } catch (MojoExecutionException e) {
            assertEquals("There are no artifactItems configured.", e.getMessage());
        }
    }

    @Test
    @InjectMojo(goal = "copy")
    void testCopyDontOverWriteReleases(CopyMojo mojo) throws Exception {
        stubFactory.setCreateFiles(true);
        Artifact release = stubFactory.getReleaseArtifact();
        assertTrue(release.getFile().setLastModified(System.currentTimeMillis() - 2000));

        ArtifactItem item = new ArtifactItem(release);

        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(item);
        mojo.setArtifactItems(list);

        mojo.setOverWriteIfNewer(false);

        mojo.execute();

        File copiedFile = new File(item.getOutputDirectory(), item.getDestFileName());

        Thread.sleep(100);
        // round up to the next second
        long time = System.currentTimeMillis() + 1000;
        time = time - (time % 1000);
        copiedFile.setLastModified(time);
        Thread.sleep(100);

        mojo.execute();

        assertEquals(time, copiedFile.lastModified());
    }

    @Test
    @InjectMojo(goal = "copy")
    void testCopyDontOverWriteSnapshots(CopyMojo mojo) throws Exception {
        stubFactory.setCreateFiles(true);
        Artifact artifact = stubFactory.getSnapshotArtifact();
        assertTrue(artifact.getFile().setLastModified(System.currentTimeMillis() - 2000));

        ArtifactItem item = new ArtifactItem(artifact);

        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(item);
        mojo.setArtifactItems(list);

        mojo.setOverWriteIfNewer(false);

        mojo.execute();

        File copiedFile = new File(item.getOutputDirectory(), item.getDestFileName());

        Thread.sleep(100);
        // round up to the next second
        long time = System.currentTimeMillis() + 1000;
        time = time - (time % 1000);
        assertTrue(copiedFile.setLastModified(time));
        Thread.sleep(100);

        mojo.execute();

        assertEquals(time, copiedFile.lastModified());
    }

    @Test
    @InjectMojo(goal = "copy")
    void testCopyOverWriteReleases(CopyMojo mojo) throws Exception {
        stubFactory.setCreateFiles(true);
        Artifact release = stubFactory.getReleaseArtifact();
        assertTrue(release.getFile().setLastModified(1000L));
        assertEquals(1000L, release.getFile().lastModified());

        ArtifactItem item = new ArtifactItem(release);

        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(item);
        mojo.setArtifactItems(list);

        mojo.setOverWriteIfNewer(false);
        mojo.setOverWriteReleases(true);
        mojo.execute();

        File copiedFile = new File(item.getOutputDirectory(), item.getDestFileName());

        assertTrue(copiedFile.setLastModified(2000L));
        assertEquals(2000L, copiedFile.lastModified());

        mojo.execute();

        long timeCopyNow = copiedFile.lastModified();
        assertEquals(1000L, timeCopyNow);
    }

    @Test
    @InjectMojo(goal = "copy")
    void testCopyOverWriteSnapshot(CopyMojo mojo) throws Exception {
        stubFactory.setCreateFiles(true);
        Artifact artifact = stubFactory.getSnapshotArtifact();
        assertTrue(artifact.getFile().setLastModified(1000L));
        assertEquals(1000L, artifact.getFile().lastModified());

        ArtifactItem item = new ArtifactItem(artifact);

        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(item);
        mojo.setArtifactItems(list);

        mojo.setOverWriteIfNewer(false);
        mojo.setOverWriteReleases(false);
        mojo.setOverWriteSnapshots(true);
        mojo.execute();

        File copiedFile = new File(item.getOutputDirectory(), item.getDestFileName());

        assertTrue(copiedFile.setLastModified(2000L));
        assertEquals(2000L, copiedFile.lastModified());

        mojo.execute();

        long timeCopyNow = copiedFile.lastModified();
        assertEquals(1000L, timeCopyNow);
    }

    @Test
    @InjectMojo(goal = "copy")
    void testCopyOverWriteIfNewer(CopyMojo mojo) throws Exception {
        stubFactory.setCreateFiles(true);
        Artifact artifact = stubFactory.getSnapshotArtifact();
        assertTrue(artifact.getFile().setLastModified(System.currentTimeMillis() - 2000));

        ArtifactItem item = new ArtifactItem(artifact);

        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(item);
        mojo.setArtifactItems(list);
        mojo.setOverWriteIfNewer(true);
        mojo.execute();

        File copiedFile = new File(item.getOutputDirectory(), item.getDestFileName());

        // set dest to be old
        long time = System.currentTimeMillis() - 10000;
        time = time - (time % 1000);
        assertTrue(copiedFile.setLastModified(time));

        // set source to be newer
        assertTrue(artifact.getFile().setLastModified(time + 4000));
        mojo.execute();

        assertTrue(time < copiedFile.lastModified());
    }

    @Test
    @InjectMojo(goal = "copy")
    void testCopyFileWithOverideLocalRepo(CopyMojo mojo) throws Exception {

        List<ArtifactItem> list = stubFactory.getArtifactItems(stubFactory.getClassifiedArtifacts());

        mojo.setArtifactItems(list);

        File execLocalRepo = new File(tempDir, "executionLocalRepo");
        assertFalse(execLocalRepo.exists());

        stubFactory.setWorkingDir(execLocalRepo);
        createArtifactItemArtifacts(list);

        mojo.setLocalRepositoryDirectory(execLocalRepo);

        mojo.execute();

        assertFilesExist(list);
    }

    private List<Dependency> createDependencyArtifacts(List<Dependency> items) throws IOException {
        for (Dependency item : items) {
            String classifier = "".equals(item.getClassifier()) ? null : item.getClassifier();
            stubFactory.createArtifact(
                    item.getGroupId(),
                    item.getArtifactId(),
                    VersionRange.createFromVersion(item.getVersion()),
                    null,
                    item.getType(),
                    classifier,
                    item.isOptional());
        }
        return items;
    }

    private List<ArtifactItem> createArtifactItemArtifacts(List<ArtifactItem> items) throws IOException {
        for (ArtifactItem item : items) {
            createArtifact(item);
        }
        return items;
    }

    private ArtifactItem createArtifact(ArtifactItem item) throws IOException {

        String classifier = "".equals(item.getClassifier()) ? null : item.getClassifier();
        String version = item.getVersion() != null ? item.getVersion() : item.getBaseVersion();
        stubFactory.createArtifact(item.getGroupId(), item.getArtifactId(), version, null, item.getType(), classifier);
        return item;
    }
}
