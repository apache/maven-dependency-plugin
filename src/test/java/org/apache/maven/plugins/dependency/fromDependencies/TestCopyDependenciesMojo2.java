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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.plugins.dependency.testUtils.stubs.DependencyProjectStub;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;

public class TestCopyDependenciesMojo2 extends AbstractDependencyMojoTestCase {

    private CopyDependenciesMojo mojo;

    @Override
    protected void setUp() throws Exception {
        // required for mojo lookups to work
        super.setUp("copy-dependencies", true);
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

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);

        project.setArtifacts(artifacts);
        mojo.markersDirectory = new File(this.testDir, "markers");

        LegacySupport legacySupport = lookup(LegacySupport.class);
        legacySupport.setSession(session);
        installLocalRepository(legacySupport);
    }

    public void testCopyDependenciesMojoIncludeCompileScope() throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.includeScope = "compile";

        mojo.execute();

        ScopeArtifactFilter saf = new ScopeArtifactFilter(mojo.includeScope);

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);

            assertEquals(saf.include(artifact), file.exists());
        }
    }

    public void testCopyDependenciesMojoIncludeTestScope() throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.includeScope = "test";

        mojo.execute();

        ScopeArtifactFilter saf = new ScopeArtifactFilter(mojo.includeScope);

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);

            assertEquals(saf.include(artifact), file.exists());
        }
    }

    public void testCopyDependenciesMojoIncludeRuntimeScope() throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.includeScope = "runtime";

        mojo.execute();

        ScopeArtifactFilter saf = new ScopeArtifactFilter(mojo.includeScope);

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);

            assertEquals(saf.include(artifact), file.exists());
        }
    }

    public void testCopyDependenciesMojoIncludeprovidedScope() throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.includeScope = "provided";

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);

            assertEquals(Artifact.SCOPE_PROVIDED.equals(artifact.getScope()), file.exists());
        }
    }

    public void testCopyDependenciesMojoIncludesystemScope() throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getScopedArtifacts());
        mojo.includeScope = "system";

        mojo.execute();

        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            String fileName = DependencyUtil.getFormattedFileName(artifact, false);
            File file = new File(mojo.outputDirectory, fileName);

            assertEquals(Artifact.SCOPE_SYSTEM.equals(artifact.getScope()), file.exists());
        }
    }

    public void testSubPerArtifact() throws Exception {
        mojo.useSubDirectoryPerArtifact = true;

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

    public void testSubPerArtifactAndType() throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArtifacts());
        mojo.useSubDirectoryPerArtifact = true;
        mojo.useSubDirectoryPerType = true;

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

    public void testSubPerArtifactAndScope() throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArtifacts());
        mojo.useSubDirectoryPerArtifact = true;
        mojo.useSubDirectoryPerScope = true;

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

    public void testRepositoryLayout() throws Exception {
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

        ArtifactFactory artifactFactory = lookup(ArtifactFactory.class);

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
                Artifact baseArtifact = artifactFactory.createArtifact(
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
        assertTrue("Target path doesn't exist: " + targetPath, Files.isDirectory(targetPath));

        assertTrue("File doesn't exist: " + file.getAbsolutePath(), file.exists());

        Collection<ArtifactMetadata> metas = artifact.getMetadataList();
        for (ArtifactMetadata meta : metas) {
            File metaFile = new File(
                    targetRepository.getBasedir(), layout.pathOfLocalRepositoryMetadata(meta, targetRepository));
            assertTrue(metaFile.exists());
        }
    }

    public void testSubPerArtifactRemoveVersion() throws Exception {
        mojo.useSubDirectoryPerArtifact = true;
        mojo.stripVersion = true;

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

    public void testSubPerArtifactAndTypeRemoveVersion() throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArtifacts());
        mojo.useSubDirectoryPerArtifact = true;
        mojo.useSubDirectoryPerType = true;
        mojo.stripVersion = true;

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

    public void testSubPerArtifactRemoveType() throws Exception {
        mojo.useSubDirectoryPerArtifact = true;
        mojo.stripType = true;

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

    public void testSubPerArtifactAndTypeRemoveType() throws Exception {
        mojo.getProject().setArtifacts(stubFactory.getTypedArtifacts());
        mojo.useSubDirectoryPerArtifact = true;
        mojo.useSubDirectoryPerType = true;
        mojo.stripType = true;

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
