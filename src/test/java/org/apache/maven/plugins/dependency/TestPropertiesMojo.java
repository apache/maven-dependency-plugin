/*
 * Copyright (c) 2016, 2025, Gluon and/or its affiliates.
 * Copyright (c) 2021, 2025, Pascal Treilhes and/or its affiliates.
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * This file is available and licensed under the following license:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Oracle Corporation and Gluon nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.apache.maven.plugins.dependency;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.dependency.testUtils.stubs.DependencyProjectStub;
import org.apache.maven.project.MavenProject;

public class TestPropertiesMojo extends AbstractDependencyMojoTestCase {

    @Override
    protected String getTestDirectoryName() {
        return "markers";
    }

    @Override
    protected boolean shouldCreateFiles() {
        return true;
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

    /**
     * tests the proper discovery and configuration of the mojo for plugin dependencies
     * Each plugin dependency should set a property in the project
     * @throws Exception in case of errors
     */
    public void testSetPropertiesForPluginDependencies() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/properties-test/plugin-config.xml");
        PropertiesMojo mojo = (PropertiesMojo) lookupMojo("properties", testPom);

        assertNotNull(mojo);
        MavenProject project = (MavenProject) getVariableValueFromObject(mojo, "project");
        assertNotNull(project);

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);

        PluginDescriptor pluginDescriptor = new PluginDescriptor();
        pluginDescriptor.setArtifacts(new ArrayList<>());
        Map pluginContext = new HashMap<>();
        pluginContext.put("pluginDescriptor", pluginDescriptor);
        mojo.setPluginContext(pluginContext);

        mojo.execute();

        for (Artifact artifact : artifacts) {
            File artifactFile = artifact.getFile();
            String depId = artifact.getDependencyConflictId();
            assertTrue(project.getProperties().containsKey(depId));
            assertTrue(project.getProperties().getProperty(depId).equals(artifactFile.getAbsolutePath()));
        }
    }
}
