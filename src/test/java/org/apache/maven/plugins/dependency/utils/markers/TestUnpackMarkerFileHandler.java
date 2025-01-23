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
package org.apache.maven.plugins.dependency.utils.markers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugins.dependency.fromConfiguration.ArtifactItem;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.testUtils.stubs.StubUnpackFileMarkerHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestUnpackMarkerFileHandler extends AbstractMojoTestCase {
    List<ArtifactItem> artifactItems = new ArrayList<>();

    @TempDir
    File outputFolder;

    @TempDir
    protected File testDir;

    protected DependencyArtifactStubFactory stubFactory;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();

        stubFactory = new DependencyArtifactStubFactory(this.testDir, false);
        Artifact artifact = stubFactory.createArtifact("test", "test", "1");
        ArtifactItem artifactItem;
        stubFactory.getArtifactItem(artifact);
        artifactItems.add(stubFactory.getArtifactItem(stubFactory.createArtifact("test", "test", "1")));
        artifact = stubFactory.createArtifact("test2", "test2", "2");
        artifactItem = new ArtifactItem(artifact);
        artifactItem.setIncludes("**/*.xml");
        artifactItems.add(artifactItem);
        artifact = stubFactory.createArtifact("test3", "test3", "3");
        artifactItem = new ArtifactItem(artifact);
        artifactItem.setExcludes("**/*.class");
        artifactItems.add(artifactItem);
        artifact = stubFactory.createArtifact("test4", "test4", "4");
        artifactItem = new ArtifactItem(artifact);
        artifactItem.setIncludes("**/*.xml");
        artifactItem.setExcludes("**/*.class");
        artifactItems.add(artifactItem);
    }

    /**
     * Assert that default functionality still exists
     *
     * @throws MojoExecutionException in case of an error.
     */
    @Test
    public void testSetMarker() throws MojoExecutionException {
        UnpackFileMarkerHandler handler = new UnpackFileMarkerHandler(artifactItems.get(0), this.outputFolder);
        assertFalse(handler.isMarkerSet());
        handler.setMarker();
        assertTrue(handler.isMarkerSet());
        handler.clearMarker();
        assertFalse(handler.isMarkerSet());

        handler.setMarker();
        assertTrue(handler.isMarkerSet());
        handler.setMarker();
        assertTrue(handler.isMarkerSet());

        handler.clearMarker();
        assertFalse(handler.isMarkerSet());
        handler.clearMarker();
        assertFalse(handler.isMarkerSet());
    }

    @Test
    public void testMarkerFile() throws MojoExecutionException, IOException {
        UnpackFileMarkerHandler handler = new UnpackFileMarkerHandler(artifactItems.get(0), this.outputFolder);

        File handle = handler.getMarkerFile();
        assertFalse(handle.exists());
        assertFalse(handler.isMarkerSet());

        handler.setMarker();
        assertTrue(handler.isMarkerSet());
        assertTrue(handle.exists());

        handle.delete();
        assertFalse(handler.isMarkerSet());

        handle.createNewFile();
        assertTrue(handler.isMarkerSet());

        handler.clearMarker();
        assertFalse(handle.exists());
    }

    @Test
    public void testMarkerTimeStamp() throws MojoExecutionException, IOException, InterruptedException {
        File theFile = new File(outputFolder, "theFile.jar");
        theFile.createNewFile();
        ArtifactItem theArtifactItem = artifactItems.get(0);
        Artifact theArtifact = theArtifactItem.getArtifact();
        theArtifact.setFile(theFile);
        UnpackFileMarkerHandler handler = new UnpackFileMarkerHandler(theArtifactItem, this.outputFolder);
        assertFalse(handler.isMarkerSet());
        // if the marker is not set, assume it is infinately older than the
        // artifact.
        assertTrue(handler.isMarkerOlder(theArtifact));
        handler.setMarker();
        assertFalse(handler.isMarkerOlder(theArtifact));

        assertTrue(theFile.setLastModified(theFile.lastModified() + 60000));
        assertTrue(handler.isMarkerOlder(theArtifact));

        theFile.delete();
        handler.clearMarker();
        assertFalse(handler.isMarkerSet());
    }

    @Test
    public void testMarkerFileException() {
        // this stub wraps the file with an object to throw exceptions
        StubUnpackFileMarkerHandler handler = new StubUnpackFileMarkerHandler(artifactItems.get(0), this.outputFolder);
        try {
            handler.setMarker();
            fail("Expected an Exception here");
        } catch (MojoExecutionException e) {

        }
    }

    @Test
    public void testGetterSetter() {
        UnpackFileMarkerHandler handler = new UnpackFileMarkerHandler(null, null);
        assertNull(handler.getArtifactItem());
        assertNull(handler.getArtifact());
        handler.setArtifactItem(artifactItems.get(0));
        assertSame(artifactItems.get(0), handler.getArtifactItem());
        assertSame(artifactItems.get(0).getArtifact(), handler.getArtifact());

        assertNull(handler.getMarkerFilesDirectory());
        handler.setMarkerFilesDirectory(outputFolder);
        assertSame(outputFolder, handler.getMarkerFilesDirectory());
    }

    @Test
    public void testNullParent() throws MojoExecutionException {
        // the parent isn't set so this will create the marker in the local
        // folder. We must clear the
        // marker to avoid leaving test droppings in root.
        UnpackFileMarkerHandler handler = new UnpackFileMarkerHandler(null, null);
        handler.setArtifactItem(artifactItems.get(0));
        handler.setMarker();
        assertTrue(handler.isMarkerSet());
        handler.clearMarker();
        assertFalse(handler.isMarkerSet());
    }

    @Test
    public void testIncludesMarker() throws MojoExecutionException, IOException {
        UnpackFileMarkerHandler handler = new UnpackFileMarkerHandler(artifactItems.get(1), outputFolder);
        File handle = handler.getMarkerFile();
        assertFalse(handle.exists());
        assertFalse(handler.isMarkerSet());

        handler.setMarker();
        assertTrue(handler.isMarkerSet());
        assertTrue(handle.exists());
        String hashCode = "" + ("**/*.xml".hashCode());
        assertTrue(handle.getName().contains(hashCode));

        handle.delete();
        assertFalse(handler.isMarkerSet());

        handle.createNewFile();
        assertTrue(handler.isMarkerSet());

        handler.clearMarker();
        assertFalse(handle.exists());
    }

    @Test
    public void testExcludesMarker() throws MojoExecutionException, IOException {
        UnpackFileMarkerHandler handler = new UnpackFileMarkerHandler(artifactItems.get(2), outputFolder);
        File handle = handler.getMarkerFile();
        assertFalse(handle.exists());
        assertFalse(handler.isMarkerSet());

        handler.setMarker();
        assertTrue(handler.isMarkerSet());
        assertTrue(handle.exists());
        String hashCode = "" + ("**/*.class".hashCode());
        assertTrue(handle.getName().contains(hashCode));

        handle.delete();
        assertFalse(handler.isMarkerSet());

        handle.createNewFile();
        assertTrue(handler.isMarkerSet());

        handler.clearMarker();
        assertFalse(handle.exists());
    }

    @Test
    public void testIncludesExcludesMarker() throws MojoExecutionException, IOException {
        UnpackFileMarkerHandler handler = new UnpackFileMarkerHandler(artifactItems.get(3), outputFolder);
        File handle = handler.getMarkerFile();
        assertFalse(handle.exists());
        assertFalse(handler.isMarkerSet());

        handler.setMarker();
        assertTrue(handler.isMarkerSet());
        assertTrue(handle.exists());
        String hashCode = "" + (0 + "**/*.class".hashCode() + "**/*.xml".hashCode());
        assertTrue(handle.getName().contains(hashCode));

        handle.delete();
        assertFalse(handler.isMarkerSet());

        handle.createNewFile();
        assertTrue(handler.isMarkerSet());

        handler.clearMarker();
        assertFalse(handle.exists());
    }
}
