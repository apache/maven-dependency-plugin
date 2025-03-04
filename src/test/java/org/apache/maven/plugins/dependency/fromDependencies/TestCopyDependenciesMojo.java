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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.plugins.dependency.testUtils.stubs.DependencyProjectStub;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.plugins.dependency.utils.markers.DefaultFileMarkerHandler;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;

public class TestCopyDependenciesMojo extends AbstractDependencyMojoTestCase {

    CopyDependenciesMojo mojo;

    @Override
    protected void setUp() throws Exception {
        // required for mojo lookups to work
        super.setUp("copy-dependencies", true, false);

        MavenProject project = new DependencyProjectStub();
        getContainer().addComponent(project, MavenProject.class.getName());

        MavenSession session = newMavenSession(project);
        getContainer().addComponent(session, MavenSession.class.getName());

        RepositorySystem repositorySystem = lookup(RepositorySystem.class);
        ResolverUtil resolverUtil = new ResolverUtil(repositorySystem, () -> session);
        getContainer().addComponent(resolverUtil, ResolverUtil.class.getName());

        File testPom = new File(getBasedir(), "target/test-classes/unit/copy-dependencies-test/plugin-config.xml");
        mojo = (CopyDependenciesMojo) lookupMojo("copy-dependencies", testPom);
        mojo.outputDirectory = new File(this.testDir, "outputDirectory");

        assertNotNull(mojo);
        assertNotNull(mojo.getProject());

        LegacySupport legacySupport = lookup(LegacySupport.class);
        legacySupport.setSession(session);
        installLocalRepository(legacySupport);

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);

        project.setArtifacts(artifacts);
        mojo.markersDirectory = new File(this.testDir, "markers");

        ArtifactHandlerManager manager = lookup(ArtifactHandlerManager.class);
        setVariableValueToObject(mojo, "artifactHandlerManager", manager);
    }

    public void assertNoMarkerFile(Artifact artifact) throws MojoExecutionException {
        DefaultFileMarkerHandler handle = new DefaultFileMarkerHandler(artifact, mojo.markersDirectory);
        assertFalse(handle.isMarkerSet());
    }

    public void testCopyArtifactFile() throws Exception {
        final Artifact srcArtifact = new ArtifactStub();
        srcArtifact.setGroupId("org.apache.maven.plugins");
        srcArtifact.setArtifactId("maven-dependency-plugin-dummy");
        srcArtifact.setVersion("1.0");
        File src = File.createTempFile("copy", null);
        srcArtifact.setFile(src);

        File dest = new File(mojo.outputDirectory, "toMe.jar");

        assertFalse(dest.exists());

        copyArtifactFile(srcArtifact, dest);
        assertTrue(dest.exists());
    }

    /**
     * Tests the copying of signature files associated with artifacts.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testCopySignatureFiles() throws Exception {
        // Enable the copySignatures parameter
        mojo.copySignatures = true;

        if (!mojo.outputDirectory.exists()) {
            assertTrue("Failed to create output directory", mojo.outputDirectory.mkdirs());
        }

        File sourceDirectory = new File(System.getProperty("java.io.tmpdir"), "test-source-" + System.currentTimeMillis());
        if (!sourceDirectory.exists()) {
            assertTrue("Failed to create source directory", sourceDirectory.mkdirs());
        }

        File artifactFile = new File(sourceDirectory, "maven-dependency-plugin-1.0.jar");
        if (!artifactFile.getParentFile().exists()) {
            assertTrue("Failed to create parent directory", artifactFile.getParentFile().mkdirs());
        }
        if (artifactFile.exists()) {
            assertTrue("Failed to delete existing artifact file", artifactFile.delete());
        }
        assertTrue("Failed to create artifact file", artifactFile.createNewFile());

        File signatureFile = new File(sourceDirectory, "maven-dependency-plugin-1.0.jar.asc");
        if (!signatureFile.getParentFile().exists()) {
            assertTrue("Failed to create parent directory", signatureFile.getParentFile().mkdirs());
        }
        if (signatureFile.exists()) {
            assertTrue("Failed to delete existing signature file", signatureFile.delete());
        }
        assertTrue("Failed to create signature file", signatureFile.createNewFile());

        Artifact artifact = stubFactory.createArtifact(
                "org.apache.maven.plugins", "maven-dependency-plugin", "1.0", Artifact.SCOPE_COMPILE);
        artifact.setFile(artifactFile);

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(artifact);
        mojo.getProject().setArtifacts(artifacts);

        mojo.execute();

        File copiedSignatureFile = new File(mojo.outputDirectory, "maven-dependency-plugin-1.0.jar.asc");
        assertTrue("Signature file was not copied", copiedSignatureFile.exists());

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
    public void testMojo() throws Exception {
        mojo.execute();
        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertTrue(file.exists());

            // there should be no markers for the copy mojo
            assertNoMarkerFile(artifact);
        }
    }

    public void testStripVersion() throws Exception {
        mojo.stripVersion = true;
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, true);
            File file = new File(mojo.outputDirectory, fileName);
            assertTrue(file.exists());
        }
    }

    public void testStripClassifier() throws Exception {
        mojo.stripClassifier = true;
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false, false, false, true);
            File file = new File(mojo.outputDirectory, fileName);
            assertTrue(file.exists());
        }
    }

    public void testUseBaseVersion() throws Exception {
        mojo.useBaseVersion = true;
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false, false, true);
            File file = new File(mojo.outputDirectory, fileName);
            assertTrue(file.exists());
        }
    }

    public void testNoTransitive() throws Exception {
        mojo.excludeTransitive = true;
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertTrue(file.exists());
        }
    }

    public void testExcludeType() throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArtifacts());

        mojo.excludeTypes = "jar";
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertEquals(artifact.getType().equalsIgnoreCase("jar"), !file.exists());
        }
    }

    public void testIncludeType() throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArtifacts());

        mojo.includeTypes = "jar";
        mojo.excludeTypes = "jar";
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

    public void testExcludeArtifactId() throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getArtifactArtifacts());
        mojo.excludeArtifactIds = "one";
        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);
            assertEquals(artifact.getArtifactId().equals("one"), !file.exists());
        }
    }

    public void testIncludeArtifactId() throws Exception {
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

    public void testIncludeGroupId() throws Exception {
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

    public void testExcludeGroupId() throws Exception {
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

    public void testExcludeMultipleGroupIds() throws Exception {
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

    public void testExcludeClassifier() throws Exception {
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

    public void testIncludeClassifier() throws Exception {
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

    public void testSubPerType() throws Exception {
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

    public void testCDMClassifier() throws Exception {
        dotestClassifierType("jdk14", null);
    }

    public void testCDMType() throws Exception {
        dotestClassifierType(null, "sources");
    }

    public void testCDMClassifierType() throws Exception {
        dotestClassifierType("jdk14", "sources");
    }

    public void dotestClassifierType(String testClassifier, String testType) throws Exception {
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
            assertNoMarkerFile(artifact);
        }
    }

    public void testArtifactResolutionException() throws MojoFailureException {
        dotestArtifactExceptions();
    }

    public void dotestArtifactExceptions() throws MojoFailureException {
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

    public void testDontOverWriteRelease()
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

    public void testOverWriteRelease() throws MojoExecutionException, IOException, MojoFailureException {

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

    public void testDontOverWriteSnap() throws MojoExecutionException, IOException, MojoFailureException {

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

    public void testOverWriteSnap() throws MojoExecutionException, IOException, MojoFailureException {

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

    public void testGetDependencies() throws MojoExecutionException {
        assertTrue(mojo.getResolvedDependencies(true)
                .containsAll(mojo.getDependencySets(true).getResolvedDependencies()));
    }

    public void testExcludeProvidedScope() throws Exception {
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

    public void testExcludeSystemScope() throws Exception {
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

    public void testExcludeCompileScope() throws Exception {
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

    public void testExcludeTestScope() throws IOException, MojoFailureException {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());

        mojo.excludeScope = "test";

        try {
            mojo.execute();
            fail("expected an exception");
        } catch (MojoExecutionException e) {

        }
    }

    public void testExcludeRuntimeScope() throws Exception {
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

    public void testCopyPom() throws Exception {
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
            assertTrue(file + " doesn't exist", file.exists());
        }
    }

    public void testPrependGroupId() throws Exception {
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
