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

/**
 * Visits the nodes of a dependency tree.
 */
public interface DependencyNodeVisitor {
    /**
     * @param node the node being visited
     * @return <code>true</code> to visit the children of the node
     */
    boolean visit(DependencyNode node);

    /**
     * @param node the node whose visit is finished
     * @return <code>true</code> to visit the following siblings of the node
     */
    boolean endVisit(DependencyNode node);
}
