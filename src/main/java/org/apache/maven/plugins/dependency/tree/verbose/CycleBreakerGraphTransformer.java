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


import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Transforms a dependency graph so that it does not contain cycles.
 *
 * <p>A cycle in a dependency graph is a situation where a path to a node from the root contains the
 * same node. For example, jaxen 1.1-beta-6 is known to have cycle with dom4j 1.6.1.
 */
public final class CycleBreakerGraphTransformer implements DependencyGraphTransformer
{

    private final Set<DependencyNode> visitedNodes = Collections.newSetFromMap(
            new IdentityHashMap<DependencyNode, Boolean>() );

    @Override
    public DependencyNode transformGraph( DependencyNode dependencyNode, DependencyGraphTransformationContext context )
            throws RepositoryException
    {

        removeCycle( null, dependencyNode, new HashSet<Artifact>() );
        return dependencyNode;
    }

    private void removeCycle( DependencyNode parent, DependencyNode node, Set<Artifact> ancestors )
    {
        Artifact artifact = node.getArtifact();

        if ( ancestors.contains( artifact ) )
        {   // Set (rather than List) gives O(1) lookup here
            // parent is not null when ancestors is not empty
            removeChildFromParent( node, parent );
            return;
        }

        if ( shouldVisitChildren( node ) )
        {
            ancestors.add( artifact );
            for ( DependencyNode child : node.getChildren() )
            {
                removeCycle( node, child, ancestors );
            }
            ancestors.remove( artifact );
        }
    }

    /**
     * Returns true if {@code node} is not visited yet and marks the node as visited.
     */
    boolean shouldVisitChildren( DependencyNode node )
    {
        return visitedNodes.add( node );
    }

    private static void removeChildFromParent( DependencyNode child, DependencyNode parent )
    {
        List<DependencyNode> filteredChildren = new ArrayList<DependencyNode>();

        for ( DependencyNode node : parent.getChildren() )
        {
            if ( node == child )
            {
                node.setChildren( new ArrayList<DependencyNode>() );
                Map<String, String> newProperties = new HashMap<>( node.getArtifact().getProperties() );
                newProperties.put( "Cycle", "True" );
                node.setArtifact( node.getArtifact().setProperties( newProperties ) );
            }
            filteredChildren.add( node );
        }

        parent.setChildren( filteredChildren );
    }
}
