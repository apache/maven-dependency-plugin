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

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Exclusion;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;

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
        WrapperNode newNode = new WrapperNode(
                parentNodes.isEmpty() ? null : parentNodes.peek(),
                node.getArtifact(),
                node.getPremanagedVersion(),
                node.getPremanagedScope(),
                node.getVersionConstraint(),
                node.getOptional(),
                node.getExclusions(),
                node.toNodeString()
        );
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

    private static class WrapperNode implements DependencyNode
    {

        private final Artifact artifact;

        private final DependencyNode parent;

        private final String premanagedVersion;

        private final String premanagedScope;

        private final String versionConstraint;

        private List<DependencyNode> children;

        private final Boolean optional;

        private final List<Exclusion> exclusions;

        private final String nodeString;

        private WrapperNode( DependencyNode parent,
                             Artifact artifact,
                             String premanagedVersion,
                             String premanagedScope,
                             String versionConstraint,
                             Boolean optional,
                             List<Exclusion> exclusions,
                             String nodeString )
        {
            this.artifact = artifact;
            this.parent = parent;
            this.premanagedVersion = premanagedVersion;
            this.premanagedScope = premanagedScope;
            this.versionConstraint = versionConstraint;
            this.optional = optional;
            this.exclusions = exclusions;
            this.nodeString = nodeString;
        }

        @Override
        public Artifact getArtifact()
        {
            return artifact;
        }

        @Override
        public List<DependencyNode> getChildren()
        {
            return children;
        }

        @Override
        public boolean accept( DependencyNodeVisitor visitor )
        {
            if ( visitor.visit( this ) )
            {
                for ( DependencyNode child : getChildren() )
                {
                    if ( !child.accept( visitor ) )
                    {
                        break;
                    }
                }
            }

            return visitor.endVisit( this );
        }

        @Override
        public DependencyNode getParent()
        {
            return parent;
        }

        @Override
        public String getPremanagedVersion()
        {
            return premanagedVersion;
        }

        @Override
        public String getPremanagedScope()
        {
            return premanagedScope;
        }

        @Override
        public String getVersionConstraint()
        {
            return versionConstraint;
        }

        @Override
        public String toNodeString()
        {
            return nodeString;
        }

        @Override
        public Boolean getOptional()
        {
            return optional;
        }

        @Override
        public List<Exclusion> getExclusions()
        {
            return exclusions;
        }

        public void setChildren( List<DependencyNode> children )
        {
            this.children = children;
        }
    }
}
