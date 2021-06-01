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
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import static java.util.Collections.*;
import static org.junit.Assert.*;

import java.util.Set;

import org.apache.maven.model.Dependency;
import org.junit.Test;

public class FilterDependenciesTest
{
    @Test
    public void testFilterDependenciesNoOp()
    {
        // given:
        final FilterDependencies filterDependencies = new FilterDependencies( new NoopFilter() );
        final Dependency comSunToolsSystemJar = AbstractArtifactFilterTest.COM_SUN_TOOLS_SYSTEM_JAR;
        final Set<Dependency> dependencies = singleton( comSunToolsSystemJar );

        // when:
        final Set<Dependency> filter = filterDependencies.filter( dependencies );

        // then
        assertTrue( filter.contains( comSunToolsSystemJar ) );
        assertEquals( 1, filter.size() );
    }

    @Test
    public void testFilterDependenciesExcludeSystemScope()
    {
        // given:
        final FilterDependencies filterDependencies = new FilterDependencies(
                new ScopeFilter( "", "system" )
        );
        final Dependency comSunToolsSystemJar = AbstractArtifactFilterTest.COM_SUN_TOOLS_SYSTEM_JAR;
        final Set<Dependency> dependencies = singleton( comSunToolsSystemJar );

        // when:
        final Set<Dependency> filter = filterDependencies.filter( dependencies );

        // then
        assertEquals( 0, filter.size() );
    }


    static class NoopFilter implements DependencyFilter
    {
        @Override
        public Set<Dependency> filter( Set<Dependency> dependencies )
        {
            return unmodifiableSet( dependencies );
        }
    }

}
