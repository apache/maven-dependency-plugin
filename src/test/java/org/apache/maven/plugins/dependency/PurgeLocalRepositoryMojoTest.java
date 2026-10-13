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
package org.apache.maven.plugins.dependency;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecution.Source;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.DefaultArtifactTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("deprecation")
class PurgeLocalRepositoryMojoTest {

    private static final String GROUP_PATH = "org/example";

    @TempDir
    Path localRepo;

    private MavenProject project;

    private MavenSession session;

    private ResolverUtil resolverUtil;

    private PurgeLocalRepositoryMojo mojo;

    @BeforeEach
    void setUp() throws Exception {
        project = mock(MavenProject.class);
        session = mock(MavenSession.class);
        resolverUtil = mock(ResolverUtil.class);

        DefaultArtifactTypeRegistry typeRegistry = new DefaultArtifactTypeRegistry();
        typeRegistry.add(new DefaultArtifactType("jar", "jar", "", "java"));
        typeRegistry.add(new DefaultArtifactType("custom", "cext", "", "java"));
        RepositorySystemSession repositorySession = mock(RepositorySystemSession.class);
        when(repositorySession.getArtifactTypeRegistry()).thenReturn(typeRegistry);
        when(session.getRepositorySession()).thenReturn(repositorySession);

        org.apache.maven.artifact.Artifact projectArtifact = new org.apache.maven.artifact.DefaultArtifact(
                "org.example.project", "project", "1.0", null, "jar", null, new DefaultArtifactHandler("jar"));
        when(project.getArtifact()).thenReturn(projectArtifact);
        when(project.getId()).thenReturn("org.example.project:project:jar:1.0");
        when(project.getArtifactId()).thenReturn("project");
        when(project.getRemoteProjectRepositories()).thenReturn(Collections.<RemoteRepository>emptyList());

        MojoExecution mojoExecution = mock(MojoExecution.class);
        when(mojoExecution.getSource()).thenReturn(Source.CLI);

        ArtifactRepository localRepository = new MavenArtifactRepository(
                "local",
                localRepo.toUri().toString(),
                new DefaultRepositoryLayout(),
                new org.apache.maven.artifact.repository.ArtifactRepositoryPolicy(),
                new org.apache.maven.artifact.repository.ArtifactRepositoryPolicy());

        mojo = new PurgeLocalRepositoryMojo(project, session, resolverUtil);
        setVariableValueToObject(mojo, "reactorProjects", Collections.singletonList(project));
        setVariableValueToObject(mojo, "mojoExecution", mojoExecution);
        setVariableValueToObject(mojo, "localRepository", localRepository);
        setVariableValueToObject(mojo, "reResolve", false);
        setVariableValueToObject(mojo, "actTransitively", true);
        setVariableValueToObject(mojo, "resolutionFuzziness", "version");
    }

    private void resolvesTo(Artifact... artifacts) throws Exception {
        when(resolverUtil.resolveDependenciesForArtifact(
                        any(Artifact.class), anyList(), any(), anyList(), any(DependencyFilter.class)))
                .thenReturn(Arrays.asList(artifacts));
    }

    private Path file(String relativePath) throws IOException {
        Path file = localRepo.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.write(file, new byte[0]);
    }

    private Path artifactFile(String artifactId, String version) throws IOException {
        return file(GROUP_PATH + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar");
    }

    private static Artifact artifact(String artifactId, String version) {
        return new DefaultArtifact("org.example", artifactId, "jar", version);
    }

    @Test
    void artifactIdFuzzinessWithSnapshotsOnlyDeletesOnlySnapshotVersions() throws Exception {
        Path snapshot = artifactFile("lib", "1.0-SNAPSHOT");
        Path otherSnapshot = artifactFile("lib", "2.0-SNAPSHOT");
        Path release = artifactFile("lib", "1.0");
        Path sibling = artifactFile("sibling", "1.0-SNAPSHOT");
        setVariableValueToObject(mojo, "resolutionFuzziness", "artifactId");
        setVariableValueToObject(mojo, "snapshotsOnly", true);
        resolvesTo(artifact("lib", "1.0-SNAPSHOT"));

        mojo.execute();

        assertThat(snapshot.getParent()).doesNotExist();
        assertThat(otherSnapshot.getParent()).doesNotExist();
        assertThat(release).exists();
        assertThat(sibling).exists();
    }

    @Test
    void groupIdFuzzinessWithSnapshotsOnlyDeletesOnlySnapshotVersions() throws Exception {
        Path snapshot = artifactFile("lib", "1.0-SNAPSHOT");
        Path release = artifactFile("lib", "1.0");
        Path siblingSnapshot = artifactFile("sibling", "3.0-SNAPSHOT");
        Path siblingRelease = artifactFile("sibling", "3.0");
        Path subGroupSnapshot = file(GROUP_PATH + "/sub/deep/1.0-SNAPSHOT/deep-1.0-SNAPSHOT.jar");
        Path subGroupRelease = file(GROUP_PATH + "/sub/deep/1.0/deep-1.0.jar");
        Path otherGroup = file("org/other/lib/1.0-SNAPSHOT/lib-1.0-SNAPSHOT.jar");
        setVariableValueToObject(mojo, "resolutionFuzziness", "groupId");
        setVariableValueToObject(mojo, "snapshotsOnly", true);
        resolvesTo(artifact("lib", "1.0-SNAPSHOT"));

        mojo.execute();

        assertThat(snapshot.getParent()).doesNotExist();
        assertThat(siblingSnapshot.getParent()).doesNotExist();
        assertThat(subGroupSnapshot.getParent()).doesNotExist();
        assertThat(release).exists();
        assertThat(siblingRelease).exists();
        assertThat(subGroupRelease).exists();
        assertThat(otherGroup).exists();
    }

    @Test
    void artifactIdFuzzinessWithoutSnapshotsOnlyDeletesWholeArtifactDirectory() throws Exception {
        Path snapshot = artifactFile("lib", "1.0-SNAPSHOT");
        Path release = artifactFile("lib", "1.0");
        Path sibling = artifactFile("sibling", "1.0");
        setVariableValueToObject(mojo, "resolutionFuzziness", "artifactId");
        resolvesTo(artifact("lib", "1.0-SNAPSHOT"));

        mojo.execute();

        assertThat(snapshot).doesNotExist();
        assertThat(release).doesNotExist();
        assertThat(localRepo.resolve(GROUP_PATH + "/lib")).doesNotExist();
        assertThat(sibling).exists();
    }

    @Test
    void groupIdFuzzinessWithoutSnapshotsOnlyDeletesWholeGroupDirectory() throws Exception {
        Path lib = artifactFile("lib", "1.0");
        Path sibling = artifactFile("sibling", "1.0");
        Path otherGroup = file("org/other/lib/1.0/lib-1.0.jar");
        setVariableValueToObject(mojo, "resolutionFuzziness", "groupId");
        resolvesTo(artifact("lib", "1.0"));

        mojo.execute();

        assertThat(lib).doesNotExist();
        assertThat(sibling).doesNotExist();
        assertThat(localRepo.resolve(GROUP_PATH)).doesNotExist();
        assertThat(otherGroup).exists();
    }

    @Test
    void includeMatchesCustomArtifactType() throws Exception {
        DependencyFilter filter = purgeFilterFor("include", "org.example:custom-lib:custom:1.0");

        assertThat(filter.accept(node("custom-lib", "1.0", "custom"), Collections.emptyList()))
                .as("type")
                .isTrue();
        assertThat(filter.accept(node("other-lib", "1.0", "custom"), Collections.emptyList()))
                .as("other artifactId")
                .isFalse();
        assertThat(filter.accept(node("custom-lib", "1.0", "jar"), Collections.emptyList()))
                .as("other type")
                .isFalse();
    }

    @Test
    void includeStillMatchesFileExtension() throws Exception {
        DependencyFilter filter = purgeFilterFor("include", "org.example:custom-lib:cext:1.0");

        assertThat(filter.accept(node("custom-lib", "1.0", "custom"), Collections.emptyList()))
                .isTrue();
    }

    @Test
    void includeSupportsWildcardsAroundCustomArtifactType() throws Exception {
        DependencyFilter filter = purgeFilterFor("include", "org.example:custom-*:custom");

        assertThat(filter.accept(node("custom-lib", "1.0", "custom"), Collections.emptyList()))
                .isTrue();
        assertThat(filter.accept(node("other-lib", "1.0", "custom"), Collections.emptyList()))
                .isFalse();
    }

    @Test
    void nodeWithoutTypePropertyFallsBackToTheExtension() throws Exception {
        DependencyFilter filter = purgeFilterFor("include", "org.example:lib:jar");
        // a plain artifact has no "type" property
        DefaultDependencyNode untyped =
                new DefaultDependencyNode(new Dependency(new DefaultArtifact("org.example:lib:jar:1.0"), "compile"));

        assertThat(filter.accept(untyped, Collections.emptyList())).isTrue();
    }

    @Test
    void excludeMatchesCustomArtifactType() throws Exception {
        DependencyFilter filter = purgeFilterFor("exclude", "org.example:custom-lib:custom");

        assertThat(filter.accept(node("custom-lib", "1.0", "custom"), Collections.emptyList()))
                .isFalse();
        assertThat(filter.accept(node("custom-lib", "1.0", "jar"), Collections.emptyList()))
                .isTrue();
        assertThat(filter.accept(node("other-lib", "1.0", "custom"), Collections.emptyList()))
                .isTrue();
    }

    private DependencyFilter purgeFilterFor(String parameter, String pattern) throws Exception {
        setVariableValueToObject(mojo, parameter, pattern);
        resolvesTo();

        mojo.execute();

        ArgumentCaptor<DependencyFilter> filter = ArgumentCaptor.forClass(DependencyFilter.class);
        verify(resolverUtil)
                .resolveDependenciesForArtifact(any(Artifact.class), anyList(), any(), anyList(), filter.capture());
        return filter.getValue();
    }

    private static DefaultDependencyNode node(String artifactId, String version, String type) {
        String extension = "custom".equals(type) ? "cext" : type;
        Artifact artifact = new DefaultArtifact(
                "org.example",
                artifactId,
                "",
                extension,
                version,
                new DefaultArtifactType(type, extension, "", "java"));
        return new DefaultDependencyNode(new Dependency(artifact, "compile"));
    }
}
