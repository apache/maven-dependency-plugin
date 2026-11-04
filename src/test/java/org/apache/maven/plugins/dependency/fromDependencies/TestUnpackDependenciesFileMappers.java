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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.components.io.filemappers.FileMapper;
import org.codehaus.plexus.components.io.filemappers.PrefixFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Excludes and {@code fileMappers} of {@code unpack-dependencies} on an archive with a top level file, a nested file
 * and a manifest.
 */
@MojoTest
@Basedir("/unit/unpack-dependencies-test")
class TestUnpackDependenciesFileMappers {
    @TempDir
    private File tempDir;

    @Inject
    private MavenProject project;

    @BeforeEach
    void setUp() throws Exception {
        File zip = new File(tempDir, "fixture.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            for (String name : new String[] {"LICENSE.txt", "org/example/nested.txt", "META-INF/MANIFEST.MF"}) {
                out.putNextEntry(new ZipEntry(name));
                out.write(name.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        DependencyArtifactStubFactory stubFactory = new DependencyArtifactStubFactory(tempDir, true, false);
        stubFactory.setSrcFile(zip);
        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());
        project.setArtifacts(stubFactory.getScopedArtifacts());
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void commaSeparatedExcludesOnlyMatchTheTopLevelOfAPlainPattern(UnpackDependenciesMojo mojo) throws Exception {
        mojo.setExcludes("*.txt,META-INF/**");

        mojo.execute();

        File out = mojo.getOutputDirectory();
        assertTrue(new File(out, "org/example/nested.txt").isFile());
        assertFalse(new File(out, "LICENSE.txt").exists());
        assertFalse(new File(out, "META-INF").exists());
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void fileMappersRewriteTheTargetPathOfTheSelectedEntries(UnpackDependenciesMojo mojo) throws Exception {
        PrefixFileMapper prefix = new PrefixFileMapper();
        prefix.setPrefix("mapped/");
        mojo.setFileMappers(new FileMapper[] {prefix});
        mojo.setExcludes("*.txt,META-INF/**");

        mojo.execute();

        File out = mojo.getOutputDirectory();
        assertTrue(new File(out, "mapped/org/example/nested.txt").isFile());
        assertFalse(new File(out, "mapped/LICENSE.txt").exists());
        assertFalse(new File(out, "mapped/META-INF").exists());
        assertFalse(new File(out, "org").exists(), "an unmapped copy of the entry");
    }
}
