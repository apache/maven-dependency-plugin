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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;

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
        catch ( ArtifactFilterException | ArtifactResolverException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }

    }

    /**
     * This method resolves the dependency artifacts from the project.
     *
     * @return set of resolved dependency artifacts.
     * @throws ArtifactFilterException in case of an error while filtering the artifacts.
     * @throws ArtifactResolverException in case of an error while resolving the artifacts.
     */
    protected Set<Artifact> resolveDependencyArtifacts()
            throws ArtifactFilterException, ArtifactResolverException
    {
        final Set<Artifact> artifacts = getProject().getDependencyArtifacts();

        return resolveFilteredArtifacts( artifacts );
    }

    /**
     * This method resolves the plugin artifacts from the project.
     *
     * @return set of resolved plugin artifacts.
     * @throws ArtifactFilterException in case of an error while filtering the artifacts.
     * @throws ArtifactResolverException in case of an error while resolving the artifacts.
     */
    protected Set<Artifact> resolvePluginArtifacts()
            throws ArtifactFilterException, ArtifactResolverException
    {
        final Set<Artifact> plugins = getProject().getPluginArtifacts();
        final Set<Artifact> reports = getProject().getReportArtifacts();

        final Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.addAll( reports );
        artifacts.addAll( plugins );

        return resolveFilteredArtifacts( artifacts );
    }

    protected Set<Artifact> resolveFilteredArtifacts( final Set<Artifact> artifacts )
            throws ArtifactFilterException, ArtifactResolverException
    {
        final FilterArtifacts filter = getArtifactsFilter();
        final Set<Artifact> filteredArtifacts = filter.filter( artifacts );

        final Set<Artifact> resolvedArtifacts = new LinkedHashSet<>( artifacts.size() );
        for ( final Artifact artifact : filteredArtifacts )
        {
            final ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );

            // resolve the new artifact
            final Artifact resolvedArtifact = getArtifactResolver()
                    .resolveArtifact( buildingRequest, artifact ).getArtifact();
            resolvedArtifacts.add( resolvedArtifact );
        }

        return resolvedArtifacts;
    }

    @Override
    protected ArtifactsFilter getMarkedArtifactFilter()
    {
        return null;
    }
}
