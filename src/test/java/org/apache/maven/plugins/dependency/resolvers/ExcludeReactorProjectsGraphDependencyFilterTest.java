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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcludeReactorProjectsGraphDependencyFilterTest {

    @Mock
    private MavenProject project;

    @Test
    void testRejectReactorTransitive() {
        Artifact reactorArtifact = anArtifact();
        when(project.getArtifact()).thenReturn(reactorArtifact);

        ExcludeReactorProjectsGraphDependencyFilter filter =
                new ExcludeReactorProjectsGraphDependencyFilter(singletonList(project));

        DependencyNode node =
                nodeFor(reactorArtifact.getGroupId(), reactorArtifact.getArtifactId(), reactorArtifact.getVersion());

        assertFalse(filter.accept(node, emptyList()));
    }

    @Test
    void testAcceptNonReactorTransitive() {
        Artifact reactorArtifact = anArtifact();
        when(project.getArtifact()).thenReturn(reactorArtifact);

        ExcludeReactorProjectsGraphDependencyFilter filter =
                new ExcludeReactorProjectsGraphDependencyFilter(singletonList(project));

        DependencyNode node = nodeFor("something-else", reactorArtifact.getArtifactId(), reactorArtifact.getVersion());

        assertTrue(filter.accept(node, emptyList()));
    }

    @Test
    void testAcceptNullArtifact() {
        Artifact reactorArtifact = anArtifact();
        when(project.getArtifact()).thenReturn(reactorArtifact);

        ExcludeReactorProjectsGraphDependencyFilter filter =
                new ExcludeReactorProjectsGraphDependencyFilter(singletonList(project));

        assertTrue(filter.accept(new DefaultDependencyNode((Dependency) null), emptyList()));
    }

    private static DependencyNode nodeFor(String groupId, String artifactId, String version) {
        org.eclipse.aether.artifact.Artifact aetherArtifact =
                new org.eclipse.aether.artifact.DefaultArtifact(groupId, artifactId, "jar", version);
        return new DefaultDependencyNode(new Dependency(aetherArtifact, null));
    }

    private Artifact anArtifact() {
        return new DefaultArtifact(
                "org.apache.maven.plugins", "maven-dependency-plugin-dummy", "1.0", null, "jar", "", null);
    }
}
