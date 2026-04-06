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
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.dependency.pom.DependencyEntry;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        setVariableValueToObject(mojo, "gav", "com.example:lib:2.0");

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

        setVariableValueToObject(mojo, "gav", "junit:junit:4.13");

        // Should succeed — inherited dep should not block adding to child
        assertDoesNotThrow(() -> mojo.execute());

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<groupId>junit</groupId>"));
    }

    @Test
    void propertyInterpolatedDependencyBlocksUpdate() throws Exception {
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

        setVariableValueToObject(mojo, "gav", "com.example:lib:1.0");

        // Should still block — property-interpolated deps cannot be safely updated
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

        setVariableValueToObject(mojo, "gav", "com.example:managed-lib:2.0");
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

        setVariableValueToObject(mojo, "gav", "com.example:lib:1.0");
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

        setVariableValueToObject(mojo, "gav", "com.example:lib:1.0");
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

        setVariableValueToObject(mojo, "gav", "com.example:lib:1.0");
        setVariableValueToObject(mojo, "profile", "dev");

        assertDoesNotThrow(() -> mojo.execute());

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<groupId>com.example</groupId>"));
        assertTrue(result.contains("<id>dev</id>"));
    }

    @Test
    void basicAddWithGavShorthand() throws Exception {
        String pom =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<project>\n" + "  <dependencies/>\n" + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);
        Model originalModel = new Model();
        when(project.getOriginalModel()).thenReturn(originalModel);

        setVariableValueToObject(mojo, "gav", "com.example:lib:1.0");
        setVariableValueToObject(mojo, "scope", "test");

        assertDoesNotThrow(() -> mojo.execute());

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<groupId>com.example</groupId>"), result);
        assertTrue(result.contains("<artifactId>lib</artifactId>"), result);
        assertTrue(result.contains("<version>1.0</version>"), result);
        assertTrue(result.contains("<scope>test</scope>"), result);
    }

    @Test
    void duplicateDependencyFailsWithError() throws Exception {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>com.example</groupId>\n"
                + "      <artifactId>lib</artifactId>\n"
                + "      <version>1.0</version>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);
        Model originalModel = new Model();
        Dependency d = new Dependency();
        d.setGroupId("com.example");
        d.setArtifactId("lib");
        originalModel.addDependency(d);
        when(project.getOriginalModel()).thenReturn(originalModel);

        setVariableValueToObject(mojo, "gav", "com.example:lib:2.0");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
    }

    @Test
    void managedWithoutVersionFails() throws Exception {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<project>\n" + "</project>\n";
        when(project.getFile()).thenReturn(createTempPom(pom));
        Model originalModel = new Model();
        when(project.getOriginalModel()).thenReturn(originalModel);

        setVariableValueToObject(mojo, "gav", "com.example:lib");
        setVariableValueToObject(mojo, "managed", true);

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("Version is required"));
    }

    @Test
    void versionInferredFromParentDependencyManagement() throws Exception {
        String pom =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<project>\n" + "  <dependencies/>\n" + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);
        Model originalModel = new Model();
        when(project.getOriginalModel()).thenReturn(originalModel);
        when(project.getModules()).thenReturn(Collections.emptyList());

        // Parent has the dependency managed
        MavenProject parentProject = mock(MavenProject.class);
        DependencyManagement parentDepMgmt = new DependencyManagement();
        Dependency managed = new Dependency();
        managed.setGroupId("com.example");
        managed.setArtifactId("lib");
        managed.setVersion("3.0.0");
        parentDepMgmt.addDependency(managed);
        when(parentProject.getDependencyManagement()).thenReturn(parentDepMgmt);
        when(parentProject.getParent()).thenReturn(null);
        when(project.getParent()).thenReturn(parentProject);
        when(project.getDependencyManagement()).thenReturn(null);

        // No version provided — should be inferred
        setVariableValueToObject(mojo, "gav", "com.example:lib");

        assertDoesNotThrow(() -> mojo.execute());

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<groupId>com.example</groupId>"));
        assertTrue(result.contains("<artifactId>lib</artifactId>"));
        // Version should NOT be in the POM — it's managed by parent
        assertFalse(result.contains("<version>"), "version should be omitted for managed deps");
    }

    @Test
    void parentPomAddWarnsAboutInheritance() throws Exception {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project>\n"
                + "  <modules>\n"
                + "    <module>child-a</module>\n"
                + "  </modules>\n"
                + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);
        Model originalModel = new Model();
        when(project.getOriginalModel()).thenReturn(originalModel);
        when(project.getModules()).thenReturn(Arrays.asList("child-a"));

        setVariableValueToObject(mojo, "gav", "com.example:lib:1.0");
        assertDoesNotThrow(() -> mojo.execute());

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<groupId>com.example</groupId>"));
    }

    @Test
    void missingGavFails() throws Exception {
        when(project.getFile()).thenReturn(createTempPom("<project></project>"));

        // No gav set
        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("You must specify -Dgav=groupId:artifactId[:version]"));
    }

    @Test
    void malformedGavFails() throws Exception {
        when(project.getFile()).thenReturn(createTempPom("<project></project>"));

        setVariableValueToObject(mojo, "gav", "only-one-part");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("GAV"));
    }

    @Test
    void explicitParamsOverrideGav() throws Exception {
        String pom =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<project>\n" + "  <dependencies/>\n" + "</project>\n";
        File pomFile = createTempPom(pom);
        when(project.getFile()).thenReturn(pomFile);
        Model originalModel = new Model();
        when(project.getOriginalModel()).thenReturn(originalModel);
        when(project.getModules()).thenReturn(Collections.emptyList());

        // GAV has version 1.0, explicit -Dscope overrides
        setVariableValueToObject(mojo, "gav", "com.example:lib:1.0");
        setVariableValueToObject(mojo, "scope", "test");

        assertDoesNotThrow(() -> mojo.execute());

        String result = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(result.contains("<version>1.0</version>"), "version from gav should be used");
        assertTrue(result.contains("<scope>test</scope>"), "explicit -Dscope should override");
    }

    // --- Convention detection unit tests ---

    @Nested
    class DetectPropertyPatternTest {

        @Test
        void detectsDotVersionSuffix() {
            List<String> versions = Arrays.asList("${guava.version}", "${junit.version}", "${slf4j.version}");
            assertEquals(
                    AddDependencyMojo.PropertyPattern.DOT_VERSION, AddDependencyMojo.detectPropertyPattern(versions));
        }

        @Test
        void detectsDashVersionSuffix() {
            List<String> versions = Arrays.asList("${guava-version}", "${junit-version}", "${slf4j-version}");
            assertEquals(
                    AddDependencyMojo.PropertyPattern.DASH_VERSION, AddDependencyMojo.detectPropertyPattern(versions));
        }

        @Test
        void detectsCamelCaseVersionSuffix() {
            List<String> versions = Arrays.asList("${guavaVersion}", "${junitVersion}", "${slf4jVersion}");
            assertEquals(
                    AddDependencyMojo.PropertyPattern.CAMEL_VERSION, AddDependencyMojo.detectPropertyPattern(versions));
        }

        @Test
        void detectsVersionDotPrefix() {
            List<String> versions = Arrays.asList("${version.guava}", "${version.junit}", "${version.slf4j}");
            assertEquals(
                    AddDependencyMojo.PropertyPattern.VERSION_PREFIX,
                    AddDependencyMojo.detectPropertyPattern(versions));
        }

        @Test
        void returnsNullForEmptyList() {
            assertNull(AddDependencyMojo.detectPropertyPattern(Collections.emptyList()));
        }

        @Test
        void returnsNullForLiteralVersionsOnly() {
            List<String> versions = Arrays.asList("1.0.0", "2.3.4", "5.0");
            assertNull(AddDependencyMojo.detectPropertyPattern(versions));
        }

        @Test
        void returnsNullForUnrecognizedPropertyPattern() {
            List<String> versions = Arrays.asList("${custom_ver}", "${another_ver}");
            assertNull(AddDependencyMojo.detectPropertyPattern(versions));
        }

        @Test
        void majorityWinsWithMixedPatterns() {
            List<String> versions =
                    Arrays.asList("${guava.version}", "${junit.version}", "${slf4j.version}", "${commons-version}");
            assertEquals(
                    AddDependencyMojo.PropertyPattern.DOT_VERSION, AddDependencyMojo.detectPropertyPattern(versions));
        }

        @Test
        void handlesSinglePropertyRef() {
            List<String> versions = Collections.singletonList("${guava-version}");
            assertEquals(
                    AddDependencyMojo.PropertyPattern.DASH_VERSION, AddDependencyMojo.detectPropertyPattern(versions));
        }

        @Test
        void ignoresNonPropertyVersionsInMix() {
            List<String> versions = Arrays.asList("1.0.0", "${guava.version}", "2.3.4", "${junit.version}");
            assertEquals(
                    AddDependencyMojo.PropertyPattern.DOT_VERSION, AddDependencyMojo.detectPropertyPattern(versions));
        }
    }

    @Nested
    class ConventionsTest {

        @Test
        void derivePropertyNameWithDotVersionPattern() {
            AddDependencyMojo.Conventions conv = new AddDependencyMojo.Conventions();
            conv.pattern = AddDependencyMojo.PropertyPattern.DOT_VERSION;
            DependencyEntry coords = new DependencyEntry("com.google.guava", "guava");
            assertEquals("guava.version", conv.derivePropertyName(coords));
        }

        @Test
        void derivePropertyNameWithDashVersionPattern() {
            AddDependencyMojo.Conventions conv = new AddDependencyMojo.Conventions();
            conv.pattern = AddDependencyMojo.PropertyPattern.DASH_VERSION;
            DependencyEntry coords = new DependencyEntry("com.google.guava", "guava");
            assertEquals("guava-version", conv.derivePropertyName(coords));
        }

        @Test
        void derivePropertyNameWithCamelVersionPattern() {
            AddDependencyMojo.Conventions conv = new AddDependencyMojo.Conventions();
            conv.pattern = AddDependencyMojo.PropertyPattern.CAMEL_VERSION;
            DependencyEntry coords = new DependencyEntry("com.google.guava", "guava");
            assertEquals("guavaVersion", conv.derivePropertyName(coords));
        }

        @Test
        void derivePropertyNameWithVersionPrefixPattern() {
            AddDependencyMojo.Conventions conv = new AddDependencyMojo.Conventions();
            conv.pattern = AddDependencyMojo.PropertyPattern.VERSION_PREFIX;
            DependencyEntry coords = new DependencyEntry("com.google.guava", "guava");
            assertEquals("version.guava", conv.derivePropertyName(coords));
        }

        @Test
        void derivePropertyNameDefaultsWhenPatternIsNull() {
            AddDependencyMojo.Conventions conv = new AddDependencyMojo.Conventions();
            conv.pattern = null;
            DependencyEntry coords = new DependencyEntry("com.google.guava", "guava");
            assertEquals("guava.version", conv.derivePropertyName(coords));
        }

        @Test
        void defaultConventionsAreAllFalse() {
            AddDependencyMojo.Conventions conv = new AddDependencyMojo.Conventions();
            assertFalse(conv.useManaged);
            assertFalse(conv.useProperty);
            assertNull(conv.pattern);
            assertNull(conv.managedPomFile);
        }
    }

    @Nested
    class PropertyPatternTest {

        @Test
        void dotVersionMatchesCorrectly() {
            assertTrue(AddDependencyMojo.PropertyPattern.DOT_VERSION.matches("guava.version"));
            assertFalse(AddDependencyMojo.PropertyPattern.DOT_VERSION.matches("guava-version"));
            assertFalse(AddDependencyMojo.PropertyPattern.DOT_VERSION.matches("version.guava"));
        }

        @Test
        void dashVersionMatchesCorrectly() {
            assertTrue(AddDependencyMojo.PropertyPattern.DASH_VERSION.matches("guava-version"));
            assertFalse(AddDependencyMojo.PropertyPattern.DASH_VERSION.matches("guava.version"));
        }

        @Test
        void camelVersionMatchesCorrectly() {
            assertTrue(AddDependencyMojo.PropertyPattern.CAMEL_VERSION.matches("guavaVersion"));
            assertFalse(AddDependencyMojo.PropertyPattern.CAMEL_VERSION.matches("guava.version"));
        }

        @Test
        void versionPrefixMatchesCorrectly() {
            assertTrue(AddDependencyMojo.PropertyPattern.VERSION_PREFIX.matches("version.guava"));
            assertFalse(AddDependencyMojo.PropertyPattern.VERSION_PREFIX.matches("guava.version"));
        }

        @Test
        void toPropertyNameProducesExpectedResults() {
            assertEquals("guava.version", AddDependencyMojo.PropertyPattern.DOT_VERSION.toPropertyName("guava"));
            assertEquals("guava-version", AddDependencyMojo.PropertyPattern.DASH_VERSION.toPropertyName("guava"));
            assertEquals("guavaVersion", AddDependencyMojo.PropertyPattern.CAMEL_VERSION.toPropertyName("guava"));
            assertEquals("version.guava", AddDependencyMojo.PropertyPattern.VERSION_PREFIX.toPropertyName("guava"));
        }
    }
}
