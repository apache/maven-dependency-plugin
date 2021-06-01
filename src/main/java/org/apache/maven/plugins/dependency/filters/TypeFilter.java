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
 * Filters dependencies by type.
 */
public class TypeFilter extends AbstractDependencyFilter
{
    private final String includeType;

    private final String excludeType;

    public TypeFilter( String includeType, String excludeType )
    {
        this.includeType = includeType == null ? "" : includeType;
        this.excludeType = excludeType == null ? "" : excludeType;
    }

    @Override
    public Set<Dependency> filter( Set<Dependency> dependencies )
    {
        Set<Dependency> filtered = new HashSet<>( dependencies );

        filtered = filterIncludeType( filtered );
        filtered = filterExcludeType( filtered );

        return filtered;
    }

    private Set<Dependency> filterExcludeType( Set<Dependency> dependencies )
    {
        if ( excludeType.trim().isEmpty() )
        {
            return dependencies;
        }

        final Set<String> excludedTypes = splitValues( excludeType );

        Set<Dependency> filtered = new HashSet<>( dependencies.size() );
        for ( Dependency dependency : dependencies )
        {
            if ( excludedTypes.contains( dependency.getType() ) )
            {
                continue;
            }

            filtered.add( dependency );
        }

        return filtered;
    }

    private Set<Dependency> filterIncludeType( Set<Dependency> dependencies )
    {
        if ( includeType.trim().isEmpty() )
        {
            return dependencies;
        }

        Set<String> includedTypes = splitValues( includeType );

        Set<Dependency> filtered = new HashSet<>( dependencies.size() );
        for ( Dependency dependency : dependencies )
        {
            if ( includedTypes.contains( dependency.getType() ) )
            {
                filtered.add( dependency );
            }
        }

        return filtered;
    }


}
