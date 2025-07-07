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
import org.apache.maven.plugins.dependency.testUtils.stubs.DependencyProjectStub;
import org.apache.maven.project.MavenProject;

public class TestPropertiesMojo extends AbstractDependencyMojoTestCase {
    protected void setUp() throws Exception {
        // required for mojo lookups to work
        super.setUp("markers", true);

        MavenProject project = new DependencyProjectStub();
        getContainer().addComponent(project, MavenProject.class.getName());

        MavenSession session = newMavenSession(project);
        getContainer().addComponent(session, MavenSession.class.getName());
    }

    /**
     * tests the proper discovery and configuration of the mojo
     *
     * @throws Exception in case of errors
     */
    public void testSetProperties() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/properties-test/plugin-config.xml");
        PropertiesMojo mojo = (PropertiesMojo) lookupMojo("properties", testPom);

        assertNotNull(mojo);
        MavenProject project = (MavenProject) getVariableValueFromObject(mojo, "project");
        assertNotNull(project);

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);

        project.setArtifacts(artifacts);

        mojo.execute();

        for (Artifact artifact : artifacts) {
            File artifactFile = artifact.getFile();
            assertNotNull(artifact.getDependencyConflictId());
            assertTrue(artifactFile.isFile());
        }
    }
}
