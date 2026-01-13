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

import java.net.InetAddress;
import java.util.Collections;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.maven.api.plugin.testing.MojoExtension.getTestPath;
import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

@MojoTest(realRepositorySession = true)
@Basedir("/unit/get-test")
class TestGetMojo {

    @Inject
    private MavenSession session;

    @BeforeEach
    void setUp() {
        Settings settings = new Settings();
        when(session.getSettings()).thenReturn(settings);

        Server server = new Server();
        server.setId("myserver");
        server.setUsername("foo");
        server.setPassword("bar");
        settings.addServer(server);
    }

    /**
     * Test transitive parameter
     *
     * @throws Exception in case of errors
     */
    @Test
    @InjectMojo(goal = "get")
    @MojoParameter(name = "transitive", value = "false")
    void testTransitive(GetMojo mojo) throws Exception {
        DefaultProjectBuildingRequest pbr = new DefaultProjectBuildingRequest();
        pbr.setRepositorySession(session.getRepositorySession());
        when(session.getProjectBuildingRequest()).thenReturn(pbr);

        mojo.setGroupId("org.apache.maven");
        mojo.setArtifactId("maven-model");
        mojo.setVersion("2.0.9");

        mojo.execute();
    }

    /**
     * Test remote repositories parameter
     *
     * @throws Exception in case of errors
     */
    @Test
    @InjectMojo(goal = "get")
    @MojoParameter(
            name = "remoteRepositories",
            value =
                    "central::default::https://repo.maven.apache.org/maven2,central::::https://repo.maven.apache.org/maven2,https://repo.maven.apache.org/maven2")
    void testRemoteRepositories(GetMojo mojo) throws Exception {
        DefaultProjectBuildingRequest pbr = new DefaultProjectBuildingRequest();
        pbr.setRepositorySession(session.getRepositorySession());
        when(session.getProjectBuildingRequest()).thenReturn(pbr);

        mojo.setGroupId("org.apache.maven");
        mojo.setArtifactId("maven-model");
        mojo.setVersion("2.0.9");

        mojo.execute();
    }

    /**
     * Test remote repositories parameter with basic authentication
     *
     * @throws Exception in case of errors
     */
    @Test
    @InjectMojo(goal = "get")
    void testRemoteRepositoriesAuthentication(GetMojo mojo) throws Exception {
        org.eclipse.jetty.server.Server server = createServer();
        try {
            server.start();
            ServerConnector serverConnector = (ServerConnector) server.getConnectors()[0];
            String url = "http://"
                    + (serverConnector.getHost() == null
                            ? InetAddress.getLoopbackAddress().getHostName()
                            : serverConnector.getHost());
            url = url + ":" + serverConnector.getLocalPort() + "/maven";

            setVariableValueToObject(mojo, "remoteRepositories", "myserver::default::" + url);

            DefaultProjectBuildingRequest pbr = new DefaultProjectBuildingRequest();
            pbr.setRepositorySession(session.getRepositorySession());
            when(session.getProjectBuildingRequest()).thenReturn(pbr);

            mojo.setGroupId("test");
            mojo.setArtifactId("test");
            mojo.setVersion("1.0");

            mojo.execute();
        } finally {
            server.stop();
        }
    }

    /**
     * Test parsing of the remote repositories parameter
     *
     * @throws Exception in case of errors
     */
    @Test
    @InjectMojo(goal = "get")
    void testParseRepository(GetMojo mojo) throws Exception {
        ArtifactRepositoryPolicy policy = null;
        ArtifactRepository repo =
                mojo.parseRepository("central::default::https://repo.maven.apache.org/maven2", policy);
        assertEquals("central", repo.getId());
        assertEquals(DefaultRepositoryLayout.class, repo.getLayout().getClass());
        assertEquals("https://repo.maven.apache.org/maven2", repo.getUrl());

        try {
            repo = mojo.parseRepository("central::legacy::https://repo.maven.apache.org/maven2", policy);
            fail("Exception expected: legacy repository not supported anymore");
        } catch (MojoFailureException e) {
        }

        repo = mojo.parseRepository("central::::https://repo.maven.apache.org/maven2", policy);
        assertEquals("central", repo.getId());
        assertEquals(DefaultRepositoryLayout.class, repo.getLayout().getClass());
        assertEquals("https://repo.maven.apache.org/maven2", repo.getUrl());

        repo = mojo.parseRepository("https://repo.maven.apache.org/maven2", policy);
        assertEquals("temp", repo.getId());
        assertEquals(DefaultRepositoryLayout.class, repo.getLayout().getClass());
        assertEquals("https://repo.maven.apache.org/maven2", repo.getUrl());

        try {
            mojo.parseRepository("::::https://repo.maven.apache.org/maven2", policy);
            fail("Exception expected");
        } catch (MojoFailureException e) {
            // expected
        }

        try {
            mojo.parseRepository("central::https://repo.maven.apache.org/maven2", policy);
            fail("Exception expected");
        } catch (MojoFailureException e) {
            // expected
        }
    }

    private ContextHandler createContextHandler() {
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setResourceBase(getTestPath("repository"));
        resourceHandler.setDirectoriesListed(true);

        ContextHandler contextHandler = new ContextHandler("/maven");
        contextHandler.setHandler(resourceHandler);
        return contextHandler;
    }

    private org.eclipse.jetty.server.Server createServer() {
        org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server(0);
        server.setStopAtShutdown(true);

        LoginService loginService = new HashLoginService("myrealm", getTestPath("realm.properties"));
        server.addBean(loginService);

        ConstraintSecurityHandler security = new ConstraintSecurityHandler();
        server.setHandler(security);

        Constraint constraint = new Constraint();
        constraint.setName("auth");
        constraint.setAuthenticate(true);
        constraint.setRoles(new String[] {"userrole"});

        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*");
        mapping.setConstraint(constraint);

        security.setConstraintMappings(Collections.singletonList(mapping));
        security.setAuthenticator(new BasicAuthenticator());
        security.setLoginService(loginService);

        ContextHandler contextHandler = createContextHandler();
        contextHandler.setServer(server);

        security.setHandler(contextHandler);
        server.setHandler(security);
        return server;
    }
}
