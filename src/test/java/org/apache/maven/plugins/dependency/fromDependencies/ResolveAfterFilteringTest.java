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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.collection.AbstractArtifactsFilter;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MDEP-744, MDEP-895: the goals ask Maven for the dependency graph only and resolve what is left after filtering.
 */
class ResolveAfterFilteringTest {

    @TempDir
    private File tempDir;

    private ResolverUtil resolverUtil;

    private MavenProject project;

    private Artifact runtime;

    private Artifact provided;

    private Artifact test;

    @BeforeEach
    void setUp() {
        resolverUtil = mock(ResolverUtil.class);
        project = new MavenProject();
        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());

        runtime = unresolvedArtifact("runtime-lib", "runtime");
        provided = unresolvedArtifact("provided-module", "provided");
        test = unresolvedArtifact("test-module", "test");
        project.setArtifacts(new LinkedHashSet<>(Arrays.asList(runtime, provided, test)));
        project.setDependencyArtifacts(new LinkedHashSet<>(Arrays.asList(runtime, provided, test)));
    }

    private static Artifact unresolvedArtifact(String artifactId, String scope) {
        return new DefaultArtifact(
                "org.example", artifactId, "1.0-SNAPSHOT", scope, "jar", null, new DefaultArtifactHandler("jar"));
    }

    private File fileFor(Artifact artifact) throws IOException {
        File file = new File(tempDir, artifact.getArtifactId() + ".jar");
        Files.write(file.toPath(), new byte[] {1});
        return file;
    }

    private void resolvesEverythingRequested() throws Exception {
        when(resolverUtil.resolveArtifacts(any(), any())).thenAnswer(invocation -> {
            Collection<org.eclipse.aether.artifact.Artifact> requested = invocation.getArgument(0);
            return requested.stream()
                    .map(a -> {
                        try {
                            File file = new File(tempDir, a.getArtifactId() + ".jar");
                            Files.write(file.toPath(), new byte[] {1});
                            return new ArtifactResult(new ArtifactRequest(a, null, null)).setArtifact(a.setFile(file));
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .collect(Collectors.toList());
        });
    }

    private CopyDependenciesMojo copyMojo(String includeScope) {
        CopyDependenciesMojo mojo = new CopyDependenciesMojo(null, null, project, resolverUtil, null, null, null);
        mojo.includeScope = includeScope;
        mojo.markersDirectory = new File(tempDir, "markers");
        mojo.outputDirectory = new File(tempDir, "out");
        return mojo;
    }

    @SuppressWarnings("unchecked")
    private List<String> requestedArtifactIds() throws Exception {
        ArgumentCaptor<Collection<org.eclipse.aether.artifact.Artifact>> captor =
                ArgumentCaptor.forClass(Collection.class);
        verify(resolverUtil).resolveArtifacts(captor.capture(), any());
        return captor.getValue().stream()
                .map(org.eclipse.aether.artifact.Artifact::getArtifactId)
                .collect(Collectors.toList());
    }

    @Test
    void onlyArtifactsSelectedByIncludeScopeAreResolved() throws Exception {
        resolvesEverythingRequested();

        DependencyStatusSets status = copyMojo("runtime").getDependencySets(true);

        assertEquals(Arrays.asList("runtime-lib"), requestedArtifactIds());
        Set<Artifact> selected = status.getResolvedDependencies();
        assertEquals(1, selected.size());
        Artifact artifact = selected.iterator().next();
        assertEquals("runtime-lib", artifact.getArtifactId());
        assertNotNull(artifact.getFile());
        assertEquals("runtime", artifact.getScope(), "collected properties must survive the resolution");
        assertNull(provided.getFile());
        assertNull(test.getFile());
    }

    private static void assertNull(Object o) {
        org.junit.jupiter.api.Assertions.assertNull(o);
    }

    @Test
    void excludeScopeKeepsUnwantedArtifactsUnresolved() throws Exception {
        resolvesEverythingRequested();
        CopyDependenciesMojo mojo = copyMojo("");
        mojo.excludeScope = "provided";

        mojo.getDependencySets(true);

        assertEquals(Arrays.asList("runtime-lib", "test-module"), requestedArtifactIds());
    }

    @Test
    void alreadyResolvedArtifactsAreNotResolvedAgain() throws Exception {
        runtime.setFile(fileFor(runtime));

        DependencyStatusSets status = copyMojo("runtime").getDependencySets(true);

        verify(resolverUtil, never()).resolveArtifacts(any(), any());
        assertEquals(1, status.getResolvedDependencies().size());
    }

    @Test
    void resolutionFailureOfSelectedArtifactFailsTheGoal() throws Exception {
        when(resolverUtil.resolveArtifacts(any(), any()))
                .thenThrow(new ArtifactResolutionException(Arrays.asList(new ArtifactResult(
                        new ArtifactRequest(org.apache.maven.RepositoryUtils.toArtifact(runtime), null, null)))));

        CopyDependenciesMojo mojo = copyMojo("runtime");
        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo.getDependencySets(true));
        assertTrue(e.getMessage().startsWith("Could not resolve dependencies for project"), e.getMessage());
    }

    @Test
    void goalsThatKeepMavenResolutionDoNotResolveAnything() throws Exception {
        UnpackDependenciesMojo unpack = new UnpackDependenciesMojo(null, null, project, resolverUtil, null, null, null);
        assertTrue(unpack.isResolvingAfterFiltering());
        assertTrue(copyMojo("").isResolvingAfterFiltering());
        assertFalse(new BuildClasspathMojo(null, null, project, resolverUtil, null, null, null)
                .isResolvingAfterFiltering());
    }

    @Test
    void withClassifierOnlyTheTranslatedArtifactsReachTheDestinationFilter() throws Exception {
        // only the sources are resolved, and a copy of the original already exists in the destination
        when(resolverUtil.resolveArtifacts(any(), any())).thenAnswer(invocation -> {
            Collection<org.eclipse.aether.artifact.Artifact> requested = invocation.getArgument(0);
            throw new ArtifactResolutionException(requested.stream()
                    .map(a -> new ArtifactResult(new ArtifactRequest(a, null, null)))
                    .collect(Collectors.toList()));
        });
        File sourcesJar = fileFor(runtime);
        when(resolverUtil.resolveArtifact(any(), any())).thenAnswer(invocation -> {
            org.eclipse.aether.artifact.Artifact sources = invocation.getArgument(0);
            return sources.setFile(sourcesJar);
        });
        org.apache.maven.artifact.handler.manager.ArtifactHandlerManager handlers =
                mock(org.apache.maven.artifact.handler.manager.ArtifactHandlerManager.class);
        when(handlers.getArtifactHandler("jar")).thenReturn(new DefaultArtifactHandler("jar"));

        // Maven's FilterArtifacts silently swallows the NullPointerException of a filter that reads a missing file,
        // so record what the marker filter is given instead of waiting for it to blow up
        List<Artifact> seenByMarkerFilter = new ArrayList<>();
        CopyDependenciesMojo mojo = new CopyDependenciesMojo(null, null, project, resolverUtil, null, handlers, null) {
            @Override
            protected ArtifactsFilter getMarkedArtifactFilter() {
                ArtifactsFilter real = super.getMarkedArtifactFilter();
                return new AbstractArtifactsFilter() {
                    @Override
                    public Set<Artifact> filter(Set<Artifact> artifacts) throws ArtifactFilterException {
                        seenByMarkerFilter.addAll(artifacts);
                        return real.filter(artifacts);
                    }
                };
            }
        };
        mojo.includeScope = "runtime";
        mojo.classifier = "sources";
        mojo.overWriteIfNewer = true;
        mojo.markersDirectory = new File(tempDir, "markers");
        mojo.outputDirectory = new File(tempDir, "out");
        assertTrue(mojo.outputDirectory.mkdirs());
        Files.write(new File(mojo.outputDirectory, "runtime-lib-1.0-SNAPSHOT.jar").toPath(), new byte[] {1});

        DependencyStatusSets status = mojo.getDependencySets(false);

        assertTrue(seenByMarkerFilter.stream().noneMatch(a -> a.getFile() == null), seenByMarkerFilter.toString());
        assertEquals(1, status.getResolvedDependencies().size());
        assertEquals(
                "sources", status.getResolvedDependencies().iterator().next().getClassifier());
    }

    private Artifact addSystemDependency(String systemPath) {
        Artifact system = unresolvedArtifact("system-lib", "system");
        project.getArtifacts().add(system);
        org.apache.maven.model.Dependency dependency = new org.apache.maven.model.Dependency();
        dependency.setGroupId(system.getGroupId());
        dependency.setArtifactId(system.getArtifactId());
        dependency.setVersion(system.getVersion());
        dependency.setScope("system");
        dependency.setSystemPath(systemPath);
        project.getModel().addDependency(dependency);
        return system;
    }

    @Test
    void systemScopeArtifactTakesItsFileFromSystemPathAndIsNeverResolved() throws Exception {
        resolvesEverythingRequested();
        File systemJar = fileFor(unresolvedArtifact("system-lib", "system"));
        Artifact system = addSystemDependency(systemJar.getAbsolutePath());

        DependencyStatusSets status = copyMojo("").getDependencySets(true);

        assertEquals(systemJar, system.getFile());
        assertTrue(status.getResolvedDependencies().contains(system));
        assertFalse(requestedArtifactIds().contains("system-lib"));
    }

    @Test
    void systemScopeArtifactWithMissingFileFailsClearly() throws Exception {
        resolvesEverythingRequested();
        addSystemDependency(new File(tempDir, "does-not-exist.jar").getAbsolutePath());

        CopyDependenciesMojo mojo = copyMojo("");
        MojoExecutionException e = assertThrows(MojoExecutionException.class, () -> mojo.getDependencySets(true));
        assertTrue(e.getMessage().contains("system-lib") && e.getMessage().contains("systemPath"), e.getMessage());
    }
}
