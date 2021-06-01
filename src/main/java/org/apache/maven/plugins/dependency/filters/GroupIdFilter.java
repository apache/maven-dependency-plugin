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
 * Filters dependencies by group ID.
 */
public class GroupIdFilter extends AbstractDependencyFilter
{
    private final String includeGroupId;

    private final String excludeGroupId;

    public GroupIdFilter( String includeGroupId, String excludeGroupId )
    {
        this.includeGroupId = includeGroupId == null ? "" : includeGroupId;
        this.excludeGroupId = excludeGroupId == null ? "" : excludeGroupId;
    }

    @Override
    public Set<Dependency> filter( Set<Dependency> dependencies )
    {
        Set<Dependency> filtered = new HashSet<>( dependencies );

        filtered = filterIncludeGroupIds( filtered );
        filtered = filterExcludeGroupIds( filtered );

        return filtered;
    }

    private Set<Dependency> filterExcludeGroupIds( Set<Dependency> dependencies )
    {
        if ( excludeGroupId.trim().isEmpty() )
        {
            return dependencies;
        }

        final Set<String> excludedGroupIds = splitValues( excludeGroupId );

        Set<Dependency> filtered = new HashSet<>( dependencies.size() );
        for ( Dependency dependency : dependencies )
        {
            if ( excludedGroupIds.contains( dependency.getGroupId() ) )
            {
                continue;
            }

            filtered.add( dependency );
        }

        return filtered;
    }

    private Set<Dependency> filterIncludeGroupIds( Set<Dependency> dependencies )
    {
        if ( includeGroupId.trim().isEmpty() )
        {
            return dependencies;
        }

        Set<String> includedGroupIds = splitValues( includeGroupId );

        Set<Dependency> filtered = new HashSet<>( dependencies.size() );
        for ( Dependency dependency : dependencies )
        {
            if ( includedGroupIds.contains( dependency.getGroupId() ) )
            {
                filtered.add( dependency );
            }
        }

        return filtered;
    }


}
