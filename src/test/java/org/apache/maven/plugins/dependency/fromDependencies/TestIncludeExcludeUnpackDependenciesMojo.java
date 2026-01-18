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
import java.util.Set;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MojoTest
@Basedir("/unit/unpack-dependencies-test")
class TestIncludeExcludeUnpackDependenciesMojo {
    @TempDir
    private File tempDir;

    @Inject
    private MavenSession session;

    @Inject
    private MavenProject project;

    private static final String UNPACKED_FILE_PREFIX = "test";

    private static final String UNPACKED_FILE_SUFFIX = ".txt";

    @BeforeEach
    void setUp() throws Exception {
        DependencyArtifactStubFactory stubFactory = new DependencyArtifactStubFactory(tempDir, true, false);
        stubFactory.setSrcFile(MojoExtension.getTestFile("test.zip"));

        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());

        Set<Artifact> artifacts = stubFactory.getScopedArtifacts();
        project.setArtifacts(artifacts);
    }

    private void assertUnpacked(UnpackDependenciesMojo mojo, boolean unpacked, String fileName) {
        File destFile = new File(mojo.getOutputDirectory().getAbsolutePath(), fileName);
        assertEquals(unpacked, destFile.exists());
    }

    /**
     * This test will validate that only the 1 and 11 files get unpacked
     *
     * @throws Exception in case of errors
     */
    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testUnpackIncludesManyFiles(UnpackDependenciesMojo mojo) throws Exception {
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
     * @throws Exception in case of errors
     */
    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testUnpackIncludesSingleFile(UnpackDependenciesMojo mojo) throws Exception {
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
     * @throws Exception in case of errors
     */
    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testUnpackIncludesAllFiles(UnpackDependenciesMojo mojo) throws Exception {
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
     * @throws Exception in case of errors
     */
    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testUnpackExcludesManyFiles(UnpackDependenciesMojo mojo) throws Exception {
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
     * @throws Exception in case of errors
     */
    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testUnpackExcludesSingleFile(UnpackDependenciesMojo mojo) throws Exception {
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
     * @throws Exception in case of errors
     */
    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testUnpackExcludesAllFiles(UnpackDependenciesMojo mojo) throws Exception {
        mojo.setExcludes("**/*");
        mojo.execute();
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, false, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testNoIncludeExcludes(UnpackDependenciesMojo mojo) throws Exception {
        mojo.execute();
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 1 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 11 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 2 + UNPACKED_FILE_SUFFIX);
        assertUnpacked(mojo, true, UNPACKED_FILE_PREFIX + 3 + UNPACKED_FILE_SUFFIX);
    }
}
