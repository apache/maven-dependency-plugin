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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.utils.markers.SourcesFileMarkerHandler;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author brianf
 */
public class TestResolveMarkerFileFilter {

    @TempDir
    File outputFolder;

    DependencyArtifactStubFactory fact;

    @BeforeEach
    protected void setUp() throws IOException {
        fact = new DependencyArtifactStubFactory(outputFolder, false);
        fact.getReleaseAndSnapshotArtifacts();
    }

    @Test
    public void testResolveFile() throws IOException, ArtifactFilterException, MojoExecutionException {
        SourcesFileMarkerHandler handler = new SourcesFileMarkerHandler(outputFolder);

        Artifact artifact = fact.getReleaseArtifact();
        handler.setArtifact(artifact);

        ResolveFileFilter filter = new ResolveFileFilter(handler);

        assertTrue(filter.isArtifactIncluded(artifact));
        handler.setMarker();
        assertFalse(filter.isArtifactIncluded(artifact));
    }
}
