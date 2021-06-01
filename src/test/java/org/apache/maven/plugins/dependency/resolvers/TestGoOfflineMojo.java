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
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import static java.util.Collections.singletonList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.project.MavenProject;

public class TestGoOfflineMojo extends AbstractDependencyMojoTestCase
{

    protected void setUp() throws Exception
    {
        // required for mojo lookups to work
        super.setUp( "goOfflineTest", false );
    }

    public void testGoOffline() throws Exception
    {
        File testPom = new File( getBasedir(), "target/test-classes/unit/resolve-test/plugin-config.xml" );
        GoOfflineMojo mojo = (GoOfflineMojo) lookupMojo( "go-offline", testPom );
        MavenProject project = mojo.getProject();
        MavenSession session = newMavenSession( project );
        setVariableValueToObject( mojo, "session", session );
        setVariableValueToObject( mojo, "includeArtifactIds", "" );
        setVariableValueToObject( mojo, "excludeArtifactIds", "" );
        setVariableValueToObject( mojo, "includeGroupIds", "" );
        setVariableValueToObject( mojo, "excludeGroupIds", "" );
        setVariableValueToObject( mojo, "includeScope", "" );
        setVariableValueToObject( mojo, "excludeScope", "system" );
        setVariableValueToObject( mojo, "includeClassifiers", "" );
        setVariableValueToObject( mojo, "excludeClassifiers", "" );
        setVariableValueToObject( mojo, "includeTypes", "" );
        setVariableValueToObject( mojo, "excludeTypes", "" );

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll( directArtifacts );
        project.setArtifacts( artifacts );
        project.setDependencies( createArtifacts( createDependencies() ) );
        //project.setDependencyArtifacts( directArtifacts );

        mojo.doExecute();
        Set<Artifact> results = mojo.getDependencies();

        assertTrue(results.isEmpty());
    }

    private List<Dependency> createDependencies()
    {
        final Dependency comSunTools = new Dependency();
        comSunTools.setGroupId( "com.sun" );
        comSunTools.setArtifactId( "tools" );
        comSunTools.setScope( "system" );
        comSunTools.setVersion( "1.8" );
        comSunTools.setType( "jar" );

        return singletonList( comSunTools );
    }

    // respects the createUnpackableFile flag of the ArtifactStubFactory
    private List<Dependency> createArtifacts( List<Dependency> items )
            throws IOException
    {
        for ( Dependency item : items )
        {
            String classifier = "".equals( item.getClassifier() ) ? null : item.getClassifier();
            stubFactory.createArtifact( item.getGroupId(), item.getArtifactId(),
                    VersionRange.createFromVersion( item.getVersion() ), null, item.getType(),
                    classifier, item.isOptional() );
        }
        return items;
    }
}
