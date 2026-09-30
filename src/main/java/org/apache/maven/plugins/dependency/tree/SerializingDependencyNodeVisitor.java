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

import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;

/**
 * Writes the dependency tree as indented text.
 */
class SerializingDependencyNodeVisitor implements DependencyNodeVisitor {

    static class GraphTokens {
        private final String nodeIndent;

        private final String lastNodeIndent;

        private final String fillIndent;

        private final String lastFillIndent;

        GraphTokens(String nodeIndent, String lastNodeIndent, String fillIndent, String lastFillIndent) {
            this.nodeIndent = nodeIndent;
            this.lastNodeIndent = lastNodeIndent;
            this.fillIndent = fillIndent;
            this.lastFillIndent = lastFillIndent;
        }

        String getNodeIndent(boolean last) {
            return last ? lastNodeIndent : nodeIndent;
        }

        String getFillIndent(boolean last) {
            return last ? lastFillIndent : fillIndent;
        }
    }

    static final GraphTokens WHITESPACE_TOKENS = new GraphTokens("   ", "   ", "   ", "   ");

    static final GraphTokens STANDARD_TOKENS = new GraphTokens("+- ", "\\- ", "|  ", "   ");

    static final GraphTokens EXTENDED_TOKENS = new GraphTokens("├─ ", "└─ ", "│  ", "   ");

    private final PrintWriter writer;

    private final GraphTokens tokens;

    private int depth;

    SerializingDependencyNodeVisitor(Writer writer, GraphTokens tokens) {
        this.writer = writer instanceof PrintWriter ? (PrintWriter) writer : new PrintWriter(writer, true);
        this.tokens = tokens;
    }

    @Override
    public boolean visit(DependencyNode node) {
        indent(node);

        writer.println(node.toNodeString());

        depth++;

        return true;
    }

    @Override
    public boolean endVisit(DependencyNode node) {
        depth--;

        return true;
    }

    private void indent(DependencyNode node) {
        for (int i = 1; i < depth; i++) {
            writer.write(tokens.getFillIndent(isLast(node, i)));
        }

        if (depth > 0) {
            writer.write(tokens.getNodeIndent(isLast(node)));
        }
    }

    private boolean isLast(DependencyNode node) {
        DependencyNode parent = node.getParent();

        if (parent == null) {
            return true;
        }

        List<DependencyNode> siblings = parent.getChildren();

        return siblings.indexOf(node) == siblings.size() - 1;
    }

    private boolean isLast(DependencyNode node, int ancestorDepth) {
        int distance = depth - ancestorDepth;

        while (distance-- > 0) {
            node = node.getParent();
        }

        return isLast(node);
    }
}
