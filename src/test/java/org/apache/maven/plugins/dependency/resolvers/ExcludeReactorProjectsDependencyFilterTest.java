package org.apache.maven.plugins.dependency.resolvers;

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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.resolve.Node;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.singletonList;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ExcludeReactorProjectsDependencyFilterTest extends AbstractDependencyMojoTestCase
{
    public void testReject()
    {
        final Artifact artifact1 = new ArtifactStub();
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

        MavenProject project = new MavenProjectStub();
        project.setArtifact(artifact1);

        Log log = mock( Log.class );
        when( log.isDebugEnabled() ).thenReturn( false );

        ExcludeReactorProjectsDependencyFilter filter = new ExcludeReactorProjectsDependencyFilter(
                singletonList( project ), log );

        Node node = new Node() {
            @Override
            public Dependency getDependency() {
                final Dependency result = new Dependency();
                result.setGroupId( artifact1.getGroupId() );
                result.setArtifactId( artifact1.getArtifactId() );
                result.setVersion( artifact1.getVersion() );
                return result;
            }
        };

        assertFalse( filter.accept( node , Collections.<Node>emptyList() ) );
    }

    public void testRejectWithLogging()
    {
        final Artifact artifact1 = new ArtifactStub();
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

        MavenProject project = new MavenProjectStub();
        project.setArtifact(artifact1);

        Log log = mock( Log.class );
        when( log.isDebugEnabled() ).thenReturn( true );

        ExcludeReactorProjectsDependencyFilter filter = new ExcludeReactorProjectsDependencyFilter(
                singletonList( project ), log );

        Node node = new Node() {
            @Override
            public Dependency getDependency() {
                final Dependency result = new Dependency();
                result.setGroupId( artifact1.getGroupId() );
                result.setArtifactId( artifact1.getArtifactId() );
                result.setVersion( artifact1.getVersion() );
                return result;
            }
        };

        filter.accept( node , Collections.<Node>emptyList() );

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass( String.class );
        verify( log ).debug( captor.capture() );
        assertTrue( captor.getValue().contains( "Skipped dependency" ) );
    }

    public void testAccept()
    {
        final Artifact artifact1 = new ArtifactStub();
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

        MavenProject project = new MavenProjectStub();
        project.setArtifact(artifact1);

        Log log = mock( Log.class );
        when( log.isDebugEnabled() ).thenReturn( false );

        ExcludeReactorProjectsDependencyFilter filter = new ExcludeReactorProjectsDependencyFilter(
                singletonList( project ), log );

        Node node = new Node() {
            @Override
            public Dependency getDependency() {
                final Dependency result = new Dependency();
                result.setGroupId( "something-else" );
                result.setArtifactId( artifact1.getArtifactId() );
                result.setVersion( artifact1.getVersion() );
                return result;
            }
        };

        assertTrue( filter.accept( node , Collections.<Node>emptyList() ) );
    }
}