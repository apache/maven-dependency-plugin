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
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractAnalyzeMojoTest {

    @Test
    void filterDependenciesDoesNotMutateInput() {
        Artifact included = artifact("org.example", "included");
        Artifact excluded = artifact("org.example", "excluded");
        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(included);
        artifacts.add(excluded);
        AnalyzeMojo mojo = new AnalyzeMojo(mock(PlexusContainer.class), mock(MavenProject.class));

        assertEquals(setOf(excluded), mojo.filterDependencies(artifacts, new String[] {"org.example:excluded"}));
        assertEquals(setOf(included), mojo.filterDependencies(artifacts, new String[] {"org.example:included"}));
        assertEquals(setOf(included, excluded), artifacts);
    }

    private Artifact artifact(String groupId, String artifactId) {
        Artifact artifact = mock(Artifact.class);
        when(artifact.getGroupId()).thenReturn(groupId);
        when(artifact.getArtifactId()).thenReturn(artifactId);
        return artifact;
    }

    private Set<Artifact> setOf(Artifact... artifacts) {
        return new LinkedHashSet<>(Arrays.asList(artifacts));
    }
}
