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

import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcludeReactorProjectsArtifactFilterTest {

    @Mock
    private Log log;

    @Mock
    private MavenProject project;

    @Test
    void testFilter() throws ArtifactFilterException {
        Artifact artifact1 = anArtifact("maven-dependency-plugin-dummy");
        Artifact artifact2 = anArtifact("maven-dependency-plugin-other-dummy");

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(artifact1);
        artifacts.add(artifact2);

        when(project.getArtifact()).thenReturn(artifact1);
        when(log.isDebugEnabled()).thenReturn(false);

        ExcludeReactorProjectsArtifactFilter filter =
                new ExcludeReactorProjectsArtifactFilter(singletonList(project), log);

        Set<Artifact> result = filter.filter(artifacts);

        assertEquals(1, result.size());
        verify(log, never()).debug(any(String.class));
    }

    @Test
    void testFilterWithLogging() throws ArtifactFilterException {
        Artifact artifact = anArtifact("maven-dependency-plugin-dummy");

        when(project.getArtifact()).thenReturn(artifact);
        when(log.isDebugEnabled()).thenReturn(true);

        ExcludeReactorProjectsArtifactFilter filter =
                new ExcludeReactorProjectsArtifactFilter(singletonList(project), log);

        filter.filter(singleton(artifact));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(log).debug(captor.capture());
        assertTrue(captor.getValue().contains("Skipped artifact"));
    }

    private Artifact anArtifact(String artifactId) {
        return new DefaultArtifact("org.apache.maven.plugins", artifactId, "1.0", null, "jar", "", null);
    }
}
