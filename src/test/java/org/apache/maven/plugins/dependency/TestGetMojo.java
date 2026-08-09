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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.dependency.utils.ParamArtifact;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.DefaultAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.apache.maven.api.plugin.testing.MojoExtension.getTestPath;
import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    private DefaultRepositorySystemSession repositorySession;

    /**
     * Test transitive parameter
     *
     * @throws Exception in case of errors
     */
    @Test
    @InjectMojo(goal = "get")
    @MojoParameter(name = "transitive", value = "false")
    void testTransitive(GetMojo mojo) throws Exception {
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
                    "central::default::https://repo.maven.apache.org/maven2,central::::https://repo.maven.apache.org/maven2,central::https://repo.maven.apache.org/maven2,https://repo.maven.apache.org/maven2")
    void testRemoteRepositories(GetMojo mojo) throws Exception {
        mojo.setGroupId("org.apache.maven");
        mojo.setArtifactId("maven-model");
        mojo.setVersion("2.0.9");

        mojo.execute();
    }

    @Test
    void testExplicitRemoteRepositoriesAlwaysRefresh() throws Exception {
        ResolverUtil resolverUtil = mock(ResolverUtil.class);
        when(resolverUtil.remoteRepositories(anyList(), eq(RepositoryPolicy.UPDATE_POLICY_ALWAYS)))
                .thenReturn(Collections.emptyList());
        GetMojo mojo = new GetMojo(resolverUtil);
        setVariableValueToObject(mojo, "remoteRepositories", "central::default::https://repo.maven.apache.org/maven2");
        mojo.setGroupId("org.apache.maven");
        mojo.setArtifactId("maven-model");
        mojo.setVersion("2.0.9");

        mojo.execute();

        verify(resolverUtil)
                .remoteRepositories(
                        Collections.singletonList("central::default::https://repo.maven.apache.org/maven2"),
                        RepositoryPolicy.UPDATE_POLICY_ALWAYS);
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
     * Test that an active proxy from the repository session is applied to the repositories given by the
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

            useIsolatedLocalRepository();
            repositorySession.setProxySelector(createProxySelector(null));

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

            useIsolatedLocalRepository();
            repositorySession.setProxySelector(
                    createProxySelector(URI.create(url).getHost()));

            mojo.setGroupId("test");
            mojo.setArtifactId("test");
            mojo.setVersion("1.0");

            mojo.execute();
        } finally {
            server.stop();
        }
    }

    @Test
    void testVersionIsRequired() {
        GetMojo mojo = new GetMojo(mock(ResolverUtil.class));
        mojo.setGroupId("org.apache.maven");
        mojo.setArtifactId("maven-model");

        MojoFailureException exception = assertThrows(MojoFailureException.class, mojo::execute);

        assertEquals(
                "You must specify an artifact, "
                        + "e.g. -Dartifact=org.apache.maven.plugins:maven-downloader-plugin:1.0",
                exception.getMessage());
    }

    @Test
    void testArtifactRetainsSeparatePackagingAndClassifier() throws Exception {
        ResolverUtil resolverUtil = mock(ResolverUtil.class);
        when(resolverUtil.remoteRepositories(anyList(), eq(RepositoryPolicy.UPDATE_POLICY_ALWAYS)))
                .thenReturn(Collections.emptyList());
        GetMojo mojo = new GetMojo(resolverUtil);
        setVariableValueToObject(mojo, "artifact", "org.apache.maven:maven-model:2.0.9");
        mojo.setPackaging("test-jar");
        mojo.setClassifier("tests");

        mojo.execute();

        ArgumentCaptor<ParamArtifact> coordinate = ArgumentCaptor.forClass(ParamArtifact.class);
        verify(resolverUtil).createArtifactFromParams(coordinate.capture());
        assertEquals("test-jar", coordinate.getValue().getPackaging());
        assertEquals("tests", coordinate.getValue().getClassifier());
    }

    @Test
    void testArtifactPackagingAndClassifierOverrideSeparateParameters() throws Exception {
        ResolverUtil resolverUtil = mock(ResolverUtil.class);
        when(resolverUtil.remoteRepositories(anyList(), eq(RepositoryPolicy.UPDATE_POLICY_ALWAYS)))
                .thenReturn(Collections.emptyList());
        GetMojo mojo = new GetMojo(resolverUtil);
        setVariableValueToObject(mojo, "artifact", "org.apache.maven:maven-model:2.0.9:jar:sources");
        mojo.setPackaging("test-jar");
        mojo.setClassifier("tests");

        mojo.execute();

        ArgumentCaptor<ParamArtifact> coordinate = ArgumentCaptor.forClass(ParamArtifact.class);
        verify(resolverUtil).createArtifactFromParams(coordinate.capture());
        assertEquals("jar", coordinate.getValue().getPackaging());
        assertEquals("sources", coordinate.getValue().getClassifier());
    }

    /**
     * Points the mojo at an empty local repository, so that repository tests depend on the transfer actually
     * happening rather than on what an earlier run left behind in the shared one.
     */
    private void useIsolatedLocalRepository() {
        repositorySession = new DefaultRepositorySystemSession(session.getRepositorySession());
        repositorySession.setAuthenticationSelector(new DefaultAuthenticationSelector()
                .add(
                        "myserver",
                        new AuthenticationBuilder()
                                .addUsername("foo")
                                .addPassword("bar")
                                .build()));
        repositorySession.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(
                repositorySession, new LocalRepository(isolatedLocalRepository.toFile())));
        when(session.getRepositorySession()).thenReturn(repositorySession);

        DefaultProjectBuildingRequest pbr = new DefaultProjectBuildingRequest();
        pbr.setRepositorySession(repositorySession);
        when(session.getProjectBuildingRequest()).thenReturn(pbr);
    }

    /**
     * Whether the failure came from the proxy rather than from, say, a missing artifact or a rejected login -- the
     * host of {@link #createProxySelector(String)} only ever appears if the proxy really was applied to the repository.
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
    private DefaultProxySelector createProxySelector(String nonProxyHosts) {
        return new DefaultProxySelector().add(new Proxy("http", PROXY_HOST, 3128), nonProxyHosts);
    }

    private String serverUrl(org.eclipse.jetty.server.Server server) throws Exception {
        ServerConnector serverConnector = (ServerConnector) server.getConnectors()[0];
        String host = serverConnector.getHost() == null
                ? InetAddress.getLoopbackAddress().getHostName()
                : serverConnector.getHost();
        return "http://" + host + ":" + serverConnector.getLocalPort() + "/maven";
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
