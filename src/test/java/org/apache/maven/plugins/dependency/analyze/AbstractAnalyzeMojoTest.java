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

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.codehaus.plexus.PlexusContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractAnalyzeMojoTest {

    @TempDir
    File targetDir;

    private final Artifact jodaTime = artifact("joda-time", "joda-time", Artifact.SCOPE_COMPILE);

    private final Artifact commonsIo = artifact("commons-io", "commons-io", Artifact.SCOPE_COMPILE);

    private ProjectDependencyAnalyzer analyzer;

    private Log log;

    private AnalyzeOnlyMojo mojo;

    private static Artifact artifact(String groupId, String artifactId, String scope) {
        return new DefaultArtifact(groupId, artifactId, "1.0", scope, "jar", null, new DefaultArtifactHandler("jar"));
    }

    private static Set<Artifact> setOf(Artifact... artifacts) {
        Set<Artifact> set = new LinkedHashSet<>();
        Collections.addAll(set, artifacts);
        return set;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = AbstractAnalyzeMojo.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    /**
     * Creates a mojo whose analyzer reports the given artifacts as used but undeclared and, at the same time, as
     * compile scoped but used in tests only.
     */
    @BeforeEach
    void setUp() throws Exception {
        analyzer = mock(ProjectDependencyAnalyzer.class);
        when(analyzer.analyze(any(MavenProject.class), any()))
                .thenReturn(new ProjectDependencyAnalysis(
                        Collections.emptySet(), setOf(jodaTime, commonsIo), Collections.emptySet(), setOf(jodaTime)));
        PlexusContainer container = mock(PlexusContainer.class);
        when(container.lookup(ProjectDependencyAnalyzer.class, "default")).thenReturn(analyzer);

        MavenProject project = new MavenProject();
        project.setGroupId("org.example");
        project.setArtifactId("module-a");
        project.setPackaging("jar");
        mojo = new AnalyzeOnlyMojo(container, project);
        log = mock(Log.class);
        mojo.setLog(log);
        set(mojo, "analyzer", "default");
        set(mojo, "outputDirectory", targetDir);
        set(mojo, "ignoredPackagings", Collections.singletonList("pom"));
        set(mojo, "ignoredDependencies", new String[0]);
        set(mojo, "ignoredUnusedDeclaredDependencies", new String[0]);
        set(mojo, "ignoredUsedUndeclaredDependencies", new String[0]);
        set(mojo, "ignoredNonTestScopedDependencies", new String[0]);
    }

    private List<String> warnings() {
        ArgumentCaptor<CharSequence> captor = ArgumentCaptor.forClass(CharSequence.class);
        verify(log, atLeast(0)).warn(captor.capture());
        return captor.getAllValues().stream().map(CharSequence::toString).collect(Collectors.toList());
    }

    @Test
    void usedUndeclaredWithoutIgnoreIsAlsoReportedAsNonTestScoped() throws Exception {
        mojo.execute();

        assertThat(warnings())
                .contains("Non-test scoped test only dependencies found in org.example:module-a:")
                .anyMatch(line ->
                        line.contains("joda-time:joda-time:jar:1.0:compile") && !line.startsWith("Used undeclared"));
    }

    @Test
    void warningHeadersNameTheModule() throws Exception {
        Artifact junit = artifact("junit", "junit", Artifact.SCOPE_COMPILE);
        analysisIs(new ProjectDependencyAnalysis(
                Collections.emptySet(), setOf(jodaTime), setOf(junit), Collections.emptySet()));

        mojo.execute();

        assertThat(warnings())
                .contains(
                        "Used undeclared dependencies found in org.example:module-a:",
                        "Unused declared dependencies found in org.example:module-a:");
    }

    @Test
    void ignoredUsedUndeclaredIsNotReportedAsNonTestScoped() throws Exception {
        set(mojo, "ignoredUsedUndeclaredDependencies", new String[] {"joda-time:joda-time"});

        mojo.execute();

        assertThat(warnings()).doesNotContain("Non-test scoped test only dependencies found in org.example:module-a:");
    }

    private void analysisIs(ProjectDependencyAnalysis analysis) throws Exception {
        when(analyzer.analyze(any(MavenProject.class), any())).thenReturn(analysis);
    }

    @Test
    void testSkipDropsTestScopedUnusedDeclared() throws Exception {
        Artifact junit = artifact("junit", "junit", Artifact.SCOPE_TEST);
        Artifact provided = artifact("javax.servlet", "servlet-api", Artifact.SCOPE_PROVIDED);
        analysisIs(new ProjectDependencyAnalysis(
                Collections.emptySet(), Collections.emptySet(), setOf(junit, provided), Collections.emptySet()));
        set(mojo, "testSkip", true);

        mojo.execute();

        assertThat(warnings())
                .contains("Unused declared dependencies found in org.example:module-a:", "   " + provided);
        assertThat(warnings()).doesNotContain("   " + junit);
        ArgumentCaptor<CharSequence> info = ArgumentCaptor.forClass(CharSequence.class);
        verify(log, atLeast(1)).info(info.capture());
        assertThat(info.getAllValues())
                .filteredOn(m -> m.toString().contains("maven.test.skip"))
                .hasSize(1);
    }

    @Test
    void testScopedUnusedDeclaredIsReportedWithoutTestSkip() throws Exception {
        Artifact junit = artifact("junit", "junit", Artifact.SCOPE_TEST);
        analysisIs(new ProjectDependencyAnalysis(
                Collections.emptySet(), Collections.emptySet(), setOf(junit), Collections.emptySet()));

        mojo.execute();

        assertThat(warnings()).contains("   " + junit);
    }

    @Test
    void unneededForcedUsageIsAWarningNotAFailure() throws Exception {
        Artifact logback = artifact("ch.qos.logback", "logback-classic", Artifact.SCOPE_TEST);
        analysisIs(new ProjectDependencyAnalysis(
                setOf(logback), Collections.emptySet(), Collections.emptySet(), Collections.emptySet()));
        set(mojo, "usedDependencies", new String[] {"ch.qos.logback:logback-classic"});

        mojo.execute();

        assertThat(warnings())
                .anyMatch(m -> m.contains("ch.qos.logback:logback-classic") && m.contains("already detected as used"));
    }

    @Test
    void unneededForcedUsageFailsTheBuildWithFailOnWarning() throws Exception {
        Artifact logback = artifact("ch.qos.logback", "logback-classic", Artifact.SCOPE_TEST);
        analysisIs(new ProjectDependencyAnalysis(
                setOf(logback), Collections.emptySet(), Collections.emptySet(), Collections.emptySet()));
        set(mojo, "usedDependencies", new String[] {"ch.qos.logback:logback-classic"});
        set(mojo, "failOnWarning", true);

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessage("Dependency problems found");
        verify(log, atLeast(1)).error(any(CharSequence.class));
    }

    @Test
    void neededForcedUsageIsStillApplied() throws Exception {
        Artifact logback = artifact("ch.qos.logback", "logback-classic", Artifact.SCOPE_TEST);
        analysisIs(new ProjectDependencyAnalysis(
                Collections.emptySet(), Collections.emptySet(), setOf(logback), Collections.emptySet()));
        set(mojo, "usedDependencies", new String[] {"ch.qos.logback:logback-classic"});
        set(mojo, "failOnWarning", true);

        mojo.execute();

        verify(log, never()).error(any(CharSequence.class));
    }

    @Test
    void forcingAnUndeclaredDependencyStillFails() throws Exception {
        analysisIs(new ProjectDependencyAnalysis(
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet()));
        set(mojo, "usedDependencies", new String[] {"not.declared:anywhere"});

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessage("Cannot analyze dependencies");
    }
}
