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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.dependency.utils.ParamArtifact;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetClasspathMojoTest {

    private static final String SEP = File.pathSeparator;

    @TempDir
    private Path temp;

    private ResolverUtil resolverUtil;

    private GetClasspathMojo mojo;

    @BeforeEach
    void setUp() throws Exception {
        resolverUtil = mock(ResolverUtil.class);
        when(resolverUtil.createArtifactFromParams(any(ParamArtifact.class))).thenReturn(new DefaultArtifact("g:a:1"));
        when(resolverUtil.remoteRepositories(any(), any())).thenReturn(Collections.<RemoteRepository>emptyList());
        mojo = new GetClasspathMojo(resolverUtil);
        mojo.setArtifact("g:a:1");
    }

    private Artifact resolved(String artifactId, String version) {
        Path file =
                temp.resolve("repo").resolve(artifactId).resolve(version).resolve(artifactId + "-" + version + ".jar");
        return new DefaultArtifact("g", artifactId, "jar", version).setFile(file.toFile());
    }

    private void resolverReturns(Artifact... artifacts) throws Exception {
        when(resolverUtil.resolveArtifactAndDependencies(any(), any(), anyBoolean()))
                .thenReturn(Arrays.asList(artifacts));
    }

    private void set(String field, Object value) throws Exception {
        java.lang.reflect.Field f = GetClasspathMojo.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(mojo, value);
    }

    @Test
    void writesClasspathInResolverOrder() throws Exception {
        Artifact a = resolved("a", "1");
        Artifact b = resolved("b", "2");
        resolverReturns(a, b);
        File out = temp.resolve("out/cp.txt").toFile();
        set("outputFile", out);

        mojo.execute();

        assertEquals(
                a.getFile().getPath() + SEP + b.getFile().getPath(),
                new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8));
        verify(resolverUtil).resolveArtifactAndDependencies(any(), any(), eq(true));
    }

    @Test
    void transitiveFalseIsPassedOn() throws Exception {
        resolverReturns(resolved("a", "1"));
        set("transitive", false);
        set("outputFile", temp.resolve("cp.txt").toFile());

        mojo.execute();

        verify(resolverUtil).resolveArtifactAndDependencies(any(), any(), eq(false));
    }

    @Test
    void separatorsFilterAndPrefix() throws Exception {
        resolverReturns(resolved("a", "1"), resolved("b", "2"));
        File out = temp.resolve("cp.txt").toFile();
        set("outputFile", out);
        set("pathSeparator", ";");
        set("prefix", "lib");
        set("fileSeparator", "\\");
        set("outputFilterFile", true);

        mojo.execute();

        assertEquals(
                "classpath=lib\\a-1.jar;lib\\b-2.jar",
                new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    void fileSeparatorReplacesSeparatorsInPaths() throws Exception {
        Artifact a = resolved("a", "1");
        resolverReturns(a);
        File out = temp.resolve("cp.txt").toFile();
        set("outputFile", out);
        set("fileSeparator", "|");

        mojo.execute();

        assertEquals(
                a.getFile().getPath().replace(File.separator, "|"),
                new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    void withoutOutputFileNothingIsWritten() throws Exception {
        resolverReturns(resolved("a", "1"));

        mojo.execute();

        try (java.util.stream.Stream<Path> files = Files.list(temp)) {
            assertEquals(Collections.<Path>emptyList(), files.collect(java.util.stream.Collectors.<Path>toList()));
        }
    }

    @Test
    void missingArtifactIsAFailure() throws Exception {
        GetClasspathMojo noArtifact = new GetClasspathMojo(resolverUtil);

        assertThrows(MojoFailureException.class, noArtifact::execute);
        verify(resolverUtil, never()).resolveArtifactAndDependencies(any(), any(), anyBoolean());
    }

    @Test
    void invalidCoordinatesAreAFailure() throws Exception {
        when(resolverUtil.createArtifactFromParams(any())).thenThrow(new IllegalArgumentException("bad"));

        assertThrows(MojoFailureException.class, mojo::execute);
    }

    @Test
    void resolutionProblemIsAnExecutionError() throws Exception {
        when(resolverUtil.resolveArtifactAndDependencies(any(), any(), anyBoolean()))
                .thenThrow(new org.eclipse.aether.resolution.DependencyResolutionException(null, "nope", null));

        assertThrows(MojoExecutionException.class, mojo::execute);
    }

    @Test
    void skipDoesNothing() throws Exception {
        set("skip", true);

        mojo.execute();

        verify(resolverUtil, never()).resolveArtifactAndDependencies(any(), any(), anyBoolean());
    }
}
