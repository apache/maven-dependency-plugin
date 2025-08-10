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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;

import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;

/**
 * A dependency node visitor that serializes visited nodes to <a href="https://en.wikipedia.org/wiki/DOT_language">DOT
 * format</a>
 *
 * @author <a href="mailto:pi.songs@gmail.com">Pi Song</a>
 * @since 2.1
 */
public class DOTDependencyNodeVisitor extends AbstractSerializingVisitor implements DependencyNodeVisitor {

    /**
     * Constructor.
     *
     * @param writer the writer to write to.
     */
    public DOTDependencyNodeVisitor(Writer writer) {
        super(writer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(DependencyNode node) {
        try {
            if (node.getParent() == null || node.getParent() == node) {
                writer.write("digraph \"" + node.toNodeString() + "\" { " + System.lineSeparator());
                writer.flush();
            }

            // Generate "currentNode -> Child" lines

            List<DependencyNode> children = node.getChildren();

            for (DependencyNode child : children) {
                writer.write("\t\"" + node.toNodeString() + "\" -> \"" + child.toNodeString() + "\" ; "
                        + System.lineSeparator());
            }
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write DOT format output", e);
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean endVisit(DependencyNode node) {
        try {
            if (node.getParent() == null || node.getParent() == node) {
                writer.write(" } " + System.lineSeparator());
                writer.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write DOT format output", e);
        }
        return true;
    }
}
