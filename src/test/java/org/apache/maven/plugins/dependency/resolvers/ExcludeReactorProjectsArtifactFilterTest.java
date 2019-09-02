package org.apache.maven.plugins.dependency.resolvers;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.plugins.dependency.CapturingLog;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;

import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

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

public class ExcludeReactorProjectsArtifactFilterTest
        extends AbstractDependencyMojoTestCase
{

    public void testFilter()
            throws ArtifactFilterException
    {
        Artifact artifact1 = new ArtifactStub();
        artifact1.setGroupId("org.apache.maven.plugins");
        artifact1.setArtifactId("maven-dependency-plugin-dummy");
        artifact1.setVersion("1.0");

        Artifact artifact2 = new ArtifactStub();
        artifact2.setGroupId("org.apache.maven.plugins");
        artifact2.setArtifactId("maven-dependency-plugin-other-dummy");
        artifact2.setVersion("1.0");

        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add( artifact1 );
        artifacts.add( artifact2 );

        CapturingLog log = new CapturingLog();
        MavenProject project = new MavenProjectStub();
        project.setArtifact(artifact1);

        ExcludeReactorProjectsArtifactFilter filter = new ExcludeReactorProjectsArtifactFilter(
                singletonList( project ), log );

        Set<Artifact> result = filter.filter( artifacts );

        assertEquals( 1, result.size() );
    }

    public void testFilterWithLogging()
            throws ArtifactFilterException
    {
        Artifact artifact = new ArtifactStub();
        artifact.setGroupId("org.apache.maven.plugins");
        artifact.setArtifactId("maven-dependency-plugin-dummy");
        artifact.setVersion("1.0");

        CapturingLog log = new CapturingLog();
        MavenProject project = new MavenProjectStub();
        project.setArtifact(artifact);

        ExcludeReactorProjectsArtifactFilter filter = new ExcludeReactorProjectsArtifactFilter(
                singletonList( project ), log );

        filter.filter( singleton( artifact ) );

        assertTrue( log.getContent().contains( "Skipped artifact" ) );
    }
}
