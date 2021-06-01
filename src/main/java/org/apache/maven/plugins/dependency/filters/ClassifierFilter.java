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
 * Filters dependencies by classifier.
 */
public class ClassifierFilter extends AbstractDependencyFilter
{
    private final String includeClassifier;

    private final String excludeClassifier;

    public ClassifierFilter( String includeClassifier, String excludeClassifier )
    {
        this.includeClassifier = includeClassifier == null ? "" : includeClassifier;
        this.excludeClassifier = excludeClassifier == null ? "" : excludeClassifier;
    }

    @Override
    public Set<Dependency> filter( Set<Dependency> dependencies )
    {
        Set<Dependency> filtered = new HashSet<>( dependencies );

        filtered = filterIncludeClassifier( filtered );
        filtered = filterExcludeClassifier( filtered );

        return filtered;
    }

    private Set<Dependency> filterExcludeClassifier( Set<Dependency> dependencies )
    {
        if ( excludeClassifier.trim().isEmpty() )
        {
            return dependencies;
        }

        final Set<String> excludedClassifiers = splitValues( excludeClassifier );

        Set<Dependency> filtered = new HashSet<>( dependencies.size() );
        for ( Dependency dependency : dependencies )
        {
            if ( excludedClassifiers.contains( dependency.getClassifier() ) )
            {
                continue;
            }

            filtered.add( dependency );
        }

        return filtered;
    }

    private Set<Dependency> filterIncludeClassifier( Set<Dependency> dependencies )
    {
        if ( includeClassifier.trim().isEmpty() )
        {
            return dependencies;
        }

        Set<String> includedClassifiers = splitValues( includeClassifier );

        Set<Dependency> filtered = new HashSet<>( dependencies.size() );
        for ( Dependency dependency : dependencies )
        {
            if ( includedClassifiers.contains( dependency.getClassifier() ) )
            {
                filtered.add( dependency );
            }
        }

        return filtered;
    }


}
