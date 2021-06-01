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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.maven.model.Dependency;

abstract class AbstractDependencyFilter implements DependencyFilter
{
    public boolean isDependencyIncluded( Dependency dependency )
    {
        Set<Dependency> set = new LinkedHashSet<>();
        set.add( dependency );

        set = filter( set );
        return set.contains( dependency );
    }

    protected Set<String> splitValues( String csvScopeList )
    {
        final String[] scopes = csvScopeList.split( "," );
        Set<String> excludeScope = new HashSet<>();

        for ( String scope : scopes )
        {
            final String cleanScope = scope.trim().toLowerCase( Locale.ROOT );

            if ( cleanScope.isEmpty() )
            {
                continue;
            }

            excludeScope.add( cleanScope );
        }

        return excludeScope;
    }
}
