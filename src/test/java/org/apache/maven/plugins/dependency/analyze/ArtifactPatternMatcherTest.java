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

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ArtifactPatternMatcherTest {

    private static Artifact artifact(String groupId, String artifactId, String version, String scope) {
        return new DefaultArtifact(groupId, artifactId, version, scope, "jar", null, new DefaultArtifactHandler("jar"));
    }

    private final Artifact compileDep = artifact("org.acme", "lib", "1.0", Artifact.SCOPE_COMPILE);

    private final Artifact runtimeDep = artifact("org.acme", "lib", "1.0", Artifact.SCOPE_RUNTIME);

    @Test
    void fourSegmentPatternIgnoresScope() {
        assertThat(ArtifactPatternMatcher.matchesAny(compileDep, new String[] {"org.acme:lib:jar:1.0"}))
                .isTrue();
        assertThat(ArtifactPatternMatcher.matchesAny(runtimeDep, new String[] {"org.acme:lib:jar:1.0"}))
                .isTrue();
    }

    @Test
    void fifthSegmentSelectsScope() {
        String[] patterns = {"org.acme:lib:jar:1.0:runtime"};
        assertThat(ArtifactPatternMatcher.matchesAny(runtimeDep, patterns)).isTrue();
        assertThat(ArtifactPatternMatcher.matchesAny(compileDep, patterns)).isFalse();
    }

    @Test
    void emptySegmentsAreWildcardsBeforeScope() {
        String[] patterns = {"::::runtime"};
        assertThat(ArtifactPatternMatcher.matchesAny(runtimeDep, patterns)).isTrue();
        assertThat(ArtifactPatternMatcher.matchesAny(compileDep, patterns)).isFalse();
    }

    @Test
    void scopeSupportsWildcards() {
        assertThat(ArtifactPatternMatcher.matchesAny(runtimeDep, new String[] {"org.acme:::*:run*"}))
                .isTrue();
        assertThat(ArtifactPatternMatcher.matchesAny(compileDep, new String[] {"org.acme:::*:run*"}))
                .isFalse();
        assertThat(ArtifactPatternMatcher.matchesAny(compileDep, new String[] {"org.acme:::*:*"}))
                .isTrue();
    }

    @Test
    void removeMatchingReturnsOnlyRemovedArtifacts() {
        // Artifact equality ignores the scope, so the runtime artifact needs its own coordinates
        Artifact runtimeLib = artifact("org.acme", "runtime-lib", "1.0", Artifact.SCOPE_RUNTIME);
        Artifact other = artifact("org.other", "x", "2.0", Artifact.SCOPE_RUNTIME);
        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(compileDep);
        artifacts.add(runtimeLib);
        artifacts.add(other);

        Set<Artifact> removed = ArtifactPatternMatcher.removeMatching(artifacts, new String[] {"org.acme:::*:runtime"});

        assertThat(removed).containsExactly(runtimeLib);
        assertThat(artifacts).containsExactly(compileDep, other);
    }

    @Test
    void nullAndEmptyPatternsMatchNothing() {
        assertThat(ArtifactPatternMatcher.matchesAny(compileDep, null)).isFalse();
        assertThat(ArtifactPatternMatcher.matchesAny(compileDep, new String[0])).isFalse();
    }

    @Test
    void warnsOncePerPatternWithMoreThanFiveSegments() {
        Log log = mock(Log.class);

        ArtifactPatternMatcher.warnAboutUnsupportedPatterns(
                log, new String[] {"org.acme:lib:jar:1.0:runtime:extra", "org.acme:lib:jar:1.0:runtime", "org.acme"});

        ArgumentCaptor<CharSequence> message = ArgumentCaptor.forClass(CharSequence.class);
        verify(log, times(1)).warn(message.capture());
        assertThat(message.getValue().toString())
                .contains("org.acme:lib:jar:1.0:runtime:extra")
                .contains("[groupId]:[artifactId]:[type]:[version]:[scope]");
        assertThat(ArtifactPatternMatcher.matchesAny(compileDep, new String[] {"org.acme:lib:jar:1.0:compile:extra"}))
                .isFalse();
    }
}
