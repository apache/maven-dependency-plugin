package org.apache.maven.plugins.dependency.tree;

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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.dependency.testUtils.stubs.CapturingLog;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyNode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.apache.maven.artifact.Artifact.SCOPE_COMPILE;
import static org.codehaus.plexus.util.ReflectionUtils.setVariableValueInObject;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests <code>TreeMojo</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @since 2.0
 */
public class TestTreeMojo
{
    private static final String GROUP_ID = "org.apache.maven.plugins";
    private static final String ARTIFACT_ID = "example-artifact";
    private static final String VERSION = "0.1-SNAPSHOT";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private DependencyGraphBuilder dependencyGraphBuilder = mock( DependencyGraphBuilder.class );
    private CapturingLog log = new CapturingLog();
    private MavenSession session = mock( MavenSession.class );

    private TreeMojo mojo = new TreeMojo();

    @Before
    public void prepareAndInjectMocks() throws IllegalAccessException, DependencyGraphBuilderException
    {
        Artifact root = new DefaultArtifact( GROUP_ID,  ARTIFACT_ID, VERSION, SCOPE_COMPILE, "jar", "", null );
        DefaultDependencyNode dependencyGraph = new DefaultDependencyNode(null, root, null, null, null);
        dependencyGraph.setChildren( Collections.<DependencyNode>emptyList() );

        when( session.getProjectBuildingRequest() ).thenReturn( mock( ProjectBuildingRequest.class ) );
        when( dependencyGraphBuilder.buildDependencyGraph(
                nullable( ProjectBuildingRequest.class ),
                nullable( ArtifactFilter.class ),
                nullable( Collection.class )
            )
        ).thenReturn(dependencyGraph);

        mojo.setLog(log);
        setVariableValueInObject( mojo, "dependencyGraphBuilder", dependencyGraphBuilder );
        setVariableValueInObject( mojo, "session", session );
    }

    @Test
    public void withSkipParameter_shouldSkipExecution()
            throws MojoFailureException, MojoExecutionException, DependencyGraphBuilderException
    {
        // Arrange
        mojo.setSkip( true );

        // Act
        mojo.execute();

        // Assert
        verify( dependencyGraphBuilder, never() ).buildDependencyGraph( any( ProjectBuildingRequest.class ),
                                                                        any( ArtifactFilter.class ),
                                                                        any( Collection.class )
        );
    }

    @Test
    public void withoutOutputFile_shouldWriteToLog()
            throws MojoFailureException, MojoExecutionException
    {
        // Arrange

        // Act
        mojo.execute();

        // Assert
        String expectedLine = String.format("%s:%s:jar:%s:%s", GROUP_ID, ARTIFACT_ID, VERSION, SCOPE_COMPILE);
        assertThat( log.getContent(), containsString( "INFO " + expectedLine ) );
    }

    @Test
    public void withOutputFile_shouldWriteToFile()
            throws MojoFailureException, MojoExecutionException, IOException, IllegalAccessException
    {
        // Arrange
        final File outputFile = temporaryFolder.newFile();
        setVariableValueInObject( mojo, "outputFile", outputFile );

        // Act
        mojo.execute();

        // Assert
        assertThat( outputFile.exists() , is ( true ) );
        assertThat( outputFile.length() , is ( greaterThan (0L ) ) );

        final List<String> lines = Files.readAllLines( outputFile.toPath(), Charset.defaultCharset() );
        assertThat( lines.size(), is ( 1 ) );
        String expectedLine = String.format("%s:%s:jar:%s:%s", GROUP_ID, ARTIFACT_ID, VERSION, SCOPE_COMPILE);
        assertThat( lines.get( 0 ), is ( expectedLine ) );
    }

    /*
     * @see org.apache.maven.plugin.testing.AbstractMojoTestCase#setUp()
     *
    protected void setUp()
        throws Exception
    {
        // required for mojo lookups to work
        super.setUp( "tree", true );
    }

    // tests ------------------------------------------------------------------

    /**
     * Tests the proper discovery and configuration of the mojo.
     *
    public void testTreeTestEnvironment()
        throws Exception
    {
        File testPom = new File( getBasedir(), "target/test-classes/unit/tree-test/plugin-config.xml" );
        TreeMojo mojo = (TreeMojo) lookupMojo( "tree", testPom );

        assertNotNull( mojo );
        assertNotNull( mojo.getProject() );
        MavenProject project = mojo.getProject();
        project.setArtifact( this.stubFactory.createArtifact( "testGroupId", "project", "1.0" ) );

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll( directArtifacts );

        project.setArtifacts( artifacts );
        project.setDependencyArtifacts( directArtifacts );

        MavenSession session = newMavenSession( new MavenProjectStub() );

        DefaultRepositorySystemSession repoSession = (DefaultRepositorySystemSession) session.getRepositorySession();
        repoSession.setLocalRepositoryManager( new SimpleLocalRepositoryManager( stubFactory.getWorkingDir() ) );

        setVariableValueToObject( mojo, "session", session );

        mojo.execute();

        DependencyNode rootNode = mojo.getDependencyGraph();
        assertNodeEquals( "testGroupId:project:jar:1.0:compile", rootNode );
        assertEquals( 2, rootNode.getChildren().size() );
        assertChildNodeEquals( "testGroupId:snapshot:jar:2.0-SNAPSHOT:compile", rootNode, 0 );
        assertChildNodeEquals( "testGroupId:release:jar:1.0:compile", rootNode, 1 );
    }

    /**
     * Test the DOT format serialization
     *
     * @throws Exception in case of an error.
     *
    public void _testTreeDotSerializing()
        throws Exception
    {
        List<String> contents = runTreeMojo( "tree1.dot", "dot" );
        assertTrue( findString( contents, "digraph \"testGroupId:project:jar:1.0:compile\" {" ) );
        assertTrue( findString( contents,
                                "\"testGroupId:project:jar:1.0:compile\" -> \"testGroupId:snapshot:jar:2.0-SNAPSHOT:compile\"" ) );
        assertTrue( findString( contents,
                                "\"testGroupId:project:jar:1.0:compile\" -> \"testGroupId:release:jar:1.0:compile\"" ) );
    }

    /**
     * Test the GraphML format serialization
     * 
     * @throws Exception in case of an error.
     *
    public void _testTreeGraphMLSerializing()
        throws Exception
    {
        List<String> contents = runTreeMojo( "tree1.graphml", "graphml" );

        assertTrue( findString( contents, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" ) );
        assertTrue( findString( contents, "<y:NodeLabel>testGroupId:project:jar:1.0:compile</y:NodeLabel>" ) );
        assertTrue( findString( contents,
                                "<y:NodeLabel>testGroupId:snapshot:jar:2.0-SNAPSHOT:compile</y:NodeLabel>" ) );
        assertTrue( findString( contents, "<y:NodeLabel>testGroupId:release:jar:1.0:compile</y:NodeLabel>" ) );
        assertTrue( findString( contents, "<key for=\"node\" id=\"d0\" yfiles.type=\"nodegraphics\"/>" ) );
        assertTrue( findString( contents, "<key for=\"edge\" id=\"d1\" yfiles.type=\"edgegraphics\"/>" ) );
    }

    /**
     * Test the TGF format serialization
     * 
     * @throws Exception in case of an error.
     *
    public void _testTreeTGFSerializing()
        throws Exception
    {
        List<String> contents = runTreeMojo( "tree1.tgf", "tgf" );
        assertTrue( findString( contents, "testGroupId:project:jar:1.0:compile" ) );
        assertTrue( findString( contents, "testGroupId:snapshot:jar:2.0-SNAPSHOT:compile" ) );
        assertTrue( findString( contents, "testGroupId:release:jar:1.0:compile" ) );
    }

    /**
     * Help finding content in the given list of string
     * 
     * @param outputFile the outputFile.
     * @param format The format.
     * @throws Exception in case of an error.
     * @return list of strings in the output file
     *
    private List<String> runTreeMojo( String outputFile, String format )
        throws Exception
    {
        File testPom = new File( getBasedir(), "target/test-classes/unit/tree-test/plugin-config.xml" );
        String outputFileName = testDir.getAbsolutePath() + outputFile;
        TreeMojo mojo = (TreeMojo) lookupMojo( "tree", testPom );
        setVariableValueToObject( mojo, "outputType", format );
        setVariableValueToObject( mojo, "outputFile", new File( outputFileName ) );

        assertNotNull( mojo );
        assertNotNull( mojo.getProject() );
        MavenProject project = mojo.getProject();
        project.setArtifact( this.stubFactory.createArtifact( "testGroupId", "project", "1.0" ) );

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll( directArtifacts );

        project.setArtifacts( artifacts );
        project.setDependencyArtifacts( directArtifacts );

        mojo.execute();

        BufferedReader fp1 = new BufferedReader( new FileReader( outputFileName ) );
        List<String> contents = new ArrayList<>();

        String line;
        while ( ( line = fp1.readLine() ) != null )
        {
            contents.add( line );
        }
        fp1.close();

        return contents;
    }

    /**
     * Help finding content in the given list of string
     * 
     * @param contents The contents.
     * @param str The content which should be checked for.
     *
    private boolean findString( List<String> contents, String str )
    {
        for ( String line : contents )
        {
            if ( line.contains( str ) )
            {
                // if match then return here
                return true;
            }
        }

        // in case no match for the whole list
        return false;
    }

    // private methods --------------------------------------------------------

    private void assertChildNodeEquals( String expectedNode, DependencyNode actualParentNode, int actualChildIndex )
    {
        DependencyNode actualNode = actualParentNode.getChildren().get( actualChildIndex );

        assertNodeEquals( expectedNode, actualNode );
    }

    private void assertNodeEquals( String expectedNode, DependencyNode actualNode )
    {
        String[] tokens = expectedNode.split( ":" );

        assertNodeEquals( tokens[0], tokens[1], tokens[2], tokens[3], tokens[4], actualNode );
    }

    private void assertNodeEquals( String expectedGroupId, String expectedArtifactId, String expectedType,
                                   String expectedVersion, String expectedScope, DependencyNode actualNode )
    {
        Artifact actualArtifact = actualNode.getArtifact();

        assertEquals( "group id", expectedGroupId, actualArtifact.getGroupId() );
        assertEquals( "artifact id", expectedArtifactId, actualArtifact.getArtifactId() );
        assertEquals( "type", expectedType, actualArtifact.getType() );
        assertEquals( "version", expectedVersion, actualArtifact.getVersion() );
        assertEquals( "scope", expectedScope, actualArtifact.getScope() );
    }
    */
}
