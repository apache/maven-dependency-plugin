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
import java.util.Map;
import java.util.Set;

/**
 * Parses dependency graph and outputs in text format for end user to review.
 */
class VerboseGraphTextSerializer extends AbstractVerboseGraphSerializer
{
    @Override
    public String serialize( DependencyNode root )
    {
        Set<String> coordinateStrings = new HashSet<>();
        Map<String, String> coordinateVersionMap = new HashMap<>();
        Map<DependencyNode, String> nodeErrors = getNodeConflictMessagesBfs( root, coordinateStrings,
                coordinateVersionMap );

        StringBuilder builder = new StringBuilder();

        // deal with root first
        Artifact rootArtifact = root.getArtifact();
        builder.append( rootArtifact.getGroupId() ).append( ":" ).append( rootArtifact.getArtifactId() ).append(
                ":" ).append( rootArtifact.getExtension() ).append( ":" ).append( rootArtifact.getVersion() );

        if ( root.getData().containsKey( "ContainsModule" ) )
        {
            builder.append( " WARNING: this tree contains a submodule. Once it reaches the submodule will print "
                    + "in nonVerbose fashion, to see the actual submodule verbose output refer to "
                    + "the rest of the output" );
        }

        builder.append( "\n" );

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

    private void dfsPrint( DependencyNode node, String start, StringBuilder builder,
                           Map<DependencyNode, String> nodeErrors )
    {
        builder.append( start );
        if ( node.getArtifact() == null )
        {
            // Should never hit this condition with a proper graph sent in
            builder.append( "Null Artifact Node" ).append( "\n" );
            callDfsPrint( node, start, builder, nodeErrors );
        }

        String coordString = "";
        boolean messageAdded = false;

        if ( node.getArtifact().getProperties().containsKey( PRE_MANAGED_VERSION ) )
        {
            coordString += " - version managed from "
                    + node.getArtifact().getProperties().get( PRE_MANAGED_VERSION );
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

        coordString = getDependencyCoordinate( node ) + coordString;

        if ( node.getArtifact().getProperties().containsKey( "Cycle" ) )
        {
            if ( messageAdded )
            {
                coordString += "; ";
            }
            else
            {
                coordString += " - ";
            }
            coordString += "omitted for cycle";
            builder.append( "(" ).append( coordString ).append( ")" ).append( "\n" );
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
            builder.append( "\n" );
        }
        else
        {
            builder.append( coordString ).append( "\n" );
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
