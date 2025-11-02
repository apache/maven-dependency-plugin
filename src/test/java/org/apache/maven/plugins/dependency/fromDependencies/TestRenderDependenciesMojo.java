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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.plugins.dependency.testUtils.stubs.DependencyProjectStub;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestRenderDependenciesMojo extends AbstractDependencyMojoTestCase {
    private RenderDependenciesMojo mojo;

    @Override
    protected String getTestDirectoryName() {
        return "render-dependencies";
    }

    @Override
    protected boolean shouldCreateFiles() {
        return true;
    }

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        final MavenProject project = new DependencyProjectStub();
        getContainer().addComponent(project, MavenProject.class.getName());

        final MavenSession session = newMavenSession(project);
        getContainer().addComponent(session, MavenSession.class.getName());

        final File testPom = new File(
                getBasedir(), "target/test-classes/unit/" + getTestDirectoryName() + "-test/plugin-config.xml");
        mojo = (RenderDependenciesMojo) lookupMojo(getTestDirectoryName(), testPom);
    }

    /**
     * Tests the rendering.
     * Note that this is a real life example of using the mojo to generate a CRD for a SparkApplication.
     * It is useful when combined with JIB for example since several versions of the CRD do not support wildcard for
     * the classpath(s).
     */
    @Test
    public void testRender() throws Exception {
        final File rendered = new File(testDir, "render-dependencies.testRender.txt");

        setupProject();

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
    public void testRenderFromFile() throws Exception {
        final File rendered = new File(testDir, "render-dependencies.testRenderFromFile.txt");
        final File template = new File(testDir, "render-dependencies.testRenderFromFile.template.vm");
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
        final MavenProject project = mojo.getProject();
        final Set<Artifact> artifacts = stubFactory.getScopedArtifacts();
        final Set<Artifact> directArtifacts = stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);
        project.setArtifacts(artifacts);
        project.setDependencyArtifacts(directArtifacts);
    }
}
