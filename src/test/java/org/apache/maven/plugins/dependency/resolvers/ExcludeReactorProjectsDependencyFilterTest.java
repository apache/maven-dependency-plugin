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
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExcludeReactorProjectsDependencyFilterTest extends AbstractDependencyMojoTestCase {
    @Test
    public void testReject() {
        final Artifact artifact1 = new ArtifactStub();
        artifact1.setGroupId("org.apache.maven.plugins");
        artifact1.setArtifactId("maven-dependency-plugin-dummy");
        artifact1.setVersion("1.0");

        Artifact artifact2 = new ArtifactStub();
        artifact2.setGroupId("org.apache.maven.plugins");
        artifact2.setArtifactId("maven-dependency-plugin-other-dummy");
        artifact2.setVersion("1.0");

        MavenProject project = new MavenProjectStub();
        project.setArtifact(artifact1);

        ExcludeReactorProjectsDependencyFilter filter =
                new ExcludeReactorProjectsDependencyFilter(singletonList(project));

        Dependency dependency = new Dependency();
        dependency.setGroupId(artifact1.getGroupId());
        dependency.setArtifactId(artifact1.getArtifactId());
        dependency.setVersion(artifact1.getVersion());

        assertFalse(filter.test(dependency));
    }

    @Test
    public void testAccept() {
        final Artifact artifact1 = new ArtifactStub();
        artifact1.setGroupId("org.apache.maven.plugins");
        artifact1.setArtifactId("maven-dependency-plugin-dummy");
        artifact1.setVersion("1.0");

        Artifact artifact2 = new ArtifactStub();
        artifact2.setGroupId("org.apache.maven.plugins");
        artifact2.setArtifactId("maven-dependency-plugin-other-dummy");
        artifact2.setVersion("1.0");

        MavenProject project = new MavenProjectStub();
        project.setArtifact(artifact1);

        ExcludeReactorProjectsDependencyFilter filter =
                new ExcludeReactorProjectsDependencyFilter(singletonList(project));

        Dependency dependency = new Dependency();
        dependency.setGroupId("something-else");
        dependency.setArtifactId(artifact1.getArtifactId());
        dependency.setVersion(artifact1.getVersion());

        assertTrue(filter.test(dependency));
    }
}
