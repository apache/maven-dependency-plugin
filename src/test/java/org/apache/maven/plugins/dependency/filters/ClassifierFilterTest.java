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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.apache.maven.model.Dependency;
import org.junit.Test;

public class ClassifierFilterTest extends AbstractArtifactFilterTest
{

    @Test
    public void testArtifactFilterExclude()
    {
        // given
        final Dependency comSunTools = COM_SUN_TOOLS_SYSTEM_JAR;
        final Dependency mavenArtifactTests = MAVEN_ARTIFACT_TESTS_TEST_JAR;
        final ClassifierFilter tools = new ClassifierFilter( "", mavenArtifactTests.getClassifier() );
        final HashSet<Dependency> dependencies = new HashSet<>( 2 );
        dependencies.add( comSunTools );
        dependencies.add( mavenArtifactTests );

        // when:
        final Set<Dependency> filtered = tools.filter( dependencies );

        // then:
        assertEquals( 1, filtered.size() );
        assertTrue( filtered.contains( comSunTools ) );
    }


    @Test
    public void testArtifactFilterInclude()
    {
        // given
        final Dependency comSunTools = COM_SUN_TOOLS_SYSTEM_JAR;
        final Dependency mavenArtifact = MAVEN_ARTIFACT_COMPILE_JAR;
        final Dependency mavenArtifactTests = MAVEN_ARTIFACT_TESTS_TEST_JAR;
        final ClassifierFilter tools = new ClassifierFilter( mavenArtifactTests.getClassifier(), "" );
        final HashSet<Dependency> dependencies = new HashSet<>( 3 );
        dependencies.add( comSunTools );
        dependencies.add( mavenArtifact );
        dependencies.add( mavenArtifactTests );

        // when:
        final Set<Dependency> filtered = tools.filter( dependencies );

        // then:
        assertEquals( 1, filtered.size() );
        assertTrue( filtered.contains( mavenArtifactTests ) );
    }
}
