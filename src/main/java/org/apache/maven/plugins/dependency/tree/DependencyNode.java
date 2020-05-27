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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugins.dependency.tree.traversal.DependencyNodeVisitor;

import java.util.List;

/**
 * Represents an artifact node within a Maven project's dependency graph. Notice there is no support for omitted nodes
 * at the moment, only dependencies kept in the resolved dependency list are available.
 *
 * @author Hervé Boutemy
 * @since 2.0
 */
public interface DependencyNode
{
    /**
     * @return Artifact for this DependencyNode.
     */
    Artifact getArtifact();

    /**
     * @return children of this DependencyNode.
     */
    List<DependencyNode> getChildren();

    /**
     * Applies the specified dependency node visitor to this dependency node and its children.
     * 
     * @param visitor the dependency node visitor to use
     * @return the visitor result of ending the visit to this node
     * @since 1.1
     */
    boolean accept( DependencyNodeVisitor visitor );

    /**
     * Gets the parent dependency node of this dependency node.
     * 
     * @return the parent dependency node
     */
    DependencyNode getParent();

    /**
     * Gets the version or version range for the dependency before dependency management was applied (if any).
     * 
     * @return The dependency version before dependency management or {@code null} if the version was not managed.
     */
    String getPremanagedVersion();

    /**
     * Gets the scope for the dependency before dependency management was applied (if any).
     * 
     * @return The dependency scope before dependency management or {@code null} if the scope was not managed.
     */
    String getPremanagedScope();

    /**
     * A constraint on versions for a dependency. A constraint can either consist of one or more version ranges or a
     * single version.
     * 
     * @return The constraint on the dependency.
     */
    String getVersionConstraint();

    /**
     * Returns a string representation of this dependency node.
     * 
     * @return the string representation
     */
    String toNodeString();

    /**
     * @return true for an optional dependency.
     */
    Boolean getOptional();
    
    /**
     * 
     * @return the exclusions of the dependency
     */
    List<Exclusion> getExclusions();
}
