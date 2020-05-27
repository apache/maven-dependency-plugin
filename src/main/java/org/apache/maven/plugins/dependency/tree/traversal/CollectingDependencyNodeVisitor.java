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
import org.apache.maven.plugins.dependency.tree.traversal.DependencyNodeVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A dependency node visitor that collects visited nodes for further processing.
 * 
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @since 1.1
 */
public class CollectingDependencyNodeVisitor
        implements DependencyNodeVisitor
{
    // fields -----------------------------------------------------------------

    /**
     * The collected list of nodes.
     */
    private final List<DependencyNode> nodes;

    // constructors -----------------------------------------------------------

    /**
     * Creates a dependency node visitor that collects visited nodes for further processing.
     */
    public CollectingDependencyNodeVisitor()
    {
        nodes = new ArrayList<DependencyNode>();
    }

    // DependencyNodeVisitor methods ------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit( DependencyNode node )
    {
        // collect node
        nodes.add( node );

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean endVisit( DependencyNode node )
    {
        return true;
    }

    // public methods ---------------------------------------------------------

    /**
     * Gets the list of collected dependency nodes.
     * 
     * @return the list of collected dependency nodes
     */
    public List<DependencyNode> getNodes()
    {
        return Collections.unmodifiableList( nodes );
    }
}
