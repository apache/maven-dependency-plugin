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

import javax.inject.Inject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.maven.api.di.Provides;
import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.internal.aether.DefaultRepositorySystemSessionFactory;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.maven.api.plugin.testing.MojoExtension.getBasedir;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MojoTest
class TestListClassesMojo {

    @Inject
    private MavenSession mavenSession;

    @Inject
    private DefaultRepositorySystemSessionFactory repoSessionFactory;

    @Mock
    private Log log;

    @Provides
    @SuppressWarnings("unused")
    private Log logProvides() {
        return log;
    }

    @BeforeEach
    void setUp() {
        ArtifactRepository localRepo = Mockito.mock(ArtifactRepository.class);
        when(localRepo.getBasedir()).thenReturn(new File(getBasedir(), "target/local-repo").getAbsolutePath());

        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setLocalRepository(localRepo);

        RepositorySystemSession systemSession = repoSessionFactory.newRepositorySession(request);
        when(mavenSession.getRepositorySession()).thenReturn(systemSession);
        when(mavenSession.getRequest()).thenReturn(request);
    }

    @Test
    @InjectMojo(goal = "list-classes")
    @MojoParameter(
            name = "remoteRepositories",
            value = "central::default::https://repo.maven.apache.org/maven2,"
                    + "central::::https://repo.maven.apache.org/maven2,"
                    + "https://repo.maven.apache.org/maven2")
    @MojoParameter(name = "transitive", value = "false")
    @MojoParameter(name = "artifact", value = "org.apache.commons:commons-lang3:3.6")
    @Basedir("/unit/list-test")
    void testListClassesNotTransitive(ListClassesMojo mojo) throws Exception {
        Path path = Paths.get(getBasedir(), "testListClassesNotTransitive.txt");
        List<String> expectedLogArgs = Files.readAllLines(path);
        ArgumentCaptor<String> infoArgsCaptor = ArgumentCaptor.forClass(String.class);

        mojo.execute();

        verify(log, Mockito.times(expectedLogArgs.size())).info(infoArgsCaptor.capture());
        Assertions.assertEquals(expectedLogArgs, infoArgsCaptor.getAllValues());
    }

    @Test
    @InjectMojo(goal = "list-classes")
    @MojoParameter(
            name = "remoteRepositories",
            value = "central::default::https://repo.maven.apache.org/maven2,"
                    + "central::::https://repo.maven.apache.org/maven2,"
                    + "https://repo.maven.apache.org/maven2")
    @MojoParameter(name = "transitive", value = "false")
    @MojoParameter(name = "groupId", value = "org.apache.commons")
    @MojoParameter(name = "artifactId", value = "commons-lang3")
    @MojoParameter(name = "version", value = "3.6")
    @Basedir("/unit/list-test")
    void testListClassesNotTransitiveByGAV(ListClassesMojo mojo) throws Exception {
        Path path = Paths.get(getBasedir(), "testListClassesNotTransitive.txt");
        List<String> expectedLogArgs = Files.readAllLines(path);
        ArgumentCaptor<String> infoArgsCaptor = ArgumentCaptor.forClass(String.class);

        mojo.execute();

        verify(log, Mockito.times(expectedLogArgs.size())).info(infoArgsCaptor.capture());
        Assertions.assertEquals(expectedLogArgs, infoArgsCaptor.getAllValues());
    }

    @Test
    @InjectMojo(goal = "list-classes")
    @MojoParameter(
            name = "remoteRepositories",
            value = "central::default::https://repo.maven.apache.org/maven2,"
                    + "central::::https://repo.maven.apache.org/maven2,"
                    + "https://repo.maven.apache.org/maven2")
    @MojoParameter(name = "transitive", value = "true")
    @MojoParameter(name = "artifact", value = "org.apache.commons:commons-lang3:3.6")
    @Basedir("/unit/list-test")
    void testListClassesTransitive(ListClassesMojo mojo) throws Exception {
        Path path = Paths.get(getBasedir(), "testListClassesTransitive.txt");
        List<String> expectedLogArgs = Files.readAllLines(path);
        ArgumentCaptor<String> infoArgsCaptor = ArgumentCaptor.forClass(String.class);

        mojo.execute();

        verify(log, Mockito.times(expectedLogArgs.size())).info(infoArgsCaptor.capture());
        Assertions.assertEquals(expectedLogArgs, infoArgsCaptor.getAllValues());
    }

    @Test
    @InjectMojo(goal = "list-classes")
    @MojoParameter(
            name = "remoteRepositories",
            value = "central::default::https://repo.maven.apache.org/maven2,"
                    + "central::::https://repo.maven.apache.org/maven2,"
                    + "https://repo.maven.apache.org/maven2")
    @MojoParameter(name = "transitive", value = "true")
    @MojoParameter(name = "groupId", value = "org.apache.commons")
    @MojoParameter(name = "artifactId", value = "commons-lang3")
    @MojoParameter(name = "version", value = "3.6")
    @Basedir("/unit/list-test")
    void testListClassesTransitiveByGAV(ListClassesMojo mojo) throws Exception {
        Path path = Paths.get(getBasedir(), "testListClassesTransitive.txt");
        List<String> expectedLogArgs = Files.readAllLines(path);
        ArgumentCaptor<String> infoArgsCaptor = ArgumentCaptor.forClass(String.class);

        mojo.execute();

        verify(log, Mockito.times(expectedLogArgs.size())).info(infoArgsCaptor.capture());
        Assertions.assertEquals(expectedLogArgs, infoArgsCaptor.getAllValues());
    }
}
