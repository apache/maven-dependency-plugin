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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoveDependencyMojoTest {

    @TempDir
    File tempDir;

    private MavenSession session;
    private BuildContext buildContext;
    private MavenProject project;
    private RemoveDependencyMojo mojo;

    @BeforeEach
    void setUp() throws Exception {
        session = mock(MavenSession.class);
        buildContext = mock(BuildContext.class);
        project = mock(MavenProject.class);
        when(buildContext.isIncremental()).thenReturn(false);

        mojo = new RemoveDependencyMojo(session, buildContext, project);
    }

    private File createTempPom(String content) throws IOException {
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return pomFile;
    }

    @Test
    void propertyInterpolatedDependencyShowsClearError() throws Exception {
        // POM has a dependency with property reference that PomEditor can't match
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>${some.group}</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "      <version>1.0</version>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);
        when(project.getModules()).thenReturn(Collections.emptyList());

        // Original model has the interpolated dependency
        Model originalModel = new Model();
        Dependency dep = new Dependency();
        dep.setGroupId("com.example");
        dep.setArtifactId("lib");
        dep.setVersion("1.0");
        originalModel.addDependency(dep);
        when(project.getOriginalModel()).thenReturn(originalModel);

        setVariableValueToObject(mojo, "gav", "com.example:lib");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("property references"));
    }

    @Test
    void removeNonexistentAndNotInModelGivesNotFoundError() throws Exception {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>existing</groupId>\n"
                + "      <artifactId>dep</artifactId>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);
        when(project.getModules()).thenReturn(Collections.emptyList());

        // Original model also doesn't have it
        Model originalModel = new Model();
        when(project.getOriginalModel()).thenReturn(originalModel);

        setVariableValueToObject(mojo, "gav", "nonexistent:lib");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void typeParameterUsedForMatching() throws Exception {
        // POM has both jar and pom variants
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>com.example</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "      <version>1.0</version>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>com.example</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "      <version>1.0</version>\n"
                + "      <type>test-jar</type>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);
        when(project.getModules()).thenReturn(Collections.emptyList());

        setVariableValueToObject(mojo, "gav", "com.example:lib");
        setVariableValueToObject(mojo, "type", "test-jar");

        mojo.execute();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(!result.contains("test-jar"), "test-jar variant should be removed");
        assertTrue(result.contains("com.example"), "jar variant should remain");
    }

    @Test
    void modelSyncPreservesOtherVariantsOnRemove() throws Exception {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>com.example</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "      <version>1.0</version>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>com.example</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "      <version>1.0</version>\n"
                + "      <type>test-jar</type>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);
        when(project.getModules()).thenReturn(Collections.emptyList());

        // Set up in-memory model with both variants
        Model model = new Model();
        Dependency jarDep = new Dependency();
        jarDep.setGroupId("com.example");
        jarDep.setArtifactId("lib");
        jarDep.setVersion("1.0");
        Dependency testJarDep = new Dependency();
        testJarDep.setGroupId("com.example");
        testJarDep.setArtifactId("lib");
        testJarDep.setVersion("1.0");
        testJarDep.setType("test-jar");
        model.addDependency(jarDep);
        model.addDependency(testJarDep);
        when(project.getModel()).thenReturn(model);

        // Remove only the test-jar variant
        setVariableValueToObject(mojo, "gav", "com.example:lib");
        setVariableValueToObject(mojo, "type", "test-jar");

        mojo.execute();

        // In-memory model should still have the jar variant
        assertTrue(model.getDependencies().size() == 1, "model should have 1 dependency remaining");
        assertTrue(
                "jar".equals(model.getDependencies().get(0).getType())
                        || model.getDependencies().get(0).getType() == null,
                "remaining dependency should be the jar variant");
    }

    @Test
    void profileNotFoundThrowsClearError() throws Exception {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <profiles>\n"
                + "    <profile>\n"
                + "      <id>dev</id>\n"
                + "    </profile>\n"
                + "  </profiles>\n"
                + "</project>\n";
        when(project.getFile()).thenReturn(createTempPom(pom));
        Model originalModel = new Model();
        when(project.getOriginalModel()).thenReturn(originalModel);

        setVariableValueToObject(mojo, "gav", "com.example:lib");
        setVariableValueToObject(mojo, "profile", "nonexistent");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("Profile 'nonexistent' not found"));
    }

    @Test
    void profileNotFoundWhenNoProfilesSectionThrowsClearError() throws Exception {
        String pom =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<project>\n" + "  <dependencies/>\n" + "</project>\n";
        when(project.getFile()).thenReturn(createTempPom(pom));
        Model originalModel = new Model();
        when(project.getOriginalModel()).thenReturn(originalModel);

        setVariableValueToObject(mojo, "gav", "com.example:lib");
        setVariableValueToObject(mojo, "profile", "dev");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("Profile 'dev' not found"));
    }

    @Test
    void removeDependencyFromProfileSucceeds() throws Exception {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <profiles>\n"
                + "    <profile>\n"
                + "      <id>dev</id>\n"
                + "      <dependencies>\n"
                + "        <dependency>\n"
                + "          <groupId>com.example</groupId>\n"
                + "          <artifactId>lib</artifactId>\n"
                + "        </dependency>\n"
                + "      </dependencies>\n"
                + "    </profile>\n"
                + "  </profiles>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);
        Model originalModel = new Model();
        when(project.getOriginalModel()).thenReturn(originalModel);

        setVariableValueToObject(mojo, "gav", "com.example:lib");
        setVariableValueToObject(mojo, "profile", "dev");

        mojo.execute();

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(!result.contains("<groupId>com.example</groupId>"), "dependency should be removed");
        assertTrue(result.contains("<id>dev</id>"), "profile should remain");
    }

    @Test
    void managedRemovalWithChildModulesWarnsAndProceeds() throws Exception {
        // Parent POM with managed dependency
        String parentPom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <modules>\n"
                + "    <module>child-a</module>\n"
                + "  </modules>\n"
                + "  <dependencyManagement>\n"
                + "    <dependencies>\n"
                + "      <dependency>\n"
                + "        <groupId>com.example</groupId>\n"
                + "        <artifactId>lib</artifactId>\n"
                + "        <version>1.0</version>\n"
                + "      </dependency>\n"
                + "    </dependencies>\n"
                + "  </dependencyManagement>\n"
                + "</project>\n";
        File pomFile = createTempPom(parentPom);
        when(project.getFile()).thenReturn(pomFile);
        when(project.getBasedir()).thenReturn(tempDir);
        when(project.getModules()).thenReturn(Arrays.asList("child-a"));

        // Child module POM that references the dependency without a version
        File childDir = new File(tempDir, "child-a");
        childDir.mkdirs();
        String childPom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <parent>\n"
                + "    <groupId>com.example</groupId>\n"
                + "    <artifactId>parent</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "  </parent>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>com.example</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        Files.write(new File(childDir, "pom.xml").toPath(), childPom.getBytes(StandardCharsets.UTF_8));

        setVariableValueToObject(mojo, "gav", "com.example:lib");
        setVariableValueToObject(mojo, "managed", true);

        // Should succeed (warning only, not blocking) — the dependency gets removed
        assertDoesNotThrow(() -> mojo.execute());

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(!result.contains("<artifactId>lib</artifactId>"), "managed dep should be removed");
    }

    @Test
    void missingGavFails() throws Exception {
        when(project.getFile()).thenReturn(createTempPom("<project></project>"));

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("You must specify -Dgav=groupId:artifactId"));
    }

    @Test
    void malformedGavFails() throws Exception {
        when(project.getFile()).thenReturn(createTempPom("<project></project>"));

        setVariableValueToObject(mojo, "gav", "only-one-part");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("GAV"));
    }

    @Test
    void removeFromManagedSectionNotFound() throws Exception {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencyManagement>\n"
                + "    <dependencies>\n"
                + "      <dependency>\n"
                + "        <groupId>other</groupId>\n"
                + "        <artifactId>dep</artifactId>\n"
                + "        <version>1.0</version>\n"
                + "      </dependency>\n"
                + "    </dependencies>\n"
                + "  </dependencyManagement>\n"
                + "</project>\n";
        when(project.getFile()).thenReturn(createTempPom(pom));
        when(project.getModules()).thenReturn(Collections.emptyList());
        Model originalModel = new Model();
        when(project.getOriginalModel()).thenReturn(originalModel);

        setVariableValueToObject(mojo, "gav", "nonexistent:lib");
        setVariableValueToObject(mojo, "managed", true);

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("not found"));
        assertTrue(ex.getMessage().contains("<dependencyManagement>"), "error should mention correct section");
    }
}
