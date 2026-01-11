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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.utils.markers.UnpackFileMarkerHandler;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@MojoTest(realRepositorySession = true)
@Basedir("/unit/unpack-dependencies-test")
class TestUnpackMojo {

    @TempDir
    private File tempDir;

    private DependencyArtifactStubFactory stubFactory;

    @Inject
    private MavenSession session;

    @Inject
    private MavenProject project;

    @Inject
    private ArchiverManager archiverManager;

    @BeforeEach
    protected void setUp() throws Exception {
        stubFactory = new DependencyArtifactStubFactory(tempDir, true, false);
        stubFactory.setUnpackableFile(archiverManager);
        stubFactory.setSrcFile(MojoExtension.getTestFile("test.txt"));

        session.getRequest().setLocalRepositoryPath(new File(tempDir, "localTestRepo"));

        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());
    }

    private ArtifactItem getSingleArtifactItem(UnpackMojo mojo) throws MojoExecutionException {
        List<ArtifactItem> list = mojo.getProcessedArtifactItems(false);
        return list.get(0);
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testGetArtifactItems(UnpackMojo mojo) throws Exception {

        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifact");
        item.setGroupId("groupId");
        item.setVersion("1.0");

        ArrayList<ArtifactItem> list = new ArrayList<>(1);
        list.add(createArtifact(item));

        mojo.setArtifactItems(list);

        ArtifactItem result = getSingleArtifactItem(mojo);
        assertEquals(mojo.getOutputDirectory(), result.getOutputDirectory());

        File output = new File(mojo.getOutputDirectory(), "override");
        item.setOutputDirectory(output);
        result = getSingleArtifactItem(mojo);
        assertEquals(output, result.getOutputDirectory());
    }

    private void assertMarkerFiles(UnpackMojo mojo, Collection<ArtifactItem> items, boolean exist) {
        for (ArtifactItem item : items) {
            assertMarkerFile(mojo, exist, item);
        }
    }

    private void assertMarkerFile(UnpackMojo mojo, boolean val, ArtifactItem item) {
        UnpackFileMarkerHandler handle = new UnpackFileMarkerHandler(item, mojo.getMarkersDirectory());
        try {
            assertEquals(val, handle.isMarkerSet());
        } catch (MojoExecutionException e) {
            fail(e.getLongMessage());
        }
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testUnpackFile(UnpackMojo mojo) throws Exception {
        List<ArtifactItem> list = stubFactory.getArtifactItems(stubFactory.getClassifiedArtifacts());

        mojo.setArtifactItems(list);

        mojo.execute();

        assertMarkerFiles(mojo, list, true);
    }

    @Test
    @InjectMojo(goal = "unpack")
    @MojoParameter(name = "skip", value = "true")
    void testSkip(UnpackMojo mojo) throws Exception {
        List<ArtifactItem> list = stubFactory.getArtifactItems(stubFactory.getClassifiedArtifacts());

        mojo.setArtifactItems(list);

        mojo.execute();

        assertMarkerFiles(mojo, list, false);
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testUnpackToLocation(UnpackMojo mojo) throws Exception {
        List<ArtifactItem> list = stubFactory.getArtifactItems(stubFactory.getClassifiedArtifacts());
        ArtifactItem item = list.get(0);
        item.setOutputDirectory(new File(mojo.getOutputDirectory(), "testOverride"));

        mojo.setArtifactItems(list);

        mojo.execute();

        assertMarkerFiles(mojo, list, true);
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testUnpackToLocationWhereLocationCannotBeCreatedThrowsException(UnpackMojo mojo) throws Exception {
        List<ArtifactItem> list = stubFactory.getArtifactItems(stubFactory.getClassifiedArtifacts());
        ArtifactItem item = list.get(0);
        item.setOutputDirectory(new File(mojo.getOutputDirectory(), "testOverride"));

        mojo.setArtifactItems(list);
        final File currentFile = mojo.getOutputDirectory();

        // pretend that the output directory cannot be found event after mkdirs has been called by the mojo
        // ifor instance in the case when the outputDirectory cannot be created because of permissions on the
        // parent of the output directory
        mojo.setOutputDirectory(new File(currentFile.getAbsolutePath()) {

            private static final long serialVersionUID = -8559876942040177020L;

            @Override
            public boolean exists() {
                // this file will always report that it does not exist
                return false;
            }
        });
        try {
            mojo.execute();
            fail("Expected Exception Here.");
        } catch (MojoExecutionException e) {
            // caught the expected exception.
        }
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testMissingVersionNotFound(UnpackMojo mojo) throws Exception {
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
    @InjectMojo(goal = "unpack")
    void testMissingVersionFromDependencies(UnpackMojo mojo) throws Exception {
        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifactId");
        item.setClassifier("");
        item.setGroupId("groupId");
        item.setType("jar");

        List<ArtifactItem> list = new ArrayList<>();
        list.add(item);
        mojo.setArtifactItems(list);

        MavenProject project = mojo.getProject();
        project.setDependencies(createArtifacts(getDependencyList(item)));

        mojo.execute();
        assertMarkerFile(mojo, true, item);
        assertEquals("2.0-SNAPSHOT", item.getVersion());
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testMissingVersionFromDependenciesWithClassifier(UnpackMojo mojo) throws Exception {
        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifactId");
        item.setClassifier("classifier");
        item.setGroupId("groupId");
        item.setType("war");

        List<ArtifactItem> list = new ArrayList<>();
        list.add(item);
        mojo.setArtifactItems(list);

        MavenProject project = mojo.getProject();
        project.setDependencies(createArtifacts(getDependencyList(item)));

        mojo.execute();
        assertMarkerFile(mojo, true, item);
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
    @InjectMojo(goal = "unpack")
    void testMissingVersionFromDependencyMgt(UnpackMojo mojo) throws Exception {
        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifactId");
        item.setClassifier("");
        item.setGroupId("groupId");
        item.setType("jar");

        MavenProject project = mojo.getProject();
        project.setDependencies(createArtifacts(getDependencyList(item)));

        item = new ArtifactItem();

        item.setArtifactId("artifactId-2");
        item.setClassifier("");
        item.setGroupId("groupId");
        item.setType("jar");

        List<ArtifactItem> list = new ArrayList<>();
        list.add(item);

        mojo.setArtifactItems(list);

        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setDependencies(createArtifacts(getDependencyMgtList(item)));
        project.getModel().setDependencyManagement(dependencyManagement);

        mojo.execute();
        assertMarkerFile(mojo, true, item);
        assertEquals("3.0-SNAPSHOT", item.getVersion());
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testMissingVersionFromDependencyMgtWithClassifier(UnpackMojo mojo) throws Exception {
        ArtifactItem item = new ArtifactItem();

        item.setArtifactId("artifactId");
        item.setClassifier("classifier");
        item.setGroupId("groupId");
        item.setType("jar");

        MavenProject project = mojo.getProject();
        project.setDependencies(createArtifacts(getDependencyList(item)));

        item = new ArtifactItem();

        item.setArtifactId("artifactId-2");
        item.setClassifier("classifier");
        item.setGroupId("groupId");
        item.setType("jar");

        stubFactory.createArtifact(
                "groupId",
                "artifactId-2",
                VersionRange.createFromVersion("3.0-SNAPSHOT"),
                null,
                "jar",
                "classifier",
                false);
        stubFactory.createArtifact(
                "groupId", "artifactId-2", VersionRange.createFromVersion("3.1"), null, "jar", "classifier", false);

        List<ArtifactItem> list = new ArrayList<>();
        list.add(item);

        mojo.setArtifactItems(list);

        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.setDependencies(createArtifacts(getDependencyMgtList(item)));
        project.getModel().setDependencyManagement(dependencyManagement);

        mojo.execute();

        assertMarkerFile(mojo, true, item);
        assertEquals("3.1", item.getVersion());
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testArtifactNotFound(UnpackMojo mojo) throws Exception {
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
    @InjectMojo(goal = "unpack")
    void testNoArtifactItems(UnpackMojo mojo) {
        try {
            mojo.getProcessedArtifactItems(false);
            fail("Expected Exception");
        } catch (MojoExecutionException e) {
            assertEquals("There are no artifactItems configured.", e.getMessage());
        }
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testUnpackDontOverWriteReleases(UnpackMojo mojo) throws Exception {
        stubFactory.setCreateFiles(true);
        Artifact release = stubFactory.getReleaseArtifact();
        assertTrue(release.getFile().setLastModified(System.currentTimeMillis() - 2000));

        ArtifactItem item = new ArtifactItem(createArtifact(release));

        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(item);
        mojo.setArtifactItems(list);

        mojo.setOverWriteIfNewer(false);

        mojo.execute();

        assertUnpacked(mojo, item, false);
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testUnpackDontOverWriteSnapshots(UnpackMojo mojo) throws Exception {
        stubFactory.setCreateFiles(true);
        Artifact artifact = stubFactory.getSnapshotArtifact();
        assertTrue(artifact.getFile().setLastModified(System.currentTimeMillis() - 2000));

        ArtifactItem item = new ArtifactItem(createArtifact(artifact));

        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(item);
        mojo.setArtifactItems(list);

        mojo.setOverWriteIfNewer(false);

        mojo.execute();

        assertUnpacked(mojo, item, false);
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testUnpackOverWriteReleases(UnpackMojo mojo) throws Exception {
        stubFactory.setCreateFiles(true);
        Artifact release = stubFactory.getReleaseArtifact();
        assertTrue(release.getFile().setLastModified(System.currentTimeMillis() - 2000));

        ArtifactItem item = new ArtifactItem(createArtifact(release));

        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(item);
        mojo.setArtifactItems(list);

        mojo.setOverWriteIfNewer(false);
        mojo.setOverWriteReleases(true);
        mojo.execute();

        assertUnpacked(mojo, item, true);
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testUnpackOverWriteSnapshot(UnpackMojo mojo) throws Exception {
        stubFactory.setCreateFiles(true);
        Artifact artifact = stubFactory.getSnapshotArtifact();
        assertTrue(artifact.getFile().setLastModified(System.currentTimeMillis() - 2000));

        ArtifactItem item = new ArtifactItem(createArtifact(artifact));

        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(item);
        mojo.setArtifactItems(list);

        mojo.setOverWriteIfNewer(false);
        mojo.setOverWriteReleases(false);
        mojo.setOverWriteSnapshots(true);
        mojo.execute();

        assertUnpacked(mojo, item, true);
    }

    /**
     * The following code has been modified to prevent the
     * JDK bug which is described in detail
     * https://bugs.openjdk.java.net/browse/JDK-8177809
     *
     */
    @Test
    @InjectMojo(goal = "unpack")
    void testUnpackOverWriteIfNewer(UnpackMojo mojo) throws Exception {
        final long now = System.currentTimeMillis();

        stubFactory.setCreateFiles(true);
        Artifact artifact = stubFactory.getSnapshotArtifact();
        assertTrue(artifact.getFile().setLastModified(now - 20000));

        ArtifactItem item = new ArtifactItem(createArtifact(artifact));

        List<ArtifactItem> list = Collections.singletonList(item);
        mojo.setArtifactItems(list);
        mojo.setOverWriteIfNewer(true);
        mojo.execute();
        File unpackedFile = getUnpackedFile(item);

        // round down to the last second
        long time = now;
        time = time - (time % 1000);
        // go back 30 more seconds for linux
        time -= 30000;
        // set to known value
        assertTrue(unpackedFile.setLastModified(time));
        // set source to be newer about some seconds,
        // especially on macOS it shouldn't be smaller than 16s in order to mitigate flapping test
        assertTrue(artifact.getFile().setLastModified(time + 16000));

        // manually set markerfile (must match getMarkerFile in DefaultMarkerFileHandler)
        File marker = new File(mojo.getMarkersDirectory(), artifact.getId().replace(':', '-') + ".marker");
        assertTrue(marker.setLastModified(time));

        mojo.execute();

        long markerLastModifiedMillis =
                Files.getLastModifiedTime(marker.toPath()).toMillis();
        long unpackedFileLastModifiedMillis =
                Files.getLastModifiedTime(unpackedFile.toPath()).toMillis();

        assertNotEquals(
                markerLastModifiedMillis,
                unpackedFileLastModifiedMillis,
                "unpackedFile '" + unpackedFile + "' lastModified() == " + markerLastModifiedMillis
                        + ": should be different");
    }

    private void assertUnpacked(UnpackMojo mojo, ArtifactItem item, boolean overWrite) throws Exception {

        File unpackedFile = getUnpackedFile(item);

        Thread.sleep(100);
        // round down to the last second
        long time = System.currentTimeMillis();
        time = time - (time % 1000);
        assertTrue(unpackedFile.setLastModified(time));

        assertEquals(time, unpackedFile.lastModified());
        mojo.execute();

        if (overWrite) {
            assertNotEquals(time, unpackedFile.lastModified());
        } else {
            assertEquals(time, unpackedFile.lastModified());
        }
    }

    private File getUnpackedFile(ArtifactItem item) {
        File unpackedFile = new File(
                item.getOutputDirectory(), DependencyArtifactStubFactory.getUnpackableFileName(item.getArtifact()));

        assertTrue(unpackedFile.exists());
        return unpackedFile;
    }

    // respects the createUnpackableFile flag of the ArtifactStubFactory
    private List<Dependency> createArtifacts(List<Dependency> items) throws IOException {
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

    private Artifact createArtifact(Artifact art) throws IOException {
        String classifier = "".equals(art.getClassifier()) ? null : art.getClassifier();
        stubFactory.createArtifact(
                art.getGroupId(),
                art.getArtifactId(),
                VersionRange.createFromVersion(art.getVersion()),
                null,
                art.getType(),
                classifier,
                art.isOptional());
        return art;
    }

    private ArtifactItem createArtifact(ArtifactItem item) throws IOException {
        String classifier = "".equals(item.getClassifier()) ? null : item.getClassifier();
        stubFactory.createArtifact(
                item.getGroupId(), item.getArtifactId(), item.getVersion(), null, item.getType(), classifier);
        return item;
    }
}
