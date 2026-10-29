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
import java.util.HashMap;
import java.util.Map;

/**
 * A dependency node visitor that serializes visited nodes to a
 * <a href="https://mermaid.js.org/syntax/flowchart.html">Mermaid flowchart</a>. Nodes with the same label are the same
 * node of the graph, and the edge to a dependency is labeled with its scope.
 *
 * @since 3.12.0
 */
public class MermaidDependencyNodeVisitor extends AbstractSerializingVisitor implements DependencyNodeVisitor {

    private final Map<String, String> ids = new HashMap<>();

    /**
     * Constructor.
     *
     * @param writer the writer to write to
     */
    public MermaidDependencyNodeVisitor(Writer writer) {
        super(writer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(DependencyNode node) {
        try {
            if (node.getParent() == null || node.getParent() == node) {
                writer.write("graph LR" + System.lineSeparator());
            }

            String id = id(node);
            for (DependencyNode child : node.getChildren()) {
                String childId = id(child);
                String scope = child.getArtifact().getScope();
                writer.write("\t" + id
                        + (scope == null || scope.isEmpty() ? " --> " : " -->|" + scope + "| ")
                        + childId + System.lineSeparator());
            }
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Mermaid format output", e);
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean endVisit(DependencyNode node) {
        return true;
    }

    /**
     * Gives the node an identifier and declares it with its label the first time it is seen. Mermaid identifiers
     * cannot hold the characters of Maven coordinates, so the label is written quoted instead.
     */
    private String id(DependencyNode node) throws IOException {
        String label = node.toNodeString();
        String id = ids.get(label);
        if (id == null) {
            id = "n" + ids.size();
            ids.put(label, id);
            writer.write("\t" + id + "[\"" + label.replace("\"", "#quot;") + "\"]" + System.lineSeparator());
        }
        return id;
    }
}
