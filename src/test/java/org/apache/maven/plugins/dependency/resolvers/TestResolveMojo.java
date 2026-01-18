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

import java.util.Set;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.utils.DependencySilentLog;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.AssertionsKt.assertNotNull;

@MojoTest
class TestResolveMojo {

    /**
     * tests the proper discovery and configuration of the mojo
     *
     * @throws Exception in case of errors.
     */
    @Test
    @InjectMojo(goal = "resolve")
    void testResolveTestEnvironment(ResolveDependenciesMojo mojo) throws Exception {
        assertNotNull(mojo);
        assertNotNull(mojo.getProject());
        MavenProject project = mojo.getProject();

        DependencyArtifactStubFactory stubFactory = new DependencyArtifactStubFactory(null, false);
        Set<Artifact> artifacts = stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);

        project.setArtifacts(artifacts);
        project.setDependencyArtifacts(directArtifacts);

        mojo.execute();
        DependencyStatusSets results = mojo.getResults();
        assertNotNull(results);
        assertEquals(artifacts.size(), results.getResolvedDependencies().size());

        setVariableValueToObject(mojo, "excludeTransitive", Boolean.TRUE);

        mojo.execute();
        results = mojo.getResults();
        assertNotNull(results);
        assertEquals(directArtifacts.size(), results.getResolvedDependencies().size());
    }

    @Test
    @InjectMojo(goal = "resolve")
    void testSilent(ResolveDependenciesMojo mojo) {
        assertFalse(mojo.getLog() instanceof DependencySilentLog);

        mojo.setSilent(true);
        assertTrue(mojo.getLog() instanceof DependencySilentLog);

        mojo.setSilent(false);
        assertFalse(mojo.getLog() instanceof DependencySilentLog);
    } // TODO: Test skipping artifacts.
}
