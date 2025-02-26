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
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.dependency.testUtils.stubs.StubDefaultFileMarkerHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author brianf
 */
public class TestDefaultMarkerFileHandler {
    List<Artifact> artifacts = new ArrayList<>();

    @TempDir
    File outputFolder;

    @BeforeEach
    protected void setUp() throws Exception {
        ArtifactHandler ah = new DefaultArtifactHandler();
        VersionRange vr = VersionRange.createFromVersion("1.1");
        Artifact artifact = new DefaultArtifact("test", "1", vr, Artifact.SCOPE_COMPILE, "jar", "", ah, false);
        artifacts.add(artifact);
        artifact = new DefaultArtifact("test", "2", vr, Artifact.SCOPE_PROVIDED, "war", "", ah, false);
        artifacts.add(artifact);
        artifact = new DefaultArtifact("test", "3", vr, Artifact.SCOPE_TEST, "sources", "", ah, false);
        artifacts.add(artifact);
        artifact = new DefaultArtifact("test", "4", vr, Artifact.SCOPE_RUNTIME, "zip", "", ah, false);
        artifacts.add(artifact);
    }

    @Test
    public void testSetMarker() throws MojoExecutionException {
        DefaultFileMarkerHandler handler = new DefaultFileMarkerHandler(artifacts.get(0), this.outputFolder);
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
        DefaultFileMarkerHandler handler = new DefaultFileMarkerHandler(artifacts.get(0), this.outputFolder);

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
        outputFolder.mkdirs();
        theFile.createNewFile();
        Artifact theArtifact = artifacts.get(0);
        theArtifact.setFile(theFile);
        DefaultFileMarkerHandler handler = new DefaultFileMarkerHandler(theArtifact, this.outputFolder);
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
        StubDefaultFileMarkerHandler handler = new StubDefaultFileMarkerHandler(artifacts.get(0), this.outputFolder);
        try {
            handler.setMarker();
            fail("Expected an Exception here");
        } catch (MojoExecutionException e) {

        }
    }

    @Test
    public void testGetterSetter() {
        DefaultFileMarkerHandler handler = new DefaultFileMarkerHandler(null, null);
        assertNull(handler.getArtifact());
        handler.setArtifact(artifacts.get(0));
        assertSame(artifacts.get(0), handler.getArtifact());

        assertNull(handler.getMarkerFilesDirectory());
        handler.setMarkerFilesDirectory(outputFolder);
        assertSame(outputFolder, handler.getMarkerFilesDirectory());
    }

    @Test
    public void testNullParent() throws MojoExecutionException {
        // the parent isn't set so this will create the marker in the local
        // folder. We must clear the
        // marker to avoid leaving test droppings in root.
        DefaultFileMarkerHandler handler = new DefaultFileMarkerHandler(null, null);
        handler.setArtifact(artifacts.get(0));
        handler.setMarker();
        assertTrue(handler.isMarkerSet());
        handler.clearMarker();
        assertFalse(handler.isMarkerSet());
    }
}
