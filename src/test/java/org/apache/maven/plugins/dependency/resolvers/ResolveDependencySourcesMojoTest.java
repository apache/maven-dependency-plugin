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
package org.apache.maven.plugins.dependency.resolvers;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResolveDependencySourcesMojoTest {

    @Test
    void testJarClassifierMapsToTestSources() {
        ArtifactHandlerManager handlerManager = mock(ArtifactHandlerManager.class);
        when(handlerManager.getArtifactHandler(anyString())).thenReturn(new DefaultArtifactHandler("jar"));
        ResolveDependencySourcesMojo mojo =
                new ResolveDependencySourcesMojo(null, null, null, null, null, handlerManager);
        mojo.setClassifier("sources");

        Set<Artifact> artifacts = new LinkedHashSet<>(Arrays.asList(
                artifact("lib", "jar", null),
                artifact("lib", "test-jar", "tests"),
                artifact("other", "jar", "client")));

        Set<String> translated = mojo.createArtifactTranslator().translate(artifacts, new SystemStreamLog()).stream()
                .map(a -> a.getArtifactId() + ":" + a.getClassifier())
                .collect(Collectors.toSet());

        assertThat(translated).containsExactlyInAnyOrder("lib:sources", "lib:test-sources", "other:sources");
    }

    private static Artifact artifact(String artifactId, String type, String classifier) {
        return new DefaultArtifact(
                "g", artifactId, "1.0", "compile", type, classifier, new DefaultArtifactHandler(type));
    }
}
