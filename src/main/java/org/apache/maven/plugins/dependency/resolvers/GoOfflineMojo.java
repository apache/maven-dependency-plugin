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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.apache.maven.shared.artifact.filter.resolve.TransformableFilter;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Goal that resolves all project dependencies, including plugins and reports and their dependencies.
 *
 * <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @author Maarten Mulders
 * @since 2.0
 */
@Mojo( name = "go-offline", threadSafe = true )
public class GoOfflineMojo
    extends AbstractResolveMojo
{
    /**
     * Main entry into mojo. Gets the list of dependencies, resolves all that are not in the Reactor, and iterates
     * through displaying the resolved versions.
     *
     * @throws MojoExecutionException with a message if an error occurs.
     */
    @Override
    protected void doExecute()
        throws MojoExecutionException
    {

        try
        {
            final Set<Artifact> plugins = resolvePluginArtifacts();

            final Set<Artifact> dependencies = resolveDependencyArtifacts();

            if ( !isSilent() )
            {
                for ( Artifact artifact : plugins )
                {
                    this.getLog().info( "Resolved plugin: "
                            + DependencyUtil.getFormattedFileName( artifact, false ) );
                }

                for ( Artifact artifact : dependencies )
                {
                    this.getLog().info( "Resolved dependency: "
                            + DependencyUtil.getFormattedFileName( artifact, false ) );
                }
            }

        }
        catch ( DependencyResolverException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }

    }

    /**
     * This method resolves the dependency artifacts from the project.
     *
     * @return set of resolved dependency artifacts.
     * @throws DependencyResolverException in case of an error while resolving the artifacts.
     */
    protected Set<Artifact> resolveDependencyArtifacts()
            throws DependencyResolverException
    {
        final Collection<Dependency> dependencies = getProject().getDependencies();
        final Set<DependableCoordinate> dependableCoordinates = new HashSet<>();

        final ProjectBuildingRequest buildingRequest = newResolveArtifactProjectBuildingRequest();

        for ( Dependency dependency : dependencies )
        {
            dependableCoordinates.add( createDependendableCoordinateFromDependency( dependency ) );
        }

        return resolveDependableCoordinate( buildingRequest, dependableCoordinates, "dependencies" );
    }

    private Set<Artifact> resolveDependableCoordinate( final ProjectBuildingRequest buildingRequest,
                                                        final Collection<DependableCoordinate> dependableCoordinates,
                                                       final String type )
            throws DependencyResolverException
    {
        final TransformableFilter filter = getTransformableFilter();

        final Set<Artifact> results = new HashSet<>();

        this.getLog().debug( "Resolving '" + type + "' with following repositories:" );
        for ( ArtifactRepository repo : buildingRequest.getRemoteRepositories() )
        {
            getLog().debug( repo.getId() + " (" + repo.getUrl() + ")" );
        }

        for ( DependableCoordinate dependableCoordinate : dependableCoordinates )
        {
            final Iterable<ArtifactResult> artifactResults = getDependencyResolver().resolveDependencies(
                    buildingRequest, dependableCoordinate, filter );

            for ( final ArtifactResult artifactResult : artifactResults )
            {
                results.add( artifactResult.getArtifact() );
            }
        }

        return results;
    }

    private TransformableFilter getTransformableFilter()
    {
        if ( this.excludeReactor )
        {
            return new ExcludeReactorProjectsDependencyFilter( this.reactorProjects, getLog() );
        }
        else
        {
            return null;
        }
    }

    /**
     * This method resolves the plugin artifacts from the project.
     *
     * @return set of resolved plugin artifacts.
     * @throws DependencyResolverException in case of an error while resolving the artifacts.
     */
    protected Set<Artifact> resolvePluginArtifacts()
            throws DependencyResolverException
    {
        final Set<DependableCoordinate> dependableCoordinates = new HashSet<>();

        final Set<Artifact> plugins = getProject().getPluginArtifacts();
        final Set<Artifact> reports = getProject().getReportArtifacts();

        final Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.addAll( reports );
        artifacts.addAll( plugins );

        final ProjectBuildingRequest buildingRequest = newResolvePluginProjectBuildingRequest();

        for ( Artifact artifact : artifacts )
        {
            dependableCoordinates.add( createDependendableCoordinateFromArtifact( artifact ) );
        }

        return resolveDependableCoordinate( buildingRequest, dependableCoordinates, "plugins" );
    }

    private DependableCoordinate createDependendableCoordinateFromArtifact( final Artifact artifact )
    {
        final DefaultDependableCoordinate result = new DefaultDependableCoordinate();
        result.setGroupId( artifact.getGroupId() );
        result.setArtifactId( artifact.getArtifactId() );
        result.setVersion( artifact.getVersion() );
        result.setType( artifact.getType() );

        return result;
    }

    private DependableCoordinate createDependendableCoordinateFromDependency( final Dependency dependency )
    {
        final DefaultDependableCoordinate result = new DefaultDependableCoordinate();
        result.setGroupId( dependency.getGroupId() );
        result.setArtifactId( dependency.getArtifactId() );
        result.setVersion( dependency.getVersion() );
        result.setType( dependency.getType() );

        return result;
    }

    @Override
    protected ArtifactsFilter getMarkedArtifactFilter()
    {
        return null;
    }
}
