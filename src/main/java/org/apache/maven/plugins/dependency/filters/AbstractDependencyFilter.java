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

import org.apache.maven.model.Dependency;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;


abstract class AbstractDependencyFilter implements DependencyFilter
{

    protected final String includeIds;
    protected final String excludeIds;

    public AbstractDependencyFilter( String includeIds, String excludeIds )
    {
        this.includeIds = includeIds == null ? "" : includeIds;
        this.excludeIds = excludeIds == null ? "" : excludeIds;
    }


    @Override
    public Set<Dependency> filter( Set<Dependency> dependencies )
    {
        Set<Dependency> filtered = new HashSet<>( dependencies );

        filtered = filterincludeIds( filtered );
        filtered = filterexcludeIds( filtered );

        return filtered;
    }

    private Set<Dependency> filterexcludeIds( Set<Dependency> dependencies )
    {
        if ( excludeIds.trim().isEmpty() ) {
            return dependencies;
        }

        final Set<String> excludedIds = splitExcludeIds( excludeIds );

        Set<Dependency> filtered = new HashSet<>( dependencies.size() );
        for ( Dependency dependency : dependencies ) {
            if ( excludedIds.contains( getContainsProperty( dependency ) ) ) {
                continue;
            }

            filtered.add( dependency );
        }

        return filtered;
    }

    private Set<Dependency> filterincludeIds( Set<Dependency> dependencies )
    {
        if ( includeIds.trim().isEmpty() ) {
            return dependencies;
        }

        Set<String> includedIds = splitIncludeIds( includeIds );

        Set<Dependency> filtered = new HashSet<>( dependencies.size() );
        for ( Dependency dependency : dependencies ) {
            if ( includedIds.contains( getContainsProperty( dependency ) ) ) {
                filtered.add( dependency );
            }
        }

        return filtered;
    }

    protected Set<String> splitExcludeIds( String excludeIds )
    {
        return splitValues( excludeIds );
    }

    protected Set<String> splitIncludeIds( String includeIds )
    {
        return splitValues( includeIds );
    }

    abstract protected String getContainsProperty( Dependency dependency );

    protected Set<String> splitValues( String csvValueList )
    {
        final String[] values = csvValueList.split( "," );
        Set<String> excludeScope = new HashSet<>();

        for ( String value : values )
        {
            // value is expected to be a scope, classifier, etc.
            // thus assuming an english word. Do not rely on Locale.getDefault()!
            final String cleanScope = value.trim().toLowerCase( Locale.ENGLISH );

            if ( cleanScope.isEmpty() )
            {
                continue;
            }

            excludeScope.add( cleanScope );
        }

        return excludeScope;
    }
}
