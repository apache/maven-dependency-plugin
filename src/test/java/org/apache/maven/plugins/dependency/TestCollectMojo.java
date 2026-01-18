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

import java.util.Set;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.dependency.resolvers.CollectDependenciesMojo;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.utils.DependencySilentLog;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MojoTest
class TestCollectMojo {

    private DependencyArtifactStubFactory stubFactory;

    @BeforeEach
    void setUp() throws Exception {
        stubFactory = new DependencyArtifactStubFactory(null, false);
    }

    /**
     * tests the proper discovery and configuration of the mojo
     *
     * @throws Exception if a problem occurs
     */
    @Test
    @InjectMojo(goal = "collect")
    void testCollectTestEnvironment(CollectDependenciesMojo mojo) throws Exception {

        assertNotNull(mojo);
        assertNotNull(mojo.getProject());
        MavenProject project = mojo.getProject();

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);

        project.setArtifacts(artifacts);
        project.setDependencyArtifacts(directArtifacts);

        mojo.execute();
        DependencyStatusSets results = mojo.getResults();
        assertNotNull(results);
        assertEquals(artifacts.size(), results.getResolvedDependencies().size());
    }

    /**
     * tests the proper discovery and configuration of the mojo
     *
     * @throws Exception if a problem occurs
     */
    @Test
    @InjectMojo(goal = "collect")
    @MojoParameter(name = "excludeTransitive", value = "true")
    void testCollectTestEnvironmentExcludeTransitive(CollectDependenciesMojo mojo) throws Exception {
        assertNotNull(mojo);
        assertNotNull(mojo.getProject());
        MavenProject project = mojo.getProject();

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);

        project.setArtifacts(artifacts);
        project.setDependencyArtifacts(directArtifacts);

        mojo.execute();
        DependencyStatusSets results = mojo.getResults();
        assertNotNull(results);
        assertEquals(directArtifacts.size(), results.getResolvedDependencies().size());
    }

    @Test
    @InjectMojo(goal = "collect")
    void testSilent(CollectDependenciesMojo mojo) throws Exception {
        assertFalse(mojo.getLog() instanceof DependencySilentLog);

        mojo.setSilent(true);
        assertTrue(mojo.getLog() instanceof DependencySilentLog);

        mojo.setSilent(false);
        assertFalse(mojo.getLog() instanceof DependencySilentLog);
    } // TODO: Test skipping artifacts.
}
