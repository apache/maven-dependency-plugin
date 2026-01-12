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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.SnapshotArtifactRepositoryMetadata;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.bridge.MavenRepositorySystem;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@MojoTest(realRepositorySession = true)
class TestCopyDependenciesMojo2 {

    @TempDir
    private File tempDir;

    private DependencyArtifactStubFactory stubFactory;

    @Inject
    private MavenSession session;

    @Inject
    private MavenProject project;

    @Inject
    private MavenRepositorySystem repositorySystem;

    @BeforeEach
    void setUp() throws Exception {
        stubFactory = new DependencyArtifactStubFactory(tempDir, true, false);

        Set<Artifact> artifacts = stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);
        project.setArtifacts(artifacts);

        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "includeScope", value = "compile")
    void testCopyDependenciesMojoIncludeCompileScope(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());

        mojo.execute();

        ScopeArtifactFilter saf = new ScopeArtifactFilter(mojo.includeScope);

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);

            assertEquals(saf.include(artifact), file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "includeScope", value = "test")
    void testCopyDependenciesMojoIncludeTestScope(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());

        mojo.execute();

        ScopeArtifactFilter saf = new ScopeArtifactFilter(mojo.includeScope);

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);

            assertEquals(saf.include(artifact), file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "includeScope", value = "runtime")
    void testCopyDependenciesMojoIncludeRuntimeScope(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());

        mojo.execute();

        ScopeArtifactFilter saf = new ScopeArtifactFilter(mojo.includeScope);

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);

            assertEquals(saf.include(artifact), file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "includeScope", value = "provided")
    void testCopyDependenciesMojoIncludeprovidedScope(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);

            assertEquals(Artifact.SCOPE_PROVIDED.equals(artifact.getScope()), file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "includeScope", value = "system")
    void testCopyDependenciesMojoIncludesystemScope(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);

            assertEquals(Artifact.SCOPE_SYSTEM.equals(artifact.getScope()), file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "useSubDirectoryPerArtifact", value = "true")
    void testSubPerArtifact(CopyDependenciesMojo mojo) throws Exception {

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File folder = DependencyUtil.getFormattedOutputDirectory(
                    false, false, true, false, false, false, mojo.outputDirectory, artifact);
            File file = new File(folder, fileName);
            assertTrue(file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "useSubDirectoryPerArtifact", value = "true")
    @MojoParameter(name = "useSubDirectoryPerType", value = "true")
    void testSubPerArtifactAndType(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArtifacts());

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File folder = DependencyUtil.getFormattedOutputDirectory(
                    false, true, true, false, false, false, mojo.outputDirectory, artifact);
            File file = new File(folder, fileName);
            assertTrue(file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "useSubDirectoryPerArtifact", value = "true")
    @MojoParameter(name = "useSubDirectoryPerScope", value = "true")
    void testSubPerArtifactAndScope(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArtifacts());

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File folder = DependencyUtil.getFormattedOutputDirectory(
                    true, false, true, false, false, false, mojo.outputDirectory, artifact);
            File file = new File(folder, fileName);
            assertTrue(file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    void testRepositoryLayout(CopyDependenciesMojo mojo) throws Exception {

        ProjectBuildingRequest pbr = new DefaultProjectBuildingRequest();
        pbr.setRepositorySession(session.getRepositorySession());
        when(session.getProjectBuildingRequest()).thenReturn(pbr);

        String baseVersion = "2.0-SNAPSHOT";
        String groupId = "testGroupId";
        String artifactId = "expanded-snapshot";

        Artifact expandedSnapshot =
                createExpandedVersionArtifact(baseVersion, groupId, artifactId, "compile", "jar", null);

        mojo.getProject().getArtifacts().add(expandedSnapshot);

        Artifact pomExpandedSnapshot =
                createExpandedVersionArtifact(baseVersion, groupId, artifactId, "compile", "pom", null);
        mojo.getProject().getArtifacts().add(pomExpandedSnapshot);

        mojo.useRepositoryLayout = true;
        mojo.execute();

        File outputDirectory = mojo.outputDirectory;
        ArtifactRepository targetRepository = new MavenArtifactRepository(
                "local",
                outputDirectory.toURI().toURL().toExternalForm(),
                new DefaultRepositoryLayout(),
                new ArtifactRepositoryPolicy(),
                new ArtifactRepositoryPolicy());

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        File baseDirectory = Paths.get(targetRepository.getBasedir()).toFile();
        assertTrue(baseDirectory.isDirectory());

        for (Artifact artifact : artifacts) {
            assertArtifactExists(artifact, targetRepository);
            if (!artifact.getBaseVersion().equals(artifact.getVersion())) {
                Artifact baseArtifact = repositorySystem.createArtifact(
                        artifact.getGroupId(),
                        artifact.getArtifactId(),
                        artifact.getBaseVersion(),
                        artifact.getScope(),
                        artifact.getType());
                assertArtifactExists(baseArtifact, targetRepository);
            }
        }
    }

    private Artifact createExpandedVersionArtifact(
            String baseVersion, String groupId, String artifactId, String scope, String type, String classifier)
            throws IOException {
        Artifact expandedSnapshot = this.stubFactory.createArtifact(
                groupId, artifactId, VersionRange.createFromVersion(baseVersion), scope, type, classifier, false);

        Snapshot snapshot = new Snapshot();
        snapshot.setTimestamp("20130710.122148");
        snapshot.setBuildNumber(1);
        RepositoryMetadata metadata = new SnapshotArtifactRepositoryMetadata(expandedSnapshot, snapshot);
        String newVersion = snapshot.getTimestamp() + "-" + snapshot.getBuildNumber();
        expandedSnapshot.setResolvedVersion(baseVersion.replace(Artifact.SNAPSHOT_VERSION, newVersion));
        expandedSnapshot.addMetadata(metadata);
        return expandedSnapshot;
    }

    private void assertArtifactExists(Artifact artifact, ArtifactRepository targetRepository) {

        ArtifactRepositoryLayout layout = targetRepository.getLayout();
        String pathOf = layout.pathOf(artifact);

        // possible change/bug in DefaultArtifactRepositoryLayout.pathOf method between Maven 3 and Maven 3.1
        pathOf = pathOf.replace("20130710.122148-1", "SNAPSHOT");

        File file = new File(targetRepository.getBasedir(), pathOf);

        Path targetPath = Paths.get(file.getParent());
        assertTrue(Files.isDirectory(targetPath), "Target path doesn't exist: " + targetPath);

        assertTrue(file.exists(), "File doesn't exist: " + file.getAbsolutePath());

        Collection<ArtifactMetadata> metas = artifact.getMetadataList();
        for (ArtifactMetadata meta : metas) {
            File metaFile = new File(
                    targetRepository.getBasedir(), layout.pathOfLocalRepositoryMetadata(meta, targetRepository));
            assertTrue(metaFile.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "useSubDirectoryPerArtifact", value = "true")
    @MojoParameter(name = "stripVersion", value = "true")
    void testSubPerArtifactRemoveVersion(CopyDependenciesMojo mojo) throws Exception {

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, true);
            File folder = DependencyUtil.getFormattedOutputDirectory(
                    false, false, true, false, true, false, mojo.outputDirectory, artifact);
            File file = new File(folder, fileName);
            assertTrue(file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "useSubDirectoryPerArtifact", value = "true")
    @MojoParameter(name = "useSubDirectoryPerType", value = "true")
    @MojoParameter(name = "stripVersion", value = "true")
    void testSubPerArtifactAndTypeRemoveVersion(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArtifacts());

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, true);
            File folder = DependencyUtil.getFormattedOutputDirectory(
                    false, true, true, false, true, false, mojo.outputDirectory, artifact);
            File file = new File(folder, fileName);
            assertTrue(file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "useSubDirectoryPerArtifact", value = "true")
    @MojoParameter(name = "stripType", value = "true")
    void testSubPerArtifactRemoveType(CopyDependenciesMojo mojo) throws Exception {

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File folder = DependencyUtil.getFormattedOutputDirectory(
                    false, false, true, false, false, true, mojo.outputDirectory, artifact);
            File file = new File(folder, fileName);
            assertTrue(file.exists());
        }
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "useSubDirectoryPerArtifact", value = "true")
    @MojoParameter(name = "useSubDirectoryPerType", value = "true")
    @MojoParameter(name = "stripType", value = "true")
    void testSubPerArtifactAndTypeRemoveType(CopyDependenciesMojo mojo) throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArtifacts());

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File folder = DependencyUtil.getFormattedOutputDirectory(
                    false, true, true, false, false, true, mojo.outputDirectory, artifact);
            File file = new File(folder, fileName);
            assertTrue(file.exists());
        }
    }
}
