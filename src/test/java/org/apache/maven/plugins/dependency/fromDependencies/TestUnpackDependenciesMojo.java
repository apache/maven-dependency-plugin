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
import java.util.Iterator;
import java.util.Set;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.markers.DefaultFileMarkerHandler;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@MojoTest(realRepositorySession = true)
@Basedir("/unit/unpack-dependencies-test")
class TestUnpackDependenciesMojo {

    @TempDir
    private File tempDir;

    private DependencyArtifactStubFactory stubFactory;

    @Inject
    private MavenSession session;

    @Inject
    private MavenProject project;

    @Inject
    private ArchiverManager archiverManager;

    private static final String UNPACKABLE_FILE = "test.txt";

    @BeforeEach
    void setUp() throws Exception {
        stubFactory = new DependencyArtifactStubFactory(tempDir, true, false);
        session.getRequest().setLocalRepositoryPath(new File(tempDir, "localTestRepo"));

        // it needs to get the archivermanager
        stubFactory.setUnpackableFile(archiverManager);
        // i'm using one file repeatedly to archive so I can test the name
        // programmatically.
        stubFactory.setSrcFile(MojoExtension.getTestFile(UNPACKABLE_FILE));

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);

        project.setArtifacts(artifacts);

        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());
    }

    private void assertUnpacked(UnpackDependenciesMojo mojo, Artifact artifact) {
        assertUnpacked(mojo, true, artifact);
    }

    private void assertUnpacked(UnpackDependenciesMojo mojo, boolean exists, Artifact artifact) {
        File folder = DependencyUtil.getFormattedOutputDirectory(
                mojo.useSubDirectoryPerScope,
                mojo.useSubDirectoryPerType,
                mojo.useSubDirectoryPerArtifact,
                mojo.useRepositoryLayout,
                mojo.stripVersion,
                mojo.stripType,
                mojo.outputDirectory,
                artifact);

        File destFile = new File(folder, DependencyArtifactStubFactory.getUnpackableFileName(artifact));

        assertEquals(exists, destFile.exists());
        assertMarkerFile(mojo, exists, artifact);
    }

    private void assertMarkerFile(UnpackDependenciesMojo mojo, boolean exists, Artifact artifact) {
        DefaultFileMarkerHandler handle = new DefaultFileMarkerHandler(artifact, mojo.markersDirectory);
        try {
            assertEquals(exists, handle.isMarkerSet());
        } catch (MojoExecutionException e) {
            fail(e.getLongMessage());
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testMojo(UnpackDependenciesMojo mojo) throws Exception {
        mojo.execute();
        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testNoTransitive(UnpackDependenciesMojo mojo) throws Exception {
        mojo.excludeTransitive = true;
        mojo.execute();
        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testExcludeType(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArchiveArtifacts());
        mojo.excludeTypes = "jar";
        mojo.execute();

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, !artifact.getType().equalsIgnoreCase("jar"), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testExcludeProvidedScope(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.excludeScope = "provided";

        mojo.execute();

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, !artifact.getScope().equals("provided"), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testExcludeSystemScope(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.excludeScope = "system";

        mojo.execute();

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, !artifact.getScope().equals("system"), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testExcludeCompileScope(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.excludeScope = "compile";
        mojo.execute();
        ScopeArtifactFilter saf = new ScopeArtifactFilter(mojo.excludeScope);

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, !saf.include(artifact), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testExcludeTestScope(UnpackDependenciesMojo mojo) throws IOException, MojoFailureException {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.excludeScope = "test";

        try {
            mojo.execute();
            fail("expected an exception");
        } catch (MojoExecutionException e) {

        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testExcludeRuntimeScope(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.excludeScope = "runtime";
        mojo.execute();
        ScopeArtifactFilter saf = new ScopeArtifactFilter(mojo.excludeScope);

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, !saf.include(artifact), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testIncludeType(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArchiveArtifacts());

        mojo.includeTypes = "jar";
        mojo.excludeTypes = "jar";
        // shouldn't get anything

        mojo.execute();

        Iterator<Artifact> iter = mojo.getProject().getArtifacts().iterator();
        while (iter.hasNext()) {
            Artifact artifact = iter.next();

            assertUnpacked(mojo, false, artifact);
        }

        mojo.excludeTypes = "";
        mojo.execute();

        iter = mojo.getProject().getArtifacts().iterator();
        while (iter.hasNext()) {
            Artifact artifact = iter.next();

            assertUnpacked(mojo, artifact.getType().equalsIgnoreCase("jar"), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testSubPerType(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArchiveArtifacts());
        mojo.useSubDirectoryPerType = true;
        mojo.execute();

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testSubPerArtifact(UnpackDependenciesMojo mojo) throws Exception {
        mojo.useSubDirectoryPerArtifact = true;
        mojo.execute();

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testSubPerArtifactAndType(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArchiveArtifacts());
        mojo.useSubDirectoryPerArtifact = true;
        mojo.useSubDirectoryPerType = true;
        mojo.execute();

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testSubPerArtifactRemoveVersion(UnpackDependenciesMojo mojo) throws Exception {
        mojo.useSubDirectoryPerArtifact = true;
        mojo.stripVersion = true;
        mojo.execute();

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testSubPerArtifactAndTypeRemoveVersion(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArchiveArtifacts());
        mojo.useSubDirectoryPerArtifact = true;
        mojo.useSubDirectoryPerType = true;
        mojo.stripVersion = true;
        mojo.execute();

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testIncludeCompileScope(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.includeScope = "compile";
        mojo.execute();
        ScopeArtifactFilter saf = new ScopeArtifactFilter(mojo.includeScope);

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, saf.include(artifact), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testIncludeTestScope(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.includeScope = "test";

        mojo.execute();
        ScopeArtifactFilter saf = new ScopeArtifactFilter(mojo.includeScope);

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, saf.include(artifact), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testIncludeRuntimeScope(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.includeScope = "runtime";
        mojo.execute();
        ScopeArtifactFilter saf = new ScopeArtifactFilter(mojo.includeScope);

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, saf.include(artifact), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testIncludeprovidedScope(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.includeScope = "provided";

        mojo.execute();
        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, Artifact.SCOPE_PROVIDED.equals(artifact.getScope()), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testIncludesystemScope(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.includeScope = "system";

        mojo.execute();

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, Artifact.SCOPE_SYSTEM.equals(artifact.getScope()), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testIncludeArtifactId(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getArtifactArtifacts());

        mojo.includeArtifactIds = "one";
        mojo.excludeArtifactIds = "one";
        // shouldn't get anything
        mojo.execute();

        Iterator<Artifact> iter = mojo.getProject().getArtifacts().iterator();
        while (iter.hasNext()) {
            Artifact artifact = iter.next();
            assertUnpacked(mojo, false, artifact);
        }
        mojo.excludeArtifactIds = "";
        mojo.execute();

        iter = mojo.getProject().getArtifacts().iterator();
        while (iter.hasNext()) {
            Artifact artifact = iter.next();
            assertUnpacked(mojo, artifact.getArtifactId().equals("one"), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testExcludeArtifactId(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getArtifactArtifacts());
        mojo.excludeArtifactIds = "one";
        mojo.execute();

        // test - get all direct dependencies and verify that they exist if they
        // do not have a classifier of "one"
        // then delete the file and at the end, verify the folder is empty.
        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, !artifact.getArtifactId().equals("one"), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testExcludeGroupId(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getGroupIdArtifacts());
        mojo.excludeGroupIds = "one";
        mojo.execute();

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            assertUnpacked(mojo, !artifact.getGroupId().equals("one"), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testIncludeGroupId(UnpackDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getGroupIdArtifacts());
        mojo.includeGroupIds = "one";
        mojo.excludeGroupIds = "one";
        // shouldn't get anything

        mojo.execute();

        Iterator<Artifact> iter = mojo.getProject().getArtifacts().iterator();
        while (iter.hasNext()) {
            Artifact artifact = iter.next();
            // Testing with artifact id because group id is not in filename
            assertUnpacked(mojo, false, artifact);
        }

        mojo.excludeGroupIds = "";
        mojo.execute();

        iter = mojo.getProject().getArtifacts().iterator();
        while (iter.hasNext()) {
            Artifact artifact = iter.next();
            // Testing with artifact id because group id is not in filename
            assertUnpacked(mojo, artifact.getGroupId().equals("one"), artifact);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testCDMClassifier(UnpackDependenciesMojo mojo) throws Exception {
        dotestClassifierType(mojo, "jdk14", null);
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testCDMType(UnpackDependenciesMojo mojo) throws Exception {
        dotestClassifierType(mojo, null, "zip");
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testCDMClassifierType(UnpackDependenciesMojo mojo) throws Exception {
        dotestClassifierType(mojo, "jdk14", "war");
    }

    private void dotestClassifierType(UnpackDependenciesMojo mojo, String testClassifier, String testType)
            throws Exception {
        mojo.classifier = testClassifier;
        mojo.type = testType;

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            String type = testType != null ? testType : artifact.getType();
            this.stubFactory.createArtifact(
                    artifact.getGroupId(),
                    artifact.getArtifactId(),
                    VersionRange.createFromVersion(artifact.getBaseVersion()),
                    artifact.getScope(),
                    type,
                    testClassifier,
                    false);
        }

        mojo.execute();

        for (Artifact artifact : mojo.getProject().getArtifacts()) {
            String useClassifier = artifact.getClassifier();
            String useType = artifact.getType();

            if (testClassifier != null && !testClassifier.isEmpty()) {
                useClassifier = testClassifier;
                // type is only used if classifier is used.
                if (testType != null && !testType.isEmpty()) {
                    useType = testType;
                }
            }
            Artifact unpacked = stubFactory.createArtifact(
                    artifact.getGroupId(),
                    artifact.getArtifactId(),
                    artifact.getVersion(),
                    Artifact.SCOPE_COMPILE,
                    useType,
                    useClassifier);
            assertUnpacked(mojo, unpacked);
        }
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    void testArtifactResolutionException(UnpackDependenciesMojo mojo) throws MojoFailureException {
        dotestArtifactExceptions(mojo);
    }

    private void dotestArtifactExceptions(UnpackDependenciesMojo mojo) throws MojoFailureException {
        mojo.classifier = "jdk";
        mojo.failOnMissingClassifierArtifact = true;
        mojo.type = "java-sources";

        try {
            mojo.execute();
            fail("ExpectedException");
        } catch (MojoExecutionException e) {
        }
    }
}
