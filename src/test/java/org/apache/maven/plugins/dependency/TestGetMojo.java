package org.apache.maven.plugins.dependency;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.security.Constraint;

public class TestGetMojo
    extends AbstractDependencyMojoTestCase
{
    private GetMojo mojo;

    protected void setUp()
        throws Exception
    {
        // required for mojo lookups to work
        super.setUp( "markers", false );

        File testPom = new File( getBasedir(), "target/test-classes/unit/get-test/plugin-config.xml" );
        mojo = (GetMojo) lookupMojo( "get", testPom );

        assertNotNull( mojo );

        LegacySupport legacySupport = lookup( LegacySupport.class );
        MavenSession mavenSession = newMavenSession( new MavenProjectStub() );
        Settings settings = mavenSession.getSettings();
        Server server = new Server();
        server.setId( "myserver" );
        server.setUsername( "foo" );
        server.setPassword( "bar" );
        settings.addServer( server );
        legacySupport.setSession( mavenSession );
        
        installLocalRepository( legacySupport );
        
        setVariableValueToObject( mojo, "session", legacySupport.getSession() );
    }

    /**
     * Test transitive parameter
     * 
     * @throws Exception in case of errors
     */
    public void testTransitive()
        throws Exception
    {
        // Set properties, transitive = default value = true
        setVariableValueToObject( mojo, "transitive", Boolean.FALSE );
        setVariableValueToObject( mojo, "remoteRepositories",
                                  "central::default::https://repo.maven.apache.org/maven2" );
        mojo.setGroupId( "org.apache.maven" );
        mojo.setArtifactId( "maven-model" );
        mojo.setVersion( "2.0.9" );

        mojo.execute();

        // Set properties, transitive = false
        setVariableValueToObject( mojo, "transitive", Boolean.FALSE );
        mojo.execute();
    }

    /**
     * Test remote repositories parameter
     * 
     * @throws Exception in case of errors
     */
    public void testRemoteRepositories()
        throws Exception
    {
        setVariableValueToObject( mojo, "remoteRepositories", "central::default::https://repo.maven.apache.org/maven2,"
            + "central::::https://repo.maven.apache.org/maven2," + "https://repo.maven.apache.org/maven2" );
        mojo.setGroupId( "org.apache.maven" );
        mojo.setArtifactId( "maven-model" );
        mojo.setVersion( "2.0.9" );

        mojo.execute();
    }

    /**
     * Test remote repositories parameter with basic authentication
     *
     * @throws Exception in case of errors
     */
    public void testRemoteRepositoriesAuthentication()
        throws Exception
    {
        org.eclipse.jetty.server.Server server = createServer();
        try {
            server.start();

            setVariableValueToObject( mojo, "remoteRepositories", "myserver::default::" + server.getURI() );
            mojo.setGroupId( "test" );
            mojo.setArtifactId( "test" );
            mojo.setVersion( "1.0" );

            mojo.execute();
        }
        finally
        {
            server.stop();
        }
    }

    /**
     * Test parsing of the remote repositories parameter
     * 
     * @throws Exception in case of errors
     */
    public void testParseRepository()
        throws Exception
    {
        ArtifactRepositoryPolicy policy = null;
        ArtifactRepository repo = mojo.parseRepository( "central::default::https://repo.maven.apache.org/maven2", policy );
        assertEquals( "central", repo.getId() );
        assertEquals( DefaultRepositoryLayout.class, repo.getLayout().getClass() );
        assertEquals( "https://repo.maven.apache.org/maven2", repo.getUrl() );

        try
        {
            repo = mojo.parseRepository( "central::legacy::https://repo.maven.apache.org/maven2", policy );
            fail( "Exception expected: legacy repository not supported anymore" );
        }
        catch ( MojoFailureException e )
        {
        }

        repo = mojo.parseRepository( "central::::https://repo.maven.apache.org/maven2", policy );
        assertEquals( "central", repo.getId() );
        assertEquals( DefaultRepositoryLayout.class, repo.getLayout().getClass() );
        assertEquals( "https://repo.maven.apache.org/maven2", repo.getUrl() );

        repo = mojo.parseRepository( "https://repo.maven.apache.org/maven2", policy );
        assertEquals( "temp", repo.getId() );
        assertEquals( DefaultRepositoryLayout.class, repo.getLayout().getClass() );
        assertEquals( "https://repo.maven.apache.org/maven2", repo.getUrl() );

        try
        {
            mojo.parseRepository( "::::https://repo.maven.apache.org/maven2", policy );
            fail( "Exception expected" );
        }
        catch ( MojoFailureException e )
        {
            // expected
        }

        try
        {
            mojo.parseRepository( "central::https://repo.maven.apache.org/maven2", policy );
            fail( "Exception expected" );
        }
        catch ( MojoFailureException e )
        {
            // expected
        }
    }

    private ContextHandler createContextHandler()
    {
        ResourceHandler resourceHandler = new ResourceHandler();
        Path resourceDirectory = Paths.get( "src", "test", "resources", "unit", "get-test", "repository" );
        resourceHandler.setResourceBase( resourceDirectory.toString() );
        resourceHandler.setDirectoriesListed( true );

        ContextHandler contextHandler = new ContextHandler( "/maven" );
        contextHandler.setHandler( resourceHandler );
        return contextHandler;
    }

    private org.eclipse.jetty.server.Server createServer()
    {
        org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server( 0 );
        server.setStopAtShutdown( true );

        LoginService loginService = new HashLoginService( "myrealm",
            "src/test/resources/unit/get-test/realm.properties" );
        server.addBean( loginService );

        ConstraintSecurityHandler security = new ConstraintSecurityHandler();
        server.setHandler( security );

        Constraint constraint = new Constraint();
        constraint.setName( "auth" );
        constraint.setAuthenticate( true );
        constraint.setRoles(new String[]{ "userrole" });

        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec( "/*" );
        mapping.setConstraint( constraint );

        security.setConstraintMappings( Collections.singletonList( mapping ) );
        security.setAuthenticator( new BasicAuthenticator() );
        security.setLoginService( loginService );

        ContextHandler contextHandler = createContextHandler();
        contextHandler.setServer( server );

        security.setHandler( contextHandler );
        server.setHandler( security );
        return server;
    }
}
