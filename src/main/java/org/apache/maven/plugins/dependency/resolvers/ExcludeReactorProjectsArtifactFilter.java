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
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.collection.AbstractArtifactsFilter;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link ArtifactFilter} implementation that excludes artifacts found in the Reactor.
 *
 * @author Maarten Mulders
 */
public class ExcludeReactorProjectsArtifactFilter extends AbstractArtifactsFilter
{
    private final Log log;
    private final Set<String> reactorArtifactKeys;

    public ExcludeReactorProjectsArtifactFilter( final List<MavenProject> reactorProjects, final Log log )
    {
        this.log = log;
        this.reactorArtifactKeys = new HashSet<>( reactorProjects.size() );
        for ( final MavenProject project : reactorProjects )
        {
            this.reactorArtifactKeys.add( ArtifactUtils.key( project.getArtifact() ) );
        }
    }

    @Override
    public Set<Artifact> filter( final Set<Artifact> artifacts ) throws ArtifactFilterException
    {
        final Set<Artifact> results = new LinkedHashSet<>( artifacts.size() );

        for ( final Artifact artifact : artifacts )
        {
            if ( !isArtifactInReactor( artifact ) )
            {
                results.add( artifact );
            }
            else
            {
                if ( log.isDebugEnabled() )
                {
                    log.debug( "Skipped artifact "
                            + ArtifactUtils.key( artifact )
                            + " because it is present in the reactor" );
                }
            }
        }

        return results;
    }

    private boolean isArtifactInReactor( final Artifact artifact )
    {
        for ( final String reactorArtifactKey : this.reactorArtifactKeys )
        {
            final String artifactKey = ArtifactUtils.key( artifact );

            // This check only includes GAV. Should we take a look at the types, too?
            if ( reactorArtifactKey.equals( artifactKey ) )
            {
                return true;
            }
        }
        return false;
    }
}
