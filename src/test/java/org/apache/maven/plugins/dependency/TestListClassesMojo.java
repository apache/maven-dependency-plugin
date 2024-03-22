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
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.dependency.testUtils.stubs.DependencyProjectStub;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class TestListClassesMojo extends AbstractDependencyMojoTestCase {
    private ListClassesMojo mojo;

    protected void setUp() throws Exception {
        super.setUp("markers", false);

        MavenProject project = new DependencyProjectStub();
        getContainer().addComponent(project, MavenProject.class.getName());

        MavenSession session = newMavenSession(project);
        getContainer().addComponent(session, MavenSession.class.getName());

        File testPom = new File(getBasedir(), "target/test-classes/unit/get-test/plugin-config.xml");

        assertTrue(testPom.exists());
        mojo = (ListClassesMojo) lookupMojo("list-classes", testPom);

        assertNotNull(mojo);

        LegacySupport legacySupport = lookup(LegacySupport.class);
        Settings settings = session.getSettings();
        Server server = new Server();
        server.setId("myserver");
        server.setUsername("foo");
        server.setPassword("bar");
        settings.addServer(server);
        legacySupport.setSession(session);

        installLocalRepository(legacySupport);
    }

    public void testListClassesNotTransitive() throws Exception {
        Path path = Paths.get("src/test/resources/unit/list-test/testListClassesNotTransitive.txt");
        List<String> expectedLogArgs = Files.readAllLines(path);
        ArgumentCaptor<String> infoArgsCaptor = ArgumentCaptor.forClass(String.class);

        setVariableValueToObject(
                mojo,
                "remoteRepositories",
                "central::default::https://repo.maven.apache.org/maven2,"
                        + "central::::https://repo.maven.apache.org/maven2," + "https://repo.maven.apache.org/maven2");
        setVariableValueToObject(mojo, "artifact", "org.apache.commons:commons-lang3:3.6");
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
                "central::default::https://repo.maven.apache.org/maven2,"
                        + "central::::https://repo.maven.apache.org/maven2," + "https://repo.maven.apache.org/maven2");
        setVariableValueToObject(mojo, "groupId", "org.apache.commons");
        setVariableValueToObject(mojo, "artifactId", "commons-lang3");
        setVariableValueToObject(mojo, "version", "3.6");
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
                "central::default::https://repo.maven.apache.org/maven2,"
                        + "central::::https://repo.maven.apache.org/maven2," + "https://repo.maven.apache.org/maven2");
        setVariableValueToObject(mojo, "artifact", "org.apache.commons:commons-lang3:3.6");
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
                "central::default::https://repo.maven.apache.org/maven2,"
                        + "central::::https://repo.maven.apache.org/maven2," + "https://repo.maven.apache.org/maven2");
        setVariableValueToObject(mojo, "groupId", "org.apache.commons");
        setVariableValueToObject(mojo, "artifactId", "commons-lang3");
        setVariableValueToObject(mojo, "version", "3.6");
        setVariableValueToObject(mojo, "transitive", Boolean.TRUE);

        Log log = Mockito.mock(Log.class);
        mojo.setLog(log);

        mojo.execute();

        Mockito.verify(log, Mockito.times(expectedLogArgs.size())).info(infoArgsCaptor.capture());
        Assert.assertEquals(expectedLogArgs, infoArgsCaptor.getAllValues());
    }
}
