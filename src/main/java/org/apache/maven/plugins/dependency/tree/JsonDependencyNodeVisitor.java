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
import java.io.Writer;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;

/**
 * A dependency node visitor that serializes visited nodes to a writer using the JSON format.
 */
public class JsonDependencyNodeVisitor extends AbstractSerializingVisitor implements DependencyNodeVisitor {

    private String indentChar = " ";

    /**
     * Creates a new instance of {@link JsonDependencyNodeVisitor}. The writer will be used to write the output.
     * @param writer  the writer to write to
     */
    public JsonDependencyNodeVisitor(Writer writer) {
        super(writer);
    }

    @Override
    public boolean visit(DependencyNode node) {
        if (node.getParent() == null || node.getParent() == node) {
            writeRootNode(node, writer);
        }
        return true;
    }

    /**
     * Writes the node to the writer. This method is recursive and will write all children nodes.
     * @param node  the node to write
     * @param writer  the writer to write to
     */
    private void writeRootNode(DependencyNode node, Writer writer) {
        int indent = 2;
        StringBuilder sb = new StringBuilder();
        sb.append("{").append("\n");
        writeNode(indent, node, sb);
        sb.append("}").append("\n");
        try {
            writer.write(sb.toString());
        } catch (IOException e) {
            throw new RuntimeException("Error while writing json output", e);
            // TODO: handle exception maybe throw runtime exception or mojo exception?
        }
    }
    /**
     * Appends the node and its children to the string builder.
     * @param indent  the current indent level
     * @param node  the node to write
     * @param sb  the string builder to append to
     */
    private void writeNode(int indent, DependencyNode node, StringBuilder sb) {
        appendNodeValues(sb, indent, node.getArtifact(), !node.getChildren().isEmpty());
        if (!node.getChildren().isEmpty()) {
            writeChildren(indent, node, sb);
        }
    }
    /**
     * Writes the children of the node to the string builder. And each children of each node will be written recursively.
     * @param indent  the current indent level
     * @param node  the node to write
     * @param sb  the string builder to append to
     */
    private void writeChildren(int indent, DependencyNode node, StringBuilder sb) {
        sb.append(indent(indent)).append("\"children\": [").append("\n");
        indent += 2;
        for (int i = 0; i < node.getChildren().size(); i++) {
            DependencyNode child = node.getChildren().get(i);
            sb.append(indent(indent));
            sb.append("{").append("\n");
            writeNode(indent + 2, child, sb);
            sb.append(indent(indent)).append("}");
            // we skip the comma for the last child
            if (i != node.getChildren().size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent(indent)).append("]").append("\n");
    }

    @Override
    public boolean endVisit(DependencyNode node) {
        return true;
    }
    /**
     * Appends the artifact values to the string builder.
     * @param sb  the string builder to append to
     * @param indent  the current indent level
     * @param artifact  the artifact to write
     * @param hasChildren  true if the artifact has children
     */
    private void appendNodeValues(StringBuilder sb, int indent, Artifact artifact, boolean hasChildren) {
        appendKeyValue(sb, indent, "groupId", StringUtils.defaultString(artifact.getGroupId()));
        appendKeyValue(sb, indent, "artifactId", StringUtils.defaultString(artifact.getArtifactId()));
        appendKeyValue(sb, indent, "version", StringUtils.defaultString(artifact.getVersion()));
        appendKeyValue(sb, indent, "type", StringUtils.defaultString(artifact.getType()));
        appendKeyValue(sb, indent, "scope", StringUtils.defaultString(artifact.getScope()));
        appendKeyValue(sb, indent, "classifier", StringUtils.defaultString(artifact.getClassifier()));
        if (hasChildren) {
            appendKeyValue(sb, indent, "optional", StringUtils.defaultString(String.valueOf(artifact.isOptional())));
        } else {
            appendKeyWithoutComma(
                    sb, indent, "optional", StringUtils.defaultString(String.valueOf(artifact.isOptional())));
        }
    }
    /**
     * Appends a key value pair to the string builder.
     * @param sb  the string builder to append to
     * @param indent  the current indent level
     * @param key  the key used as json key
     * @param value  the value used as json value
     */
    private void appendKeyValue(StringBuilder sb, int indent, String key, String value) {
        sb.append(indent(indent))
                .append("\"")
                .append(key)
                .append("\"")
                .append(":")
                .append(indentChar)
                .append("\"")
                .append(value)
                .append("\"")
                .append(",")
                .append("\n");
    }
    /**
     * Appends a key value pair to the string builder without a comma at the end. This is used for the last children of a node.
     * @param sb  the string builder to append to
     * @param indent  the current indent level
     * @param key  the key used as json key
     * @param value  the value used as json value
     */
    private void appendKeyWithoutComma(StringBuilder sb, int indent, String key, String value) {
        sb.append(indent(indent))
                .append("\"")
                .append(key)
                .append("\"")
                .append(":")
                .append(indentChar)
                .append("\"")
                .append(value)
                .append("\"")
                .append("\n");
    }

    /**
     * Returns a string of {@link #indentChar} for the indent level.
     * @param indent  the number of indent levels
     * @return  the string of indent characters
     */
    private String indent(int indent) {
        return StringUtils.repeat(indentChar, indent);
    }
}
