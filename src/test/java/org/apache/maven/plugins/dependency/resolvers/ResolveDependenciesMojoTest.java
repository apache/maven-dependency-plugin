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

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;

public class ResolveDependenciesMojoTest extends AbstractDependencyMojoTestCase {
    @Override
    protected void setUp() throws Exception {
        // required for mojo lookups to work
        super.setUp("dss", true);
    }

    public void testDependencyStatusLog() throws IOException {
        Set<Artifact> artifacts = this.stubFactory.getMixedArtifacts();
        doTestDependencyStatusLog(artifacts);
    }

    public void testDependencyStatusLogNullFiles() throws IOException {
        this.stubFactory.setCreateFiles(false);
        Set<Artifact> artifacts = this.stubFactory.getMixedArtifacts();
        doTestDependencyStatusLog(artifacts);
    }

    public void testDependencyStatusEmptySet() {
        doTestDependencyStatusLog(new HashSet<>());
    }

    public void testOptionalDependencyFormatting() throws IOException {
        Set<Artifact> set = new HashSet<>();
        Artifact artifact =
                stubFactory.createArtifact("g", "a", VersionRange.createFromVersion("1.0"), "test", "jar", null, true);
        set.add(artifact);
        doTestDependencyStatusLog(set);
        ResolveDependenciesMojo mojo = newMojo(new DependencyStatusSets());
        mojo.results.setResolvedDependencies(set);
        String output = mojo.getOutput(false, true, false);
        assertTrue(output.contains("g:a:jar:1.0:test (optional)" + System.lineSeparator()));
    }

    private void doTestDependencyStatusLog(Set<Artifact> artifacts) {
        // this test is just looking for unexpected exceptions.

        ResolveDependenciesMojo mojo = newMojo(new DependencyStatusSets());
        mojo.getOutput(false, true, false);
        mojo.getOutput(true, true, false);

        mojo = newMojo(new DependencyStatusSets(artifacts, null, null));
        mojo.getOutput(false, true, false);
        mojo.getOutput(true, true, false);

        mojo = newMojo(new DependencyStatusSets(null, artifacts, null));
        mojo.getOutput(false, true, false);
        mojo.getOutput(true, true, false);

        mojo = newMojo(new DependencyStatusSets(null, null, artifacts));
        mojo.getOutput(false, true, false);
        mojo.getOutput(true, true, false);

        mojo = newMojo(new DependencyStatusSets(artifacts, artifacts, null));
        mojo.getOutput(false, true, false);
        mojo.getOutput(true, true, false);

        mojo = newMojo(new DependencyStatusSets(null, artifacts, artifacts));
        mojo.getOutput(false, true, false);
        mojo.getOutput(true, true, false);

        mojo = newMojo(new DependencyStatusSets(artifacts, null, artifacts));
        mojo.getOutput(false, true, false);
        mojo.getOutput(true, true, false);

        mojo = newMojo(new DependencyStatusSets(artifacts, artifacts, artifacts));
        mojo.getOutput(false, true, false);
        mojo.getOutput(true, true, false);
        mojo.getOutput(false, false, false);
        mojo.getOutput(true, false, false);
    }

    private ResolveDependenciesMojo newMojo(final DependencyStatusSets dss) {
        ResolveDependenciesMojo mojo = new ResolveDependenciesMojo();
        mojo.results = dss;
        return mojo;
    }
}
