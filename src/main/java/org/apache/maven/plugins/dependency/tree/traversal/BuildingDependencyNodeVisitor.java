package org.apache.maven.plugins.dependency.tree.traversal;

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

import org.apache.maven.plugins.dependency.tree.DependencyNode;
import org.apache.maven.plugins.dependency.tree.internal.DefaultDependencyNode;
import org.apache.maven.plugins.dependency.tree.traversal.DependencyNodeVisitor;

import java.util.ArrayList;
import java.util.Stack;

/**
 * A dependency node visitor that clones visited nodes into a new dependency tree. This can be used in conjunction with
 * a dependency node filter to construct subtrees.
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
public class BuildingDependencyNodeVisitor
        implements DependencyNodeVisitor
{
    // fields -----------------------------------------------------------------

    /**
     * The dependency node visitor to apply on the resultant dependency tree, or <code>null</code> for none.
     */
    private final DependencyNodeVisitor visitor;

    /**
     * The resultant tree parent nodes for the currently visited node.
     */
    private final Stack<DependencyNode> parentNodes;

    /**
     * The root node of the resultant tree.
     */
    private DependencyNode rootNode;

    // constructors -----------------------------------------------------------

    /**
     * Creates a dependency node visitor that clones visited nodes into a new dependency tree.
     */
    public BuildingDependencyNodeVisitor()
    {
        this( null );
    }

    /**
     * Creates a dependency node visitor that clones visited nodes into a new dependency tree, and then applies the
     * specified dependency node visitor on the resultant dependency tree.
     * 
     * @param visitor the dependency node visitor to apply on the resultant dependency tree, or <code>null</code> for
     *            none
     */
    public BuildingDependencyNodeVisitor( DependencyNodeVisitor visitor )
    {
        this.visitor = visitor;

        parentNodes = new Stack<DependencyNode>();
    }

    // DependencyNodeVisitor methods ------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit( DependencyNode node )
    {
        // clone the node
        DefaultDependencyNode newNode =
            new DefaultDependencyNode( parentNodes.isEmpty() ? null : parentNodes.peek(), node.getArtifact(),
                                       node.getPremanagedVersion(), node.getPremanagedScope(),
                                       node.getVersionConstraint(),
                                       node.getOptional(), node.getExclusions() );
        newNode.setChildren( new ArrayList<DependencyNode>() );

        if ( parentNodes.empty() )
        {
            rootNode = newNode;
        }
        else
        {
            DependencyNode parentNode = parentNodes.peek();
            parentNode.getChildren().add( newNode );
        }

        parentNodes.push( newNode );

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean endVisit( DependencyNode node )
    {
        parentNodes.pop();

        // apply the visitor to the resultant tree on the last visit
        if ( parentNodes.empty() && visitor != null )
        {
            rootNode.accept( visitor );
        }

        return true;
    }

    // public methods ---------------------------------------------------------

    /**
     * Gets the dependency node visitor that this visitor applies on the resultant dependency tree.
     * 
     * @return the dependency node visitor, or <code>null</code> for none
     */
    public DependencyNodeVisitor getDependencyNodeVisitor()
    {
        return visitor;
    }

    /**
     * Gets the root node of the resultant dependency tree constructed by this visitor.
     * 
     * @return the root node, or <code>null</code> if the source tree has not yet been visited
     */
    public DependencyNode getDependencyTree()
    {
        return rootNode;
    }
}
