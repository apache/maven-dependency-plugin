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
package org.apache.maven.plugins.dependency;

import java.util.ArrayList;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractDependencyMojoTest {
    private MavenSession session = mock(MavenSession.class);

    private ProjectBuildingRequest buildingRequest = mock(ProjectBuildingRequest.class);

    private ArrayList<ArtifactRepository> artifactRepos = new ArrayList<>();

    private ArrayList<ArtifactRepository> pluginRepos = new ArrayList<>();

    static class ConcreteDependencyMojo extends AbstractDependencyMojo {

        protected ConcreteDependencyMojo(MavenSession session, BuildContext buildContext, MavenProject project) {
            super(session, buildContext, project);
        }

        @Override
        protected void doExecute() {}
    }

    @BeforeEach
    public void setUp() throws Exception {
        pluginRepos.add(newRepositoryWithId("pr-central"));
        pluginRepos.add(newRepositoryWithId("pr-plugins"));

        artifactRepos.add(newRepositoryWithId("ar-central"));
        artifactRepos.add(newRepositoryWithId("ar-snapshots"));
        artifactRepos.add(newRepositoryWithId("ar-staging"));

        when(buildingRequest.getRepositoryMerging()).thenReturn(ProjectBuildingRequest.RepositoryMerging.POM_DOMINANT);
        when(session.getProjectBuildingRequest()).thenReturn(buildingRequest);
    }

    private static ArtifactRepository newRepositoryWithId(String id) {
        ArtifactRepository repo = mock(ArtifactRepository.class);
        when(repo.getId()).thenReturn(id);
        return repo;
    }
}
