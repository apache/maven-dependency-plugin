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
import java.util.Collections;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
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

class AddDependencyMojoTest {

    @TempDir
    File tempDir;

    private MavenSession session;
    private BuildContext buildContext;
    private MavenProject project;
    private AddDependencyMojo mojo;

    @BeforeEach
    void setUp() throws Exception {
        session = mock(MavenSession.class);
        buildContext = mock(BuildContext.class);
        project = mock(MavenProject.class);
        when(buildContext.isIncremental()).thenReturn(false);

        mojo = new AddDependencyMojo(session, buildContext, project);
    }

    private File createTempPom(String content) throws IOException {
        File pomFile = new File(tempDir, "pom.xml");
        Files.write(pomFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return pomFile;
    }

    @Test
    void moduleWithNullReactorProjectsThrowsClearError() throws Exception {
        when(session.getProjects()).thenReturn(null);
        when(project.getFile()).thenReturn(createTempPom("<project></project>"));

        setVariableValueToObject(mojo, "module", "some-module");
        setVariableValueToObject(mojo, "groupId", "com.example");
        setVariableValueToObject(mojo, "artifactId", "lib");
        setVariableValueToObject(mojo, "version", "1.0");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("no reactor projects available"));
    }

    @Test
    void moduleWithEmptyReactorProjectsThrowsClearError() throws Exception {
        when(session.getProjects()).thenReturn(Collections.emptyList());
        when(project.getFile()).thenReturn(createTempPom("<project></project>"));

        setVariableValueToObject(mojo, "module", "some-module");
        setVariableValueToObject(mojo, "groupId", "com.example");
        setVariableValueToObject(mojo, "artifactId", "lib");
        setVariableValueToObject(mojo, "version", "1.0");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("no reactor projects available"));
    }

    @Test
    void propertyInterpolatedDependencyBlocksAdd() throws Exception {
        // POM has a dependency using a property reference that PomEditor can't match literally
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>${some.property}</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "      <version>1.0</version>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);

        // Original model returns the interpolated dependency
        Model originalModel = new Model();
        Dependency dep = new Dependency();
        dep.setGroupId("com.example");
        dep.setArtifactId("lib");
        dep.setVersion("1.0");
        originalModel.addDependency(dep);
        when(project.getOriginalModel()).thenReturn(originalModel);
        when(project.getModules()).thenReturn(Collections.emptyList());

        setVariableValueToObject(mojo, "groupId", "com.example");
        setVariableValueToObject(mojo, "artifactId", "lib");
        setVariableValueToObject(mojo, "version", "2.0");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("property references"));
    }

    @Test
    void inheritedDependencyDoesNotBlockAddToChild() throws Exception {
        // Child POM has no dependencies declared
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <groupId>com.example</groupId>\n"
                + "  <artifactId>child</artifactId>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);

        // Original model has NO dependencies (the dependency is inherited, not declared)
        Model originalModel = new Model();
        when(project.getOriginalModel()).thenReturn(originalModel);
        when(project.getModules()).thenReturn(Collections.emptyList());

        setVariableValueToObject(mojo, "groupId", "junit");
        setVariableValueToObject(mojo, "artifactId", "junit");
        setVariableValueToObject(mojo, "version", "4.13");

        // Should succeed — inherited dep should not block adding to child
        assertDoesNotThrow(() -> mojo.execute());

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<groupId>junit</groupId>"));
    }

    @Test
    void propertyInterpolatedDependencyBlocksEvenWithUpdateExisting() throws Exception {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>${my.group}</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);

        Model originalModel = new Model();
        Dependency dep = new Dependency();
        dep.setGroupId("com.example");
        dep.setArtifactId("lib");
        originalModel.addDependency(dep);
        when(project.getOriginalModel()).thenReturn(originalModel);
        when(project.getModules()).thenReturn(Collections.emptyList());

        setVariableValueToObject(mojo, "groupId", "com.example");
        setVariableValueToObject(mojo, "artifactId", "lib");
        setVariableValueToObject(mojo, "version", "1.0");
        setVariableValueToObject(mojo, "updateExisting", true);

        // Should still block — no duplicate allowed even with updateExisting
        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("property references"));
    }

    @Test
    void managedDependencyOriginalModelCrossReference() throws Exception {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencyManagement>\n"
                + "    <dependencies>\n"
                + "      <dependency>\n"
                + "        <groupId>${parent.group}</groupId>\n"
                + "        <artifactId>managed-lib</artifactId>\n"
                + "        <version>1.0</version>\n"
                + "      </dependency>\n"
                + "    </dependencies>\n"
                + "  </dependencyManagement>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);

        Model originalModel = new Model();
        DependencyManagement depMgmt = new DependencyManagement();
        Dependency dep = new Dependency();
        dep.setGroupId("com.example");
        dep.setArtifactId("managed-lib");
        dep.setVersion("1.0");
        depMgmt.addDependency(dep);
        originalModel.setDependencyManagement(depMgmt);
        when(project.getOriginalModel()).thenReturn(originalModel);
        when(project.getModules()).thenReturn(Collections.emptyList());

        setVariableValueToObject(mojo, "groupId", "com.example");
        setVariableValueToObject(mojo, "artifactId", "managed-lib");
        setVariableValueToObject(mojo, "version", "2.0");
        setVariableValueToObject(mojo, "managed", true);

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("property references"));
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

        setVariableValueToObject(mojo, "groupId", "com.example");
        setVariableValueToObject(mojo, "artifactId", "lib");
        setVariableValueToObject(mojo, "version", "1.0");
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

        setVariableValueToObject(mojo, "groupId", "com.example");
        setVariableValueToObject(mojo, "artifactId", "lib");
        setVariableValueToObject(mojo, "version", "1.0");
        setVariableValueToObject(mojo, "profile", "dev");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("Profile 'dev' not found"));
    }

    @Test
    void addDependencyToProfileSucceeds() throws Exception {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <profiles>\n"
                + "    <profile>\n"
                + "      <id>dev</id>\n"
                + "    </profile>\n"
                + "  </profiles>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);
        Model originalModel = new Model();
        when(project.getOriginalModel()).thenReturn(originalModel);

        setVariableValueToObject(mojo, "groupId", "com.example");
        setVariableValueToObject(mojo, "artifactId", "lib");
        setVariableValueToObject(mojo, "version", "1.0");
        setVariableValueToObject(mojo, "profile", "dev");

        assertDoesNotThrow(() -> mojo.execute());

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<groupId>com.example</groupId>"));
        assertTrue(result.contains("<id>dev</id>"));
    }
}
