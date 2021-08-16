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

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.resolve.AbstractFilter;
import org.apache.maven.shared.artifact.filter.resolve.Node;
import org.apache.maven.shared.artifact.filter.resolve.TransformableFilter;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link TransformableFilter} implementation that excludes artifacts found in the Reactor.
 *
 * @author Maarten Mulders
 */
public class ExcludeReactorProjectsDependencyFilter extends AbstractFilter
{
    private final Log log;
    private final Set<String> reactorArtifactKeys;

    public ExcludeReactorProjectsDependencyFilter( final List<MavenProject> reactorProjects, final Log log )
    {
        this.log = log;
        this.reactorArtifactKeys = reactorProjects.stream()
                .map( project -> ArtifactUtils.key( project.getArtifact() ) )
                .collect( Collectors.toSet() );
    }

    @Override
    public boolean accept( final Node node, final List<Node> parents )
    {
        final Dependency dependency = node.getDependency();
        if ( dependency != null )
        {
            final String dependencyArtifactKey = ArtifactUtils.key(
                    dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion() );

            final boolean result = isDependencyArtifactInReactor( dependencyArtifactKey );

            if ( log.isDebugEnabled() && result )
            {
                log.debug( "Skipped dependency "
                        + dependencyArtifactKey
                        + " because it is present in the reactor" );
            }

            return !result;
        }
        return true;
    }

    private boolean isDependencyArtifactInReactor( final String dependencyArtifactKey )
    {
        for ( final String reactorArtifactKey : this.reactorArtifactKeys )
        {
            // This check only includes GAV. Should we take a look at the types, too?
            if ( reactorArtifactKey.equals( dependencyArtifactKey ) )
            {
                return true;
            }
        }
        return false;
    }
}
