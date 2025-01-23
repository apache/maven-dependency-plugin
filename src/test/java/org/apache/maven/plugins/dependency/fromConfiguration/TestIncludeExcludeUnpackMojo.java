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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.plugins.dependency.testUtils.stubs.DependencyProjectStub;
import org.apache.maven.plugins.dependency.utils.markers.UnpackFileMarkerHandler;
import org.apache.maven.project.MavenProject;

public class TestIncludeExcludeUnpackMojo extends AbstractDependencyMojoTestCase {
    private final String PACKED_FILE = "test.zip";

    private final String UNPACKED_FILE_PREFIX = "test";

    private final String UNPACKED_FILE_SUFFIX = ".txt";

    private final String PACKED_FILE_PATH = "target/test-classes/unit/unpack-dependencies-test/" + PACKED_FILE;

    private UnpackMojo mojo;

    protected void setUp() throws Exception {
        // required for mojo lookups to work
        super.setUp("unpack", true, false);

        MavenProject project = new DependencyProjectStub();
        getContainer().addComponent(project, MavenProject.class.getName());

        MavenSession session = newMavenSession(project);
        getContainer().addComponent(session, MavenSession.class.getName());

        File testPom = new File(getBasedir(), "target/test-classes/unit/unpack-test/plugin-config.xml");
        mojo = (UnpackMojo) lookupMojo("unpack", testPom);
        mojo.setOutputDirectory(new File(this.testDir, "outputDirectory"));

        // i'm using one file repeatedly to archive so I can test the name
        // programmatically.
        stubFactory.setSrcFile(new File(getBasedir() + File.separatorChar + PACKED_FILE_PATH));
        Artifact artifact = stubFactory.createArtifact("test", "test", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        ArtifactItem item = stubFactory.getArtifactItem(artifact);
        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(item);
        assertNotNull(mojo);
        assertNotNull(mojo.getProject());

        mojo.setMarkersDirectory(new File(this.testDir, "markers"));
        mojo.setArtifactItems(list);

        LegacySupport legacySupport = lookup(LegacySupport.class);
        legacySupport.setSession(session);
        installLocalRepository(legacySupport);
    }

    protected void tearDown() {
        super.tearDown();

        mojo = null;
        System.gc();
    }

    public void assertMarkerFiles(Collection<ArtifactItem> items, boolean exist) {
        for (ArtifactItem item : items) {
            assertMarkerFile(exist, item);
        }
    }

    public void assertMarkerFile(boolean val, ArtifactItem item) {
        UnpackFileMarkerHandler handle = new UnpackFileMarkerHandler(item, mojo.getMarkersDirectory());
        try {
            assertEquals(val, handle.isMarkerSet());
        } catch (MojoExecutionException e) {
            fail(e.getLongMessage());
        }
    }

    private void assertUnpacked(boolean unpacked, String fileName) {
        File destFile = new File(mojo.getOutputDirectory().getAbsolutePath(), fileName);
        assertEquals(unpacked, destFile.exists());
    }

    /**
     * This test will validate that only the 1 and 11 files get unpacked
     *
     * @throws Exception in case of errors.
     */
    public void testUnpackIncludesManyFiles() throws Exception {
        mojo.setIncludes("**/*1" + UNPACKED_FILE_SUFFIX);
        mojo.execute();
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    /**
     * This test will verify only the 2 file gets unpacked
     *
     * @throws Exception in case of errors.
     */
    public void testUnpackIncludesSingleFile() throws Exception {
        mojo.setIncludes("**/test2" + UNPACKED_FILE_SUFFIX);
        mojo.execute();
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    /**
     * This test will verify all files get unpacked
     *
     * @throws Exception in case of errors.
     */
    public void testUnpackIncludesAllFiles() throws Exception {
        mojo.setIncludes("**/*");
        mojo.execute();
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    /**
     * This test will validate that only the 2 and 3 files get unpacked
     *
     * @throws Exception in case of errors.
     */
    public void testUnpackExcludesManyFiles() throws Exception {
        mojo.setExcludes("**/*1" + UNPACKED_FILE_SUFFIX);
        mojo.execute();
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    /**
     * This test will verify only the 1, 11 &amp; 3 files get unpacked
     *
     * @throws Exception in case of errors.
     */
    public void testUnpackExcludesSingleFile() throws Exception {
        mojo.setExcludes("**/test2" + UNPACKED_FILE_SUFFIX);
        mojo.execute();
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    /**
     * This test will verify no files get unpacked
     *
     * @throws Exception in case of errors.
     */
    public void testUnpackExcludesAllFiles() throws Exception {
        mojo.setExcludes("**/*");
        mojo.execute();
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    public void testNoIncludeExcludes() throws Exception {
        mojo.execute();
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    public void testIncludeArtifactItemOverride() throws Exception {
        Artifact artifact = stubFactory.createArtifact("test", "test", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        ArtifactItem item = stubFactory.getArtifactItem(artifact);
        item.setIncludes("**/*");
        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(item);
        mojo.setArtifactItems(list);
        mojo.setIncludes("**/test2" + UNPACKED_FILE_SUFFIX);
        mojo.execute();
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    public void testExcludeArtifactItemOverride() throws Exception {
        Artifact artifact = stubFactory.createArtifact("test", "test", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        ArtifactItem item = stubFactory.getArtifactItem(artifact);
        item.setExcludes("**/*");
        List<ArtifactItem> list = new ArrayList<>(1);
        list.add(item);
        mojo.setArtifactItems(list);
        mojo.setExcludes("**/test2" + UNPACKED_FILE_SUFFIX);
        mojo.execute();
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    public void testIncludeArtifactItemMultipleMarker() throws Exception {
        List<ArtifactItem> list = new ArrayList<>();
        Artifact artifact = stubFactory.createArtifact("test", "test", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        ArtifactItem item = stubFactory.getArtifactItem(artifact);
        item.setOverWrite("false");
        item.setIncludes("**/test2" + UNPACKED_FILE_SUFFIX);
        list.add(item);
        item = stubFactory.getArtifactItem(artifact);
        item.setOverWrite("false");
        item.setIncludes("**/test3" + UNPACKED_FILE_SUFFIX);
        list.add(item);
        mojo.setArtifactItems(list);
        mojo.execute();
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
        assertMarkerFiles(mojo.getArtifactItems(), true);
    }

    public void testIncludeArtifactItemMultipleExecutions() throws Exception {
        List<ArtifactItem> list = new ArrayList<>();
        Artifact artifact = stubFactory.createArtifact("test", "test", "1.0", Artifact.SCOPE_COMPILE, "jar", null);
        ArtifactItem item = stubFactory.getArtifactItem(artifact);
        item.setOverWrite("false");
        item.setIncludes("**/test2" + UNPACKED_FILE_SUFFIX);
        list.add(item);
        item = stubFactory.getArtifactItem(artifact);
        item.setOverWrite("false");
        item.setIncludes("**/test3" + UNPACKED_FILE_SUFFIX);
        list.add(item);
        mojo.setArtifactItems(list);
        mojo.execute();
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(false, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
        assertMarkerFiles(mojo.getArtifactItems(), true);

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
