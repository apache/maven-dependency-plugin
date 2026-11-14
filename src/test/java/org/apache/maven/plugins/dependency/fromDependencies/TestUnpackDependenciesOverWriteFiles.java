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
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.components.io.filemappers.FileMapper;
import org.codehaus.plexus.components.io.filemappers.PrefixFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * MDEP-480: existing files can be kept when unpacking.
 */
@MojoTest
@Basedir("/unit/unpack-dependencies-test")
class TestUnpackDependenciesOverWriteFiles {
    private static final long ENTRY_TIME = 1_000_000_000_000L;

    private static final String EXISTING = "existing";

    @TempDir
    private File tempDir;

    @Inject
    private MavenProject project;

    @BeforeEach
    void setUp() throws Exception {
        File zip = new File(tempDir, "entries.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            for (String name : new String[] {"existing.txt", "old.txt", "added.txt"}) {
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(ENTRY_TIME);
                out.putNextEntry(entry);
                out.write("from archive".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        DependencyArtifactStubFactory stubFactory = new DependencyArtifactStubFactory(tempDir, true, false);
        stubFactory.setSrcFile(zip);
        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());
        Set<Artifact> artifacts = stubFactory.getScopedArtifacts();
        project.setArtifacts(artifacts);
    }

    private static void createExisting(File dir) throws Exception {
        dir.mkdirs();
        File newer = new File(dir, "existing.txt");
        Files.write(newer.toPath(), EXISTING.getBytes(StandardCharsets.UTF_8));
        newer.setLastModified(ENTRY_TIME + 86_400_000L);
        File older = new File(dir, "old.txt");
        Files.write(older.toPath(), EXISTING.getBytes(StandardCharsets.UTF_8));
        older.setLastModified(ENTRY_TIME - 86_400_000L);
    }

    private static String read(File dir, String name) throws Exception {
        return new String(Files.readAllBytes(new File(dir, name).toPath()), StandardCharsets.UTF_8);
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testExistingFilesAreReplacedByDefault(UnpackDependenciesMojo mojo) throws Exception {
        createExisting(mojo.getOutputDirectory());
        mojo.execute();
        assertEquals("from archive", read(mojo.getOutputDirectory(), "existing.txt"));
        assertEquals("from archive", read(mojo.getOutputDirectory(), "old.txt"));
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testNewerExistingFileIsKept(UnpackDependenciesMojo mojo) throws Exception {
        createExisting(mojo.getOutputDirectory());
        mojo.setOverWriteFiles(false);
        mojo.execute();
        assertEquals(EXISTING, read(mojo.getOutputDirectory(), "existing.txt"));
        // timestamps do not matter: a file older than its archive entry is kept as well
        assertEquals(EXISTING, read(mojo.getOutputDirectory(), "old.txt"));
        assertEquals("from archive", read(mojo.getOutputDirectory(), "added.txt"));
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testMappedTargetIsChecked(UnpackDependenciesMojo mojo) throws Exception {
        File mapped = new File(mojo.getOutputDirectory(), "sub");
        createExisting(mapped);
        PrefixFileMapper mapper = new PrefixFileMapper();
        mapper.setPrefix("sub/");
        mojo.setFileMappers(new FileMapper[] {mapper});
        mojo.setOverWriteFiles(false);
        mojo.execute();
        assertEquals(EXISTING, read(mapped, "existing.txt"));
        assertEquals(EXISTING, read(mapped, "old.txt"));
        assertEquals("from archive", read(mapped, "added.txt"));
        // nothing was written to the unmapped paths
        assertFalse(new File(mojo.getOutputDirectory(), "added.txt").exists());
    }
}
