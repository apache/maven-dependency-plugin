package org.apache.maven.plugins.dependency.fromDependencies;

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
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.project.MavenProject;

public class TestBuildClasspathMojo
    extends AbstractDependencyMojoTestCase
{
    
    private BuildClasspathMojo mojo;

    protected void setUp()
        throws Exception
    {
        // required for mojo lookups to work
        super.setUp( "build-classpath", true );
        
        File testPom = new File( getBasedir(), "target/test-classes/unit/build-classpath-test/plugin-config.xml" );
        mojo = (BuildClasspathMojo) lookupMojo( "build-classpath", testPom );

        assertNotNull( mojo );
        assertNotNull( mojo.getProject() );
    }

    /**
     * Tests the proper discovery and configuration of the mojo.
     */
    public void testEnvironment()
        throws Exception
    {
        MavenProject project = mojo.getProject();

        // mojo.silent = true;
        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll( directArtifacts );

        project.setArtifacts( artifacts );
        project.setDependencyArtifacts( directArtifacts );

        mojo.execute();
        try
        {
            mojo.readClasspathFile();

            fail( "Expected an illegal Argument Exception" );
        }
        catch ( IllegalArgumentException e )
        {
            // expected to catch this.
        }

        mojo.setOutputFile( new File( testDir, "buildClasspath.txt" ) );
        mojo.execute();

        String file = mojo.readClasspathFile();
        assertNotNull( file );
        assertTrue( file.length() > 0 );

        assertTrue( file.contains( File.pathSeparator ) );
        assertTrue( file.contains( File.separator ) );

        String fileSep = "#####";
        String pathSep = "%%%%%";

        mojo.setFileSeparator( fileSep );
        mojo.setPathSeparator( pathSep );
        mojo.execute();

        file = mojo.readClasspathFile();
        assertNotNull( file );
        assertTrue( file.length() > 0 );

        assertFalse( file.contains( File.pathSeparator ) );
        assertFalse( file.contains( File.separator ) );
        assertTrue( file.contains( fileSep ) );
        assertTrue( file.contains( pathSep ) );

        String propertyValue = project.getProperties().getProperty( "outputProperty" );
        assertNull( propertyValue );
        mojo.setOutputProperty( "outputProperty" );
        mojo.execute();
        propertyValue = project.getProperties().getProperty( "outputProperty" );
        assertNotNull( propertyValue );

    }

    public void testPath()
        throws Exception
    {
        MavenSession session = newMavenSession( mojo.getProject() );
        setVariableValueToObject( mojo, "session", session );
     
        LegacySupport legacySupport = lookup( LegacySupport.class );
        legacySupport.setSession( session );    
        installLocalRepository( legacySupport );
        
        Artifact artifact = stubFactory.getReleaseArtifact();

        StringBuilder sb = new StringBuilder();
        mojo.setPrefix( null );
        mojo.setStripVersion( false );
        mojo.appendArtifactPath( artifact, sb );
        assertEquals( artifact.getFile().getPath(), sb.toString() );

        mojo.setLocalRepoProperty( "$M2_REPO" );
        sb = new StringBuilder();
        mojo.appendArtifactPath( artifact, sb );
        assertEquals( "$M2_REPO" + File.separator + artifact.getFile().getName(), sb.toString() );

        mojo.setLocalRepoProperty( "%M2_REPO%" );
        sb = new StringBuilder();
        mojo.appendArtifactPath( artifact, sb );
        assertEquals( "%M2_REPO%" + File.separator + artifact.getFile().getName(), sb.toString() );

        mojo.setLocalRepoProperty( "%M2_REPO%" );
        sb = new StringBuilder();
        mojo.setPrependGroupId( true );
        mojo.appendArtifactPath( artifact, sb );
        assertEquals( "If prefix is null, prependGroupId has no impact ",
                      "%M2_REPO%" + File.separator + DependencyUtil.getFormattedFileName( artifact, false, false ),
                      sb.toString() );

        mojo.setLocalRepoProperty( "" );
        mojo.setPrefix( "prefix" );
        sb = new StringBuilder();
        mojo.setPrependGroupId( true );
        mojo.appendArtifactPath( artifact, sb );
        assertEquals( "prefix" + File.separator + DependencyUtil.getFormattedFileName( artifact, false, true ),
                      sb.toString() );
        mojo.setPrependGroupId( false );

        mojo.setLocalRepoProperty( "" );
        mojo.setPrefix( "prefix" );
        sb = new StringBuilder();
        mojo.appendArtifactPath( artifact, sb );
        assertEquals( "prefix" + File.separator + artifact.getFile().getName(), sb.toString() );

        mojo.setPrefix( "prefix" );
        mojo.setStripVersion( true );
        sb = new StringBuilder();
        mojo.appendArtifactPath( artifact, sb );
        assertEquals( "prefix" + File.separator + DependencyUtil.getFormattedFileName( artifact, true ),
                      sb.toString() );

    }
}
