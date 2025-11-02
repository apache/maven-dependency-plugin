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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.testUtils.stubs.DependencyProjectStub;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestUnpackDependenciesMojo2 extends AbstractDependencyMojoTestCase {

    private static final String UNPACKABLE_FILE = "test.txt";

    private static final String UNPACKABLE_FILE_PATH =
            "target/test-classes/unit/unpack-dependencies-test/" + UNPACKABLE_FILE;

    private UnpackDependenciesMojo mojo;

    @Override
    protected String getTestDirectoryName() {
        return "unpack-dependencies";
    }

    @Override
    protected boolean shouldCreateFiles() {
        return true;
    }

    @BeforeEach
    public void setUp() throws Exception {
        // required for mojo lookups to work
        super.setUp();

        MavenProject project = new DependencyProjectStub();
        getContainer().addComponent(project, MavenProject.class.getName());

        MavenSession session = newMavenSession(project);
        getContainer().addComponent(session, MavenSession.class.getName());

        File testPom = new File(getBasedir(), "target/test-classes/unit/unpack-dependencies-test/plugin-config.xml");
        mojo = (UnpackDependenciesMojo) lookupMojo("unpack-dependencies", testPom);
        mojo.outputDirectory = new File(this.testDir, "outputDirectory");

        // it needs to get the archivermanager
        stubFactory.setUnpackableFile(lookup(ArchiverManager.class));
        // i'm using one file repeatedly to archive so I can test the name
        // programmatically.
        stubFactory.setSrcFile(new File(getBasedir() + File.separatorChar + UNPACKABLE_FILE_PATH));

        assertNotNull(mojo);
        assertNotNull(mojo.getProject());

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);

        project.setArtifacts(artifacts);
        mojo.markersDirectory = new File(this.testDir, "markers");
    }

    public File getUnpackedFile(Artifact artifact) {
        File destDir = DependencyUtil.getFormattedOutputDirectory(
                mojo.isUseSubDirectoryPerScope(),
                mojo.isUseSubDirectoryPerType(),
                mojo.isUseSubDirectoryPerArtifact(),
                mojo.useRepositoryLayout,
                mojo.stripVersion,
                mojo.stripType,
                mojo.getOutputDirectory(),
                artifact);
        File unpacked = new File(destDir, DependencyArtifactStubFactory.getUnpackableFileName(artifact));
        assertTrue(unpacked.exists());
        return unpacked;
    }

    @Test
    public void testDontOverWriteRelease()
            throws MojoExecutionException, InterruptedException, IOException, MojoFailureException {

        Set<Artifact> artifacts = new HashSet<>();
        Artifact release = stubFactory.getReleaseArtifact();
        assertTrue(release.getFile().setLastModified(System.currentTimeMillis() - 2000));

        artifacts.add(release);

        mojo.getProject().setArtifacts(artifacts);

        mojo.overWriteIfNewer = false;

        mojo.execute();

        assertUnpacked(release, false);
    }

    @Test
    public void testOverWriteRelease()
            throws MojoExecutionException, InterruptedException, IOException, MojoFailureException {

        Set<Artifact> artifacts = new HashSet<>();
        Artifact release = stubFactory.getReleaseArtifact();
        assertTrue(release.getFile().setLastModified(System.currentTimeMillis() - 2000));

        artifacts.add(release);

        mojo.getProject().setArtifacts(artifacts);

        mojo.overWriteReleases = true;
        mojo.overWriteIfNewer = false;

        mojo.execute();

        assertUnpacked(release, true);
    }

    @Test
    public void testDontOverWriteSnap()
            throws MojoExecutionException, InterruptedException, IOException, MojoFailureException {

        Set<Artifact> artifacts = new HashSet<>();
        Artifact snap = stubFactory.getSnapshotArtifact();
        assertTrue(snap.getFile().setLastModified(System.currentTimeMillis() - 2000));

        artifacts.add(snap);

        mojo.getProject().setArtifacts(artifacts);

        mojo.overWriteReleases = false;
        mojo.overWriteSnapshots = false;
        mojo.overWriteIfNewer = false;

        mojo.execute();

        assertUnpacked(snap, false);
    }

    @Test
    public void testOverWriteSnap()
            throws MojoExecutionException, InterruptedException, IOException, MojoFailureException {

        Set<Artifact> artifacts = new HashSet<>();
        Artifact snap = stubFactory.getSnapshotArtifact();
        assertTrue(snap.getFile().setLastModified(System.currentTimeMillis() - 2000));

        artifacts.add(snap);

        mojo.getProject().setArtifacts(artifacts);

        mojo.overWriteReleases = false;
        mojo.overWriteSnapshots = true;
        mojo.overWriteIfNewer = false;

        mojo.execute();

        assertUnpacked(snap, true);
    }

    @Test
    public void testOverWriteIfNewer()
            throws MojoExecutionException, InterruptedException, IOException, MojoFailureException {

        Set<Artifact> artifacts = new HashSet<>();
        Artifact snap = stubFactory.getSnapshotArtifact();
        assertTrue(snap.getFile().setLastModified(System.currentTimeMillis() - 2000));

        artifacts.add(snap);

        mojo.getProject().setArtifacts(artifacts);

        mojo.overWriteReleases = false;
        mojo.overWriteSnapshots = false;
        mojo.overWriteIfNewer = false;

        mojo.execute();

        File unpackedFile = getUnpackedFile(snap);

        // round down to the last second
        long time = System.currentTimeMillis();
        time = time - (time % 1000);
        // set source to be newer and dest to be a known value.
        assertTrue(snap.getFile().setLastModified(time + 3000));
        assertTrue(unpackedFile.setLastModified(time));
        // wait at least a second for filesystems that only record to the
        // nearest second.
        Thread.sleep(1000);

        assertEquals(time, unpackedFile.lastModified());
        mojo.execute();

        // make sure it didn't overwrite
        assertEquals(time, unpackedFile.lastModified());

        mojo.overWriteIfNewer = true;

        mojo.execute();

        assertNotEquals(time, unpackedFile.lastModified());
    }

    public void assertUnpacked(Artifact artifact, boolean overWrite)
            throws InterruptedException, MojoExecutionException, MojoFailureException {
        File unpackedFile = getUnpackedFile(artifact);

        Thread.sleep(100);
        // round down to the last second
        long time = System.currentTimeMillis();
        time = time - (time % 1000);
        assertTrue(unpackedFile.setLastModified(time));
        // wait at least a second for filesystems that only record to the
        // nearest second.
        Thread.sleep(1000);

        assertEquals(time, unpackedFile.lastModified());
        mojo.execute();

        if (overWrite) {
            assertNotEquals(time, unpackedFile.lastModified());
        } else {
            assertEquals(time, unpackedFile.lastModified());
        }
    }
}
