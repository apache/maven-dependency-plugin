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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.codehaus.plexus.i18n.I18N;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyzeReportRendererTest {

    @Mock
    private Sink sink;

    @Mock
    private I18N i18n;

    @Test
    void rendersAmbiguousUsedClassesWithCandidatesAndSelectedArtifact() {
        Artifact api = artifact("com.example", "api", "1.0");
        Artifact shaded = new DefaultArtifact(
                "com.example",
                "api",
                "1.0",
                Artifact.SCOPE_COMPILE,
                "jar",
                "shaded",
                new DefaultArtifactHandler("jar"));
        Map<String, Set<Artifact>> candidates = new LinkedHashMap<>();
        candidates.put("com.example.Service", new LinkedHashSet<>(Arrays.asList(api, shaded)));
        Map<String, Artifact> selected = new LinkedHashMap<>();
        selected.put("com.example.Service", shaded);
        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.singleton("com.example.Service"),
                candidates,
                selected);

        render(analysis);

        ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        verify(sink, atLeastOnce()).text(written.capture());
        assertThat(written.getAllValues())
                .contains(
                        "report.analyze.AmbiguousUsedClasses",
                        "com.example.Service",
                        "com.example:api:jar:1.0, com.example:api:jar:shaded:1.0",
                        "com.example:api:jar:shaded:1.0")
                .doesNotContain("report.analyze.noDependencyProblems");
    }

    @Test
    void rendersUsedClassesThatNoDependencyProvides() {
        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.singleton("com.missing.Gone"),
                Collections.singletonMap("com.missing.Gone", Collections.emptySet()),
                Collections.emptyMap());

        render(analysis);

        ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        verify(sink, atLeastOnce()).text(written.capture());
        assertThat(written.getAllValues())
                .contains("report.analyze.UnmappedUsedClasses", "com.missing.Gone")
                .doesNotContain("report.analyze.noDependencyProblems");
    }

    @Test
    void omitsAmbiguousSectionForAnalysisWithoutCandidateData() {
        ProjectDependencyAnalysis analysis = new ProjectDependencyAnalysis(
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(), Collections.emptySet());

        render(analysis);

        verify(sink, never()).text("report.analyze.AmbiguousUsedClasses");
        verify(sink, never()).text("report.analyze.UnmappedUsedClasses");
    }

    private void render(ProjectDependencyAnalysis analysis) {
        when(i18n.getString(anyString(), any(Locale.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        new AnalyzeReportRenderer(sink, i18n, Locale.ENGLISH, analysis).renderBody();
    }

    private static Artifact artifact(String groupId, String artifactId, String version) {
        return new DefaultArtifact(
                groupId, artifactId, version, Artifact.SCOPE_COMPILE, "jar", null, new DefaultArtifactHandler("jar"));
    }
}
