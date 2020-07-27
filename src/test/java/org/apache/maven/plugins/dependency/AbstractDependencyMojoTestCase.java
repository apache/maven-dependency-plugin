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
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;

public abstract class AbstractDependencyMojoTestCase
    extends AbstractMojoTestCase
{

    protected File testDir;

    protected DependencyArtifactStubFactory stubFactory;

    protected void setUp( String testDirStr, boolean createFiles )
        throws Exception
    {
        setUp( testDirStr, createFiles, true );
    }

    protected void setUp( String testDirStr, boolean createFiles, boolean flattenedPath )
        throws Exception
    {
        // required for mojo lookups to work
        super.setUp();
        testDir = new File( getBasedir(), "target" + File.separatorChar + "unit-tests" + File.separatorChar + testDirStr
            + File.separatorChar );
        FileUtils.deleteDirectory( testDir );
        assertFalse( testDir.exists() );

        stubFactory = new DependencyArtifactStubFactory( this.testDir, createFiles, flattenedPath );
    }

    protected void tearDown()
    {
        if ( testDir != null )
        {
            try
            {
                FileUtils.deleteDirectory( testDir );
            }
            catch ( IOException e )
            {
                e.printStackTrace();
                fail( "Trying to remove directory:" + testDir + System.lineSeparator() + e.toString() );
            }
            assertFalse( testDir.exists() );
        }
    }

    protected void copyFile( AbstractDependencyMojo mojo, File artifact, File destFile )
        throws MojoExecutionException
    {
        mojo.copyFile( artifact, destFile );
    }
    

    protected void installLocalRepository( LegacySupport legacySupport )
        throws ComponentLookupException
    {
        DefaultRepositorySystemSession repoSession =
            (DefaultRepositorySystemSession) legacySupport.getRepositorySession();
        RepositorySystem system = lookup( RepositorySystem.class );
        String directory = stubFactory.getWorkingDir().toString();
        LocalRepository localRepository = new LocalRepository( directory  );
        LocalRepositoryManager manager = system.newLocalRepositoryManager( repoSession, localRepository );
        repoSession.setLocalRepositoryManager( manager );
    }
}
