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
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;

/**
 * A dependency node visitor that serializes visited nodes to a writer using the
 * <a href="https://en.wikipedia.org/wiki/Trivial_Graph_Format">TGF format</a>.
 *
 * @author <a href="mailto:jerome.creignou@gmail.com">Jerome Creignou</a>
 * @since 2.1
 */
public class TGFDependencyNodeVisitor extends AbstractSerializingVisitor implements DependencyNodeVisitor {

    /**
     * Utility class to write an Edge.
     *
     * @author <a href="mailto:jerome.creignou@gmail.com">Jerome Creignou</a>
     */
    static final class EdgeAppender {
        /**
         * Edge start.
         */
        private final DependencyNode from;

        /**
         * Edge end.
         */
        private final DependencyNode to;

        /**
         * Edge label. (optional)
         */
        private final String label;

        /**
         * Build a new EdgeAppender.
         *
         * @param from edge start.
         * @param to edge end
         * @param label optional label.
         */
        EdgeAppender(DependencyNode from, DependencyNode to, String label) {
            super();
            this.from = from;
            this.to = to;
            this.label = label;
        }

        /**
         * build a string representing the edge.
         */
        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(generateId(from));
            result.append(' ').append(generateId(to));
            if (label != null) {
                result.append(' ').append(label);
            }
            return result.toString();
        }
    }

    /**
     * List of edges.
     */
    private final List<EdgeAppender> edges = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param writer the writer to write to.
     */
    public TGFDependencyNodeVisitor(Writer writer) {
        super(writer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean endVisit(DependencyNode node) {
        try {
            if (node.getParent() == null || node.getParent() == node) {
                // dump edges on last node endVisit
                writer.write("#" + System.lineSeparator());
                for (EdgeAppender edge : edges) {
                    writer.write(edge.toString() + System.lineSeparator());
                }
                writer.flush();
            } else {
                DependencyNode parent = node.getParent();
                // using scope as edge label.
                edges.add(new EdgeAppender(parent, node, node.getArtifact().getScope()));
            }
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write TGF format output", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(DependencyNode node) {
        try {
            // Write node
            writer.write(generateId(node));
            writer.write(" ");
            writer.write(node.toNodeString());
            writer.write(System.lineSeparator());
            writer.flush();
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write TGF format output", e);
        }
    }

    /**
     * Generate a unique id from a DependencyNode.
     * <p>
     * Current implementation is rather simple and uses hashcode.
     * </p>
     *
     * @param node the DependencyNode to use.
     * @return the unique id.
     */
    private static String generateId(DependencyNode node) {
        return String.valueOf(node.hashCode());
    }
}
