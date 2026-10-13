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
package org.apache.maven.plugins.dependency.resolvers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MDEP-895: resolve-sources must not need the main artifact of a dependency that the filters drop, nor of a
 * reactor module that has not been built.
 */
class ResolveDependencySourcesMojoTest {

    @TempDir
    private File tempDir;

    private static Artifact unresolvedArtifact(String artifactId, String scope) {
        return new DefaultArtifact(
                "org.example", artifactId, "1.0", scope, "jar", null, new DefaultArtifactHandler("jar"));
    }

    @Test
    void unbuiltModuleDoesNotFailTheGoalAndSourcesOfTheOthersAreResolved() throws Exception {
        Artifact library = unresolvedArtifact("library", "compile");
        Artifact module = unresolvedArtifact("unbuilt-module", "compile");
        MavenProject project = new MavenProject();
        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());
        project.setArtifacts(new LinkedHashSet<>(Arrays.asList(library, module)));
        project.setDependencyArtifacts(new LinkedHashSet<>(Arrays.asList(library, module)));

        ResolverUtil resolverUtil = mock(ResolverUtil.class);
        // the main jar of the unbuilt module is absent, the library jar is there
        File libraryJar = new File(tempDir, "library.jar");
        Files.write(libraryJar.toPath(), new byte[] {1});
        when(resolverUtil.resolveArtifacts(any(), any())).thenAnswer(invocation -> {
            Collection<org.eclipse.aether.artifact.Artifact> requested = invocation.getArgument(0);
            return requested.stream()
                    .map(a -> {
                        ArtifactResult result = new ArtifactResult(new ArtifactRequest(a, null, null));
                        return "library".equals(a.getArtifactId()) ? result.setArtifact(a.setFile(libraryJar)) : result;
                    })
                    .collect(Collectors.toList());
        });
        when(resolverUtil.resolveArtifact(any(), any())).thenAnswer(invocation -> {
            org.eclipse.aether.artifact.Artifact sources = invocation.getArgument(0);
            if (!"library".equals(sources.getArtifactId())) {
                throw new ArtifactResolutionException(
                        Arrays.asList(new ArtifactResult(new ArtifactRequest(sources, null, null))));
            }
            return sources.setFile(writeSourcesJar());
        });

        ArtifactHandlerManager handlers = mock(ArtifactHandlerManager.class);
        when(handlers.getArtifactHandler("jar")).thenReturn(new DefaultArtifactHandler("jar"));
        ResolveDependencySourcesMojo mojo =
                new ResolveDependencySourcesMojo(null, null, project, resolverUtil, null, handlers);
        mojo.setClassifier("sources");
        mojo.setMarkersDirectory(new File(tempDir, "markers"));

        mojo.execute();

        assertEquals(
                Arrays.asList("library"),
                mojo.getResults().getResolvedDependencies().stream()
                        .map(Artifact::getArtifactId)
                        .collect(Collectors.toList()));
        assertTrue(mojo.getResults()
                .getResolvedDependencies()
                .iterator()
                .next()
                .getFile()
                .isFile());
        // only the classifier artifact that failed is reported, not the originals
        assertEquals(
                Arrays.asList("org.example:unbuilt-module:jar:sources:1.0"),
                mojo.getResults().getUnResolvedDependencies().stream()
                        .map(Artifact::getId)
                        .collect(Collectors.toList()));
        verify(resolverUtil, never()).resolveArtifacts(any(), any());
    }

    private File writeSourcesJar() throws IOException {
        File file = new File(tempDir, "library-sources.jar");
        Files.write(file.toPath(), new byte[] {1});
        return file;
    }
}
