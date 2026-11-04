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
package org.apache.maven.plugins.dependency.analyze;

import javax.inject.Inject;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.api.di.Provides;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.DependencyUsage;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests how the {@code ignore*} parameters of {@code dependency:analyze} filter an analysis result. Each case feeds
 * the mojo the result the analyzer reports for the matching former integration test, with {@code failOnWarning}
 * set, so any dependency the parameters fail to ignore fails the build.
 */
@ExtendWith(MockitoExtension.class)
@MojoTest
class AnalyzeMojoIgnoreTest {

    private static final Artifact MAVEN_PROJECT = artifact("maven-project", "2.0.6", Artifact.SCOPE_COMPILE);

    private static final Artifact MAVEN_PROJECT_RUNTIME = artifact("maven-project", "2.0.6", Artifact.SCOPE_RUNTIME);

    private static final Artifact MAVEN_ARTIFACT_RUNTIME = artifact("maven-artifact", "2.0.6", Artifact.SCOPE_RUNTIME);

    private static final Artifact MAVEN_MODEL = artifact("maven-model", "2.0.6", Artifact.SCOPE_COMPILE);

    private static final Artifact MAVEN_SETTINGS = artifact("maven-settings", "2.0.6", Artifact.SCOPE_COMPILE);

    private static final Artifact MAVEN_CORE_3 = artifact("maven-core", "3.9.16", Artifact.SCOPE_COMPILE);

    private static final Artifact MAVEN_MODEL_3 = artifact("maven-model", "3.9.16", Artifact.SCOPE_COMPILE);

    private static final Artifact MAVEN_SETTINGS_3 = artifact("maven-settings", "3.9.16", Artifact.SCOPE_COMPILE);

    @TempDir
    private File outputDirectory;

    @Inject
    private MavenProject project;

    @Inject
    private Log log;

    @Mock
    private ProjectDependencyAnalyzer analyzer;

    @Provides
    private ProjectDependencyAnalyzer analyzerProvides() {
        return analyzer;
    }

    @BeforeEach
    void setUp() {
        lenient().when(project.getPackaging()).thenReturn("jar");
        lenient().when(project.getGroupId()).thenReturn("org.example");
        lenient().when(project.getArtifactId()).thenReturn("module-a");
    }

    @Test
    @InjectMojo(goal = "analyze")
    @MojoParameter(name = "failOnWarning", value = "true")
    @MojoParameter(name = "verbose", value = "true")
    @MojoParameter(
            name = "ignoredDependencies",
            value = "org.apache.maven:maven-project,org.apache.maven:maven-model,org.apache.maven:maven-settings")
    void ignoredDependenciesCoverUsedUndeclaredAndUnusedDeclared(AnalyzeMojo mojo) throws Exception {
        analysis(
                mojo,
                Collections.emptySet(),
                set(MAVEN_SETTINGS, MAVEN_MODEL),
                set(MAVEN_PROJECT),
                Collections.emptySet());

        mojo.execute();

        InOrder order = inOrder(log);
        order.verify(log).info("Ignored used undeclared dependencies:");
        order.verify(log).info("   " + MAVEN_SETTINGS);
        order.verify(log).info("   " + MAVEN_MODEL);
        order.verify(log).info("Ignored unused declared dependencies:");
        order.verify(log).info("   " + MAVEN_PROJECT);
    }

    @Test
    @InjectMojo(goal = "analyze")
    @MojoParameter(name = "failOnWarning", value = "true")
    @MojoParameter(name = "verbose", value = "true")
    @MojoParameter(name = "ignoredUnusedDeclaredDependencies", value = "org.apache.maven:maven-project")
    void ignoredUnusedDeclaredDependencies(AnalyzeMojo mojo) throws Exception {
        analysis(mojo, Collections.emptySet(), Collections.emptySet(), set(MAVEN_PROJECT), Collections.emptySet());

        mojo.execute();

        InOrder order = inOrder(log);
        order.verify(log).info("Ignored unused declared dependencies:");
        order.verify(log).info("   " + MAVEN_PROJECT);
    }

    @Test
    @InjectMojo(goal = "analyze")
    @MojoParameter(name = "failOnWarning", value = "true")
    @MojoParameter(name = "verbose", value = "true")
    @MojoParameter(name = "ignoredUsedUndeclaredDependencies", value = "org.apache.maven:maven-model")
    void ignoredUsedUndeclaredDependencies(AnalyzeMojo mojo) throws Exception {
        analysis(mojo, set(MAVEN_CORE_3), set(MAVEN_MODEL_3), Collections.emptySet(), Collections.emptySet());

        mojo.execute();

        InOrder order = inOrder(log);
        order.verify(log).info("Used declared dependencies found:");
        order.verify(log).info("   " + MAVEN_CORE_3);
        order.verify(log).info("Ignored used undeclared dependencies:");
        order.verify(log).info("   " + MAVEN_MODEL_3);
    }

    @Test
    @InjectMojo(goal = "analyze")
    @MojoParameter(name = "failOnWarning", value = "true")
    @MojoParameter(name = "verbose", value = "true")
    @MojoParameter(name = "ignoredNonTestScopedDependencies", value = "org.apache.maven:maven-settings")
    void ignoredNonTestScopedDependencies(AnalyzeMojo mojo) throws Exception {
        analysis(mojo, set(MAVEN_SETTINGS_3), Collections.emptySet(), Collections.emptySet(), set(MAVEN_SETTINGS_3));

        mojo.execute();

        InOrder order = inOrder(log);
        order.verify(log).info("Ignored non-test scoped test only dependencies:");
        order.verify(log).info("   " + MAVEN_SETTINGS_3);
    }

    @Test
    @InjectMojo(goal = "analyze")
    @MojoParameter(name = "failOnWarning", value = "true")
    @MojoParameter(name = "verbose", value = "true")
    @MojoParameter(name = "ignoreAllNonTestScoped", value = "true")
    void ignoreAllNonTestScoped(AnalyzeMojo mojo) throws Exception {
        analysis(mojo, set(MAVEN_SETTINGS_3), Collections.emptySet(), Collections.emptySet(), set(MAVEN_SETTINGS_3));

        mojo.execute();

        InOrder order = inOrder(log);
        order.verify(log).info("Ignored non-test scoped test only dependencies:");
        order.verify(log).info("   " + MAVEN_SETTINGS_3);
    }

    @Test
    @InjectMojo(goal = "analyze")
    @MojoParameter(name = "failOnWarning", value = "true")
    @MojoParameter(name = "verbose", value = "true")
    @MojoParameter(name = "ignoreUnusedRuntime", value = "true")
    void ignoreUnusedRuntime(AnalyzeMojo mojo) throws Exception {
        analysis(
                mojo,
                Collections.emptySet(),
                Collections.emptySet(),
                set(MAVEN_PROJECT_RUNTIME),
                Collections.emptySet());

        mojo.execute();

        verify(log).info("No dependency problems found");
        verify(log, never()).error(any(CharSequence.class));
    }

    @Test
    @InjectMojo(goal = "analyze")
    @MojoParameter(name = "failOnWarning", value = "true")
    void unusedRuntimeDependencyFailsWithoutIgnoreUnusedRuntime(AnalyzeMojo mojo) throws Exception {
        analysis(
                mojo,
                Collections.emptySet(),
                Collections.emptySet(),
                set(MAVEN_PROJECT_RUNTIME),
                Collections.emptySet());

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessage("Dependency problems found");
        verify(log).error("Unused declared dependencies found: (in org.example:module-a)");
        verify(log).error("   " + MAVEN_PROJECT_RUNTIME);
    }

    @Test
    @InjectMojo(goal = "analyze")
    @MojoParameter(name = "failOnWarning", value = "true")
    @MojoParameter(name = "verbose", value = "true")
    @MojoParameter(name = "ignoredUnusedDeclaredDependencies", value = "org.apache.maven:::*:runtime")
    void ignoredUnusedDeclaredDependenciesMatchByScope(AnalyzeMojo mojo) throws Exception {
        analysis(
                mojo,
                Collections.emptySet(),
                Collections.emptySet(),
                set(MAVEN_PROJECT, MAVEN_ARTIFACT_RUNTIME),
                Collections.emptySet());

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessage("Dependency problems found");

        verify(log).error("Unused declared dependencies found: (in org.example:module-a)");
        verify(log).error("   " + MAVEN_PROJECT);
        verify(log, never()).error("   " + MAVEN_ARTIFACT_RUNTIME);
        verify(log).info("Ignored unused declared dependencies:");
        verify(log).info("   " + MAVEN_ARTIFACT_RUNTIME);
    }

    @Test
    @InjectMojo(goal = "analyze")
    void packagingsPomAndEarAreIgnoredByDefault(AnalyzeMojo mojo) throws Exception {
        for (String packaging : new String[] {"pom", "ear"}) {
            when(project.getPackaging()).thenReturn(packaging);

            mojo.execute();

            verify(log).info("Skipping " + packaging + " project");
        }
        verifyNoInteractions(analyzer);
    }

    @Test
    @InjectMojo(goal = "analyze")
    void otherPackagingsAreAnalyzedByDefault(AnalyzeMojo mojo) throws Exception {
        when(project.getPackaging()).thenReturn("ejb");
        analysis(mojo, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

        mojo.execute();

        verify(log, never()).info("Skipping ejb project");
        verify(analyzer).analyze(any(), any());
    }

    @Test
    @InjectMojo(goal = "analyze")
    @MojoParameter(name = "failOnWarning", value = "true")
    @MojoParameter(name = "ignoredPackagings", value = "pom,ear,ejb")
    void ignoredPackagingsCanBeConfigured(AnalyzeMojo mojo) throws Exception {
        for (String packaging : new String[] {"pom", "ear", "ejb"}) {
            when(project.getPackaging()).thenReturn(packaging);

            mojo.execute();

            verify(log).info("Skipping " + packaging + " project");
        }
        verifyNoInteractions(analyzer);
    }

    @Test
    @InjectMojo(goal = "analyze")
    @MojoParameter(name = "verbose", value = "true")
    void verboseDoesNotReportIgnoredSectionsWithoutIgnores(AnalyzeMojo mojo) throws Exception {
        Artifact commonsLang = artifact("org.apache.commons", "commons-lang3", "3.12.0", Artifact.SCOPE_COMPILE);
        Artifact commonsText = artifact("org.apache.commons", "commons-text", "1.10.0", Artifact.SCOPE_COMPILE);
        usages(
                mojo,
                Collections.singletonMap(commonsLang, usedBy("org.apache.commons.lang3.StringUtils")),
                commonsText);

        mojo.execute();

        InOrder order = inOrder(log);
        order.verify(log).warn("Used undeclared dependencies found: (in org.example:module-a)");
        order.verify(log).warn("   " + commonsLang);
        order.verify(log).warn("      class org.apache.commons.lang3.StringUtils");
        order.verify(log).warn("Unused declared dependencies found: (in org.example:module-a)");
        order.verify(log).warn("   " + commonsText);
        verify(log, never()).info("Ignored used undeclared dependencies:");
        verify(log, never()).info("Ignored unused declared dependencies:");
    }

    @Test
    @InjectMojo(goal = "analyze-only")
    @MojoParameter(name = "verbose", value = "true")
    void analyzeOnlyVerboseShowsTheClassesOfUsedUndeclaredDependencies(AnalyzeOnlyMojo mojo) throws Exception {
        Artifact commonsIo = artifact("commons-io", "commons-io", "2.11.0", Artifact.SCOPE_COMPILE);
        Artifact sharedUtils =
                artifact("org.apache.maven.shared", "maven-shared-utils", "3.4.2", Artifact.SCOPE_COMPILE);
        usages(mojo, Collections.singletonMap(commonsIo, usedBy("org.apache.commons.io.IOUtils")), sharedUtils);

        mojo.execute();

        InOrder order = inOrder(log);
        order.verify(log).warn("Used undeclared dependencies found: (in org.example:module-a)");
        order.verify(log).warn("   " + commonsIo);
        order.verify(log).warn("      class org.apache.commons.io.IOUtils");
        order.verify(log).warn("Unused declared dependencies found: (in org.example:module-a)");
        order.verify(log).warn("   " + sharedUtils);
    }

    @Test
    @InjectMojo(goal = "analyze")
    @MojoParameter(name = "scriptableOutput", value = "true")
    @MojoParameter(name = "scriptableFlag", value = "TESTFLAG")
    void scriptableOutputHasNoNullClassifier(AnalyzeMojo mojo) throws Exception {
        Artifact plain = artifact("maven-model", "2.0.6", Artifact.SCOPE_COMPILE);
        Artifact tests = new DefaultArtifact(
                "org.apache.maven",
                "maven-core",
                "2.0.6",
                Artifact.SCOPE_TEST,
                "test-jar",
                "tests",
                new DefaultArtifactHandler("test-jar"));
        setVariableValueToObject(mojo, "baseDir", outputDirectory);
        usages(mojo, mapOf(plain, usedBy("org.apache.maven.model.Model"), tests, usedBy("T")));

        mojo.execute();

        ArgumentCaptor<CharSequence> info = ArgumentCaptor.forClass(CharSequence.class);
        verify(log, atLeastOnce()).info(info.capture());
        assertThat(info.getAllValues()).anyMatch(m -> m.toString().startsWith("Missing dependencies: "));
        String pom = new File(outputDirectory, "pom.xml").getAbsolutePath();
        List<String> flagged = info.getAllValues().stream()
                .flatMap(m -> Arrays.stream(m.toString().split("\\R")))
                .filter(line -> line.startsWith("TESTFLAG:"))
                .collect(Collectors.toList());
        assertThat(flagged)
                .containsExactlyInAnyOrder(
                        "TESTFLAG:" + pom + ":org.apache.maven:maven-model:jar::2.0.6:compile",
                        "TESTFLAG:" + pom + ":org.apache.maven:maven-core:test-jar:tests:tests:2.0.6:test");
    }

    private static Set<DependencyUsage> usedBy(String clazz) {
        return Collections.singleton(new DependencyUsage(clazz, "usedBy"));
    }

    private static Map<Artifact, Set<DependencyUsage>> mapOf(
            Artifact first, Set<DependencyUsage> firstUsage, Artifact second, Set<DependencyUsage> secondUsage) {
        Map<Artifact, Set<DependencyUsage>> map = new LinkedHashMap<>();
        map.put(first, firstUsage);
        map.put(second, secondUsage);
        return map;
    }

    /** An analysis whose used undeclared dependencies carry the classes that reference them. */
    private void usages(Mojo mojo, Map<Artifact, Set<DependencyUsage>> usedUndeclared, Artifact... unusedDeclared)
            throws Exception {
        setVariableValueToObject(mojo, "outputDirectory", outputDirectory);
        when(analyzer.analyze(any(), any()))
                .thenReturn(new ProjectDependencyAnalysis(
                        Collections.emptyMap(),
                        usedUndeclared,
                        new LinkedHashSet<>(Arrays.asList(unusedDeclared)),
                        Collections.emptySet()));
    }

    private void analysis(
            AnalyzeMojo mojo,
            Set<Artifact> usedDeclared,
            Set<Artifact> usedUndeclared,
            Set<Artifact> unusedDeclared,
            Set<Artifact> testArtifactsWithNonTestScope)
            throws Exception {
        setVariableValueToObject(mojo, "outputDirectory", outputDirectory);
        when(analyzer.analyze(any(), any()))
                .thenReturn(new ProjectDependencyAnalysis(
                        usedDeclared, usedUndeclared, unusedDeclared, testArtifactsWithNonTestScope));
    }

    private static Set<Artifact> set(Artifact... artifacts) {
        return new LinkedHashSet<>(Arrays.asList(artifacts));
    }

    private static Artifact artifact(String artifactId, String version, String scope) {
        return artifact("org.apache.maven", artifactId, version, scope);
    }

    private static Artifact artifact(String groupId, String artifactId, String version, String scope) {
        return new DefaultArtifact(groupId, artifactId, version, scope, "jar", null, new DefaultArtifactHandler("jar"));
    }
}
