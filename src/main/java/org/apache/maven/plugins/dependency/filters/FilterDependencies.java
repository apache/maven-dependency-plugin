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
import static java.util.Collections.unmodifiableList;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.Dependency;

/**
 * Applies null to any number of filters to a given collection of dependencies.
 */
public class FilterDependencies
{

    private final List<DependencyFilter> filters;

    /**
     * Created new instance.
     */
    public FilterDependencies( DependencyFilter... filters )
    {
        this.filters = unmodifiableList( asList( filters ) );
    }

    /**
     * <p>filter.</p>
     *
     * @param dependencies The {@link Dependency}s to filter.
     * @return The resulting artifacts set.
     */
    public Set<Dependency> filter( Collection<Dependency> dependencies )
    {
        Set<Dependency> filtered = new HashSet<>( dependencies );

        for ( DependencyFilter filter : filters )
        {
            try
            {
                filtered = filter.filter( filtered );
            }
            catch ( NullPointerException e )
            {
                // TODO: log
            }
        }

        return filtered;
    }

    /**
     * <p>Getter for the field <code>filters</code>.</p>
     *
     * @return the filters.
     */
    public List<DependencyFilter> getFilters()
    {
        return this.filters;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder( "FilterDependencies{" );
        sb.append( "filters=" ).append( filters );
        sb.append( '}' );
        return sb.toString();
    }
}
