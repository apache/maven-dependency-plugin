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
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.api.di.Provides;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        verify(log).error("Unused declared dependencies found:");
        verify(log).error("   " + MAVEN_PROJECT_RUNTIME);
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
        return new DefaultArtifact(
                "org.apache.maven", artifactId, version, scope, "jar", null, new DefaultArtifactHandler("jar"));
    }
}
