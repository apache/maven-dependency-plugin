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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Parses dependency graph and outputs in DOT format for end user to review.
 */
class VerboseGraphDotSerializer extends AbstractVerboseGraphSerializer
{
    @Override
    public String serialize( DependencyNode root )
    {
        Set<String> coordinateStrings = new HashSet<>();
        Map<String, String> coordinateVersionMap = new HashMap<>();
        Map<DependencyNode, String> nodeErrors = getNodeConflictMessagesBfs( root, coordinateStrings,
                coordinateVersionMap );
        Set<DependencyNode> visitedNodes = new HashSet<>();
        Queue<DependencyNode> queue = new LinkedList<>();
        queue.add( root );
        Artifact rootArtifact = root.getArtifact();

        StringBuilder result = new StringBuilder( "digraph" );
        result.append( " \"" ).append( rootArtifact.getGroupId() ).append( ":" ).append(
                rootArtifact.getArtifactId() ).append( ":" ).append( rootArtifact.getExtension() ).append( ":" ).append(
                rootArtifact.getVersion() );

        if ( root.getData().containsKey( "ContainsModule" ) )
        {
            result.append( " WARNING: this tree contains a submodule. Once it reaches the submodule will print "
                    + "in nonVerbose fashion, to see the actual submodule verbose output refer to the "
                    + "rest of the output" );
        }

        result.append( "\" {" ).append( "\n" );

        while ( !queue.isEmpty() )
        {
            DependencyNode node = queue.poll();
            for ( DependencyNode child : node.getChildren() )
            {
                result.append( " \"" );
                String coordString = "";
                boolean messageAdded = false;

                if ( child.getArtifact().getProperties().containsKey( PRE_MANAGED_VERSION ) )
                {
                    coordString +=
                            " - version managed from " + child.getArtifact().getProperties().get(
                                    PRE_MANAGED_VERSION );
                    messageAdded = true;
                }
                if ( child.getArtifact().getProperties().containsKey( PRE_MANAGED_SCOPE ) )
                {
                    if ( messageAdded )
                    {
                        coordString += "; ";
                    }
                    else
                    {
                        coordString += " - ";
                        messageAdded = true;
                    }
                    coordString +=
                            "scope managed from " + child.getArtifact().getProperties().get( PRE_MANAGED_SCOPE );
                }
                coordString = getDependencyCoordinate( child ) + coordString;
                result.append( getDependencyCoordinate( node ) ).append( "\"" ).append( " -> \"" ).append(
                        coordString );
                if ( child.getArtifact().getProperties().containsKey( "Cycle" ) )
                {
                    if ( !messageAdded )
                    {
                        result.append( " -" );
                    }
                    result.append( " omitted due to cycle" );
                }
                else if ( nodeErrors.get( child ) != null )
                {
                    if ( !messageAdded )
                    {
                        result.append( " - " );
                    }
                    result.append( nodeErrors.get( child ) );
                }
                else if ( !visitedNodes.contains( child ) )
                {
                    visitedNodes.add( child );
                    queue.add( child );
                }
                result.append( "\" ;" ).append( "\n" );
            }
        }
        result.append( "}" );
        return result.toString();
    }
}
