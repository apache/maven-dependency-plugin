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

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;

/**
 * Filters dependencies by scope.
 */
public class ScopeFilter extends AbstractDependencyFilter
{
    private final String includeScope;

    private final String excludeScope;

    public ScopeFilter( String includeScope, String excludeScope )
    {
        this.includeScope = includeScope == null ? "" : includeScope;
        this.excludeScope = excludeScope == null ? "" : excludeScope;
    }

    @Override
    public Set<Dependency> filter( Set<Dependency> dependencies )
    {
        Set<Dependency> filtered = new HashSet<>( dependencies );

        filtered = filterIncludeScope( filtered );
        filtered = filterExcludeScope( filtered );

        return filtered;
    }

    private Set<Dependency> filterExcludeScope( Set<Dependency> dependencies )
    {
        if ( excludeScope.trim().isEmpty() )
        {
            return dependencies;
        }

        final Set<String> excludedScopes = toResolvedScopes( excludeScope, true );

        Set<Dependency> filtered = new HashSet<>( dependencies.size() );
        for ( Dependency dependency : dependencies )
        {
            if ( excludedScopes.contains( dependency.getScope() ) )
            {
                continue;
            }

            filtered.add( dependency );
        }

        return filtered;
    }

    private Set<Dependency> filterIncludeScope( Set<Dependency> dependencies )
    {
        if ( includeScope.trim().isEmpty() )
        {
            return dependencies;
        }

        Set<String> includedScopes = toResolvedScopes( includeScope, false );

        Set<Dependency> filtered = new HashSet<>( dependencies.size() );
        for ( Dependency dependency : dependencies )
        {
            if ( includedScopes.contains( dependency.getScope() ) )
            {
                filtered.add( dependency );
            }
        }

        return filtered;
    }

    private Set<String> toResolvedScopes( String scope, boolean isExcludeScope )
    {
        if ( isExcludeScope )
        {
            /*
             * runtime exclude scope excludes runtime and compile dependencies,
             * compile exclude scope excludes compile, provided, and system dependencies,
             * test exclude scope excludes all dependencies, then not really a legitimate option: it will
             * fail, you probably meant to configure includeScope = compile
             * provided exclude scope just excludes provided dependencies,
             * system exclude scope just excludes system dependencies.
             */
            switch ( scope )
            {
                case Artifact.SCOPE_RUNTIME:
                    return new HashSet<>( asList(
                            Artifact.SCOPE_RUNTIME,
                            Artifact.SCOPE_COMPILE ) );
                case Artifact.SCOPE_COMPILE:
                    return new HashSet<>( asList(
                            Artifact.SCOPE_COMPILE,
                            Artifact.SCOPE_PROVIDED,
                            Artifact.SCOPE_SYSTEM ) );
                case Artifact.SCOPE_TEST:
                    throw new IllegalArgumentException( "invalid exclude scope: test. Did you mean compile?" );
                case Artifact.SCOPE_PROVIDED:
                    return new HashSet<>( singletonList( Artifact.SCOPE_PROVIDED ) );
                case Artifact.SCOPE_SYSTEM:
                    return new HashSet<>( singletonList( Artifact.SCOPE_SYSTEM ) );
                default:
                    throw new IllegalArgumentException( "Invalid Scope: " + scope );
            }
        }
        else
        {
            /*
             * runtime include scope gives runtime and compile dependencies,
             * compile include scope gives compile, provided, and system dependencies,
             * test include scope gives all dependencies (equivalent to default),
             * provided include scope just gives provided dependencies,
             * system include scope just gives system dependencies.
             */
            switch ( scope )
            {
                case Artifact.SCOPE_RUNTIME:
                    return new HashSet<>( asList(
                            Artifact.SCOPE_RUNTIME,
                            Artifact.SCOPE_COMPILE ) );
                case Artifact.SCOPE_COMPILE:
                    return new HashSet<>( asList(
                            Artifact.SCOPE_COMPILE,
                            Artifact.SCOPE_PROVIDED,
                            Artifact.SCOPE_SYSTEM ) );
                case Artifact.SCOPE_TEST:
                    return new HashSet<>( asList(
                            Artifact.SCOPE_COMPILE,
                            Artifact.SCOPE_PROVIDED,
                            Artifact.SCOPE_SYSTEM,
                            Artifact.SCOPE_TEST,
                            Artifact.SCOPE_RUNTIME ) );
                case Artifact.SCOPE_PROVIDED:
                    return new HashSet<>( singletonList( Artifact.SCOPE_PROVIDED ) );
                case Artifact.SCOPE_SYSTEM:
                    return new HashSet<>( singletonList( Artifact.SCOPE_SYSTEM ) );
                default:
                    throw new IllegalArgumentException( "Invalid Scope: " + scope );
            }
        }
    }

}
