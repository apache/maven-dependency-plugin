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
import java.util.Locale;
import java.util.Set;


abstract class AbstractDependencyFilter implements DependencyFilter
{
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
