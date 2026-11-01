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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.dependency.utils.CopyUtil;
import org.apache.maven.plugins.dependency.utils.ParamArtifact;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link CopyDependenciesMojo#setArtifact(String)}: resolving a single artifact (and its transitive
 * dependencies) directly from the repository system, bypassing this project's own dependencies entirely.
 */
class CopyDependenciesMojoArtifactParamTest {

    @TempDir
    private File tempDir;

    private CopyDependenciesMojo newMojo(ResolverUtil resolverUtil, File outputDirectory) {
        MavenSession session = mock(MavenSession.class);
        BuildContext buildContext = mock(BuildContext.class);
        MavenProject project = mock(MavenProject.class);
        when(project.getRemoteProjectRepositories()).thenReturn(Collections.emptyList());
        ProjectBuilder projectBuilder = mock(ProjectBuilder.class);
        ArtifactHandlerManager artifactHandlerManager = mock(ArtifactHandlerManager.class);
        CopyUtil copyUtil = new CopyUtil(buildContext);

        CopyDependenciesMojo mojo = new CopyDependenciesMojo(
                session, buildContext, project, resolverUtil, projectBuilder, artifactHandlerManager, copyUtil);
        mojo.outputDirectory = outputDirectory;
        return mojo;
    }

    private File writeFile(String name, String content) throws Exception {
        File file = new File(tempDir, name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void copiesResolvedArtifactAndItsTransitiveDependencies() throws Exception {
        DefaultArtifact rootArtifact = (DefaultArtifact) new DefaultArtifact("org.example", "demo", null, "jar", "1.0")
                .setFile(writeFile("demo-1.0.jar", "root"));
        DefaultArtifact depArtifact =
                (DefaultArtifact) new DefaultArtifact("org.example", "demo-dep", null, "jar", "2.0")
                        .setFile(writeFile("demo-dep-2.0.jar", "dep"));

        ResolverUtil resolverUtil = mock(ResolverUtil.class);
        when(resolverUtil.createArtifactFromParams(any(ParamArtifact.class))).thenReturn(rootArtifact);
        when(resolverUtil.resolveDependencies(eq(rootArtifact), anyList()))
                .thenReturn(Arrays.asList(rootArtifact, depArtifact));

        File outputDirectory = new File(tempDir, "out");
        CopyDependenciesMojo mojo = newMojo(resolverUtil, outputDirectory);
        mojo.setArtifact("org.example:demo:1.0");

        mojo.execute();

        assertTrue(new File(outputDirectory, "demo-1.0.jar").exists(), "root artifact was not copied");
        assertTrue(new File(outputDirectory, "demo-dep-2.0.jar").exists(), "transitive dependency was not copied");
    }

    @Test
    void excludeTransitiveKeepsOnlyTheRequestedArtifact() throws Exception {
        DefaultArtifact rootArtifact = (DefaultArtifact) new DefaultArtifact("org.example", "demo", null, "jar", "1.0")
                .setFile(writeFile("demo-1.0.jar", "root"));
        DefaultArtifact depArtifact =
                (DefaultArtifact) new DefaultArtifact("org.example", "demo-dep", null, "jar", "2.0")
                        .setFile(writeFile("demo-dep-2.0.jar", "dep"));

        ResolverUtil resolverUtil = mock(ResolverUtil.class);
        when(resolverUtil.createArtifactFromParams(any(ParamArtifact.class))).thenReturn(rootArtifact);
        when(resolverUtil.resolveDependencies(eq(rootArtifact), anyList()))
                .thenReturn(Arrays.asList(rootArtifact, depArtifact));

        File outputDirectory = new File(tempDir, "out");
        CopyDependenciesMojo mojo = newMojo(resolverUtil, outputDirectory);
        mojo.setArtifact("org.example:demo:1.0");
        mojo.excludeTransitive = true;

        mojo.execute();

        assertTrue(new File(outputDirectory, "demo-1.0.jar").exists(), "root artifact was not copied");
        assertFalse(new File(outputDirectory, "demo-dep-2.0.jar").exists(), "transitive dependency should be excluded");
    }

    @Test
    void failsClearlyWhenTheArtifactCannotBeResolved() throws Exception {
        DefaultArtifact rootArtifact = new DefaultArtifact("org.example", "missing", null, "jar", "9.9");

        ResolverUtil resolverUtil = mock(ResolverUtil.class);
        when(resolverUtil.createArtifactFromParams(any(ParamArtifact.class))).thenReturn(rootArtifact);
        when(resolverUtil.resolveDependencies(eq(rootArtifact), anyList()))
                .thenThrow(new DependencyResolutionException(null, new RuntimeException("not found")));

        CopyDependenciesMojo mojo = newMojo(resolverUtil, new File(tempDir, "out"));
        mojo.setArtifact("org.example:missing:9.9");

        Exception exception = assertThrows(Exception.class, mojo::execute);
        assertTrue(exception.getMessage().contains("org.example:missing:jar:9.9"), exception.getMessage());
    }
}
