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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.utils.markers.UnpackFileMarkerHandler;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@MojoTest(realRepositorySession = true)
@Basedir("/unit/unpack-dependencies-test")
@MojoParameter(name = "artifact", value = "test:test:1.0")
class TestIncludeExcludeUnpackMojo {

    @TempDir
    private File tempDir;

    private DependencyArtifactStubFactory stubFactory;

    @Inject
    private MavenSession session;

    @Inject
    private MavenProject project;

    private static final String UNPACKED_FILE_PREFIX = "test";

    private static final String UNPACKED_FILE_SUFFIX = ".txt";

    @BeforeEach
    void setUp() throws Exception {
        stubFactory = new DependencyArtifactStubFactory(tempDir, true, false);
        stubFactory.setSrcFile(MojoExtension.getTestFile("test.zip"));
        stubFactory.createArtifact("test", "test", "1.0", Artifact.SCOPE_COMPILE, "jar", null);

        session.getRequest().setLocalRepositoryPath(new File(tempDir, "localTestRepo"));

        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());
    }

    private void assertMarkerFiles(UnpackMojo mojo, Collection<ArtifactItem> items) {
        for (ArtifactItem item : items) {
            assertMarkerFile(mojo, item);
        }
    }

    private void assertMarkerFile(UnpackMojo mojo, ArtifactItem item) {
        UnpackFileMarkerHandler handle = new UnpackFileMarkerHandler(item, mojo.getMarkersDirectory());
        try {
            assertTrue(handle.isMarkerSet());
        } catch (MojoExecutionException e) {
            fail(e.getLongMessage());
        }
    }

    private void assertUnpacked(UnpackMojo mojo, boolean unpacked, String fileName) {
        File destFile = new File(mojo.getOutputDirectory().getAbsolutePath(), fileName);
        assertEquals(unpacked, destFile.exists());
    }

    /**
     * This test will validate that only the 1 and 11 files get unpacked
     *
     * @throws Exception in case of errors.
     */
    @Test
    @InjectMojo(goal = "unpack")
    void testUnpackIncludesManyFiles(UnpackMojo mojo) throws Exception {
        mojo.setIncludes("**/*1" + UNPACKED_FILE_SUFFIX);
        mojo.execute();
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    /**
     * This test will verify only the 2 file gets unpacked
     *
     * @throws Exception in case of errors.
     */
    @Test
    @InjectMojo(goal = "unpack")
    void testUnpackIncludesSingleFile(UnpackMojo mojo) throws Exception {
        mojo.setIncludes("**/test2" + UNPACKED_FILE_SUFFIX);
        mojo.execute();
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    /**
     * This test will verify all files get unpacked
     *
     * @throws Exception in case of errors.
     */
    @Test
    @InjectMojo(goal = "unpack")
    void testUnpackIncludesAllFiles(UnpackMojo mojo) throws Exception {
        mojo.setIncludes("**/*");
        mojo.execute();
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    /**
     * This test will validate that only the 2 and 3 files get unpacked
     *
     * @throws Exception in case of errors.
     */
    @Test
    @InjectMojo(goal = "unpack")
    void testUnpackExcludesManyFiles(UnpackMojo mojo) throws Exception {
        mojo.setExcludes("**/*1" + UNPACKED_FILE_SUFFIX);
        mojo.execute();
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    /**
     * This test will verify only the 1, 11 &amp; 3 files get unpacked
     *
     * @throws Exception in case of errors.
     */
    @Test
    @InjectMojo(goal = "unpack")
    void testUnpackExcludesSingleFile(UnpackMojo mojo) throws Exception {
        mojo.setExcludes("**/test2" + UNPACKED_FILE_SUFFIX);
        mojo.execute();
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    /**
     * This test will verify no files get unpacked
     *
     * @throws Exception in case of errors.
     */
    @Test
    @InjectMojo(goal = "unpack")
    void testUnpackExcludesAllFiles(UnpackMojo mojo) throws Exception {
        mojo.setExcludes("**/*");
        mojo.execute();
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testNoIncludeExcludes(UnpackMojo mojo) throws Exception {
        mojo.execute();
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testIncludeArtifactItemOverride(UnpackMojo mojo) throws Exception {
        Artifact artifact = stubFactory.createArtifact("test", "test", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        ArtifactItem item = new ArtifactItem(artifact);
        item.setIncludes("**/*");
        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(item);
        mojo.setArtifactItems(list);
        mojo.setIncludes("**/test2" + UNPACKED_FILE_SUFFIX);
        mojo.execute();
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testExcludeArtifactItemOverride(UnpackMojo mojo) throws Exception {
        Artifact artifact = stubFactory.createArtifact("test", "test", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        ArtifactItem item = new ArtifactItem(artifact);
        item.setExcludes("**/*");
        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(item);
        mojo.setArtifactItems(list);
        mojo.setExcludes("**/test2" + UNPACKED_FILE_SUFFIX);
        mojo.execute();
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testIncludeArtifactItemMultipleMarker(UnpackMojo mojo) throws Exception {
        List<ArtifactItem> list = new ArrayList<>();
        Artifact artifact = stubFactory.createArtifact("test", "test", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        ArtifactItem item = new ArtifactItem(artifact);
        item.setOverWrite("false");
        item.setIncludes("**/test2" + UNPACKED_FILE_SUFFIX);
        list.add(item);
        item = new ArtifactItem(artifact);
        item.setOverWrite("false");
        item.setIncludes("**/test3" + UNPACKED_FILE_SUFFIX);
        list.add(item);
        mojo.setArtifactItems(list);
        mojo.execute();
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
        assertMarkerFiles(mojo, mojo.getArtifactItems());
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testIncludeArtifactItemMultipleExecutions(UnpackMojo mojo) throws Exception {
        List<ArtifactItem> list = new ArrayList<>();
        Artifact artifact = stubFactory.createArtifact("test", "test", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        ArtifactItem item = new ArtifactItem(artifact);
        item.setOverWrite("false");
        item.setIncludes("**/test2" + UNPACKED_FILE_SUFFIX);
        list.add(item);
        item = new ArtifactItem(artifact);
        item.setOverWrite("false");
        item.setIncludes("**/test3" + UNPACKED_FILE_SUFFIX);
        list.add(item);
        mojo.setArtifactItems(list);
        mojo.execute();
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
        assertMarkerFiles(mojo, mojo.getArtifactItems());

        // Now run again and make sure the extracted files haven't gotten overwritten
        File destFile2 =
                new File(mojo.getOutputDirectory().getAbsolutePath(), UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        File destFile3 =
                new File(mojo.getOutputDirectory().getAbsolutePath(), UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
        long time = System.currentTimeMillis();
        time = time - (time % 1000);
        assertTrue(destFile2.setLastModified(time));
        assertTrue(destFile3.setLastModified(time));
        assertEquals(time, destFile2.lastModified());
        assertEquals(time, destFile3.lastModified());
        Thread.sleep(100);
        mojo.execute();
        assertEquals(time, destFile2.lastModified());
        assertEquals(time, destFile3.lastModified());
    }
}
