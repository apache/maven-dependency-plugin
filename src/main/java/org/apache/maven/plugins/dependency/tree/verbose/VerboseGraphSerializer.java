package org.apache.maven.plugins.dependency.tree.verbose;

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


import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
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
public final class VerboseGraphSerializer
{
    private static final String LINE_START_LAST_CHILD = "\\- ";
    private static final String LINE_START_CHILD = "+- ";

    public String serialize( DependencyNode root, DependencyManagement dependencyManagement )
    {
        Set<String> coordinateStrings = new HashSet<>();
        Map<String, String> coordinateVersionMap = new HashMap<>();
        StringBuilder builder = new StringBuilder();

        Map<String, Dependency> dependencyManagementMap = createDependencyManagementMap( dependencyManagement );
        // need to process dependencyManagement first
        for ( DependencyNode node : root.getChildren() )
        {
            for ( DependencyNode transitiveDependency : node.getChildren() )
            {
                applyDependencyManagement( transitiveDependency, dependencyManagementMap );
            }
        }

        // Use BFS to mirror how Maven resolves dependencies and use DFS to print the tree easily
        Map<DependencyNode, String> nodeErrors = getNodeConflictMessagesBfs( root, coordinateStrings,
                coordinateVersionMap, dependencyManagementMap );

        // deal with root first
        Artifact rootArtifact = root.getArtifact();
        builder.append( rootArtifact.getGroupId() ).append( ":" ).append( rootArtifact.getArtifactId() ).append( ":" )
                .append( rootArtifact.getExtension() ).append( ":" ).append( rootArtifact.getVersion() ).append(
                        System.lineSeparator() );

        for ( int i = 0; i < root.getChildren().size(); i++ )
        {
            if ( i == root.getChildren().size() - 1 )
            {
                dfsPrint( root.getChildren().get( i ), LINE_START_LAST_CHILD, true, builder, nodeErrors,
                        dependencyManagementMap );
            }
            else
            {
                dfsPrint( root.getChildren().get( i ), LINE_START_CHILD, true, builder, nodeErrors,
                        dependencyManagementMap );
            }
        }
        return builder.toString();
    }

    private void applyDependencyManagement( DependencyNode node,
                                                      Map<String, Dependency> dependencyManagementMap )
    {
        if ( dependencyManagementMap.containsKey( getDependencyManagementCoordinate( node.getArtifact() ) ) )
        {
            Dependency manager = dependencyManagementMap.get( getDependencyManagementCoordinate( node.getArtifact() ) );
            Map<String, String> artifactProperties = new HashMap<>();
            // Artifact.getProperties returns an immutable map so must copy over to a mutable map
            for ( Map.Entry<String, String> entry : node.getArtifact().getProperties().entrySet() )
            {
                artifactProperties.put( entry.getKey(), entry.getValue() );
            }

            if ( !node.getArtifact().getVersion().equals( manager.getVersion() ) )
            {
                artifactProperties.put( "version", manager.getVersion() );
            }
            if ( !node.getDependency().getScope().equals( manager.getScope() ) )
            {
                artifactProperties.put( "scope", manager.getScope() );
            }

            node.getArtifact().setProperties( artifactProperties );
        }

        for ( DependencyNode child : node.getChildren() )
        {
            applyDependencyManagement( child, dependencyManagementMap );
        }
    }

    private static Map<String, Dependency> createDependencyManagementMap(
            DependencyManagement dependencyManagement )
    {
        Map<String, Dependency> dependencyManagementMap = new HashMap<>();

        if ( dependencyManagement == null )
        {
            return dependencyManagementMap;
        }

        for ( Dependency dependency : dependencyManagement.getDependencies() )
        {
            dependencyManagementMap.put( getDependencyManagementCoordinate( dependency ), dependency );
        }
        return dependencyManagementMap;
    }

    private static String getDependencyManagementCoordinate( org.eclipse.aether.artifact.Artifact artifact )
    {
        StringBuilder string = new StringBuilder();
        string.append( artifact.getGroupId() ).append( ":" ).append( artifact.getArtifactId() ).append( ":" )
                .append( artifact.getExtension() );
        if ( artifact.getClassifier() != null && !artifact.getClassifier().equals( "" ) )
        {
            string.append( ":" ).append( artifact.getClassifier() );
        }
        return string.toString();
    }

    private static String getDependencyManagementCoordinate( Dependency dependency )
    {
        StringBuilder string = new StringBuilder();
        string.append( dependency.getGroupId() ).append( ":" ).append( dependency.getArtifactId() )
                .append( ":" ).append( dependency.getType() );
        if ( dependency.getClassifier() != null && !dependency.getClassifier().equals( "" ) )
        {
            string.append( ":" ).append( dependency.getClassifier() );
        }
        return string.toString();
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

        String version, scope;
        // Use the properties field for dependency management version/scope
        if ( artifact.getProperties().containsKey( "version" ) )
        {
            version = artifact.getProperties().get( "version" );
        }
        else
        {
            version = artifact.getVersion();
        }

        if ( artifact.getProperties().containsKey( "scope" ) )
        {
            scope = artifact.getProperties().get( "scope" );
        }
        else
        {
            scope = node.getDependency().getScope();
        }

        String coords = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension() + ":"
                + version;

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
            String version;
            if ( artifact.getProperties().containsKey( "version" ) )
            {
                version = artifact.getProperties().get( "version" );
            }
            else
            {
                version = artifact.getVersion();
            }

            String coordinate = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension()
                    + ":" + version + ":" + scope;
            if ( coordinateStrings.contains( coordinate ) )
            {
                return scope;
            }
        }
        return null;
    }

    private StringBuilder callDfsPrint( DependencyNode node, String start, StringBuilder builder,
                                        Map<DependencyNode, String> nodeErrors,
                                        Map<String, Dependency> dependencyManagementMap )
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
                dfsPrint( node.getChildren().get( i ), start.concat( LINE_START_LAST_CHILD ), false, builder,
                        nodeErrors, dependencyManagementMap );
            }
            else
            {
                dfsPrint( node.getChildren().get( i ), start.concat( LINE_START_CHILD ), false, builder,
                        nodeErrors, dependencyManagementMap );
            }
        }
        return builder;
    }

    private Map<DependencyNode, String> getNodeConflictMessagesBfs( DependencyNode root, Set<String> coordinateStrings
            , Map<String, String> coordinateVersionMap, Map<String, Dependency> dependencyManagementMap )
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
                nodeErrors.put( node, "omitted for conflict with "
                        + scopeConflict( node, coordinateStrings ) );
            }
            else if ( versionConflict( node, coordinateVersionMap ) != null )
            {
                nodeErrors.put( node, "omitted for conflict with "
                        + versionConflict( node, coordinateVersionMap ) );
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
                        nodeErrors.put( node, "omitted for introducing a cycle with "
                                + getDependencyCoordinate( child ) );
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

    private StringBuilder dfsPrint( DependencyNode node, String start, boolean firstLevel, StringBuilder builder,
                                    Map<DependencyNode, String> nodeErrors,
                                    Map<String, Dependency> dependencyManagementMap )
    {
        builder.append( start );
        if ( node.getArtifact() == null )
        {
            // Should never reach hit this condition with a proper graph sent in
            builder.append( "Null Artifact Node" ).append( System.lineSeparator() );
            callDfsPrint( node, start, builder, nodeErrors, dependencyManagementMap );
        }

        String coordString = "";

        boolean messageAdded = false;
        String version, scope;
        if ( !firstLevel && node.getArtifact().getProperties().containsKey( "version" ) )
        {
            version = node.getArtifact().getProperties().get( "version" );
            coordString = coordString.concat( " - version managed from " + node.getArtifact().getVersion() );
            messageAdded = true;
        }
        else
        {
            version = node.getArtifact().getVersion();
        }

        if ( !firstLevel && node.getArtifact().getProperties().containsKey( "scope" ) )
        {
            scope = node.getArtifact().getProperties().get( "scope" );
            if ( messageAdded )
            {
                coordString = coordString.concat( "; scope managed from " + node.getDependency().getScope() );
            }
            else
            {
                coordString = coordString.concat( " - scope managed from " + node.getDependency().getScope() );
                messageAdded = true;
            }
        }
        else
        {
            scope = node.getDependency().getScope();
        }

        /*if ( !firstLevel && dependencyManagementMap.containsKey( getDependencyManagementCoordinate( node.getArtifact() )
        ) )
        {
            Dependency manager = dependencyManagementMap.get( getDependencyManagementCoordinate( node.getArtifact() ) );

            if ( !manager.getVersion().equals( node.getArtifact().getVersion() ) )
            {
                version = manager.getVersion();
                coordString = coordString.concat( " - version managed from " + node.getArtifact().getVersion() );
                messageAdded = true;
            }
            else
            {
                version = node.getArtifact().getVersion();
            }

            if ( !manager.getScope().equals( node.getDependency().getScope() ) )
            {
                scope = manager.getScope();
                if ( messageAdded )
                {
                    coordString = coordString.concat( "; scope managed from " + node.getDependency().getScope() );
                }
                else
                {
                    coordString = coordString.concat( " - scope managed from " + node.getDependency().getScope() );
                    messageAdded = true;
                }
            }
            else
            {
                scope = node.getDependency().getScope();
            }

            coordString = node.getArtifact().getGroupId() + ":" + node.getArtifact().getArtifactId() + ":"
                    + node.getArtifact().getExtension() + ":" + version + ":" + scope + coordString;
        }
        else
        {
            coordString = getDependencyCoordinate( node );
        }*/

        coordString = node.getArtifact().getGroupId() + ":" + node.getArtifact().getArtifactId() + ":"
                + node.getArtifact().getExtension() + ":" + version + ":" + scope + coordString;

        if ( node.getDependency().getScope().equals( "test" ) && !firstLevel )
        {
            // don't want transitive test dependencies included
            return builder;
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
            callDfsPrint( node, start, builder, nodeErrors, dependencyManagementMap );
        }
        return builder;
    }
}
