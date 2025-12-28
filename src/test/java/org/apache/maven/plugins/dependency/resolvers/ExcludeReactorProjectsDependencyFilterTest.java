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
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcludeReactorProjectsDependencyFilterTest {

    @Mock
    private MavenProject project;

    @Test
    void testReject() {
        Artifact artifact1 = anArtifact();

        when(project.getArtifact()).thenReturn(artifact1);

        ExcludeReactorProjectsDependencyFilter filter =
                new ExcludeReactorProjectsDependencyFilter(singletonList(project));

        Dependency dependency = new Dependency();
        dependency.setGroupId(artifact1.getGroupId());
        dependency.setArtifactId(artifact1.getArtifactId());
        dependency.setVersion(artifact1.getVersion());

        assertFalse(filter.test(dependency));
    }

    @Test
    void testAccept() {
        Artifact artifact1 = anArtifact();

        when(project.getArtifact()).thenReturn(artifact1);

        ExcludeReactorProjectsDependencyFilter filter =
                new ExcludeReactorProjectsDependencyFilter(singletonList(project));

        Dependency dependency = new Dependency();
        dependency.setGroupId("something-else");
        dependency.setArtifactId(artifact1.getArtifactId());
        dependency.setVersion(artifact1.getVersion());

        assertTrue(filter.test(dependency));
    }

    private Artifact anArtifact() {
        return new DefaultArtifact(
                "org.apache.maven.plugins", "maven-dependency-plugin-dummy", "1.0", null, "jar", "", null);
    }
}
