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
 * Parses dependency graph and outputs in TGF format for end user to review.
 */
class VerboseGraphTgfSerializer extends AbstractVerboseGraphSerializer
{

    @Override
    public String serialize( DependencyNode root )
    {
        Set<String> coordinateStrings = new HashSet<>();
        Map<String, String> coordinateVersionMap = new HashMap<>();
        Map<DependencyNode, String> nodeErrors = getNodeConflictMessagesBfs( root, coordinateStrings,
                coordinateVersionMap );

        StringBuilder nodes = new StringBuilder();
        StringBuilder edges = new StringBuilder( "#" );
        edges.append( "\n" );

        // deal with root first
        Artifact rootArtifact = root.getArtifact();
        nodes.append( root.hashCode() ).append( " " ).append( rootArtifact.getGroupId() ).append( ":" ).append(
                rootArtifact.getArtifactId() ).append( ":" ).append( rootArtifact.getExtension() ).append( ":" ).append(
                rootArtifact.getVersion() );

        if ( root.getData().containsKey( "ContainsModule" ) )
        {
            nodes.append( " WARNING: this tree contains a submodule. Once it reaches the submodule will print "
                    + "in nonVerbose fashion, to see the actual submodule "
                    + "verbose output refer to the rest of the output" );
        }

        nodes.append( "\n" );

        for ( DependencyNode child : root.getChildren() )
        {
            edges.append( root.hashCode() ).append( " " ).append( child.hashCode() ).append( " " ).append(
                    child.getDependency().getScope() ).append( "\n" );
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
            coordString += " - version managed from " + node.getArtifact().getProperties().get( PRE_MANAGED_VERSION );
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
            nodes.append( "(" ).append( coordString ).append( ")" ).append( "\n" );
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
            nodes.append( ")" ).append( "\n" );
        }
        else
        {
            nodes.append( coordString ).append( "\n" );
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
                edges.append( "\n" );
                serializeTgfDfs( child, nodeErrors, nodes, edges );
            }
        }
    }
}
