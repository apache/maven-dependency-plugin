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

import java.io.File;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.dependency.resolvers.CollectDependenciesMojo;
import org.apache.maven.plugins.dependency.testUtils.stubs.DependencyProjectStub;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.project.MavenProject;

public class TestCollectMojo extends AbstractDependencyMojoTestCase {

    @Override
    protected String getTestDirectoryName() {
        return "markers";
    }

    @Override
    protected boolean shouldCreateFiles() {
        return false;
    }

    @Override
    protected void setUp() throws Exception {
        // required for mojo lookups to work
        super.setUp();

        MavenProject project = new DependencyProjectStub();
        getContainer().addComponent(project, MavenProject.class.getName());

        MavenSession session = newMavenSession(project);
        getContainer().addComponent(session, MavenSession.class.getName());
    }

    /**
     * tests the proper discovery and configuration of the mojo
     *
     * @throws Exception if a problem occurs
     */
    public void testCollectTestEnvironment() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/collect-test/plugin-config.xml");
        CollectDependenciesMojo mojo = (CollectDependenciesMojo) lookupMojo("collect", testPom);

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
    public void testCollectTestEnvironmentExcludeTransitive() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/collect-test/plugin-config.xml");
        CollectDependenciesMojo mojo = (CollectDependenciesMojo) lookupMojo("collect", testPom);

        assertNotNull(mojo);
        assertNotNull(mojo.getProject());
        MavenProject project = mojo.getProject();

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);

        project.setArtifacts(artifacts);
        project.setDependencyArtifacts(directArtifacts);

        setVariableValueToObject(mojo, "excludeTransitive", Boolean.TRUE);

        mojo.execute();
        DependencyStatusSets results = mojo.getResults();
        assertNotNull(results);
        assertEquals(directArtifacts.size(), results.getResolvedDependencies().size());
    }
}
