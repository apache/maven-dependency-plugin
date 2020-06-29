package org.apache.maven.plugins.dependency.resolvers;

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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.AbstractDependencyMojo;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.transfer.artifact.ArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;

/**
 * Goal that resolves all project plugin dependencies and then lists the repositories used by the build
 *
 * @author <a href="mailto:pim.moerenhout@gmail.com">Pim Moerenhout</a>
 * @since 3.1.2
 */
@Mojo( name = "list-plugin-repositories", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true )
public class ListPluginRepositoriesMojo
    extends AbstractDependencyMojo
{
    /**
     * Maven Project Builder component.
    */
    @Component
    private ProjectBuilder projectBuilder;

    /**
     * Component used to resolve artifacts and download their files from remote repositories.
     */
    @Component
    private ArtifactResolver artifactResolver;

    /**
     * The system settings for Maven. This is the instance resulting from
     * merging global and user-level settings files.
     */
    @Parameter( defaultValue = "${settings}", readonly = true, required = true )
    private Settings settings;

    /**
     * Plugin repositories used for the project.
     */
    @Parameter( defaultValue = "${project.pluginArtifactRepositories}", readonly = true, required = true )
    private List<ArtifactRepository> pluginRepositories;

    /**
     * Sets whether the plugin runs in verbose mode. As of plugin version 2.3, the default value is derived from Maven's
     * global debug flag (compare command line switch <code>-X</code>).
     *
     * @since 3.1.2
     */
    @Parameter( property = "verbose" )
    private boolean verbose;

    /**
     * Displays a list of the plugin repositories used by this build.
     *
     * @throws MojoExecutionException with a message if an error occurs
     */
    @Override
    protected void doExecute()
        throws MojoExecutionException
    {
        final List<ArtifactRepository> repositories = pluginRepositories;
        final Set<Artifact> artifacts = getProject().getPluginArtifacts();

        this.getLog().info( "Plugin repositories used by this build:" );
        for ( ArtifactRepository repo : repositories )
        {
            if ( isVerbose() )
            {
                Set<String> locations = new HashSet<>();
                for ( Mirror mirror : settings.getMirrors() )
                {
                    if ( mirror.getId().equals( repo.getId() )
                        && ( mirror.getUrl().equals( repo.getUrl() ) ) )
                    {
                        locations.add( "Maven settings (user/global)" );
                    }
                }

                Artifact projectArtifact = getProject().getArtifact();
                MavenProject project = getMavenProject( ArtifactUtils.key( projectArtifact ) );
                traversePom( repo, projectArtifact, project, locations );

                for ( Artifact artifact : artifacts )
                {
                    MavenProject artifactProject = getMavenProject( ArtifactUtils.key( artifact ) );
                    traversePom( repo, artifact, artifactProject, locations );
                }
                writeRepository( repo, locations );
            }
            else
            {
                this.getLog().info( repo.toString() );
            }
        }
    }

    private void writeRepository( ArtifactRepository artifactRepository, Set<String> locations )
    {
        StringBuilder sb = new StringBuilder( 256 );
        sb.append( artifactRepository.toString() );
        for ( String location : locations )
        {
            sb.append( " location: " ).append( location ).append( System.lineSeparator() );
        }
        this.getLog().info( sb.toString() );
    }

    /**
     * Parses the given String into GAV artifact coordinate information, adding the given type.
     *
     * @param artifactString should respect the format <code>groupId:artifactId[:version]</code>
     * @param type the extension for the artifact, must not be <code>null</code>
     * @return the <code>Artifact</code> object for the <code>artifactString</code> parameter
     * @throws MojoExecutionException if the <code>artifactString</code> doesn't respect the format
     */
    private ArtifactCoordinate getArtifactCoordinate( String artifactString, String type )
        throws MojoExecutionException
    {
        if ( org.codehaus.plexus.util.StringUtils.isEmpty( artifactString ) )
        {
            throw new IllegalArgumentException( "artifact parameter is empty" );
        }

        String groupId; // required
        String artifactId; // required
        String version; // optional

        String[] artifactParts = artifactString.split( ":" );
        switch ( artifactParts.length )
        {
            case 2:
                groupId = artifactParts[0];
                artifactId = artifactParts[1];
                version = Artifact.LATEST_VERSION;
                break;
            case 3:
                groupId = artifactParts[0];
                artifactId = artifactParts[1];
                version = artifactParts[2];
                break;
            default:
                throw new MojoExecutionException( "The artifact parameter '" + artifactString
                    + "' should be conform to: " + "'groupId:artifactId[:version]'." );
        }
        return getArtifactCoordinate( groupId, artifactId, version, type );
    }

    private ArtifactCoordinate getArtifactCoordinate( String groupId, String artifactId, String version, String type )
    {
        DefaultArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
        coordinate.setGroupId( groupId );
        coordinate.setArtifactId( artifactId );
        coordinate.setVersion( version );
        coordinate.setExtension( type );
        return coordinate;
    }

    /**
     * Retrieves the Maven Project associated with the given artifact String, in the form of
     * <code>groupId:artifactId[:version]</code>. This resolves the POM artifact at those coordinates and then builds
     * the Maven project from it.
     *
     * @param artifactString coordinates of the Maven project to get
     * @return new Maven project
     * @throws MojoExecutionException if there was an error while getting the Maven project
     */
    private MavenProject getMavenProject( String artifactString )
        throws MojoExecutionException
    {
        ArtifactCoordinate coordinate = getArtifactCoordinate( artifactString, "pom" );
        try
        {
            ProjectBuildingRequest pbr = new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );
            pbr.setRemoteRepositories( pluginRepositories );
            pbr.setProject( null );
            pbr.setValidationLevel( ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL );
            pbr.setResolveDependencies( false );
            Artifact artifact = artifactResolver.resolveArtifact( pbr, coordinate ).getArtifact();
            return projectBuilder.build( artifact.getFile(), pbr ).getProject();
        }
        catch ( ArtifactResolverException | ProjectBuildingException | IllegalArgumentException e )
        {
            throw new MojoExecutionException( "Unable to get the POM for the artifact '" + artifactString
                + "'. Verify the artifact parameter.", e );
        }
    }

    private void traversePom( ArtifactRepository artifactRepository,
                              Artifact artifact, MavenProject mavenProject, Set<String> locations )
        throws MojoExecutionException
    {
        getLog().debug( "Looking for locations for repository " + repositoryAsString( artifactRepository )
            + " for " + artifact );
        if ( mavenProject != null )
        {
            for ( Repository pluginRepository : mavenProject.getOriginalModel().getPluginRepositories() )
            {
                getLog().debug( "Found plugin repository: " + repositoryAsString( pluginRepository )
                    + " @ " + artifact + ":" + mavenProject.getOriginalModel().getPomFile() );
                if ( isRepositoryEqual( pluginRepository, artifactRepository ) )
                {
                    locations.add( mavenProject.getModel().getPomFile().toString() );
                }
            }
            traverseParentPom( artifactRepository, mavenProject, locations );
        }
        else
        {
            throw new MojoExecutionException( "No POM for the artifact '" + artifact + "'" );
        }
    }

    private void traverseParentPom( ArtifactRepository artifactRepository,
                                    MavenProject mavenProject, Set<String> locations )
        throws MojoExecutionException
    {
        MavenProject parent = mavenProject.getParent();
        if ( parent != null )
        {
            Model originalModel = parent.getOriginalModel();
            if ( originalModel.getRepositories().size() != 0
                || originalModel.getPluginRepositories().size() != 0 )
            {
                String artifactKey =
                    ArtifactUtils.key( parent.getGroupId(), parent.getArtifactId(), parent.getVersion() );
                MavenProject parentPom = getMavenProject( artifactKey );

                for ( Repository pluginRepository : originalModel.getPluginRepositories() )
                {
                    verbose( "Found parent plugin repository: " + repositoryAsString( pluginRepository )
                        + " @ " + parentPom.getArtifact() + ":" + parentPom.getFile() );
                    if ( isRepositoryEqual( pluginRepository, artifactRepository ) )
                    {
                        locations.add( parentPom.getFile().toString() );
                    }
                }
            }
            traverseParentPom( artifactRepository, parent, locations );
        }
    }

    private String repositoryAsString( Repository repository )
    {
        StringBuilder sb = new StringBuilder( 32 );
        sb.append( repository.getId() );
        sb.append( " (" );
        sb.append( repository.getUrl() );
        sb.append( ")" );
        return sb.toString();
    }

    private String repositoryAsString( ArtifactRepository repository )
    {
        StringBuilder sb = new StringBuilder( 32 );
        sb.append( repository.getId() );
        sb.append( " (" );
        sb.append( repository.getUrl() );
        sb.append( ")" );
        return sb.toString();
    }

    private boolean isVerbose()
    {
        return ( verbose || getLog().isDebugEnabled() );
    }

    private void verbose( String message )
    {
        if ( isVerbose() )
        {
            getLog().info( message );
        }
    }

    private boolean isRepositoryEqual( Repository repository, ArtifactRepository artifactRepository )
    {
        return repository.getId().equals( artifactRepository.getId() )
            && repository.getUrl().equals( artifactRepository.getUrl() );
    }

}
