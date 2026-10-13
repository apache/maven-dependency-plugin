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
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MDEP-496: the default excludes of the file selector must be switchable.
 */
@MojoTest
@Basedir("/unit/unpack-dependencies-test")
class TestUnpackDependenciesDefaultExcludes {
    @TempDir
    private File tempDir;

    @Inject
    private MavenProject project;

    @BeforeEach
    void setUp() throws Exception {
        File zip = new File(tempDir, "with-svn.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            for (String name : new String[] {"kept.txt", "dropped.log", ".svn/entries"}) {
                out.putNextEntry(new ZipEntry(name));
                out.write(name.getBytes("UTF-8"));
                out.closeEntry();
            }
        }
        DependencyArtifactStubFactory stubFactory = new DependencyArtifactStubFactory(tempDir, true, false);
        stubFactory.setSrcFile(zip);
        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());
        Set<Artifact> artifacts = stubFactory.getScopedArtifacts();
        project.setArtifacts(artifacts);
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testDefaultExcludesApplyWhenExcludesAreSet(UnpackDependenciesMojo mojo) throws Exception {
        mojo.setExcludes("**/*.log");
        mojo.execute();
        assertTrue(new File(mojo.getOutputDirectory(), "kept.txt").exists());
        assertFalse(new File(mojo.getOutputDirectory(), "dropped.log").exists());
        assertFalse(new File(mojo.getOutputDirectory(), ".svn/entries").exists());
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testDefaultExcludesCanBeDisabled(UnpackDependenciesMojo mojo) throws Exception {
        mojo.setExcludes("**/*.log");
        mojo.setUseDefaultExcludes(false);
        mojo.execute();
        assertTrue(new File(mojo.getOutputDirectory(), "kept.txt").exists());
        assertFalse(new File(mojo.getOutputDirectory(), "dropped.log").exists());
        assertTrue(new File(mojo.getOutputDirectory(), ".svn/entries").exists());
    }
}
