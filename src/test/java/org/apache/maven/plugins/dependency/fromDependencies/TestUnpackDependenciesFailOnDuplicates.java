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
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MDEP-772: failing when several entries are unpacked to the same path. Every stub artifact holds the same archive, so
 * the artifacts deliver the same paths.
 */
@MojoTest
@Basedir("/unit/unpack-dependencies-test")
class TestUnpackDependenciesFailOnDuplicates {

    @TempDir
    private File tempDir;

    @Inject
    private MavenProject project;

    private DependencyArtifactStubFactory stubFactory;

    @BeforeEach
    void setUp() throws Exception {
        File zip = new File(tempDir, "entries.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("dir/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("same.txt"));
            out.write("from archive".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        stubFactory = new DependencyArtifactStubFactory(tempDir, true, false);
        stubFactory.setSrcFile(zip);
        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());
        Set<Artifact> artifacts = stubFactory.getScopedArtifacts();
        project.setArtifacts(artifacts);
    }

    private static int markerCount(UnpackDependenciesMojo mojo) {
        String[] markers = mojo.getMarkersDirectory().list();
        return markers == null ? 0 : markers.length;
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testDuplicatesAreAllowedByDefault(UnpackDependenciesMojo mojo) throws Exception {
        assertDoesNotThrow(mojo::execute);
        assertTrue(new File(mojo.getOutputDirectory(), "same.txt").exists());
        assertTrue(markerCount(mojo) > 1);
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testDuplicateAcrossArtifactsFails(UnpackDependenciesMojo mojo) throws Exception {
        mojo.setFailOnDuplicates(true);
        MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("same.txt"), e.getMessage());
        assertTrue(e.getMessage().contains("<-"), e.getMessage());
        // the first copy was written, nothing is marked as unpacked
        assertTrue(new File(mojo.getOutputDirectory(), "same.txt").exists());
        assertEquals(0, markerCount(mojo));
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testPerArtifactSubdirectoriesAreNotDuplicates(UnpackDependenciesMojo mojo) throws Exception {
        mojo.setFailOnDuplicates(true);
        mojo.setUseSubDirectoryPerArtifact(true);
        assertDoesNotThrow(mojo::execute);
        assertTrue(markerCount(mojo) > 1);
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testExistingFileIsNotADuplicate(UnpackDependenciesMojo mojo) throws Exception {
        // a single artifact of the set, so that nothing else delivers the path
        mojo.setFailOnDuplicates(true);
        project.setArtifacts(Collections.singleton(
                stubFactory.createArtifact("test", "one", "1.0", Artifact.SCOPE_COMPILE, "jar", null)));
        File existing = new File(mojo.getOutputDirectory(), "same.txt");
        existing.getParentFile().mkdirs();
        Files.write(existing.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        assertDoesNotThrow(mojo::execute);
        assertEquals("from archive", new String(Files.readAllBytes(existing.toPath()), StandardCharsets.UTF_8));
    }
}
