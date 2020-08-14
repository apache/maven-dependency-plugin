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

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class VerboseGraphSerializerTest extends AbstractMojoTestCase
{
    private AbstractVerboseGraphSerializer serializer;

    private static final String PRE_MANAGED_SCOPE = "preManagedScope", PRE_MANAGED_VERSION = "preManagedVersion",
            MANAGED_SCOPE = "managedScope";

    @Test
    public void testBasicTree() throws IOException
    {
        serializer = new VerboseGraphTextSerializer();
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
        String expected = "com.google:rootArtifact:jar:1.0.0\n" + "+- org.apache:left:xml:0.1-SNAPSHOT:test\n"
                + "\\- org.xyz:right:zip:1:provided\n";

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testLargeTree() throws IOException
    {
        serializer = new VerboseGraphTextSerializer();
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
        String expected = "com.google:rootArtifact:jar:1.0.0\n" + "+- org.apache:left:xml:0.1-SNAPSHOT:test\n"
                + "|  +- org.maven:a4:jar:2.2.1:system\n" + "|  \\- com.google:a5:zip:0:import\n"
                + "|     \\- com.xyz:a6:xml:1.2.1:provided\n" + "|        \\- com.example:a7:jar:2.2.2:provided\n"
                + "|           +- com.comm:a7:jar:1:compile\n" + "|           |  \\- com.example:a8:xml:2.1:compile\n"
                + "|           \\- (com.comm:a7:jar:1:compile - omitted for duplicate)\n"
                + "\\- org.xyz:right:zip:1:provided\n" + "   \\- com.xyz:a9:xml:1.2:runtime\n";

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testLargeGraphWithCycles() throws IOException
    {
        serializer = new VerboseGraphTextSerializer();
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
        String expected = "com.google:rootArtifact:jar:1.0.0\n" + "+- org.apache:left:xml:0.1-SNAPSHOT:test\n"
                + "|  +- org.maven:a4:jar:2.2.1:system\n" + "|  \\- com.google:a5:zip:0:import\n"
                + "|     \\- com.xyz:a6:xml:1.2.1:compile\n" + "|        \\- com.example:a7:jar:2.2.2:provided\n"
                + "|           +- (com.comm:a7:jar:1:compile - omitted for introducing a cycle with org.maven:a4:"
                + "jar:2.2.1:system)\n" + "|           \\- (com.comm:a7:jar:1:compile - omitted for duplicate)\n"
                + "\\- org.xyz:right:zip:1:provided\n" + "   \\- com.xyz:a9:xml:1.2:runtime\n";

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTreeWithOptional() throws IOException
    {
        serializer = new VerboseGraphTextSerializer();
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
        String expected = "com.google:rootArtifact:jar:1.0.0\n"
                + "+- (org.apache:left:xml:0.1-SNAPSHOT:test - omitted due to optional dependency)\n"
                + "\\- org.xyz:right:zip:1:provided\n";

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTreeWithScopeConflict() throws IOException
    {
        serializer = new VerboseGraphTextSerializer();
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
        String expected = "com.google:rootArtifact:jar:1.0.0\n" + "+- org.apache:left:xml:0.1-SNAPSHOT:test\n"
                + "|  \\- (com.google:conflictArtifact:jar:1.0.0:compile - omitted for conflict with test)\n"
                + "\\- com.google:conflictArtifact:jar:1.0.0:test\n";

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTreeWithVersionConflict() throws IOException
    {
        serializer = new VerboseGraphTextSerializer();
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
        String expected = "com.google:rootArtifact:jar:1.0.0\n" + "+- org.apache:left:xml:0.1-SNAPSHOT:test\n"
                + "|  \\- (org.apache:left:xml:0.3.1:compile - omitted for conflict with 0.1-SNAPSHOT)\n"
                + "\\- com.google:artifact:jar:2.0.0:test\n";

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testDotOutput()
    {
        serializer = new VerboseGraphDotSerializer();
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

        String actual = serializer.serialize( root );
        String expected = "digraph \"org.example:root:jar:3.1.1\" {"
                + "\n"
                + " \"org.example:root:jar:3.1.1\" -> \"org.duplicate:duplicate:xml:2:compile\" ;"
                + "\n"
                + " \"org.example:root:jar:3.1.1\" -> \"org.apache:maven:jar:1.0-SNAPSHOT:test\" ;"
                + "\n"
                + " \"org.example:root:jar:3.1.1\" -> \"org.apache:maven-dependency:jar:1.0-SNAPSHOT:test\" ;"
                + "\n"
                + " \"org.example:root:jar:3.1.1\" -> \"org.apache:optional:jar:1.1:test - omitted due to optional "
                + "dependency\" ;" + "\n"
                + " \"org.duplicate:duplicate:xml:2:compile\" -> \"org.duplicate:duplicate"
                + ":xml:2:compile - omitted for duplicate\" ;" + "\n"
                + " \"org.duplicate:duplicate:xml:2:compile\" -> \"org.cycle:cycle:zip:3:compile - omitted due "
                + "to cycle\" ;" + "\n"
                + " \"org.apache:maven:jar:1.0-SNAPSHOT:test\" -> \"org.apache:maven-dependency"
                + ":jar:1.0-SNAPSHOT:compile - omitted for conflict with test\" ;" + "\n"
                + " \"org.apache:maven-dependency:jar:1.0-SNAPSHOT:test\" -> \"org.scopeManaged:scope-managed:zip"
                + ":2.1:compile - scope managed from runtime\" ;" + "\n"
                + " \"org.scopeManaged:scope-managed:zip:2.1:compile\" -> \"org.versionManaged:version-manged:"
                + "pom:3.3.3:provided - version managed from 1.1.0\" ;" + "\n"
                + " \"org.versionManaged:version-manged:pom:3.3.3:provided\" -> \"org.scopeVersionManaged:"
                + "scope-version-managed:xml:2:compile - version managed from 3.1; scope managed from runtime\" ;"
                + "\n" + " \"org.scopeVersionManaged:scope-version-managed:xml:2:compile\" -> "
                + "\"org.apache:maven-dependency:jar:2.1:test - omitted for conflict with 1.0-SNAPSHOT\" ;"
                + "\n" + "}";
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTgfOutput()
    {
        serializer = new VerboseGraphTgfSerializer();
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

        String actual = serializer.serialize( root );
        String expected = root.hashCode() + " org.example:root:jar:3.1.1" + "\n"
                + l1left.hashCode() + " org.duplicate:duplicate:xml:2:compile" + "\n"
                + l2left.hashCode() + " (org.duplicate:duplicate:xml:2:compile - omitted for duplicate)"
                + "\n" + l2middleLeft.hashCode()
                + " (org.cycle:cycle:zip:3:compile - omitted for cycle)" + "\n"
                + l1middle.hashCode() + " org.apache:maven:jar:1.0-SNAPSHOT:test" + "\n"
                + l2middleRight.hashCode()
                + " (org.apache:maven-dependency:jar:1.0-SNAPSHOT:compile - omitted for conflict with test)"
                + "\n" + l1right.hashCode() + " org.apache:maven-dependency:jar:1.0-SNAPSHOT:test"
                + "\n" + l2right.hashCode()
                + " org.scopeManaged:scope-managed:zip:2.1:compile - scope managed from runtime"
                + "\n" + l3right.hashCode()
                + " org.versionManaged:version-manged:pom:3.3.3:provided - version managed from 1.1.0"
                + "\n" + l4right.hashCode()
                + " org.scopeVersionManaged:scope-version-managed:xml:2:compile - version managed from 3.1; "
                + "scope managed from runtime" + "\n" + l5right.hashCode()
                + " (org.apache:maven-dependency:jar:2.1:test - omitted for conflict with 1.0-SNAPSHOT)"
                + "\n" + l1Optional.hashCode()
                + " (org.apache:optional:jar:1.1:test - omitted due to optional dependency)"
                + "\n" + "#" + "\n" + root.hashCode() + " " + l1left.hashCode()
                + " compile" + "\n"
                + l1left.hashCode() + " " + l2left.hashCode() + " compile omitted for duplicate"
                + "\n" + l1left.hashCode() + " " + l2middleLeft.hashCode()
                + " compile omitted for cycle" + "\n" + root.hashCode() + " " + l1middle.hashCode()
                + " test" + "\n" + l1middle.hashCode() + " " + l2middleRight.hashCode()
                + " compile omitted for conflict with test" + "\n" + root.hashCode() + " "
                + l1right.hashCode() + " test" + "\n" + l1right.hashCode() + " " + l2right.hashCode()
                + " compile managed from runtime" + "\n" + l2right.hashCode() + " "
                + l3right.hashCode() + " provided" + "\n" + l3right.hashCode() + " "
                + l4right.hashCode() + " compile managed from runtime" + "\n" + l4right.hashCode()
                + " " + l5right.hashCode() + " test omitted for conflict with 1.0-SNAPSHOT" + "\n" +
                root.hashCode() + " " + l1Optional.hashCode() + " test" + "\n";
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testGraphmlOutput()
    {
        serializer = new VerboseGraphGraphmlSerializer();
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

        String actual = serializer.serialize( root );
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <graphml xmlns=\"http://graphml.graph"
                + "drawing.org/xmlns\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:y=\"http://www.ywo"
                + "rks.com/xml/graphml\" xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns http://graphml."
                + "graphdrawing.org/xmlns/1.0/graphml.xsd\">" + "\n" + "  <key for=\"node\" id=\"d0\" "
                + "yfiles.type=\"nodegraphics\"/>" + "\n" + "  <key for=\"edge\" id=\"d1\" yfiles"
                + ".type=\"edgegraphics\"/>" + "\n"
                + "<graph id=\"dependencies\" edgedefault=\"directed\">"
                + "\n" + "<node id=\"" + root.hashCode() + "\"><data key=\"d0\"><y:ShapeNode>"
                + "<y:NodeLabel>" + "org.example:root:jar:3.1.1</y:NodeLabel></y:ShapeNode></data></node>"
                + "\n" + "<node id=\"" + l1left.hashCode() + "\"><data key=\"d0\"><y:ShapeNode>"
                + "<y:NodeLabel>org.duplicate:duplicate:xml:" + "2:compile</y:NodeLabel></y:ShapeNode></data></node>"
                + "\n"
                + "<node id=\"" + l1middle.hashCode() + "\"><data key=\"d0\"><y:ShapeNode><y:NodeLabel>org.apache:"
                + "maven:jar:1.0-SNAPSHOT:test</y:NodeLabel></y:ShapeNode></data></node>" + "\n"
                + "<node id=\"" + l1right.hashCode() + "\"><data key=\"d0\"><y:ShapeNode><y:NodeLabel>org.apache:maven"
                + "-dependency:jar:1.0-SNAPSHOT:test</y:NodeLabel></y:ShapeNode></data></node>" + "\n"
                + "<node id=\"" + l1Optional.hashCode() + "\"><data key=\"d0\"><y:ShapeNode><y:NodeLabel>org.apache:"
                + "optional:jar:1.1:test - omitted due to optional dependency</y:NodeLabel></y:ShapeNode></data></node>"
                + "\n" + "<node id=\"" + l2left.hashCode() + "\"><data key=\"d0\"><y:ShapeNode>"
                + "<y:NodeLabel>org.duplicate:duplicate:xml:2:compile - omitted for duplicate</y:NodeLabel>"
                + "</y:ShapeNode></data></node>" + "\n" + "<node id=\"" + l2middleLeft.hashCode()
                + "\"><data key=\"d0\"><y:ShapeNode><y:NodeLabel>org.cycle:cycle:zip:3:compile - omitted due to cycle"
                + "</y:NodeLabel></y:ShapeNode></data></node>" + "\n"
                + "<node id=\"" + l2middleRight.hashCode() + "\">"
                + "<data key=\"d0\"><y:ShapeNode><y:NodeLabel>org.apache:maven-dependency:jar:1.0-SNAPSHOT:compile "
                + "- omitted for conflict with test</y:NodeLabel></y:ShapeNode></data></node>" + "\n"
                + "<node id=\"" + l2right.hashCode() + "\"><data key=\"d0\"><y:ShapeNode><y:NodeLabel>org.scopeManaged"
                + ":scope-managed:zip:2.1:compile - scope managed from runtime</y:NodeLabel></y:ShapeNode></data>"
                + "</node>" + "\n" + "<node id=\"" + l3right.hashCode() + "\"><data key=\"d0\">"
                + "<y:ShapeNode><y:NodeLabel>org.versionManaged:version-manged:pom:3.3.3:provided - version managed "
                + "from 1.1.0</y:NodeLabel></y:ShapeNode></data></node>" + "\n"
                + "<node id=\"" + l4right.hashCode()
                + "\"><data key=\"d0\"><y:ShapeNode><y:NodeLabel>org.scopeVersionManaged:scope-version-managed:xml:"
                + "2:compile - version managed from 3.1; scope managed from runtime</y:NodeLabel></y:ShapeNode>"
                + "</data></node>" + "\n" + "<node id=\"" + l5right.hashCode() + "\"><data key="
                + "\"d0\"><y:ShapeNode><y:NodeLabel>org.apache:maven-dependency:jar:2.1:test - omitted for"
                + " conflict with 1.0-SNAPSHOT</y:NodeLabel></y:ShapeNode></data></node>" + "\n"
                + "<edge source=\"" + root.hashCode() + "\" target=\"" + l1left.hashCode()
                + "\"><data key=\"d1\"><y:PolyLineEdge><y:EdgeLabel>compile</y:EdgeLabel></y:PolyLineEdge></data>"
                + "</edge>" + "\n" + "<edge source=\"" + root.hashCode() + "\" target=\""
                + l1middle.hashCode() + "\"><data key=\"d1\"><y:PolyLineEdge><y:EdgeLabel>test</y:EdgeLabel>"
                + "</y:PolyLineEdge></data></edge>" + "\n" + "<edge source=\"" + root.hashCode()
                + "\" target=\"" + l1right.hashCode() + "\"><data key=\"d1\"><y:PolyLineEdge><y:EdgeLabel>test"
                + "</y:EdgeLabel></y:PolyLineEdge></data></edge>" + "\n" + "<edge source=\""
                + root.hashCode() + "\" target=\"" + l1Optional.hashCode() + "\"><data key=\"d1\"><y:PolyLineEdge>"
                + "<y:EdgeLabel>test omitted due to optional dependency</y:EdgeLabel></y:PolyLineEdge></data></edge>"
                + "\n" + "<edge source=\"" + l1left.hashCode() + "\" target=\""
                + l2left.hashCode() + "\"><data key=\"d1\"><y:PolyLineEdge><y:EdgeLabel>compile omitted "
                + "for duplicate</y:EdgeLabel></y:PolyLineEdge></data></edge>" + "\n"
                + "<edge source=\"" + l1left.hashCode() + "\" target=\"" + l2middleLeft.hashCode()
                + "\"><data key=\"d1\"><y:PolyLineEdge><y:EdgeLabel>compile omitted due to cycle</y:EdgeLabel>"
                + "</y:PolyLineEdge></data></edge>" + "\n" + "<edge source=\"" + l1middle.hashCode()
                + "\" target=\""
                + l2middleRight.hashCode() + "\"><data key=\"d1\"><y:PolyLineEdge><y:EdgeLabel>compile omitted"
                + " for conflict with test</y:EdgeLabel></y:PolyLineEdge></data></edge>" + "\n"
                + "<edge source=\"" + l1right.hashCode() + "\" target=\"" + l2right.hashCode() + "\"><data key=\"d1\">"
                + "<y:PolyLineEdge><y:EdgeLabel>compile, scope managed from runtime</y:EdgeLabel></y:PolyLineEdge>"
                + "</data></edge>" + "\n" + "<edge source=\"" + l2right.hashCode() + "\" target="
                + "\"" + l3right.hashCode() + "\"><data key=\"d1\"><y:PolyLineEdge><y:EdgeLabel>provided"
                + "</y:EdgeLabel></y:PolyLineEdge></data></edge>" + "\n" + "<edge source=\""
                + l3right.hashCode() + "\" target=\"" + l4right.hashCode() + "\"><data key=\"d1\"><y:PolyLineEdge>"
                + "<y:EdgeLabel>compile, scope managed from runtime</y:EdgeLabel></y:PolyLineEdge></data>"
                + "</edge>" + "\n" + "<edge source=\"" + l4right.hashCode() + "\" target=\""
                + l5right.hashCode() + "\"><data key=\"d1\"><y:PolyLineEdge><y:EdgeLabel>test omitted "
                + "for conflict with 1.0-SNAPSHOT</y:EdgeLabel></y:PolyLineEdge></data></edge>" + "\n"
                + "</graph></graphml>";
        Assert.assertEquals(expected, actual);
    }
}
