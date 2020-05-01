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

import junit.framework.TestCase;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.ProjectBuildingRequest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.apache.maven.plugins.dependency.AbstractDependencyMojoTest.ConcreteDependencyMojo.createConcreteDependencyMojoWithArtifactRepositories;
import static org.apache.maven.plugins.dependency.AbstractDependencyMojoTest.ConcreteDependencyMojo.createConcreteDependencyMojoWithPluginRepositories;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractDependencyMojoTest extends TestCase
{
    private MavenSession session = mock( MavenSession.class );

    private ProjectBuildingRequest buildingRequest = mock( ProjectBuildingRequest.class );

    private ArrayList<ArtifactRepository> artifactRepos = new ArrayList<>();

    private ArrayList<ArtifactRepository> pluginRepos = new ArrayList<>();

    static class ConcreteDependencyMojo extends AbstractDependencyMojo
    {
        static ConcreteDependencyMojo createConcreteDependencyMojoWithArtifactRepositories(
                MavenSession mavenSession, List<ArtifactRepository> artifactRepos )
                throws NoSuchFieldException, IllegalAccessException
        {
            ConcreteDependencyMojo cdm = new ConcreteDependencyMojo();
            cdm.session = mavenSession;

            Field par = AbstractDependencyMojo.class.getDeclaredField( "remoteRepositories" );
            par.setAccessible( true );
            par.set( cdm, artifactRepos );

            return cdm;
        }

        static ConcreteDependencyMojo createConcreteDependencyMojoWithPluginRepositories(
                MavenSession mavenSession, List<ArtifactRepository> pluginRepos )
                throws NoSuchFieldException, IllegalAccessException
        {
            ConcreteDependencyMojo cdm = new ConcreteDependencyMojo();
            cdm.session = mavenSession;

            Field par = AbstractDependencyMojo.class.getDeclaredField( "remotePluginRepositories" );
            par.setAccessible( true );
            par.set( cdm, pluginRepos );

            return cdm;
        }

        @Override
        protected void doExecute()
        {
        }
    }

    @Override
    protected void setUp() throws Exception
    {
        pluginRepos.add( newRepositoryWithId( "pr-central" ) );
        pluginRepos.add( newRepositoryWithId( "pr-plugins" ) );

        artifactRepos.add( newRepositoryWithId( "ar-central" ) );
        artifactRepos.add( newRepositoryWithId( "ar-snapshots" ) );
        artifactRepos.add( newRepositoryWithId( "ar-staging" ) );

        when( session.getProjectBuildingRequest() ).thenReturn( buildingRequest );
    }

    private static ArtifactRepository newRepositoryWithId( String id )
    {
        ArtifactRepository repo = mock( ArtifactRepository.class );
        when( repo.getId() ).thenReturn( id );
        return repo;
    }

    public void testNewResolveArtifactProjectBuildingRequestRemoteRepositoriesSize()
            throws NoSuchFieldException, IllegalAccessException
    {
        AbstractDependencyMojo mojo = createConcreteDependencyMojoWithArtifactRepositories( session, artifactRepos );

        ProjectBuildingRequest pbr = mojo.newResolveArtifactProjectBuildingRequest();
        List<ArtifactRepository> rrepos = pbr.getRemoteRepositories();

        assertEquals( 3, rrepos.size() );
    }

    public void testNewResolveArtifactProjectBuildingRequestRemoteRepositoriesContents()
            throws NoSuchFieldException, IllegalAccessException
    {
        AbstractDependencyMojo mojo = createConcreteDependencyMojoWithArtifactRepositories( session, artifactRepos );

        ProjectBuildingRequest pbr = mojo.newResolveArtifactProjectBuildingRequest();
        List<ArtifactRepository> rrepos = pbr.getRemoteRepositories();

        assertEquals( "ar-central", rrepos.get( 0 ).getId() );
        assertEquals( "ar-snapshots", rrepos.get( 1 ).getId() );
        assertEquals( "ar-staging", rrepos.get( 2 ).getId() );
    }

    public void testNewResolvePluginProjectBuildingRequestRemoteRepositoriesSize()
            throws NoSuchFieldException, IllegalAccessException
    {
        AbstractDependencyMojo mojo = createConcreteDependencyMojoWithPluginRepositories( session, pluginRepos );

        ProjectBuildingRequest pbr = mojo.newResolvePluginProjectBuildingRequest();
        List<ArtifactRepository> rrepos = pbr.getRemoteRepositories();

        assertEquals( 2, rrepos.size() );
    }

    public void testNewResolvePluginProjectBuildingRequestRemoteRepositoriesContents()
            throws NoSuchFieldException, IllegalAccessException
    {
        AbstractDependencyMojo mojo = createConcreteDependencyMojoWithPluginRepositories( session, pluginRepos );

        ProjectBuildingRequest pbr = mojo.newResolvePluginProjectBuildingRequest();
        List<ArtifactRepository> rrepos = pbr.getRemoteRepositories();

        assertEquals( "pr-central", rrepos.get( 0 ).getId() );
        assertEquals( "pr-plugins", rrepos.get( 1 ).getId() );
    }
}