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
package org.apache.maven.plugins.dependency.utils;

import java.io.File;
import java.io.IOException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.testing.stubs.DefaultArtifactHandlerStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author brianf
 */
class TestDependencyUtil {

    private static final String TEST_CONTENT =
            "Test line 1" + System.lineSeparator() + "Test line 2" + System.lineSeparator();

    @TempDir
    File temDir;

    Artifact snap;

    Artifact snapResolvedVersion;

    Artifact release;

    Artifact sources;

    @BeforeEach
    protected void setUp() {

        ArtifactHandler ah = new DefaultArtifactHandlerStub("jar", null);
        VersionRange vr = VersionRange.createFromVersion("1.1");
        release = new DefaultArtifact("test", "one", vr, Artifact.SCOPE_COMPILE, "jar", "sources", ah, false);

        ah = new DefaultArtifactHandlerStub("war", null);
        vr = VersionRange.createFromVersion("1.1-SNAPSHOT");
        snap = new DefaultArtifact("test", "two", vr, Artifact.SCOPE_PROVIDED, "war", null, ah, false);

        ah = new DefaultArtifactHandlerStub("war", null);
        vr = VersionRange.createFromVersion("1.1-SNAPSHOT");
        snapResolvedVersion = new DefaultArtifact("test", "three", vr, Artifact.SCOPE_PROVIDED, "war", null, ah, false);
        snapResolvedVersion.setResolvedVersion("1.1-20121003.035531-117");

        ah = new DefaultArtifactHandlerStub("war", null);
        vr = VersionRange.createFromVersion("1.1-SNAPSHOT");
        sources = new DefaultArtifact("test", "two", vr, Artifact.SCOPE_PROVIDED, "sources", "sources", ah, false);
    }

    @Test
    void testDirectoryName() {
        File folder = new File("target/a");
        final Artifact artifact = release;
        File name =
                DependencyUtil.getFormattedOutputDirectory(false, false, false, false, false, false, folder, artifact);
        // object is the same.
        assertEquals(folder, name);

        name = DependencyUtil.getFormattedOutputDirectory(false, false, false, true, false, false, folder, artifact);
        String expectedResult = folder.getAbsolutePath() + File.separatorChar + "test" + File.separatorChar + "one"
                + File.separatorChar + "1.1";
        assertTrue(expectedResult.equalsIgnoreCase(name.getAbsolutePath()));

        name = DependencyUtil.getFormattedOutputDirectory(false, true, false, false, false, false, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "jars";
        assertTrue(expectedResult.equalsIgnoreCase(name.getAbsolutePath()));

        name = DependencyUtil.getFormattedOutputDirectory(true, false, false, false, false, false, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "compile";
        assertEquals(expectedResult, name.getAbsolutePath());
        assertTrue(expectedResult.equalsIgnoreCase(name.getAbsolutePath()));

        name = DependencyUtil.getFormattedOutputDirectory(false, false, true, false, false, false, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "one-1.1-sources-jar";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, false, true, false, true, false, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "one-sources-jar";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, false, true, false, false, true, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "one-1.1-sources";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, false, true, false, true, true, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "one-sources";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, true, true, false, false, false, folder, artifact);
        expectedResult =
                folder.getAbsolutePath() + File.separatorChar + "jars" + File.separatorChar + "one-1.1-sources-jar";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, true, true, false, true, false, folder, artifact);
        expectedResult =
                folder.getAbsolutePath() + File.separatorChar + "jars" + File.separatorChar + "one-sources-jar";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, true, true, false, false, true, folder, artifact);
        expectedResult =
                folder.getAbsolutePath() + File.separatorChar + "jars" + File.separatorChar + "one-1.1-sources";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, true, true, false, true, true, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "jars" + File.separatorChar + "one-sources";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(true, false, true, false, true, false, folder, artifact);
        expectedResult =
                folder.getAbsolutePath() + File.separatorChar + "compile" + File.separatorChar + "one-sources-jar";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(true, false, true, false, false, true, folder, artifact);
        expectedResult =
                folder.getAbsolutePath() + File.separatorChar + "compile" + File.separatorChar + "one-1.1-sources";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(true, false, true, false, true, true, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "compile" + File.separatorChar + "one-sources";
        assertEquals(expectedResult, name.getAbsolutePath());
    }

    @Test
    void testDirectoryName2() {
        File folder = new File("target/a");
        final Artifact artifact = snap;
        File name =
                DependencyUtil.getFormattedOutputDirectory(false, false, false, false, false, false, folder, artifact);
        // object is the same.
        assertEquals(folder, name);

        name = DependencyUtil.getFormattedOutputDirectory(false, true, false, false, false, false, folder, artifact);
        String expectedResult = folder.getAbsolutePath() + File.separatorChar + "wars";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, false, false, true, false, false, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "test" + File.separatorChar + "two"
                + File.separatorChar + "1.1-SNAPSHOT";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, false, true, false, false, false, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "two-1.1-SNAPSHOT-war";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, true, true, false, false, false, folder, artifact);
        expectedResult =
                folder.getAbsolutePath() + File.separatorChar + "wars" + File.separatorChar + "two-1.1-SNAPSHOT-war";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, false, true, false, true, false, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "two-war";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, false, true, false, false, true, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "two-1.1-SNAPSHOT";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, false, true, false, true, true, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "two";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, true, true, false, true, false, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "wars" + File.separatorChar + "two-war";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, true, true, false, false, true, folder, artifact);
        expectedResult =
                folder.getAbsolutePath() + File.separatorChar + "wars" + File.separatorChar + "two-1.1-SNAPSHOT";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, true, true, false, true, true, folder, artifact);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "wars" + File.separatorChar + "two";
        assertEquals(expectedResult, name.getAbsolutePath());
    }

    @Test
    void testDirectoryNameSources() {
        File folder = new File("target/a");
        File name = DependencyUtil.getFormattedOutputDirectory(false, false, true, false, true, false, folder, sources);
        String expectedResult = folder.getAbsolutePath() + File.separatorChar + "two-sources";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, false, true, false, true, true, folder, sources);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "two-sources";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, false, true, false, false, false, folder, sources);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "two-1.1-SNAPSHOT-sources";
        assertEquals(expectedResult, name.getAbsolutePath());

        name = DependencyUtil.getFormattedOutputDirectory(false, false, true, false, false, true, folder, sources);
        expectedResult = folder.getAbsolutePath() + File.separatorChar + "two-1.1-SNAPSHOT-sources";
        assertEquals(expectedResult, name.getAbsolutePath());
    }

    @Test
    void testFileName() {
        Artifact artifact = release;

        String name = DependencyUtil.getFormattedFileName(artifact, false);
        String expectedResult = "one-1.1-sources.jar";
        assertEquals(expectedResult, name);
        name = DependencyUtil.getFormattedFileName(artifact, true);
        expectedResult = "one-sources.jar";
        assertEquals(expectedResult, name);
    }

    @Test
    void testFileNameUseBaseVersion() {
        Artifact artifact = snapResolvedVersion;

        String name = DependencyUtil.getFormattedFileName(artifact, false, false, true);
        String expectedResult = "three-1.1-SNAPSHOT.war";
        assertEquals(expectedResult, name);
        name = DependencyUtil.getFormattedFileName(artifact, false, false, false);
        expectedResult = "three-1.1-20121003.035531-117.war";
        assertEquals(expectedResult, name);
    }

    @Test
    void testTestJar() {
        ArtifactHandler ah = new DefaultArtifactHandlerStub("test-jar", null);
        VersionRange vr = VersionRange.createFromVersion("1.1-SNAPSHOT");
        Artifact artifact =
                new DefaultArtifact("test", "two", vr, Artifact.SCOPE_PROVIDED, "test-jar", null, ah, false);

        String name = DependencyUtil.getFormattedFileName(artifact, false);
        String expectedResult = "two-1.1-SNAPSHOT.jar";
        assertEquals(expectedResult, name);
    }

    @Test
    void testFileNameClassifier() {
        ArtifactHandler ah = new DefaultArtifactHandlerStub("jar", "sources");
        VersionRange vr = VersionRange.createFromVersion("1.1-SNAPSHOT");
        Artifact artifact =
                new DefaultArtifact("test", "two", vr, Artifact.SCOPE_PROVIDED, "jar", "sources", ah, false);

        String name = DependencyUtil.getFormattedFileName(artifact, false);
        String expectedResult = "two-1.1-SNAPSHOT-sources.jar";
        assertEquals(expectedResult, name);

        name = DependencyUtil.getFormattedFileName(artifact, true);
        expectedResult = "two-sources.jar";
        assertEquals(expectedResult, name);

        name = DependencyUtil.getFormattedFileName(artifact, false, false, false, true);
        expectedResult = "two-1.1-SNAPSHOT.jar";
        assertEquals(expectedResult, name);

        ah = new DefaultArtifactHandlerStub("war", null);
        artifact = new DefaultArtifact("test", "two", vr, Artifact.SCOPE_PROVIDED, "war", "", ah, false);
        name = DependencyUtil.getFormattedFileName(artifact, true);
        expectedResult = "two.war";
        assertEquals(expectedResult, name);
    }

    @Test
    void testFileNameClassifierWithFile() {
        // specifically testing the default operation that getFormattedFileName
        // returns
        // the actual name of the file if available unless remove version is
        // set.
        ArtifactHandler ah = new DefaultArtifactHandlerStub("war", "sources");
        VersionRange vr = VersionRange.createFromVersion("1.1-SNAPSHOT");
        Artifact artifact =
                new DefaultArtifact("test", "two", vr, Artifact.SCOPE_PROVIDED, "war", "sources", ah, false);
        File file = new File("/target", "test-file-name.jar");
        artifact.setFile(file);

        String name = DependencyUtil.getFormattedFileName(artifact, false);
        String expectedResult = "two-1.1-SNAPSHOT-sources.war";
        assertEquals(expectedResult, name);

        name = DependencyUtil.getFormattedFileName(artifact, false, false, false, true);
        expectedResult = "two-1.1-SNAPSHOT.war";
        assertEquals(expectedResult, name);

        name = DependencyUtil.getFormattedFileName(artifact, true);
        expectedResult = "two-sources.war";
        assertEquals(expectedResult, name);

        artifact = new DefaultArtifact("test", "two", vr, Artifact.SCOPE_PROVIDED, "war", "", ah, false);
        name = DependencyUtil.getFormattedFileName(artifact, true);
        expectedResult = "two.war";
        assertEquals(expectedResult, name);

        // test that we pickup the correct extension in the file name if set.
        ah = new DefaultArtifactHandlerStub("jar", null);
        artifact = new DefaultArtifact("test", "two", vr, Artifact.SCOPE_PROVIDED, "war", "", ah, false);
        name = DependencyUtil.getFormattedFileName(artifact, true);
        expectedResult = "two.jar";
        assertEquals(expectedResult, name);
    }

    @Test
    void testTokenizer() {
        String[] tokens = DependencyUtil.tokenizer(" alpha,bravo, charlie , delta kappa, theta");
        String[] expected = new String[] {"alpha", "bravo", "charlie", "delta kappa", "theta"};
        // easier to see in the JUnit reports
        assertEquals(String.join(", ", expected), String.join(", ", tokens));
        assertEquals(expected.length, tokens.length);

        tokens = DependencyUtil.tokenizer(" \r\n a, \t \n \r b \t \n \r");
        assertEquals(2, tokens.length);
        assertEquals("a", tokens[0]);
        assertEquals("b", tokens[1]);

        tokens = DependencyUtil.tokenizer(null);
        assertEquals(0, tokens.length);

        tokens = DependencyUtil.tokenizer("  ");
        assertEquals(0, tokens.length);
    }

    @Test
    void outputFileShouldBeOverridden() throws IOException {
        File file = new File(temDir, "file1.out");
        assertThat(file).doesNotExist();

        DependencyUtil.write(TEST_CONTENT, file, false, "UTF-8");
        assertThat(file).hasContent(TEST_CONTENT);

        DependencyUtil.write(TEST_CONTENT, file, false, "UTF-8");
        assertThat(file).hasContent(TEST_CONTENT);
    }

    @Test
    void outputFileShouldBeAppended() throws IOException {
        File file = new File(temDir, "file2.out");
        assertThat(file).doesNotExist();

        DependencyUtil.write(TEST_CONTENT, file, true, "UTF-8");
        assertThat(file).hasContent(TEST_CONTENT);

        DependencyUtil.write(TEST_CONTENT, file, true, "UTF-8");
        assertThat(file).hasContent(TEST_CONTENT + TEST_CONTENT);
    }
}
