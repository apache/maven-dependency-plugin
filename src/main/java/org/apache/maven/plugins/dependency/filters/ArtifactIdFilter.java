package org.apache.maven.plugins.dependency.filters;

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

import java.util.HashSet;
import java.util.Set;

import org.apache.maven.model.Dependency;

/**
 * Filters dependencies by artifact ID.
 */
public class ArtifactIdFilter extends AbstractDependencyFilter
{
    private final String includeArtifactIds;

    private final String excludeArtifactIds;

    public ArtifactIdFilter( String includeArtifactIds, String excludeArtifactIds )
    {
        this.includeArtifactIds = includeArtifactIds == null ? "" : includeArtifactIds;
        this.excludeArtifactIds = excludeArtifactIds == null ? "" : excludeArtifactIds;
    }

    @Override
    public Set<Dependency> filter( Set<Dependency> dependencies )
    {
        Set<Dependency> filtered = new HashSet<>( dependencies );

        filtered = filterIncludeArtifactIds( filtered );
        filtered = filterExcludeArtifactIds( filtered );

        return filtered;
    }

    private Set<Dependency> filterExcludeArtifactIds( Set<Dependency> dependencies )
    {
        if ( excludeArtifactIds.trim().isEmpty() )
        {
            return dependencies;
        }

        final Set<String> excludedArtifactIds = splitValues( excludeArtifactIds );

        Set<Dependency> filtered = new HashSet<>( dependencies.size() );
        for ( Dependency dependency : dependencies )
        {
            if ( excludedArtifactIds.contains( dependency.getArtifactId() ) )
            {
                continue;
            }

            filtered.add( dependency );
        }

        return filtered;
    }

    private Set<Dependency> filterIncludeArtifactIds( Set<Dependency> dependencies )
    {
        if ( includeArtifactIds.trim().isEmpty() )
        {
            return dependencies;
        }

        Set<String> includedArtifactIds = splitValues( includeArtifactIds );

        Set<Dependency> filtered = new HashSet<>( dependencies.size() );
        for ( Dependency dependency : dependencies )
        {
            if ( includedArtifactIds.contains( dependency.getArtifactId() ) )
            {
                filtered.add( dependency );
            }
        }

        return filtered;
    }


}
