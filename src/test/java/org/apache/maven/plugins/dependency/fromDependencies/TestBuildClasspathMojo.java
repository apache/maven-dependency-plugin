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
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.AssertionsKt.assertNotNull;
import static org.junit.jupiter.api.AssertionsKt.assertNull;
import static org.mockito.Mockito.when;

@MojoTest
class TestBuildClasspathMojo {

    @TempDir
    private File testDir;

    @Inject
    private MavenSession session;

    @Inject
    private MavenProject project;

    /**
     * Tests the proper discovery and configuration of the mojo.
     */
    @Test
    @InjectMojo(goal = "build-classpath")
    void testEnvironment(BuildClasspathMojo mojo) throws Exception {

        Set<Artifact> artifacts = getArtifacts();
        when(project.getArtifacts()).thenReturn(artifacts);

        mojo.execute();
        try {
            mojo.readClasspathFile();

            fail("Expected an illegal Argument Exception");
        } catch (IllegalArgumentException e) {
            // expected to catch this.
        }

        mojo.setOutputFile(new File(testDir, "buildClasspath.txt"));
        mojo.execute();

        String file = mojo.readClasspathFile();
        assertNotNull(file);
        assertFalse(file.isEmpty());

        assertTrue(file.contains(File.pathSeparator));
        assertTrue(file.contains(File.separator));

        String fileSep = "#####";
        String pathSep = "%%%%%";

        mojo.setFileSeparator(fileSep);
        mojo.setPathSeparator(pathSep);
        mojo.execute();

        file = mojo.readClasspathFile();
        assertNotNull(file);
        assertFalse(file.isEmpty());

        assertFalse(file.contains(File.pathSeparator));
        assertFalse(file.contains(File.separator));
        assertTrue(file.contains(fileSep));
        assertTrue(file.contains(pathSep));

        String propertyValue = project.getProperties().getProperty("outputProperty");
        assertNull(propertyValue);
        mojo.setOutputProperty("outputProperty");
        mojo.execute();
        propertyValue = project.getProperties().getProperty("outputProperty");
        assertNotNull(propertyValue);
    }

    @Test
    @InjectMojo(goal = "build-classpath")
    void testPath(BuildClasspathMojo mojo) {

        File localRepo = new File(testDir, "local-rep").getAbsoluteFile();

        RepositorySystemSession repoSession = Mockito.mock(RepositorySystemSession.class);
        when(repoSession.getLocalRepository()).thenReturn(new LocalRepository(localRepo));
        when(session.getRepositorySession()).thenReturn(repoSession);

        Artifact artifact = new DefaultArtifact(
                "testGroupId", "release", "1.0", null, "jar", "", new DefaultArtifactHandler("jar"));
        artifact.setFile(new File(localRepo, "release-1.0.jar"));

        StringBuilder sb = new StringBuilder();
        mojo.setPrefix(null);
        mojo.setStripVersion(false);
        mojo.appendArtifactPath(artifact, sb);
        assertEquals(artifact.getFile().getPath(), sb.toString());

        mojo.setLocalRepoProperty("$M2_REPO");
        sb = new StringBuilder();
        mojo.appendArtifactPath(artifact, sb);
        assertEquals("$M2_REPO" + File.separator + artifact.getFile().getName(), sb.toString());

        mojo.setLocalRepoProperty("%M2_REPO%");
        sb = new StringBuilder();
        mojo.appendArtifactPath(artifact, sb);
        assertEquals("%M2_REPO%" + File.separator + artifact.getFile().getName(), sb.toString());

        mojo.setLocalRepoProperty("%M2_REPO%");
        sb = new StringBuilder();
        mojo.setPrependGroupId(true);
        mojo.appendArtifactPath(artifact, sb);
        assertEquals(
                "%M2_REPO%" + File.separator + DependencyUtil.getFormattedFileName(artifact, false, false),
                sb.toString(),
                "If prefix is null, prependGroupId has no impact ");

        mojo.setLocalRepoProperty("");
        mojo.setPrefix("prefix");
        sb = new StringBuilder();
        mojo.setPrependGroupId(true);
        mojo.appendArtifactPath(artifact, sb);
        assertEquals(
                "prefix" + File.separator + DependencyUtil.getFormattedFileName(artifact, false, true), sb.toString());
        mojo.setPrependGroupId(false);

        mojo.setLocalRepoProperty("");
        mojo.setPrefix("prefix");
        sb = new StringBuilder();
        mojo.appendArtifactPath(artifact, sb);
        assertEquals("prefix" + File.separator + artifact.getFile().getName(), sb.toString());

        mojo.setPrefix("prefix");
        mojo.setStripVersion(true);
        sb = new StringBuilder();
        mojo.appendArtifactPath(artifact, sb);
        assertEquals("prefix" + File.separator + DependencyUtil.getFormattedFileName(artifact, true), sb.toString());
    }

    private Set<Artifact> getArtifacts() {
        Artifact artifact1 = new DefaultArtifact(
                "testGroupId", "release1", "1.0", null, "jar", "", new DefaultArtifactHandler("jar"));
        artifact1.setFile(new File(testDir, "local-repo/release1-1.0.jar"));
        Artifact artifact2 = new DefaultArtifact(
                "testGroupId", "release2", "1.0", null, "jar", "", new DefaultArtifactHandler("jar"));
        artifact2.setFile(new File(testDir, "local-repo/release2-1.0.jar"));
        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(artifact1);
        artifacts.add(artifact2);
        return artifacts;
    }
}
