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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractAnalyzeMojoTest {

    @TempDir
    File targetDir;

    private final Artifact jodaTime = artifact("joda-time", "joda-time", Artifact.SCOPE_COMPILE);

    private final Artifact commonsIo = artifact("commons-io", "commons-io", Artifact.SCOPE_COMPILE);

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
        ProjectDependencyAnalyzer analyzer = mock(ProjectDependencyAnalyzer.class);
        when(analyzer.analyze(any(MavenProject.class), any()))
                .thenReturn(new ProjectDependencyAnalysis(
                        Collections.emptySet(), setOf(jodaTime, commonsIo), Collections.emptySet(), setOf(jodaTime)));
        PlexusContainer container = mock(PlexusContainer.class);
        when(container.lookup(ProjectDependencyAnalyzer.class, "default")).thenReturn(analyzer);

        MavenProject project = new MavenProject();
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
                .contains("Non-test scoped test only dependencies found:")
                .anyMatch(line ->
                        line.contains("joda-time:joda-time:jar:1.0:compile") && !line.startsWith("Used undeclared"));
    }

    @Test
    void ignoredUsedUndeclaredIsNotReportedAsNonTestScoped() throws Exception {
        set(mojo, "ignoredUsedUndeclaredDependencies", new String[] {"joda-time:joda-time"});

        mojo.execute();

        assertThat(warnings()).doesNotContain("Non-test scoped test only dependencies found:");
    }
}
