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
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugins.dependency.tree.VerboseGraphSerializer;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class VerboseGraphSerializerTest extends AbstractMojoTestCase
{
    private final VerboseGraphSerializer serializer = new VerboseGraphSerializer();
    private static final String PRE_MANAGED_SCOPE = "preManagedScope", PRE_MANAGED_VERSION = "preManagedVersion",
            MANAGED_SCOPE = "managedScope";

    @Test
    public void testBasicTree() throws IOException
    {
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0" ), null)
        );
        DependencyNode left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" ), "test" )
        );
        DependencyNode right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.xyz", "right", "zip", "1" ), "provided" )
        );

        root.setChildren( Arrays.asList( left, right ) );

        String actual = serializer.serialize( root );
        File file = new File(getBasedir(), "/target/test-classes/unit/verbose-serializer-test/BasicTree.txt");
        String expected = FileUtils.readFileToString( file );

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testLargeTree() throws IOException
    {
        // Construct nodes for tree l1 = level 1 with the root being l0
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0" ), null )
        );
        DependencyNode l1left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" ), "test" )
        );
        DependencyNode l1right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.xyz", "right", "zip", "1" ), "provided" )
        );
        DependencyNode l2left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.maven", "a4", "jar", "2.2.1" ), "system" )
        );
        DependencyNode l2middle = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "a5", "zip", "0" ), "import" )
        );
        DependencyNode l2right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.xyz", "a9", "xml", "1.2" ), "runtime" )
        );
        DependencyNode l3 = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.xyz", "a6", "xml", "1.2.1" ), "provided" )
        );
        DependencyNode l4 = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.example", "a7", "jar", "2.2.2" ), "provided" )
        );
        DependencyNode l5right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.comm", "a7", "jar", "1" ), "compile" )
        );
        DependencyNode l5left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.comm", "a7", "jar", "1" ), "compile" )
        );
        DependencyNode l6left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.example", "a8", "xml", "2.1" ), "compile" )
        );

        // Set Node Relationships
        l5left.setChildren( Arrays.asList( l6left ) );
        l4.setChildren( Arrays.asList( l5left, l5right ) );
        l3.setChildren( Arrays.asList( l4 ) );
        l2middle.setChildren( Arrays.asList( l3 ) );

        l1left.setChildren( Arrays.asList( l2left, l2middle ) );
        l1right.setChildren( Arrays.asList( l2right ) );

        root.setChildren( Arrays.asList( l1left, l1right ) );

        String actual = serializer.serialize( root );
        File file = new File(getBasedir(), "/target/test-classes/unit/verbose-serializer-test/LargeTree.txt");
        String expected = FileUtils.readFileToString(file);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testLargeGraphWithCycles() throws IOException
    {
        // Construct nodes for tree l1 = level 1 with the root being l0
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0" ), null )
        );
        DependencyNode l1left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" ), "test" )
        );
        DependencyNode l1right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.xyz", "right", "zip", "1" ), "provided" )
        );
        DependencyNode l2left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.maven", "a4", "jar", "2.2.1" ), "system" )
        );
        DependencyNode l2middle = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "a5", "zip", "0" ), "import" )
        );
        DependencyNode l2right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.xyz", "a9", "xml", "1.2" ), "runtime" )
        );
        DependencyNode l3 = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.xyz", "a6", "xml", "1.2.1" ), "compile" )
        );
        DependencyNode l4 = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.example", "a7", "jar", "2.2.2" ), "provided" )
        );
        DependencyNode l5right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.comm", "a7", "jar", "1" ), "compile" )
        );
        DependencyNode l5left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.comm", "a7", "jar", "1" ), "compile" )
        );
        DependencyNode l6left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.example", "a8", "xml", "2.1" ), "runtime" )
        );

        // Set Node Relationships
        l5left.setChildren( Arrays.asList( l6left ) );
        l4.setChildren( Arrays.asList( l5left, l5right ) );
        l3.setChildren( Arrays.asList( l4 ) );
        l2middle.setChildren( Arrays.asList( l3 ) );

        l1left.setChildren( Arrays.asList( l2left, l2middle ) );
        l1right.setChildren( Arrays.asList( l2right ) );

        root.setChildren( Arrays.asList( l1left, l1right ) );

        // Introduce cycles
        l5left.setChildren( Arrays.asList( l2left, l1right, l3 ) );

        String actual = serializer.serialize( root );
        File file = new File(getBasedir(),
                "/target/test-classes/unit/verbose-serializer-test/LargeGraphWithCycles.txt");
        String expected = FileUtils.readFileToString(file);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTreeWithOptional() throws IOException
    {
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0" ), "")
        );
        DependencyNode left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" ), "test", true )
        );
        DependencyNode right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.xyz", "right", "zip", "1" ), "provided" )
        );

        root.setChildren( Arrays.asList( left, right ) );

        String actual = serializer.serialize( root );
        File file = new File(getBasedir(),
                "/target/test-classes/unit/verbose-serializer-test/OptionalDependency.txt");
        String expected = FileUtils.readFileToString(file);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTreeWithScopeConflict() throws IOException
    {
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0" ), null )
        );
        DependencyNode left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" ), "test" )
        );
        DependencyNode right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "conflictArtifact", "jar", "1.0.0" ), "test" )
        );
        DependencyNode leftChild = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "conflictArtifact", "jar", "1.0.0" ), "compile" )
        );

        left.setChildren( Arrays.asList( leftChild ) );
        root.setChildren( Arrays.asList( left, right ) );

        String actual = serializer.serialize( root );
        File file = new File(getBasedir(), "/target/test-classes/unit/verbose-serializer-test/ScopeConflict.txt");
        String expected = FileUtils.readFileToString(file);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTreeWithVersionConflict() throws IOException
    {
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0" ), "rootScope" )
        );
        DependencyNode left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" ), "test" )
        );
        DependencyNode right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "artifact", "jar", "2.0.0" ), "test" )
        );
        // Note that as of now the serializer does not deal with conflicts with the project/root node itself
        DependencyNode leftChild = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "left", "xml", "0.3.1" ), "compile" )
        );

        left.setChildren( Arrays.asList( leftChild ) );
        root.setChildren( Arrays.asList( left, right ) );

        String actual = serializer.serialize( root );
        File file = new File(getBasedir(),
                "/target/test-classes/unit/verbose-serializer-test/VersionConflict.txt");
        String expected = FileUtils.readFileToString(file);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testDotOutput()
    {
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.example", "root", "jar", "3.1.1" ), "" )
        );

        DependencyNode l1left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.duplicate", "duplicate", "xml", "2" ), "compile" )
        );
        DependencyNode l2left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.duplicate", "duplicate", "xml", "2" ), "compile" )
        );
        Artifact cycleArtifact = new DefaultArtifact( "org.cycle", "cycle", "zip", "3" );
        cycleArtifact = cycleArtifact.setProperties( Collections.singletonMap( "Cycle", "true" ) );
        DependencyNode l2middleLeft = new DefaultDependencyNode(
                new Dependency( cycleArtifact, "compile" )
        );

        DependencyNode l1middle = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "maven", "jar", "1.0-SNAPSHOT" ), "test" )
        );
        // should have scope conflict with l1middle, check to make sure its not version
        DependencyNode l2middleRight = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "maven-dependency", "jar", "1.0-SNAPSHOT" ), "compile" )
        );

        DependencyNode l1right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "maven-dependency", "jar", "1.0-SNAPSHOT" ), "test" )
        );
        DependencyNode l5right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "maven-dependency", "jar", "2.1" ), "test" )
        );
        DependencyNode l6right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.abc", "shouldn't show", "xml", "1" ), "compile" )
        );

        Artifact scopeManaged = new DefaultArtifact( "org.scopeManaged", "scope-managed", "zip", "2.1" );
        Map<String, String> artifactProperties = new HashMap<>( scopeManaged.getProperties() );
        artifactProperties.put( PRE_MANAGED_SCOPE, "runtime" );
        artifactProperties.put( MANAGED_SCOPE, "compile" );
        scopeManaged = scopeManaged.setProperties( artifactProperties );
        DependencyNode l2right = new DefaultDependencyNode(
                new Dependency( scopeManaged, "scope" )
        );

        Artifact versionManaged = new DefaultArtifact( "org.versionManaged", "version-manged", "pom", "3.3.3" );
        artifactProperties = new HashMap<>( versionManaged.getProperties() );
        artifactProperties.put( PRE_MANAGED_VERSION, "1.1.0" );
        versionManaged = versionManaged.setProperties( artifactProperties );
        DependencyNode l3right = new DefaultDependencyNode(
                new Dependency( versionManaged, "provided" )
        );

        Artifact scopeVersionManaged = new DefaultArtifact( "org.scopeVersionManaged", "scope-version-managed",
                "xml", "2" );
        artifactProperties = new HashMap<>( scopeVersionManaged.getProperties() );
        artifactProperties.put( PRE_MANAGED_SCOPE, "runtime" );
        artifactProperties.put( MANAGED_SCOPE, "compile" );
        artifactProperties.put( PRE_MANAGED_VERSION, "3.1" );
        scopeVersionManaged = scopeVersionManaged.setProperties( artifactProperties );

        DependencyNode l4right = new DefaultDependencyNode(
                new Dependency( scopeVersionManaged, "runtime" )
        );


        Dependency optionalDependency = new Dependency(
                new DefaultArtifact( "org.apache", "optional", "jar", "1.1" ), "test" );
        optionalDependency = optionalDependency.setOptional( true );
        DependencyNode l1Optional = new DefaultDependencyNode( optionalDependency );

        root.setChildren( Arrays.asList( l1left, l1middle, l1right, l1Optional ) );
        l1left.setChildren( Arrays.asList( l2left, l2middleLeft ) );
        l2left.setChildren( Collections.singletonList( l6right ) ); // l6right shouldn't show due to omitted parent node
        l2middleLeft.setChildren( Collections.singletonList( l6right ) );
        l1Optional.setChildren( Collections.singletonList( l6right ) );
        l1middle.setChildren( Collections.singletonList( l2middleRight ) );
        l1right.setChildren( Collections.singletonList( l2right ) );
        l2right.setChildren( Collections.singletonList( l3right ) );
        l3right.setChildren( Collections.singletonList( l4right ) );
        l4right.setChildren( Collections.singletonList( l5right ) );
        l5right.setChildren( Collections.singletonList( l6right ) );

        String actual = serializer.serialize( root, "dot" );
        String expected = "digraph \"org.example:root:jar:3.1.1\" {"
                + System.lineSeparator()
                + " \"org.example:root:jar:3.1.1\" -> \"org.duplicate:duplicate:xml:2:compile\" ;"
                + System.lineSeparator()
                + " \"org.example:root:jar:3.1.1\" -> \"org.apache:maven:jar:1.0-SNAPSHOT:test\" ;"
                + System.lineSeparator()
                + " \"org.example:root:jar:3.1.1\" -> \"org.apache:maven-dependency:jar:1.0-SNAPSHOT:test\" ;"
                + System.lineSeparator()
                + " \"org.example:root:jar:3.1.1\" -> \"org.apache:optional:jar:1.1:test - omitted due to optional "
                + "dependency\" ;" + System.lineSeparator()
                + " \"org.duplicate:duplicate:xml:2:compile\" -> \"org.duplicate:duplicate"
                + ":xml:2:compile - omitted for duplicate\" ;" + System.lineSeparator()
                + " \"org.duplicate:duplicate:xml:2:compile\" -> \"org.cycle:cycle:zip:3:compile - omitted due "
                + "to cycle\" ;" + System.lineSeparator()
                + " \"org.apache:maven:jar:1.0-SNAPSHOT:test\" -> \"org.apache:maven-dependency"
                + ":jar:1.0-SNAPSHOT:compile - omitted for conflict with test\" ;" + System.lineSeparator()
                + " \"org.apache:maven-dependency:jar:1.0-SNAPSHOT:test\" -> \"org.scopeManaged:scope-managed:zip"
                + ":2.1:compile - scope managed from runtime\" ;" + System.lineSeparator()
                + " \"org.scopeManaged:scope-managed:zip:2.1:compile\" -> \"org.versionManaged:version-manged:"
                + "pom:3.3.3:provided - version managed from 1.1.0\" ;" + System.lineSeparator()
                + " \"org.versionManaged:version-manged:pom:3.3.3:provided\" -> \"org.scopeVersionManaged:"
                + "scope-version-managed:xml:2:compile - version managed from 3.1; scope managed from runtime\" ;"
                + System.lineSeparator() + " \"org.scopeVersionManaged:scope-version-managed:xml:2:compile\" -> "
                + "\"org.apache:maven-dependency:jar:2.1:test - omitted for conflict with 1.0-SNAPSHOT\" ;"
                + System.lineSeparator() + "}";
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTgfOutput()
    {
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.example", "root", "jar", "3.1.1" ), "" )
        );

        DependencyNode l1left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.duplicate", "duplicate", "xml", "2" ), "compile" )
        );
        DependencyNode l2left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.duplicate", "duplicate", "xml", "2" ), "compile" )
        );
        Artifact cycleArtifact = new DefaultArtifact( "org.cycle", "cycle", "zip", "3" );
        cycleArtifact = cycleArtifact.setProperties( Collections.singletonMap( "Cycle", "true" ) );
        DependencyNode l2middleLeft = new DefaultDependencyNode(
                new Dependency( cycleArtifact, "compile" )
        );

        DependencyNode l1middle = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "maven", "jar", "1.0-SNAPSHOT" ), "test" )
        );
        // should have scope conflict with l1middle, check to make sure its not version
        DependencyNode l2middleRight = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "maven-dependency", "jar", "1.0-SNAPSHOT" ), "compile" )
        );

        DependencyNode l1right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "maven-dependency", "jar", "1.0-SNAPSHOT" ), "test" )
        );
        DependencyNode l2right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "maven-dependency", "jar", "2.1" ), "test" )
        );

        Artifact scopeManaged = new DefaultArtifact( "org.scopeManaged", "scope-managed", "zip", "2.1" );
        Map<String, String> artifactProperties = new HashMap<>( scopeManaged.getProperties() );
        artifactProperties.put( PRE_MANAGED_SCOPE, "runtime" );
        artifactProperties.put( MANAGED_SCOPE, "compile" );
        scopeManaged = scopeManaged.setProperties( artifactProperties );
        DependencyNode l3right = new DefaultDependencyNode(
                new Dependency( scopeManaged, "scope" )
        );

        Artifact versionManaged = new DefaultArtifact( "org.versionManaged", "version-manged", "pom", "3.3.3" );
        artifactProperties = new HashMap<>( versionManaged.getProperties() );
        artifactProperties.put( PRE_MANAGED_VERSION, "1.1.0" );
        versionManaged = versionManaged.setProperties( artifactProperties );
        DependencyNode l4right = new DefaultDependencyNode(
                new Dependency( versionManaged, "provided" )
        );

        Artifact scopeVersionManaged = new DefaultArtifact( "org.scopeVersionManaged", "scope-version-managed",
                "xml", "2" );
        artifactProperties = new HashMap<>( scopeVersionManaged.getProperties() );
        artifactProperties.put( PRE_MANAGED_SCOPE, "runtime" );
        artifactProperties.put( MANAGED_SCOPE, "compile" );
        artifactProperties.put( PRE_MANAGED_VERSION, "3.1" );
        scopeVersionManaged = scopeVersionManaged.setProperties( artifactProperties );

        DependencyNode l5right = new DefaultDependencyNode(
                new Dependency( scopeVersionManaged, "runtime" )
        );


        Dependency optionalDependency = new Dependency(
                new DefaultArtifact( "org.apache", "optional", "jar", "1.1" ), "test" );
        optionalDependency = optionalDependency.setOptional( true );
        DependencyNode l1Optional = new DefaultDependencyNode( optionalDependency );

        root.setChildren( Arrays.asList( l1left, l1middle, l1right, l1Optional ) );
        l1left.setChildren( Arrays.asList( l2left, l2middleLeft ) );
        l1middle.setChildren( Collections.singletonList( l2middleRight ) );
        l1right.setChildren( Collections.singletonList( l2right ) );
        l2right.setChildren( Collections.singletonList( l3right ) );
        l3right.setChildren( Collections.singletonList( l4right ) );
        l4right.setChildren( Collections.singletonList( l5right ) );

        String actual = serializer.serialize( root, "tgf" );
        String expected = root.hashCode() + " org.example:root:jar:3.1.1" + System.lineSeparator()
                + l1left.hashCode() + "  org.duplicate:duplicate:xml:2:compile" + System.lineSeparator()
                + l2left.hashCode() + " (org.duplicate:duplicate:xml:2:compile - omitted for duplicate)"
                + System.lineSeparator() + l2middleLeft.hashCode()
                + " (org.cycle:cycle:zip:3:compile - omitted for cycle)" + System.lineSeparator()
                + l1middle.hashCode() + " org.apache:maven:jar:1.0-SNAPSHOT:test" + System.lineSeparator()
                + l2middleRight.hashCode()
                + " (org.apache:maven-dependency:jar:1.0-SNAPSHOT:compile - omitted for conflict with test)"
                + System.lineSeparator() + l1right.hashCode() + "  org.apache:maven-dependency:jar:1.0-SNAPSHOT:test"
                + System.lineSeparator() + "" ;
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testGraphmlOutput()
    {

    }
}
