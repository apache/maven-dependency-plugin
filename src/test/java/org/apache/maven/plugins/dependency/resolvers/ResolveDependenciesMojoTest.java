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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ResolveDependenciesMojoTest {

    @TempDir
    File tempDir;

    private DependencyArtifactStubFactory stubFactory;

    @BeforeEach
    void setUp() {
        stubFactory = new DependencyArtifactStubFactory(null, false, false);
    }

    @Test
    void testDependencyStatusLog() throws IOException {
        Set<Artifact> artifacts = this.stubFactory.getMixedArtifacts();
        doTestDependencyStatusLog(artifacts);
    }

    @Test
    void testDependencyStatusLogNullFiles() throws IOException {
        this.stubFactory.setCreateFiles(false);
        Set<Artifact> artifacts = this.stubFactory.getMixedArtifacts();
        doTestDependencyStatusLog(artifacts);
    }

    @Test
    void testDependencyStatusEmptySet() {
        doTestDependencyStatusLog(new HashSet<>());
    }

    @Test
    void testOptionalDependencyFormatting() throws IOException {
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

    @Test
    void testModuleInfoIsAppendedToResolvedJars() throws IOException {
        assumeTrue(hasModuleSystem(), "module names need Java 9 or later");
        ResolveDependenciesMojo mojo = newMojo(new DependencyStatusSets());
        mojo.outputFile = new File(tempDir, "list.txt");
        mojo.includeModuleInfo = true;
        mojo.results.setResolvedDependencies(Collections.singleton(automaticModuleArtifact()));

        assertEquals(
                "   g:a:jar:1.0:compile -- module demo.auto [auto]" + System.lineSeparator(),
                resolvedLines(mojo.getOutput(false, true, false)));
    }

    @Test
    void testModuleInfoCanBeSwitchedOff() throws IOException {
        assumeTrue(hasModuleSystem(), "module names need Java 9 or later");
        ResolveDependenciesMojo mojo = newMojo(new DependencyStatusSets());
        mojo.outputFile = new File(tempDir, "list.txt");
        mojo.includeModuleInfo = false;
        mojo.results.setResolvedDependencies(Collections.singleton(automaticModuleArtifact()));

        String output = mojo.getOutput(false, true, false);

        assertEquals("   g:a:jar:1.0:compile" + System.lineSeparator(), resolvedLines(output));
        assertFalse(output.contains("\u001B"), "no escape codes in a file");
    }

    private static boolean hasModuleSystem() {
        try {
            Class.forName("java.lang.module.ModuleFinder");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** The artifact lines of the output, without the header and the trailing blank line. */
    private static String resolvedLines(String output) {
        String header = "The following files have been resolved:" + System.lineSeparator();
        assertTrue(output.contains(header), output);
        return output.substring(output.indexOf(header) + header.length()).replaceFirst("(\\R)+$", "$1");
    }

    private Artifact automaticModuleArtifact() throws IOException {
        File jar = new File(tempDir, "a-1.0.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Automatic-Module-Name", "demo.auto");
        new JarOutputStream(new FileOutputStream(jar), manifest).close();
        Artifact artifact =
                new DefaultArtifact("g", "a", "1.0", "compile", "jar", null, new DefaultArtifactHandler("jar"));
        artifact.setFile(jar);
        return artifact;
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
        ResolveDependenciesMojo mojo = new ResolveDependenciesMojo(null, null, null, null, null, null);
        mojo.results = dss;
        return mojo;
    }
}
