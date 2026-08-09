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
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.LocalRepository;
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
import org.junit.jupiter.api.io.TempDir;

import static org.apache.maven.api.plugin.testing.MojoExtension.getTestPath;
import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

@MojoTest(realRepositorySession = true)
@Basedir("/unit/get-test")
class TestGetMojo {

    private static final String PROXY_HOST = "proxy.invalid";

    @Inject
    private MavenSession session;

    @Inject
    private RepositorySystem repositorySystem;

    @TempDir
    private Path isolatedLocalRepository;

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
     * Test remote repositories parameter with basic authentication.
     */
    @Test
    @InjectMojo(goal = "get")
    void testRemoteRepositoriesAuthentication(GetMojo mojo) throws Exception {
        org.eclipse.jetty.server.Server server = createServer();
        try {
            server.start();

            setVariableValueToObject(mojo, "remoteRepositories", "myserver::default::" + serverUrl(server));

            useIsolatedLocalRepository();

            mojo.setGroupId("test");
            mojo.setArtifactId("test");
            mojo.setVersion("1.0");

            mojo.execute();
        } finally {
            server.stop();
        }
    }

    /**
     * Test that an active proxy from the settings is applied to the repositories given by the
     * <code>remoteRepositories</code> parameter. The proxy points at a host that cannot resolve, so a successful
     * resolution would mean the proxy was never applied.
     */
    @Test
    @InjectMojo(goal = "get")
    void testRemoteRepositoriesProxy(GetMojo mojo) throws Exception {
        org.eclipse.jetty.server.Server server = createServer();
        try {
            server.start();

            setVariableValueToObject(mojo, "remoteRepositories", "myserver::default::" + serverUrl(server));

            session.getSettings().addProxy(createProxy(null));
            useIsolatedLocalRepository();

            mojo.setGroupId("test");
            mojo.setArtifactId("test");
            mojo.setVersion("1.0");

            MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
            assertTrue(
                    mentionsProxyHost(e),
                    "Expected the resolution to have been attempted through the unreachable proxy, got: " + e);
        } finally {
            server.stop();
        }
    }

    /**
     * Test that <code>nonProxyHosts</code> excludes a repository from the proxy that would otherwise match it: the
     * same unreachable proxy as above must not be applied, so the resolution has to succeed.
     * <p>
     * This one asserts a success, so on its own it cannot tell "nonProxyHosts was honoured" apart from "proxies are
     * not applied at all". It is only meaningful together with {@link #testRemoteRepositoriesProxy(GetMojo)}, which
     * fails in that second case -- do not delete one and keep the other.
     */
    @Test
    @InjectMojo(goal = "get")
    void testRemoteRepositoriesNonProxyHosts(GetMojo mojo) throws Exception {
        org.eclipse.jetty.server.Server server = createServer();
        try {
            server.start();
            String url = serverUrl(server);

            setVariableValueToObject(mojo, "remoteRepositories", "myserver::default::" + url);

            session.getSettings().addProxy(createProxy(URI.create(url).getHost()));
            useIsolatedLocalRepository();

            mojo.setGroupId("test");
            mojo.setArtifactId("test");
            mojo.setVersion("1.0");

            mojo.execute();
        } finally {
            server.stop();
        }
    }

    /**
     * Points the mojo at an empty local repository, so that the tests above depend on the transfer actually
     * happening rather than on what an earlier run left behind in the shared one.
     */
    private void useIsolatedLocalRepository() {
        DefaultRepositorySystemSession repositorySession =
                new DefaultRepositorySystemSession(session.getRepositorySession());
        repositorySession.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(
                repositorySession, new LocalRepository(isolatedLocalRepository.toFile())));
        when(session.getRepositorySession()).thenReturn(repositorySession);

        DefaultProjectBuildingRequest pbr = new DefaultProjectBuildingRequest();
        pbr.setRepositorySession(repositorySession);
        when(session.getProjectBuildingRequest()).thenReturn(pbr);
    }

    /**
     * Whether the failure came from the proxy rather than from, say, a missing artifact or a rejected login — the
     * host of {@link #createProxy(String)} only ever appears if the proxy really was applied to the repository.
     */
    private boolean mentionsProxyHost(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(PROXY_HOST)) {
                return true;
            }
        }
        return false;
    }

    /**
     * An active HTTP proxy pointing at a name that is guaranteed not to resolve (RFC 2606 reserved TLD), so that
     * applying it is always observable as a failure.
     */
    private Proxy createProxy(String nonProxyHosts) {
        Proxy proxy = new Proxy();
        proxy.setActive(true);
        proxy.setProtocol("http");
        proxy.setHost(PROXY_HOST);
        proxy.setPort(3128);
        proxy.setNonProxyHosts(nonProxyHosts);
        return proxy;
    }

    private String serverUrl(org.eclipse.jetty.server.Server server) throws Exception {
        ServerConnector serverConnector = (ServerConnector) server.getConnectors()[0];
        String host = serverConnector.getHost() == null
                ? InetAddress.getLoopbackAddress().getHostName()
                : serverConnector.getHost();
        return "http://" + host + ":" + serverConnector.getLocalPort() + "/maven";
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
