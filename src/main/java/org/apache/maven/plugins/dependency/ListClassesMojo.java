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
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.transfer.artifact.ArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;


/**
 * Retrieves and lists all classes contained in the specified artifact from the specified remote repositories.
 */
@Mojo( name = "list-classes", requiresProject = false, threadSafe = true )
public class ListClassesMojo
    extends AbstractMojo
{
    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile( "(.+)::(.*)::(.+)" );

    @Parameter( defaultValue = "${session}", required = true, readonly = true )
    private MavenSession session;

    @Component
    private ArtifactResolver artifactResolver;

    @Component
    private DependencyResolver dependencyResolver;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    /**
     * Map that contains the layouts.
     */
    @Component( role = ArtifactRepositoryLayout.class )
    private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    /**
     * The repository system.
     */
    @Component
    private RepositorySystem repositorySystem;

    private DefaultDependableCoordinate coordinate = new DefaultDependableCoordinate();

    /**
     * The group ID of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter( property = "groupId" )
    private String groupId;

    /**
     * The artifact ID of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter( property = "artifactId" )
    private String artifactId;

    /**
     * The version of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter( property = "version" )
    private String version;

    /**
     * The classifier of the artifact to download. Ignored if {@link #artifact} is used.
     *
     * @since 2.3
     */
    @Parameter( property = "classifier" )
    private String classifier;

    /**
     * The packaging of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter( property = "packaging", defaultValue = "jar" )
    private String packaging = "jar";

    /**
     * Repositories in the format id::[layout]::url or just URLs, separated by comma. That is,
     * central::default::https://repo.maven.apache.org/maven2,myrepo::::https://repo.acme.com,https://repo.acme2.com
     */
    @Parameter( property = "remoteRepositories" )
    private String remoteRepositories;

    /**
     * A string of the form groupId:artifactId:version[:packaging[:classifier]].
     */
    @Parameter( property = "artifact" )
    private String artifact;

    @Parameter( defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true )
    private List<ArtifactRepository> pomRemoteRepositories;

    /**
     * Download transitively, retrieving the specified artifact and all of its dependencies.
     */
    @Parameter( property = "transitive", defaultValue = "false" )
    private boolean transitive = false;

    /**
     * Skip plugin execution completely.
     */
    @Parameter( property = "mdep.skip", defaultValue = "false" )
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        ProjectBuildingRequest buildingRequest = makeBuildingRequest();

        try
        {
            if ( transitive )
            {
                Iterable<ArtifactResult> artifacts = dependencyResolver
                        .resolveDependencies( buildingRequest, coordinate, null );

                for ( ArtifactResult result : artifacts )
                {
                    printClassesFromArtifactResult( result );
                }
            }
            else
            {
                ArtifactResult result = artifactResolver
                        .resolveArtifact( buildingRequest, toArtifactCoordinate( coordinate ) );

                printClassesFromArtifactResult( result );
            }
        }
        catch ( ArtifactResolverException | DependencyResolverException | IOException e )
        {
            throw new MojoExecutionException( "Couldn't download artifact: " + e.getMessage(), e );
        }
    }

    private void printClassesFromArtifactResult( ArtifactResult result )
            throws IOException
    {
        // open jar file in try-with-resources statement to guarantee the file closes after use regardless of errors
        try ( JarFile jarFile = new JarFile( result.getArtifact().getFile() ) )
        {
            Enumeration<JarEntry> entries = jarFile.entries();

            while ( entries.hasMoreElements() )
            {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // filter out files that do not end in .class
                if ( !entryName.endsWith( ".class" ) )
                {
                    continue;
                }

                // remove .class from the end and change format to use periods instead of forward slashes
                String className = entryName.substring( 0, entryName.length() - 6 ).replace( '/', '.' );
                getLog().info( className );
            }
        }
    }

    private ProjectBuildingRequest makeBuildingRequest()
            throws MojoExecutionException, MojoFailureException
    {
        if ( artifact == null )
        {
            throw new MojoFailureException( "You must specify an artifact, "
                    + "e.g. -Dartifact=org.apache.maven.plugins:maven-downloader-plugin:1.0" );
        }
        if ( artifact != null )
        {
            String[] tokens = artifact.split( ":" );
            if ( tokens.length < 3 || tokens.length > 5 )
            {
                throw new MojoFailureException( "Invalid artifact, you must specify "
                        + "groupId:artifactId:version[:packaging[:classifier]] " + artifact );
            }
            coordinate.setGroupId( tokens[0] );
            coordinate.setArtifactId( tokens[1] );
            coordinate.setVersion( tokens[2] );
            if ( tokens.length >= 4 )
            {
                coordinate.setType( tokens[3] );
            }
            if ( tokens.length == 5 )
            {
                coordinate.setClassifier( tokens[4] );
            }
        }

        ArtifactRepositoryPolicy always =
                new ArtifactRepositoryPolicy( true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS,
                        ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN );

        List<ArtifactRepository> repoList = new ArrayList<>();

        if ( pomRemoteRepositories != null )
        {
            repoList.addAll( pomRemoteRepositories );
        }

        if ( remoteRepositories != null )
        {
            // Use the same format as in the deploy plugin id::layout::url
            String[] repos = remoteRepositories.split( "," );
            for ( String repo : repos )
            {
                repoList.add( parseRepository( repo, always ) );
            }
        }

        ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );

        Settings settings = session.getSettings();
        repositorySystem.injectMirror( repoList, settings.getMirrors() );
        repositorySystem.injectProxy( repoList, settings.getProxies() );
        repositorySystem.injectAuthentication( repoList, settings.getServers() );

        buildingRequest.setRemoteRepositories( repoList );

        return buildingRequest;
    }

    private ArtifactCoordinate toArtifactCoordinate( DependableCoordinate dependableCoordinate )
    {
        ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler( dependableCoordinate.getType() );
        DefaultArtifactCoordinate artifactCoordinate = new DefaultArtifactCoordinate();
        artifactCoordinate.setGroupId( dependableCoordinate.getGroupId() );
        artifactCoordinate.setArtifactId( dependableCoordinate.getArtifactId() );
        artifactCoordinate.setVersion( dependableCoordinate.getVersion() );
        artifactCoordinate.setClassifier( dependableCoordinate.getClassifier() );
        artifactCoordinate.setExtension( artifactHandler.getExtension() );
        return artifactCoordinate;
    }

    protected ArtifactRepository parseRepository( String repo, ArtifactRepositoryPolicy policy )
            throws MojoFailureException
    {
        // if it's a simple url
        String id = "temp";
        ArtifactRepositoryLayout layout = getLayout( "default" );

        // if it's an extended repo URL of the form id::layout::url
        if ( repo.contains( "::" ) )
        {
            Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher( repo );
            if ( !matcher.matches() )
            {
                throw new MojoFailureException( repo, "Invalid syntax for repository: " + repo,
                        "Invalid syntax for repository. Use \"id::layout::url\" or \"URL\"." );
            }

            id = matcher.group( 1 ).trim();
            if ( !( matcher.group( 2 ) == null || matcher.group( 2 ).trim().isEmpty() ) )
            {
                layout = getLayout( matcher.group( 2 ).trim() );
            }
            repo = matcher.group( 3 ).trim();
        }
        return new MavenArtifactRepository( id, repo, layout, policy, policy );
    }

    private ArtifactRepositoryLayout getLayout( String id )
            throws MojoFailureException
    {
        ArtifactRepositoryLayout layout = repositoryLayouts.get( id );

        if ( layout == null )
        {
            throw new MojoFailureException( id, "Invalid repository layout", "Invalid repository layout: " + id );
        }

        return layout;
    }
}
