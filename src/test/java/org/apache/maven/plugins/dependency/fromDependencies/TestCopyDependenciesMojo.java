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
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.utils.CopyUtil;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.markers.DefaultFileMarkerHandler;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@MojoTest(realRepositorySession = true)
class TestCopyDependenciesMojo {

    @TempDir
    private File tempDir;

    private DependencyArtifactStubFactory stubFactory;

    @Inject
    private MavenSession session;

    @Inject
    private MavenProject project;

    @Inject
    private CopyUtil copyUtil;

    @BeforeEach
    void setUp() throws Exception {
        stubFactory = new DependencyArtifactStubFactory(tempDir, true, false);
        session.getRequest().setLocalRepositoryPath(new File(tempDir, "localTestRepo"));

        Set<Artifact> artifacts = stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);
        project.setArtifacts(artifacts);

        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());
    }

    private void assertNoMarkerFile(CopyDependenciesMojo mojo, Artifact artifact) throws MojoExecutionException {
        DefaultFileMarkerHandler handle = new DefaultFileMarkerHandler(artifact, mojo.markersDirectory);
        assertFalse(handle.isMarkerSet());
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testCopyArtifactFile(CopyDependenciesMojo mojo) throws Exception {
        final Artifact srcArtifact = new ArtifactStub();
        srcArtifact.setGroupId("org.apache.maven.plugins");
        srcArtifact.setArtifactId("maven-dependency-plugin-dummy");
        srcArtifact.setVersion("1.0");
        File src = File.createTempFile("copy", null);
        srcArtifact.setFile(src);

        File dest = new File(mojo.outputDirectory, "toMe.jar");

        assertFalse(dest.exists());

        copyUtil.copyArtifactFile(srcArtifact, dest);
        assertTrue(dest.exists());
    }

    /**
     * Tests the copying of signature files associated with artifacts.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "copySignatures", value = "true")
    void testCopySignatureFiles(CopyDependenciesMojo mojo) throws Exception {

        if (!mojo.outputDirectory.exists()) {
            assertTrue(mojo.outputDirectory.mkdirs(), "Failed to create output directory");
        }

        File sourceDirectory =
                new File(System.getProperty("java.io.tmpdir"), "test-source-" + System.currentTimeMillis());
        if (!sourceDirectory.exists()) {
            assertTrue(sourceDirectory.mkdirs(), "Failed to create source directory");
        }

        File artifactFile = new File(sourceDirectory, "maven-dependency-plugin-1.0.jar");
        if (!artifactFile.getParentFile().exists()) {
            assertTrue(artifactFile.getParentFile().mkdirs(), "Failed to create parent directory");
        }
        if (artifactFile.exists()) {
            assertTrue(artifactFile.delete(), "Failed to delete existing artifact file");
        }
        assertTrue(artifactFile.createNewFile(), "Failed to create artifact file");

        File signatureFile = new File(sourceDirectory, "maven-dependency-plugin-1.0.jar.asc");
        if (!signatureFile.getParentFile().exists()) {
            assertTrue(signatureFile.getParentFile().mkdirs(), "Failed to create parent directory");
        }
        if (signatureFile.exists()) {
            assertTrue(signatureFile.delete(), "Failed to delete existing signature file");
        }
        assertTrue(signatureFile.createNewFile(), "Failed to create signature file");

        Artifact artifact = stubFactory.createArtifact(
                "org.apache.maven.plugins", "maven-dependency-plugin", "1.0", Artifact.SCOPE_COMPILE);
        artifact.setFile(artifactFile);

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(artifact);
        mojo.getProject().setArtifacts(artifacts);

        mojo.execute();

        File copiedSignatureFile = new File(mojo.outputDirectory, "maven-dependency-plugin-1.0.jar.asc");
        assertTrue(copiedSignatureFile.exists(), "Signature file was not copied");

        // Clean up
        artifactFile.delete();
        signatureFile.delete();
        sourceDirectory.delete();
    }

    /**
     * Tests the proper discovery and configuration of the mojo.
     *
     * @throws Exception in case of an error
     */
    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testMojo(CopyDependenciesMojo mojo) throws Exception {
        mojo.execute();
        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertTrue(file.exists());

            // there should be no markers for the copy mojo
            assertNoMarkerFile(mojo, artifact);
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "stripVersion", value = "true")
    void testStripVersion(CopyDependenciesMojo mojo) throws Exception {
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, true);
            File file = new File(mojo.outputDirectory, fileName);
            assertTrue(file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "stripClassifier", value = "true")
    void testStripClassifier(CopyDependenciesMojo mojo) throws Exception {
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false, false, false, true);
            File file = new File(mojo.outputDirectory, fileName);
            assertTrue(file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "useBaseVersion", value = "true")
    void testUseBaseVersion(CopyDependenciesMojo mojo) throws Exception {
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false, false, true);
            File file = new File(mojo.outputDirectory, fileName);
            assertTrue(file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "excludeTransitive", value = "true")
    void testNoTransitive(CopyDependenciesMojo mojo) throws Exception {
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertTrue(file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "excludeTypes", value = "jar")
    void testExcludeType(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArtifacts());

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertEquals(artifact.getType().equalsIgnoreCase("jar"), !file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "includeTypes", value = "jar")
    @MojoParameter(name = "excludeTypes", value = "jar")
    void testIncludeType(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArtifacts());

        // shouldn't get anything.

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertFalse(file.exists());
        }

        mojo.excludeTypes = "";
        mojo.execute();

        artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertEquals(artifact.getType().equalsIgnoreCase("jar"), file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "excludeArtifactIds", value = "one")
    void testExcludeArtifactId(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getArtifactArtifacts());
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertEquals(artifact.getArtifactId().equals("one"), !file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testIncludeArtifactId(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getArtifactArtifacts());

        mojo.includeArtifactIds = "one";
        mojo.excludeArtifactIds = "one";
        // shouldn't get anything

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertFalse(file.exists());
        }

        mojo.excludeArtifactIds = "";
        mojo.execute();

        artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertEquals(artifact.getArtifactId().equals("one"), file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testIncludeGroupId(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getGroupIdArtifacts());

        mojo.includeGroupIds = "one";
        mojo.excludeGroupIds = "one";
        // shouldn't get anything

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertFalse(file.exists());
        }

        mojo.excludeGroupIds = "";
        mojo.execute();

        artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertEquals(artifact.getGroupId().equals("one"), file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testExcludeGroupId(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getGroupIdArtifacts());

        mojo.excludeGroupIds = "one";
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);

            assertEquals(artifact.getGroupId().equals("one"), !file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testExcludeMultipleGroupIds(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getGroupIdArtifacts());

        mojo.excludeGroupIds = "one,two";
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);

            assertEquals(
                    artifact.getGroupId().equals("one") || artifact.getGroupId().equals("two"), !file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testExcludeClassifier(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getClassifiedArtifacts());

        mojo.excludeClassifiers = "one";
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertEquals(artifact.getClassifier().equals("one"), !file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testIncludeClassifier(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getClassifiedArtifacts());

        mojo.includeClassifiers = "one";
        mojo.excludeClassifiers = "one";
        // shouldn't get anything

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertFalse(file.exists());
        }

        mojo.excludeClassifiers = "";
        mojo.execute();

        artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertEquals(artifact.getClassifier().equals("one"), file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testSubPerType(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArtifacts());

        mojo.useSubDirectoryPerType = true;
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File folder = DependencyUtil.getFormattedOutputDirectory(
                    false, true, false, false, false, false, mojo.outputDirectory, artifact);
            File file = new File(folder, fileName);
            assertTrue(file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testCDMClassifier(CopyDependenciesMojo mojo) throws Exception {
        dotestClassifierType(mojo, "jdk14", null);
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testCDMType(CopyDependenciesMojo mojo) throws Exception {
        dotestClassifierType(mojo, null, "sources");
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testCDMClassifierType(CopyDependenciesMojo mojo) throws Exception {
        dotestClassifierType(mojo, "jdk14", "sources");
    }

    private void dotestClassifierType(CopyDependenciesMojo mojo, String testClassifier, String testType)
            throws Exception {
        mojo.classifier = testClassifier;
        mojo.type = testType;

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            String type = testType != null ? testType : artifact.getType();

            stubFactory.createArtifact(
                    artifact.getGroupId(),
                    artifact.getArtifactId(),
                    artifact.getVersion(),
                    artifact.getScope(),
                    type,
                    testClassifier);
        }

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String useClassifier = artifact.getClassifier();
            String useType = artifact.getType();

            if (testClassifier != null && !testClassifier.isEmpty()) {
                useClassifier = "-" + testClassifier;
                // type is only used if classifier is used.
                if (testType != null && !testType.isEmpty()) {
                    useType = testType;
                }
            }
            String fileName = artifact.getArtifactId() + "-" + artifact.getVersion() + useClassifier + "." + useType;
            File file = new File(mojo.outputDirectory, fileName);

            if (!file.exists()) {
                fail("Can't find:" + file.getAbsolutePath());
            }

            // there should be no markers for the copy mojo
            assertNoMarkerFile(mojo, artifact);
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testArtifactResolutionException(CopyDependenciesMojo mojo) throws MojoFailureException {
        dotestArtifactExceptions(mojo);
    }

    private void dotestArtifactExceptions(CopyDependenciesMojo mojo) throws MojoFailureException {
        mojo.classifier = "jdk";
        mojo.failOnMissingClassifierArtifact = true;
        mojo.type = "java-sources";

        try {
            mojo.execute();
            fail("ExpectedException");
        } catch (MojoExecutionException e) {

        }
    }

    /*
     * public void testOverwrite() { stubFactory.setCreateFiles( false ); Artifact artifact =
     * stubFactory.createArtifact( "test", "artifact", "1.0" ); File testFile = new File( getBasedir() +
     * File.separatorChar + "target/test-classes/unit/copy-dependencies-test/test.zip" ); }
     */

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testDontOverWriteRelease(CopyDependenciesMojo mojo)
            throws MojoExecutionException, InterruptedException, IOException, MojoFailureException {

        Set<Artifact> artifacts = new HashSet<>();
        Artifact release = stubFactory.getReleaseArtifact();
        assertTrue(release.getFile().setLastModified(System.currentTimeMillis() - 2000));

        artifacts.add(release);

        mojo.getProject().setArtifacts(artifacts);

        mojo.overWriteIfNewer = false;

        mojo.execute();

        File copiedFile = new File(mojo.outputDirectory, DependencyUtil.getFormattedFileName(release, false));

        Thread.sleep(100);
        // round up to the next second
        long time = System.currentTimeMillis() + 1000;
        time = time - (time % 1000);
        assertTrue(copiedFile.setLastModified(time));
        Thread.sleep(100);

        mojo.execute();

        assertEquals(time, copiedFile.lastModified());
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testOverWriteRelease(CopyDependenciesMojo mojo)
            throws MojoExecutionException, IOException, MojoFailureException {

        Set<Artifact> artifacts = new HashSet<>();
        Artifact release = stubFactory.getReleaseArtifact();

        assertTrue(release.getFile().setLastModified(1000L));
        assertEquals(1000L, release.getFile().lastModified());

        artifacts.add(release);

        mojo.getProject().setArtifacts(artifacts);

        mojo.overWriteReleases = true;
        mojo.overWriteIfNewer = false;

        mojo.execute();

        File copiedFile = new File(mojo.outputDirectory, DependencyUtil.getFormattedFileName(release, false));

        assertTrue(copiedFile.setLastModified(2000L));
        assertEquals(2000L, copiedFile.lastModified());

        mojo.execute();

        long timeCopyNow = copiedFile.lastModified();
        assertEquals(1000L, timeCopyNow);
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testDontOverWriteSnap(CopyDependenciesMojo mojo)
            throws MojoExecutionException, IOException, MojoFailureException {

        Set<Artifact> artifacts = new HashSet<>();
        Artifact snap = stubFactory.getSnapshotArtifact();
        assertTrue(snap.getFile().setLastModified(1000L));
        assertEquals(1000L, snap.getFile().lastModified());

        artifacts.add(snap);

        mojo.getProject().setArtifacts(artifacts);

        mojo.overWriteReleases = false;
        mojo.overWriteSnapshots = false;
        mojo.overWriteIfNewer = false;

        mojo.execute();

        File copiedFile = new File(mojo.outputDirectory, DependencyUtil.getFormattedFileName(snap, false));

        assertTrue(copiedFile.setLastModified(2000L));
        assertEquals(2000L, copiedFile.lastModified());

        mojo.execute();

        long timeCopyNow = copiedFile.lastModified();
        assertEquals(2000L, timeCopyNow);
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testOverWriteSnap(CopyDependenciesMojo mojo) throws MojoExecutionException, IOException, MojoFailureException {

        Set<Artifact> artifacts = new HashSet<>();
        Artifact snap = stubFactory.getSnapshotArtifact();
        assertTrue(snap.getFile().setLastModified(1000L));
        assertEquals(1000L, snap.getFile().lastModified());

        artifacts.add(snap);

        mojo.getProject().setArtifacts(artifacts);
        mojo.getProject().setDependencyArtifacts(artifacts);

        mojo.overWriteReleases = false;
        mojo.overWriteSnapshots = true;
        mojo.overWriteIfNewer = false;

        mojo.execute();

        File copiedFile = new File(mojo.outputDirectory, DependencyUtil.getFormattedFileName(snap, false));

        assertTrue(copiedFile.setLastModified(2000L));
        assertEquals(2000L, copiedFile.lastModified());

        mojo.execute();

        long timeCopyNow = copiedFile.lastModified();
        assertEquals(1000L, timeCopyNow);
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testGetDependencies(CopyDependenciesMojo mojo) throws MojoExecutionException {
        assertTrue(mojo.getResolvedDependencies(true)
                .containsAll(mojo.getDependencySets(true).getResolvedDependencies()));
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testExcludeProvidedScope(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());

        mojo.excludeScope = "provided";

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertEquals(artifact.getScope().equals("provided"), !file.exists());
            file.delete();
            assertFalse(file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testExcludeSystemScope(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());

        mojo.excludeScope = "system";

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertEquals(artifact.getScope().equals("system"), !file.exists());
            file.delete();
            assertFalse(file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testExcludeCompileScope(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());

        mojo.excludeScope = "compile";
        mojo.execute();
        ScopeArtifactFilter saf = new ScopeArtifactFilter(mojo.excludeScope);

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);

            assertEquals(!saf.include(artifact), file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testExcludeTestScope(CopyDependenciesMojo mojo) throws IOException, MojoFailureException {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());

        mojo.excludeScope = "test";

        try {
            mojo.execute();
            fail("expected an exception");
        } catch (MojoExecutionException e) {

        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testExcludeRuntimeScope(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());

        mojo.excludeScope = "runtime";
        mojo.execute();
        ScopeArtifactFilter saf = new ScopeArtifactFilter(mojo.excludeScope);

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);

            assertEquals(!saf.include(artifact), file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testCopyPom(CopyDependenciesMojo mojo) throws Exception {
        mojo.setCopyPom(true);

        Set<Artifact> set = new HashSet<>();
        set.add(stubFactory.createArtifact("org.apache.maven", "maven-artifact", "2.0.7", Artifact.SCOPE_COMPILE));
        stubFactory.createArtifact("org.apache.maven", "maven-artifact", "2.0.7", Artifact.SCOPE_COMPILE, "pom", null);
        mojo.getProject().setArtifacts(set);
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName.substring(0, fileName.length() - 4) + ".pom");
            assertTrue(file.exists(), file + " doesn't exist");
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testPrependGroupId(CopyDependenciesMojo mojo) throws Exception {
        mojo.prependGroupId = true;
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false, true);
            File file = new File(mojo.outputDirectory, fileName);
            assertTrue(file.exists());
        }
    }
}
