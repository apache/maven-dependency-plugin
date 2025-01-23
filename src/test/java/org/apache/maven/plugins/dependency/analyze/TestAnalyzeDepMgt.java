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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.testUtils.stubs.DependencyProjectStub;
import org.apache.maven.project.MavenProject;

import static org.junit.Assert.assertNotEquals;

public class TestAnalyzeDepMgt extends TestCase {

    AnalyzeDepMgt mojo;

    DependencyArtifactStubFactory stubFactory;

    Dependency exclusion;

    Exclusion ex;

    DependencyManagement depMgt;

    @Override
    protected void setUp() throws Exception {

        MavenProject project = new DependencyProjectStub();
        mojo = new AnalyzeDepMgt(project);

        stubFactory = new DependencyArtifactStubFactory(new File(""), false);

        Set<Artifact> allArtifacts = stubFactory.getMixedArtifacts();
        Set<Artifact> directArtifacts = stubFactory.getClassifiedArtifacts();

        Artifact exclusionArtifact = stubFactory.getReleaseArtifact();
        directArtifacts.add(exclusionArtifact);
        ex = new Exclusion();
        ex.setArtifactId(exclusionArtifact.getArtifactId());
        ex.setGroupId(exclusionArtifact.getGroupId());

        exclusion = new Dependency();
        exclusion.setArtifactId(exclusionArtifact.getArtifactId());
        exclusion.setGroupId(exclusionArtifact.getGroupId());
        exclusion.setType(exclusionArtifact.getType());
        exclusion.setClassifier("");
        exclusion.setVersion("3.0");

        exclusion.addExclusion(ex);
        List<Dependency> list = new ArrayList<>();
        list.add(exclusion);

        depMgt = new DependencyManagement();
        depMgt.setDependencies(list);

        project.setArtifacts(allArtifacts);
        project.setDependencyArtifacts(directArtifacts);
    }

    public void testGetManagementKey() throws IOException {
        Dependency dep = new Dependency();
        dep.setArtifactId("artifact");
        dep.setClassifier("class");
        dep.setGroupId("group");
        dep.setType("type");

        // version isn't used in the key, it can be different
        dep.setVersion("1.1");

        Artifact artifact =
                stubFactory.createArtifact("group", "artifact", "1.0", Artifact.SCOPE_COMPILE, "type", "class");

        // basic case ok
        assertEquals(dep.getManagementKey(), mojo.getArtifactManagementKey(artifact));

        // now change each one and make sure it fails, then set it back and make
        // sure it's ok before
        // testing the next one
        dep.setType("t");
        dep.clearManagementKey();
        assertNotEquals(dep.getManagementKey(), mojo.getArtifactManagementKey(artifact));

        dep.setType("type");
        dep.clearManagementKey();
        assertEquals(dep.getManagementKey(), mojo.getArtifactManagementKey(artifact));

        dep.setArtifactId("a");
        dep.clearManagementKey();
        assertNotEquals(dep.getManagementKey(), mojo.getArtifactManagementKey(artifact));

        dep.setArtifactId("artifact");
        dep.clearManagementKey();
        assertEquals(dep.getManagementKey(), mojo.getArtifactManagementKey(artifact));

        dep.setClassifier("c");
        dep.clearManagementKey();
        assertNotEquals(dep.getManagementKey(), mojo.getArtifactManagementKey(artifact));

        dep.setClassifier("class");
        dep.clearManagementKey();
        assertEquals(dep.getManagementKey(), mojo.getArtifactManagementKey(artifact));

        dep.setGroupId("g");
        dep.clearManagementKey();
        assertNotEquals(dep.getManagementKey(), mojo.getArtifactManagementKey(artifact));

        dep.setGroupId("group");
        dep.setClassifier(null);
        dep.clearManagementKey();
        artifact = stubFactory.createArtifact("group", "artifact", "1.0", Artifact.SCOPE_COMPILE, "type", null);
        assertEquals(dep.getManagementKey(), mojo.getArtifactManagementKey(artifact));

        dep.setClassifier("");
        dep.clearManagementKey();
        artifact = stubFactory.createArtifact("group", "artifact", "1.0", Artifact.SCOPE_COMPILE, "type", "");
        assertEquals(dep.getManagementKey(), mojo.getArtifactManagementKey(artifact));
    }

    public void testAddExclusions() {

        assertEquals(0, mojo.addExclusions(null).size());

        List<Exclusion> list = new ArrayList<>();
        list.add(ex);
        Map<String, Exclusion> map = mojo.addExclusions(list);

        assertEquals(1, map.size());
        assertTrue(map.containsKey(mojo.getExclusionKey(ex)));
        assertSame(ex, map.get(mojo.getExclusionKey(ex)));
    }

    public void testGetExclusionErrors() {
        List<Exclusion> list = new ArrayList<>();
        list.add(ex);

        // already tested this method so I can trust it.
        Map<String, Exclusion> map = mojo.addExclusions(list);

        List<Artifact> l = mojo.getExclusionErrors(map, mojo.getProject().getArtifacts());

        assertEquals(1, l.size());

        assertEquals(mojo.getExclusionKey(ex), mojo.getExclusionKey(l.get(0)));
    }

    public void testGetMismatch() throws IOException {
        Map<String, Dependency> depMgtMap = new HashMap<>();

        depMgtMap.put(exclusion.getManagementKey(), exclusion);

        Map<Artifact, Dependency> results =
                mojo.getMismatch(depMgtMap, mojo.getProject().getArtifacts());

        assertEquals(1, results.size());
        // the release artifact is used to create the exclusion
        assertTrue(results.containsKey(stubFactory.getReleaseArtifact()));
        assertSame(exclusion, results.get(stubFactory.getReleaseArtifact()));
    }

    public void testMojo() throws IOException, MojoExecutionException, MojoFailureException {
        mojo.setIgnoreDirect(false);
        // test with nothing in depMgt
        mojo.execute();

        DependencyProjectStub project = (DependencyProjectStub) mojo.getProject();
        project.setDependencyManagement(depMgt);
        // test with exclusion
        mojo.execute();

        try {
            // test with exclusion
            mojo.setFailBuild(true);
            mojo.execute();
            fail("Expected exception to fail the build.");
        } catch (MojoExecutionException e) {
        }

        mojo.setFailBuild(true);
        mojo.setIgnoreDirect(true);
        mojo.execute();
    }
}
