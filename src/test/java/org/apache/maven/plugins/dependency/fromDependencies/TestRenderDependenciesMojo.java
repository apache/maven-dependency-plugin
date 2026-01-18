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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@MojoTest
class TestRenderDependenciesMojo {

    private DependencyArtifactStubFactory stubFactory;

    @TempDir
    private File tempDir;

    @Inject
    private MavenProject project;

    @BeforeEach
    void setUp() throws Exception {
        stubFactory = new DependencyArtifactStubFactory(null, false);
        setupProject();
    }

    /**
     * Tests the rendering.
     * Note that this is a real life example of using the mojo to generate a CRD for a SparkApplication.
     * It is useful when combined with JIB for example since several versions of the CRD do not support wildcard for
     * the classpath(s).
     */
    @Test
    @InjectMojo(goal = "render-dependencies")
    void testRender(RenderDependenciesMojo mojo) throws Exception {
        final File rendered = new File(tempDir, "render-dependencies.testRender.txt");

        mojo.setTemplate("deps:\n"
                + "  jars:\n"
                + "#foreach($dep in $sorter.sort($artifacts, [\"artifactId:asc\"]))\n"
                + "#set($type = $dep.type)\n"
                + "#if(!$type || $type.trim().isEmpty())\n"
                + "  #set($type = \"jar\")\n"
                + "#end\n"
                + "#set($classifierSuffix = \"\")\n"
                + "#if($dep.classifier && !$dep.classifier.trim().isEmpty())\n"
                + "  #set($classifierSuffix = \"-$dep.classifier\")\n"
                + "#end\n"
                + "  - local:///opt/test/libs/$dep.artifactId-$dep.baseVersion$classifierSuffix.$type\n"
                + "#end");
        mojo.setOutputFile(rendered);
        mojo.execute();

        assertThat(rendered)
                .content()
                .isEqualTo("deps:\n"
                        + "  jars:\n"
                        + "  - local:///opt/test/libs/compile-1.0.jar\n"
                        + "  - local:///opt/test/libs/provided-1.0.jar\n"
                        + "  - local:///opt/test/libs/release-1.0.jar\n"
                        + "  - local:///opt/test/libs/runtime-1.0.jar\n"
                        + "  - local:///opt/test/libs/snapshot-2.0-SNAPSHOT.jar\n"
                        + "  - local:///opt/test/libs/system-1.0.jar\n"
                        + "  - local:///opt/test/libs/test-1.0.jar\n");
    }

    /**
     * Tests the rendering with a file template.
     */
    @Test
    @InjectMojo(goal = "render-dependencies")
    void testRenderFromFile(RenderDependenciesMojo mojo) throws Exception {
        final File rendered = new File(tempDir, "render-dependencies.testRenderFromFile.txt");
        final File template = new File(tempDir, "render-dependencies.testRenderFromFile.template.vm");
        Files.write(
                template.toPath(),
                ("deps:\n"
                                + "  jars:\n"
                                + "#foreach($dep in $sorter.sort($artifacts, [\"artifactId:asc\"]))\n"
                                + "#set($type = $dep.type)\n"
                                + "#if(!$type || $type.trim().isEmpty())\n"
                                + "  #set($type = \"jar\")\n"
                                + "#end\n"
                                + "#set($classifierSuffix = \"\")\n"
                                + "#if($dep.classifier && !$dep.classifier.trim().isEmpty())\n"
                                + "  #set($classifierSuffix = \"-$dep.classifier\")\n"
                                + "#end\n"
                                + "  - local:///opt/test/libs/$dep.artifactId-$dep.baseVersion$classifierSuffix.$type\n"
                                + "#end")
                        .getBytes(StandardCharsets.UTF_8));

        setupProject();

        mojo.setTemplate(template.getAbsolutePath());
        mojo.setOutputFile(rendered);
        mojo.execute();

        assertThat(rendered)
                .content()
                .isEqualTo("deps:\n"
                        + "  jars:\n"
                        + "  - local:///opt/test/libs/compile-1.0.jar\n"
                        + "  - local:///opt/test/libs/provided-1.0.jar\n"
                        + "  - local:///opt/test/libs/release-1.0.jar\n"
                        + "  - local:///opt/test/libs/runtime-1.0.jar\n"
                        + "  - local:///opt/test/libs/snapshot-2.0-SNAPSHOT.jar\n"
                        + "  - local:///opt/test/libs/system-1.0.jar\n"
                        + "  - local:///opt/test/libs/test-1.0.jar\n");
    }

    private void setupProject() throws IOException {
        final Set<Artifact> artifacts = stubFactory.getScopedArtifacts();
        final Set<Artifact> directArtifacts = stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);
        project.setArtifacts(artifacts);
        project.setDependencyArtifacts(directArtifacts);
        project.setArtifact(stubFactory.createArtifact("g", "a", "1.0", "jar"));
    }
}
