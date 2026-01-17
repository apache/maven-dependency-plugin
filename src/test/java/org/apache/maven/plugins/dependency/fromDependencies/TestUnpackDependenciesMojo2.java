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

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MojoTest
@Basedir("/unit/unpack-dependencies-test")
class TestUnpackDependenciesMojo2 {

    @TempDir
    private File tempDir;

    private DependencyArtifactStubFactory stubFactory;

    @Inject
    private MavenSession session;

    @Inject
    private MavenProject project;

    @Inject
    private ArchiverManager archiverManager;

    private static final String UNPACKABLE_FILE = "test.txt";

    @BeforeEach
    void setUp() throws Exception {
        stubFactory = new DependencyArtifactStubFactory(tempDir, true, false);
        session.getRequest().setLocalRepositoryPath(new File(tempDir, "localTestRepo"));

        // it needs to get the archivermanager
        stubFactory.setUnpackableFile(archiverManager);
        // i'm using one file repeatedly to archive so I can test the name
        // programmatically.
        stubFactory.setSrcFile(MojoExtension.getTestFile(UNPACKABLE_FILE));

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);

        project.setArtifacts(artifacts);

        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());
    }

    private File getUnpackedFile(UnpackDependenciesMojo mojo, Artifact artifact) {
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
    @InjectMojo(goal = "unpack-dependencies")
    void testDontOverWriteRelease(UnpackDependenciesMojo mojo)
            throws MojoExecutionException, InterruptedException, IOException, MojoFailureException {

        Set<Artifact> artifacts = new HashSet<>();
        Artifact release = stubFactory.getReleaseArtifact();
        assertTrue(release.getFile().setLastModified(System.currentTimeMillis() - 2000));

        artifacts.add(release);

        mojo.getProject().setArtifacts(artifacts);

        mojo.overWriteIfNewer = false;

        mojo.execute();

        assertUnpacked(mojo, release, false);
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testOverWriteRelease(UnpackDependenciesMojo mojo)
            throws MojoExecutionException, InterruptedException, IOException, MojoFailureException {

        Set<Artifact> artifacts = new HashSet<>();
        Artifact release = stubFactory.getReleaseArtifact();
        assertTrue(release.getFile().setLastModified(System.currentTimeMillis() - 2000));

        artifacts.add(release);

        mojo.getProject().setArtifacts(artifacts);

        mojo.overWriteReleases = true;
        mojo.overWriteIfNewer = false;

        mojo.execute();

        assertUnpacked(mojo, release, true);
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testDontOverWriteSnap(UnpackDependenciesMojo mojo)
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

        assertUnpacked(mojo, snap, false);
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testOverWriteSnap(UnpackDependenciesMojo mojo)
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

        assertUnpacked(mojo, snap, true);
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testOverWriteIfNewer(UnpackDependenciesMojo mojo)
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

        File unpackedFile = getUnpackedFile(mojo, snap);

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

    private void assertUnpacked(UnpackDependenciesMojo mojo, Artifact artifact, boolean overWrite)
            throws InterruptedException, MojoExecutionException, MojoFailureException {
        File unpackedFile = getUnpackedFile(mojo, artifact);

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
