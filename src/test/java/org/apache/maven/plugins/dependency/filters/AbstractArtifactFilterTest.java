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

import org.apache.maven.model.Dependency;

public abstract class AbstractArtifactFilterTest
{

    protected static final Dependency COM_SUN_TOOLS_SYSTEM_JAR = createComSunToolsJarSystemDependency();

    protected static final Dependency MAVEN_ARTIFACT_COMPILE_JAR = createMavenArtifactDependency();

    protected static final Dependency MAVEN_ARTIFACT_TESTS_TEST_JAR = createMavenArtifactTestDependency();

    protected static final Dependency JUNIT_BOM_POM = createJunitBomPomDependency();

    private static Dependency createComSunToolsJarSystemDependency()
    {
        final Dependency comSunTools = new Dependency();
        comSunTools.setGroupId( "com.sun" );
        comSunTools.setArtifactId( "tools" );
        comSunTools.setScope( "system" );
        comSunTools.setVersion( "1.8" );
        comSunTools.setType( "jar" );
        comSunTools.setClassifier( "" );

        return comSunTools;
    }

    private static Dependency createMavenArtifactDependency()
    {
        final Dependency mavenArtifactDependency = new Dependency();
        mavenArtifactDependency.setGroupId( "org.apache.maven" );
        mavenArtifactDependency.setArtifactId( "maven-artifact" );
        mavenArtifactDependency.setScope( "compile" );
        mavenArtifactDependency.setVersion( "2.0.4" );
        mavenArtifactDependency.setType( "jar" );
        mavenArtifactDependency.setClassifier( "" );

        return mavenArtifactDependency;
    }

    private static Dependency createMavenArtifactTestDependency()
    {
        final Dependency mavenArtifactDependency = new Dependency();

        mavenArtifactDependency.setGroupId( "org.apache.maven" );
        mavenArtifactDependency.setArtifactId( "maven-artifact" );
        mavenArtifactDependency.setScope( "test" );
        mavenArtifactDependency.setVersion( "2.0.4" );
        mavenArtifactDependency.setType( "jar" );
        mavenArtifactDependency.setClassifier( "tests" );

        return mavenArtifactDependency;

    }

    private static Dependency createJunitBomPomDependency()
    {
        final Dependency mavenArtifactDependency = new Dependency();
        mavenArtifactDependency.setGroupId( "org.junit" );
        mavenArtifactDependency.setArtifactId( "junit-bom" );
        mavenArtifactDependency.setScope( "provided" );
        mavenArtifactDependency.setVersion( "5.7.2" );
        mavenArtifactDependency.setType( "pom" );
        mavenArtifactDependency.setClassifier( "" );

        return mavenArtifactDependency;
    }

}
