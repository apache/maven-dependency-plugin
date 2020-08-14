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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Parses dependency graph and outputs in text format for end user to review.
 */
final class VerboseGraphSerializer
{
    private static final String LINE_START_LAST_CHILD = "\\- ", LINE_START_CHILD = "+- ";
    private static final String PRE_MANAGED_SCOPE = "preManagedScope", PRE_MANAGED_VERSION = "preManagedVersion",
            MANAGED_SCOPE = "managedScope";

    public String serialize( DependencyNode root )
    {
        return serialize( root, "" );
    }

    public String serialize( DependencyNode root, String outputType )
    {
        Set<String> coordinateStrings = new HashSet<>();
        Map<String, String> coordinateVersionMap = new HashMap<>();
        // Use BFS to mirror how Maven resolves dependencies and use DFS to print the tree easily
        Map<DependencyNode, String> nodeErrors = getNodeConflictMessagesBfs( root, coordinateStrings,
                coordinateVersionMap );

        if ( "graphml".equals( outputType ) )
        {
            return serializeGraphml( root, nodeErrors );
        }
        else if ( "tgf".equals( outputType ) )
        {
            return serializeTgf( root, nodeErrors );
        }
        else if ( "dot".equals( outputType ) )
        {
            return serializeDot( root, nodeErrors );
        }
        else
        {
            return serializeText( root, nodeErrors );
        }
    }

    private String serializeGraphml( DependencyNode root, Map<DependencyNode, String> nodeErrors )
    {
        Set<DependencyNode> visitedNodes = new HashSet<>();
        Queue<DependencyNode> queue = new LinkedList<>();
        queue.add( root );
        StringBuilder result = new StringBuilder( "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
                        + "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xmlns:y=\"http://www.yworks.com/xml/graphml\" "
                + "xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns "
                + "http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">"
                + System.lineSeparator()
                + "  <key for=\"node\" id=\"d0\" yfiles.type=\"nodegraphics\"/>"
                + System.lineSeparator()
                + "  <key for=\"edge\" id=\"d1\" yfiles.type=\"edgegraphics\"/>"
                + System.lineSeparator()
                + "<graph id=\"dependencies\" edgedefault=\"directed\">" + System.lineSeparator() );

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
        builder.append( "</y:EdgeLabel></y:PolyLineEdge></data></edge>" ).append( System.lineSeparator() );
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
            coordString = coordString.concat(
                    " - version managed from " + node.getArtifact().getProperties().get( PRE_MANAGED_VERSION ) );
            messageAdded = true;
        }
        if ( node.getArtifact().getProperties().containsKey( PRE_MANAGED_SCOPE ) )
        {
            if ( messageAdded )
            {
                coordString = coordString.concat( "; " );
            }
            else
            {
                coordString = coordString.concat( " - " );
                messageAdded = true;
            }
            coordString = coordString.concat(
                    "scope managed from " + node.getArtifact().getProperties().get( PRE_MANAGED_SCOPE ) );
        }
        builder.append( getDependencyCoordinate( node ) ).append( coordString );
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
        builder.append( "</y:NodeLabel></y:ShapeNode></data></node>" ).append( System.lineSeparator() );
        return builder.toString();
    }

    private String serializeDot( DependencyNode root, Map<DependencyNode, String> nodeErrors )
    {
        Set<DependencyNode> visitedNodes = new HashSet<>();
        Queue<DependencyNode> queue = new LinkedList<>();
        queue.add( root );
        Artifact rootArtifact = root.getArtifact();

        StringBuilder result = new StringBuilder( "digraph" );
        result.append( " \"" ).append( rootArtifact.getGroupId() ).append( ":" ).append(
                rootArtifact.getArtifactId() ).append( ":" ).append( rootArtifact.getExtension() ).append( ":" ).append(
                rootArtifact.getVersion() ).append( "\" {" ).append( System.lineSeparator() );

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
                    coordString = coordString.concat(
                            " - version managed from " + child.getArtifact().getProperties().get(
                                    PRE_MANAGED_VERSION ) );
                    messageAdded = true;
                }
                if ( child.getArtifact().getProperties().containsKey( PRE_MANAGED_SCOPE ) )
                {
                    if ( messageAdded )
                    {
                        coordString = coordString.concat( "; " );
                    }
                    else
                    {
                        coordString = coordString.concat( " - " );
                        messageAdded = true;
                    }
                    coordString = coordString.concat(
                            "scope managed from " + child.getArtifact().getProperties().get( PRE_MANAGED_SCOPE ) );
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
                result.append( "\" ;" ).append( System.lineSeparator() );
            }
        }
        result.append( "}" );
        return result.toString();
    }

    private String serializeTgf( DependencyNode root, Map<DependencyNode, String> nodeErrors )
    {
        StringBuilder nodes = new StringBuilder();
        StringBuilder edges = new StringBuilder( "#" );
        edges.append( System.lineSeparator() );

        // deal with root first
        Artifact rootArtifact = root.getArtifact();
        nodes.append( root.hashCode() ).append( " " ).append( rootArtifact.getGroupId() ).append( ":" ).append(
                rootArtifact.getArtifactId() ).append( ":" ).append( rootArtifact.getExtension() ).append( ":" ).append(
                rootArtifact.getVersion() ).append( System.lineSeparator() );

        for ( DependencyNode child : root.getChildren() )
        {
            edges.append( root.hashCode() ).append( " " ).append( child.hashCode() ).append( " " ).append(
                    child.getDependency().getScope() ).append( System.lineSeparator() );
            serializeTgfDfs( child, nodeErrors, nodes, edges );
        }

        return nodes.append( edges ).toString();
    }

    private void serializeTgfDfs( DependencyNode node, Map<DependencyNode, String> nodeErrors, StringBuilder nodes,
                                  StringBuilder edges )
    {
        nodes.append( node.hashCode() ).append( " " );
        String coordString = "";
        boolean messageAdded = false;

        if ( node.getArtifact().getProperties().containsKey( PRE_MANAGED_VERSION ) )
        {
            coordString = coordString.concat(
                    " - version managed from " + node.getArtifact().getProperties().get( PRE_MANAGED_VERSION ) );
            messageAdded = true;
        }
        if ( node.getArtifact().getProperties().containsKey( PRE_MANAGED_SCOPE ) )
        {
            if ( messageAdded )
            {
                coordString = coordString.concat( "; " );
            }
            else
            {
                coordString = coordString.concat( " - " );
                messageAdded = true;
            }
            coordString = coordString.concat(
                    "scope managed from " + node.getArtifact().getProperties().get( PRE_MANAGED_SCOPE ) );
        }
        coordString = getDependencyCoordinate( node ) + coordString;


        if ( node.getArtifact().getProperties().containsKey( "Cycle" ) )
        {
            if ( messageAdded )
            {
                coordString = coordString.concat( "; " );
            }
            else
            {
                coordString = coordString.concat( " - " );
            }
            coordString = coordString.concat( "omitted for cycle" );
            nodes.append( "(" ).append( coordString ).append( ")" ).append( System.lineSeparator() );
        }
        else if ( nodeErrors.get( node ) != null )
        {
            nodes.append( "(" );
            if ( messageAdded )
            {
                nodes.append( coordString ).append( "; " ).append( nodeErrors.get( node ) );
            }
            else
            {
                nodes.append( coordString ).append( " - " ).append( nodeErrors.get( node ) );
            }
            nodes.append( ")" ).append( System.lineSeparator() );
        }
        else
        {
            nodes.append( coordString ).append( System.lineSeparator() );
            for ( DependencyNode child : node.getChildren() )
            {
                edges.append( node.hashCode() ).append( " " ).append( child.hashCode() ).append( " " );
                if ( child.getArtifact().getProperties().get( MANAGED_SCOPE ) != null )
                {
                    edges.append( child.getArtifact().getProperties().get( MANAGED_SCOPE ) ).append( " managed from " )
                            .append( child.getArtifact().getProperties().get( PRE_MANAGED_SCOPE ) );
                }
                else
                {
                    edges.append( child.getDependency().getScope() );
                }

                if ( child.getArtifact().getProperties().containsKey( "Cycle" ) )
                {
                    edges.append( " omitted for cycle" );
                }
                else if ( nodeErrors.get( child ) != null )
                {
                    edges.append( " " ).append( nodeErrors.get( child ) );
                }
                edges.append( System.lineSeparator() );
                serializeTgfDfs( child, nodeErrors, nodes, edges );
            }
        }
    }

    private String serializeText( DependencyNode root, Map<DependencyNode, String> nodeErrors )
    {
        StringBuilder builder = new StringBuilder();

        // deal with root first
        Artifact rootArtifact = root.getArtifact();
        builder.append( rootArtifact.getGroupId() ).append( ":" ).append( rootArtifact.getArtifactId() ).append(
                ":" ).append( rootArtifact.getExtension() ).append( ":" ).append( rootArtifact.getVersion() ).append(
                System.lineSeparator() );

        for ( int i = 0; i < root.getChildren().size(); i++ )
        {
            if ( i == root.getChildren().size() - 1 )
            {
                dfsPrint( root.getChildren().get( i ), LINE_START_LAST_CHILD, builder, nodeErrors );
            }
            else
            {
                dfsPrint( root.getChildren().get( i ), LINE_START_CHILD, builder, nodeErrors );
            }
        }
        return builder.toString();
    }

    private static String getDependencyCoordinate( DependencyNode node )
    {
        Artifact artifact = node.getArtifact();

        if ( node.getDependency() == null )
        {
            // should only get here if node is root
            return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension() + ":"
                    + artifact.getVersion();
        }

        String scope;
        if ( artifact.getProperties().containsKey( MANAGED_SCOPE ) )
        {
            scope = artifact.getProperties().get( MANAGED_SCOPE );
        }
        else
        {
            scope = node.getDependency().getScope();
        }

        String coords = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension() + ":"
                + artifact.getVersion();

        if ( scope != null && !scope.isEmpty() )
        {
            coords = coords.concat( ":" + scope );
        }
        return coords;
    }

    private static String getVersionlessScopelessCoordinate( DependencyNode node )
    {
        Artifact artifact = node.getArtifact();
        // scope not included because we check for scope conflicts separately
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension();
    }

    private static boolean isDuplicateDependencyCoordinate( DependencyNode node, Set<String> coordinateStrings )
    {
        return coordinateStrings.contains( getDependencyCoordinate( node ) );
    }

    private static String versionConflict( DependencyNode node, Map<String, String> coordinateVersionMap )
    {
        if ( coordinateVersionMap.containsKey( getVersionlessScopelessCoordinate( node ) ) )
        {
            return coordinateVersionMap.get( getVersionlessScopelessCoordinate( node ) );
        }
        return null;
    }

    private static String scopeConflict( DependencyNode node, Set<String> coordinateStrings )
    {
        Artifact artifact = node.getArtifact();
        List<String> scopes = Arrays.asList( "compile", "provided", "runtime", "test", "system" );

        for ( String scope : scopes )
        {
            String coordinate = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension()
                    + ":" + artifact.getVersion() + ":" + scope;
            if ( coordinateStrings.contains( coordinate ) )
            {
                return scope;
            }
        }
        return null;
    }

    private Map<DependencyNode, String> getNodeConflictMessagesBfs( DependencyNode root, Set<String> coordinateStrings,
                                                                    Map<String, String> coordinateVersionMap )
    {
        Map<DependencyNode, String> nodeErrors = new HashMap<>();
        Set<DependencyNode> visitedNodes = new HashSet<>( 512 );
        Queue<DependencyNode> queue = new LinkedList<>();
        visitedNodes.add( root );
        queue.add( root );

        while ( !queue.isEmpty() )
        {
            DependencyNode node = queue.poll();

            if ( node == null || node.getArtifact() == null )
            {
                // Should never reach hit this condition with a proper graph sent in
                nodeErrors.put( node, "Null Artifact Node" );
                break;
            }

            if ( isDuplicateDependencyCoordinate( node, coordinateStrings ) )
            {
                nodeErrors.put( node, "omitted for duplicate" );
            }
            else if ( scopeConflict( node, coordinateStrings ) != null )
            {
                nodeErrors.put( node, "omitted for conflict with " + scopeConflict( node, coordinateStrings ) );
            }
            else if ( versionConflict( node, coordinateVersionMap ) != null )
            {
                nodeErrors.put( node, "omitted for conflict with " + versionConflict( node, coordinateVersionMap ) );
            }
            else if ( node.getDependency() != null && node.getDependency().isOptional() )
            {
                nodeErrors.put( node, "omitted due to optional dependency" );
            }
            else
            {
                boolean ignoreNode = false;
                nodeErrors.put( node, null );

                if ( node.getArtifact() != null )
                {
                    coordinateVersionMap.put( getVersionlessScopelessCoordinate( node ),
                            node.getArtifact().getVersion() );
                }

                for ( DependencyNode child : node.getChildren() )
                {
                    if ( visitedNodes.contains( child ) )
                    {
                        ignoreNode = true;
                        nodeErrors.put( node,
                                "omitted for introducing a cycle with " + getDependencyCoordinate( child ) );
                        node.setChildren( new ArrayList<DependencyNode>() );
                        break;
                    }
                }

                if ( !ignoreNode )
                {
                    for ( int i = 0; i < node.getChildren().size(); ++i )
                    {
                        DependencyNode child = node.getChildren().get( i );

                        if ( !visitedNodes.contains( child ) )
                        {
                            visitedNodes.add( child );
                            queue.add( child );
                        }
                    }
                }
            }
            coordinateStrings.add( getDependencyCoordinate( node ) );
        }
        return nodeErrors;
    }

    private void dfsPrint( DependencyNode node, String start, StringBuilder builder,
                           Map<DependencyNode, String> nodeErrors )
    {
        builder.append( start );
        if ( node.getArtifact() == null )
        {
            // Should never hit this condition with a proper graph sent in
            builder.append( "Null Artifact Node" ).append( System.lineSeparator() );
            callDfsPrint( node, start, builder, nodeErrors );
        }

        String coordString = "";
        boolean messageAdded = false;

        if ( node.getArtifact().getProperties().containsKey( PRE_MANAGED_VERSION ) )
        {
            coordString = coordString.concat(
                    " - version managed from " + node.getArtifact().getProperties().get( PRE_MANAGED_VERSION ) );
            messageAdded = true;
        }

        if ( node.getArtifact().getProperties().containsKey( PRE_MANAGED_SCOPE ) )
        {
            if ( messageAdded )
            {
                coordString = coordString.concat( "; " );
            }
            else
            {
                coordString = coordString.concat( " - " );
                messageAdded = true;
            }
            coordString = coordString.concat(
                    "scope managed from " + node.getArtifact().getProperties().get( PRE_MANAGED_SCOPE ) );
        }

        coordString = getDependencyCoordinate( node ) + coordString;

        if ( node.getArtifact().getProperties().containsKey( "Cycle" ) )
        {
            if ( messageAdded )
            {
                coordString = coordString.concat( "; " );
            }
            else
            {
                coordString = coordString.concat( " - " );
            }
            coordString = coordString.concat( "omitted for cycle" );
            builder.append( "(" ).append( coordString ).append( ")" ).append( System.lineSeparator() );
        }
        else if ( nodeErrors.get( node ) != null )
        {
            builder.append( "(" );
            if ( messageAdded )
            {
                builder.append( coordString ).append( "; " ).append( nodeErrors.get( node ) );
            }
            else
            {
                builder.append( coordString ).append( " - " ).append( nodeErrors.get( node ) );
            }
            builder.append( ")" );
            builder.append( System.lineSeparator() );
        }
        else
        {
            builder.append( coordString ).append( System.lineSeparator() );
            callDfsPrint( node, start, builder, nodeErrors );
        }

    }

    private void callDfsPrint( DependencyNode node, String start, StringBuilder builder,
                               Map<DependencyNode, String> nodeErrors )
    {
        for ( int i = 0; i < node.getChildren().size(); i++ )
        {
            if ( start.endsWith( LINE_START_CHILD ) )
            {
                start = start.replace( LINE_START_CHILD, "|  " );
            }
            else if ( start.endsWith( LINE_START_LAST_CHILD ) )
            {
                start = start.replace( LINE_START_LAST_CHILD, "   " );
            }

            if ( i == node.getChildren().size() - 1 )
            {
                dfsPrint( node.getChildren().get( i ), start.concat( LINE_START_LAST_CHILD ), builder, nodeErrors );
            }
            else
            {
                dfsPrint( node.getChildren().get( i ), start.concat( LINE_START_CHILD ), builder, nodeErrors );
            }
        }
    }
}
