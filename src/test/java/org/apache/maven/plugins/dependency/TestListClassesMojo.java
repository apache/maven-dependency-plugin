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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.dependency.testUtils.stubs.DependencyProjectStub;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.junit.Assert;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class TestListClassesMojo extends AbstractDependencyMojoTestCase {
    private ListClassesMojo mojo;

    @Before
    @Override
    public void setUp() throws Exception {
        // Call superclass setup (initializes mojo lookups and default test directory)
        super.setUp();
        customizeSetUp("markers", false, true);

        MavenProject project = new DependencyProjectStub();
        getContainer().addComponent(project, MavenProject.class.getName());

        MavenSession session = newMavenSession(project);

        RepositorySystem repositorySystem = lookup(RepositorySystem.class);
        ResolverUtil resolverUtil = new ResolverUtil(repositorySystem, () -> session);
        getContainer().addComponent(resolverUtil, ResolverUtil.class.getName());

        getContainer().addComponent(session, MavenSession.class.getName());

        File testPom = new File(getBasedir(), "target/test-classes/unit/get-test/plugin-config.xml");

        assertTrue(testPom.exists());
        mojo = (ListClassesMojo) lookupMojo("list-classes", testPom);

        assertNotNull(mojo);

        installLocalRepository(session.getRepositorySession());
    }

    public void testListClassesNotTransitive() throws Exception {
        Path path = Paths.get("src/test/resources/unit/list-test/testListClassesNotTransitive.txt");
        List<String> expectedLogArgs = Files.readAllLines(path);
        ArgumentCaptor<String> infoArgsCaptor = ArgumentCaptor.forClass(String.class);

        setVariableValueToObject(
                mojo,
                "remoteRepositories",
                Arrays.asList(
                        "central::default::https://repo.maven.apache.org/maven2",
                        "central::::https://repo.maven.apache.org/maven2",
                        "https://repo.maven.apache.org/maven2"));
        mojo.setArtifact("org.apache.commons:commons-lang3:3.6");
        setVariableValueToObject(mojo, "transitive", Boolean.FALSE);

        Log log = Mockito.mock(Log.class);
        mojo.setLog(log);

        mojo.execute();

        Mockito.verify(log, Mockito.times(expectedLogArgs.size())).info(infoArgsCaptor.capture());
        Assert.assertEquals(expectedLogArgs, infoArgsCaptor.getAllValues());
    }

    public void testListClassesNotTransitiveByGAV() throws Exception {
        Path path = Paths.get("src/test/resources/unit/list-test/testListClassesNotTransitive.txt");
        List<String> expectedLogArgs = Files.readAllLines(path);
        ArgumentCaptor<String> infoArgsCaptor = ArgumentCaptor.forClass(String.class);

        setVariableValueToObject(
                mojo,
                "remoteRepositories",
                Arrays.asList(
                        "central1::default::https://repo.maven.apache.org/maven2",
                        "central2::::https://repo.maven.apache.org/maven2",
                        "https://repo.maven.apache.org/maven2"));

        mojo.setGroupId("org.apache.commons");
        mojo.setArtifactId("commons-lang3");
        mojo.setVersion("3.6");

        setVariableValueToObject(mojo, "transitive", Boolean.FALSE);

        Log log = Mockito.mock(Log.class);
        mojo.setLog(log);

        mojo.execute();

        Mockito.verify(log, Mockito.times(expectedLogArgs.size())).info(infoArgsCaptor.capture());
        Assert.assertEquals(expectedLogArgs, infoArgsCaptor.getAllValues());
    }

    public void testListClassesTransitive() throws Exception {
        Path path = Paths.get("src/test/resources/unit/list-test/testListClassesTransitive.txt");
        List<String> expectedLogArgs = Files.readAllLines(path);
        ArgumentCaptor<String> infoArgsCaptor = ArgumentCaptor.forClass(String.class);

        setVariableValueToObject(
                mojo,
                "remoteRepositories",
                Arrays.asList(
                        "central::default::https://repo.maven.apache.org/maven2",
                        "central::::https://repo.maven.apache.org/maven2",
                        "https://repo.maven.apache.org/maven2"));

        mojo.setArtifact("org.apache.commons:commons-lang3:3.6");
        setVariableValueToObject(mojo, "transitive", Boolean.TRUE);

        Log log = Mockito.mock(Log.class);
        mojo.setLog(log);

        mojo.execute();

        Mockito.verify(log, Mockito.times(expectedLogArgs.size())).info(infoArgsCaptor.capture());
        Assert.assertEquals(expectedLogArgs, infoArgsCaptor.getAllValues());
    }

    public void testListClassesTransitiveByGAV() throws Exception {
        Path path = Paths.get("src/test/resources/unit/list-test/testListClassesTransitive.txt");
        List<String> expectedLogArgs = Files.readAllLines(path);
        ArgumentCaptor<String> infoArgsCaptor = ArgumentCaptor.forClass(String.class);

        setVariableValueToObject(
                mojo,
                "remoteRepositories",
                Arrays.asList(
                        "central::default::https://repo.maven.apache.org/maven2",
                        "central::::https://repo.maven.apache.org/maven2",
                        "https://repo.maven.apache.org/maven2"));
        mojo.setGroupId("org.apache.commons");
        mojo.setArtifactId("commons-lang3");
        mojo.setVersion("3.6");
        setVariableValueToObject(mojo, "transitive", Boolean.TRUE);

        Log log = Mockito.mock(Log.class);
        mojo.setLog(log);

        mojo.execute();

        Mockito.verify(log, Mockito.times(expectedLogArgs.size())).info(infoArgsCaptor.capture());
        Assert.assertEquals(expectedLogArgs, infoArgsCaptor.getAllValues());
    }
}
