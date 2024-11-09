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
package org.apache.maven.plugins.dependency.utils.filters;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.utils.markers.DefaultFileMarkerHandler;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author brianf
 */
public class TestMarkerFileFilter {
    Set<Artifact> artifacts = new HashSet<>();

    @TempDir
    File outputFolder;

    DependencyArtifactStubFactory fact;

    @BeforeEach
    protected void setUp() throws Exception {
        this.fact = new DependencyArtifactStubFactory(outputFolder, false);
        artifacts = fact.getReleaseAndSnapshotArtifacts();
    }

    @Test
    public void testMarkerFile() throws ArtifactFilterException {
        MarkerFileFilter filter = new MarkerFileFilter(true, true, false, new DefaultFileMarkerHandler(outputFolder));
        Set<Artifact> result = filter.filter(artifacts);
        assertEquals(2, result.size());

        filter.setOverWriteReleases(false);
        filter.setOverWriteSnapshots(false);
        result = filter.filter(artifacts);
        assertEquals(2, result.size());
    }

    public void testMarkerSnapshots() throws ArtifactFilterException, MojoExecutionException, IOException {
        DefaultFileMarkerHandler handler = new DefaultFileMarkerHandler(fact.getSnapshotArtifact(), outputFolder);
        handler.setMarker();

        MarkerFileFilter filter = new MarkerFileFilter(true, false, false, new DefaultFileMarkerHandler(outputFolder));
        Set<Artifact> result = filter.filter(artifacts);
        assertEquals(1, result.size());

        filter.setOverWriteSnapshots(true);
        result = filter.filter(artifacts);
        assertEquals(2, result.size());
        assertTrue(handler.clearMarker());
    }

    public void testMarkerRelease() throws IOException, ArtifactFilterException, MojoExecutionException {
        DefaultFileMarkerHandler handler = new DefaultFileMarkerHandler(fact.getReleaseArtifact(), outputFolder);
        handler.setMarker();

        MarkerFileFilter filter = new MarkerFileFilter(false, false, false, new DefaultFileMarkerHandler(outputFolder));
        Set<Artifact> result = filter.filter(artifacts);
        assertEquals(1, result.size());

        filter.setOverWriteReleases(true);
        result = filter.filter(artifacts);
        assertEquals(2, result.size());

        assertTrue(handler.clearMarker());
    }

    @Test
    public void testMarkerTimestamp() throws IOException, MojoExecutionException, ArtifactFilterException {
        // filter includes release artifact because no marker present
        // filter includes snapshot artifact because it is newer than marker
        DependencyArtifactStubFactory fileFact = new DependencyArtifactStubFactory(outputFolder, true);
        Artifact snap = fileFact.getSnapshotArtifact();
        Artifact release = fileFact.getReleaseArtifact();
        Set<Artifact> tempArtifacts = new HashSet<>();
        tempArtifacts.add(snap);
        tempArtifacts.add(release);
        DefaultFileMarkerHandler handler = new DefaultFileMarkerHandler(snap, outputFolder);
        handler.setMarker();
        assertTrue(snap.getFile().setLastModified(snap.getFile().lastModified() + 1500));
        MarkerFileFilter filter = new MarkerFileFilter(false, false, true, new DefaultFileMarkerHandler(outputFolder));
        Set<Artifact> result = filter.filter(tempArtifacts);
        assertEquals(2, result.size());

        // update marker; filter won't include snapshot because timestamps equal
        handler.setMarker();
        result = filter.filter(tempArtifacts);
        assertEquals(1, result.size());

        // filter won't include snapshot because it is older than marker
        assertTrue(snap.getFile().setLastModified(snap.getFile().lastModified() - 10000));

        result = filter.filter(tempArtifacts);
        assertEquals(1, result.size());

        assertTrue(handler.clearMarker());
        assertFalse(handler.isMarkerSet());
        snap.getFile().delete();
        release.getFile().delete();
    }

    @Test
    public void testGettersSetters() {
        MarkerFileFilter filter = new MarkerFileFilter(true, false, true, new DefaultFileMarkerHandler(outputFolder));
        assertTrue(filter.isOverWriteReleases());
        assertFalse(filter.isOverWriteSnapshots());
        assertTrue(filter.isOverWriteIfNewer());

        filter.setOverWriteReleases(false);
        filter.setOverWriteSnapshots(true);
        filter.setOverWriteIfNewer(false);

        assertFalse(filter.isOverWriteReleases());
        assertTrue(filter.isOverWriteSnapshots());
        assertFalse(filter.isOverWriteIfNewer());
    }
}
