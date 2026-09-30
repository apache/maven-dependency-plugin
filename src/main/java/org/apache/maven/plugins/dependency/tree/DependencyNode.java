/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.dependency.tree;

import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.Artifact;

/**
 * A node of the dependency tree displayed by the {@link TreeMojo}.
 */
public class DependencyNode {
    private final DependencyNode parent;

    private final Artifact artifact;

    private final String nodeString;

    private List<DependencyNode> children = Collections.emptyList();

    /**
     * @param parent the parent node, or <code>null</code> for the root
     * @param artifact the artifact of this node
     * @param nodeString the label of this node
     */
    public DependencyNode(DependencyNode parent, Artifact artifact, String nodeString) {
        this.parent = parent;
        this.artifact = artifact;
        this.nodeString = nodeString;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public DependencyNode getParent() {
        return parent;
    }

    public List<DependencyNode> getChildren() {
        return children;
    }

    public void setChildren(List<DependencyNode> children) {
        this.children = children;
    }

    /**
     * @return the label of this node
     */
    public String toNodeString() {
        return nodeString;
    }

    /**
     * Applies the visitor to this node and, as long as the visitor agrees, to its descendants.
     *
     * @param visitor the visitor
     * @return the result of {@link DependencyNodeVisitor#endVisit(DependencyNode)}
     */
    public boolean accept(DependencyNodeVisitor visitor) {
        if (visitor.visit(this)) {
            for (DependencyNode child : children) {
                if (!child.accept(visitor)) {
                    break;
                }
            }
        }

        return visitor.endVisit(this);
    }
}
