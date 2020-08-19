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

import org.eclipse.aether.graph.DependencyNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Parses dependency graph and outputs in GraphML format for end user to review.
 */
public class VerboseGraphGraphmlSerializer extends AbstractVerboseGraphSerializer
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
        StringBuilder result = new StringBuilder( "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
                + "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xmlns:y=\"http://www.yworks.com/xml/graphml\" "
                + "xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns "
                + "http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">"
                + "\n"
                + "  <key for=\"node\" id=\"d0\" yfiles.type=\"nodegraphics\"/>"
                + "\n"
                + "  <key for=\"edge\" id=\"d1\" yfiles.type=\"edgegraphics\"/>"
                + "\n"
                + "<graph id=\"dependencies\" edgedefault=\"directed\">" + "\n" );

        StringBuilder nodes = new StringBuilder();
        StringBuilder edges = new StringBuilder();
        while ( !queue.isEmpty() )
        {
            DependencyNode node = queue.poll();
            nodes.append( getGraphmlNodeLine( node, nodeErrors ) );
            if ( nodeErrors.get( node ) == null && !node.getArtifact().getProperties().containsKey( "Cycle" ) )
            {
                for ( DependencyNode child : node.getChildren() )
                {
                    if ( !visitedNodes.contains( child ) )
                    {
                        visitedNodes.add( child );
                        queue.add( child );
                    }
                    edges.append( getGraphmlEdgeLine( node, child, nodeErrors ) );
                }
            }
        }
        result.append( nodes ).append( edges );
        result.append( "</graph></graphml>" );
        return result.toString();
    }

    private String getGraphmlEdgeLine( DependencyNode parent, DependencyNode child,
                                       Map<DependencyNode, String> nodeErrors )
    {
        StringBuilder builder = new StringBuilder();
        builder.append( "<edge source=\"" ).append( parent.hashCode() ).append( "\" target=\"" ).append(
                child.hashCode() ).append( "\"><data key=\"d1\"><y:PolyLineEdge><y:EdgeLabel>" );


        boolean messageAdded = false;

        if ( child.getArtifact().getProperties().containsKey( PRE_MANAGED_SCOPE ) )
        {
            messageAdded = true;
            builder.append( child.getArtifact().getProperties().get( MANAGED_SCOPE ) );
            builder.append( ", scope managed from " ).append(
                    child.getArtifact().getProperties().get( PRE_MANAGED_SCOPE ) );
        }
        else
        {
            builder.append( child.getDependency().getScope() );
        }
        if ( child.getArtifact().getProperties().containsKey( "Cycle" ) )
        {
            if ( messageAdded )
            {
                builder.append( "," );
            }
            builder.append( " omitted due to cycle" );
        }
        else if ( nodeErrors.get( child ) != null )
        {
            if ( messageAdded )
            {
                builder.append( "," );
            }
            builder.append( " " ).append( nodeErrors.get( child ) );
        }
        builder.append( "</y:EdgeLabel></y:PolyLineEdge></data></edge>" ).append( "\n" );
        return builder.toString();
    }

    private String getGraphmlNodeLine( DependencyNode node, Map<DependencyNode, String> nodeErrors )
    {
        StringBuilder builder = new StringBuilder();
        builder.append( "<node id=\"" ).append( node.hashCode() ).append(
                "\"><data key=\"d0\"><y:ShapeNode>" + "<y:NodeLabel>" );

        String coordString = "";
        boolean messageAdded = false;

        if ( node.getArtifact().getProperties().containsKey( PRE_MANAGED_VERSION ) )
        {
            coordString +=
                    " - version managed from " + node.getArtifact().getProperties().get( PRE_MANAGED_VERSION );
            messageAdded = true;
        }
        if ( node.getArtifact().getProperties().containsKey( PRE_MANAGED_SCOPE ) )
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
                    "scope managed from " + node.getArtifact().getProperties().get( PRE_MANAGED_SCOPE );
        }
        builder.append( getDependencyCoordinate( node ) ).append( coordString );
        if ( node.getData().containsKey( "ContainsModule" ) )
        {
            builder.append( " WARNING: this tree contains a submodule. Once it reaches the submodule will print "
                    + "in nonVerbose fashion, to see the actual submodule "
                    + "verbose output refer to the rest of the output" );
        }
        if ( node.getArtifact().getProperties().containsKey( "Cycle" ) )
        {
            if ( !messageAdded )
            {
                builder.append( " - " );
            }
            builder.append( "omitted due to cycle" );
        }
        else if ( nodeErrors.get( node ) != null )
        {
            if ( !messageAdded )
            {
                builder.append( " - " );
            }
            builder.append( nodeErrors.get( node ) );
        }
        builder.append( "</y:NodeLabel></y:ShapeNode></data></node>" ).append( "\n" );
        return builder.toString();
    }
}
