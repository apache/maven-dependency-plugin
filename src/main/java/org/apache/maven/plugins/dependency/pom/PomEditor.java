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
package org.apache.maven.plugins.dependency.pom;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Formatting-preserving POM editor using DOM-level XML parsing.
 * Preserves comments, whitespace, and indentation style when modifying {@code pom.xml} files.
 */
public class PomEditor {

    private final Document document;
    private final File pomFile;
    private final String indent;
    private final String lineEnding;

    private PomEditor(Document document, File pomFile, String indent, String lineEnding) {
        this.document = document;
        this.pomFile = pomFile;
        this.indent = indent;
        this.lineEnding = lineEnding;
    }

    /**
     * Loads a {@code pom.xml} file, preserving its structure.
     *
     * @param pomFile the POM file to load
     * @return a new PomEditor instance
     * @throws IOException if the file cannot be read or parsed
     */
    public static PomEditor load(File pomFile) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile);

            String content = new String(Files.readAllBytes(pomFile.toPath()), detectEncoding(doc));
            String lineEnding = detectLineEnding(content);
            String indent = detectIndent(content);

            return new PomEditor(doc, pomFile, indent, lineEnding);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse POM file: " + pomFile, e);
        }
    }

    /**
     * Finds an existing dependency matching the given groupId and artifactId
     * in the specified section.
     *
     * @param groupId    the groupId to match
     * @param artifactId the artifactId to match
     * @param managed    if {@code true}, search in {@code <dependencyManagement>}; otherwise in {@code <dependencies>}
     * @return the matching {@code <dependency>} element, or {@code null} if not found
     */
    public Element findDependency(String groupId, String artifactId, boolean managed) {
        Element depsElement = getDependenciesElement(managed, false);
        if (depsElement == null) {
            return null;
        }
        NodeList children = depsElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "dependency".equals(node.getNodeName())) {
                Element dep = (Element) node;
                String g = getChildText(dep, "groupId");
                String a = getChildText(dep, "artifactId");
                if (groupId.equals(g) && artifactId.equals(a)) {
                    return dep;
                }
            }
        }
        return null;
    }

    /**
     * Adds a new dependency to the POM.
     *
     * @param coords the dependency coordinates
     * @param managed if {@code true}, add to {@code <dependencyManagement>}
     */
    public void addDependency(DependencyCoordinates coords, boolean managed) {
        Element depsElement = getDependenciesElement(managed, true);
        Element dep = buildDependencyElement(coords, 2);
        int depthForDep = managed ? 3 : 2;
        String baseIndent = repeatIndent(depthForDep);

        depsElement.appendChild(document.createTextNode(lineEnding + baseIndent));
        depsElement.appendChild(dep);

        // Ensure the closing tag is properly indented
        ensureClosingIndent(depsElement, managed ? 2 : 1);
    }

    /**
     * Updates an existing dependency element with the provided coordinate fields.
     * Only updates fields that are non-null in the provided coordinates.
     *
     * @param existing the existing dependency element
     * @param coords   the new coordinate values
     */
    public void updateDependency(Element existing, DependencyCoordinates coords) {
        if (coords.getVersion() != null) {
            setOrCreateChild(existing, "version", coords.getVersion());
        }
        if (coords.getScope() != null) {
            setOrCreateChild(existing, "scope", coords.getScope());
        }
        if (coords.getType() != null) {
            setOrCreateChild(existing, "type", coords.getType());
        }
        if (coords.getClassifier() != null) {
            setOrCreateChild(existing, "classifier", coords.getClassifier());
        }
    }

    /**
     * Removes a dependency element from the POM.
     *
     * @param groupId    the groupId to match
     * @param artifactId the artifactId to match
     * @param managed    if {@code true}, remove from {@code <dependencyManagement>}
     * @return {@code true} if the dependency was found and removed
     */
    public boolean removeDependency(String groupId, String artifactId, boolean managed) {
        Element depsElement = getDependenciesElement(managed, false);
        if (depsElement == null) {
            return false;
        }
        Element dep = findDependency(groupId, artifactId, managed);
        if (dep == null) {
            return false;
        }

        // Remove preceding whitespace text node
        Node prev = dep.getPreviousSibling();
        if (prev != null
                && prev.getNodeType() == Node.TEXT_NODE
                && prev.getTextContent().trim().isEmpty()) {
            depsElement.removeChild(prev);
        }

        depsElement.removeChild(dep);
        return true;
    }

    /**
     * Writes the modified POM back to disk.
     *
     * @throws IOException if the file cannot be written
     */
    public void save() throws IOException {
        try {
            // Serialize to string, then do post-processing
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(
                    OutputKeys.ENCODING, detectEncoding(document).name());

            // Check if original has standalone
            String standalone = document.getXmlStandalone() ? "yes" : "no";
            transformer.setOutputProperty(OutputKeys.STANDALONE, standalone);

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));

            String output = writer.toString();

            // Normalize line endings to match original
            output = output.replace("\r\n", "\n").replace("\r", "\n");
            if (!"\n".equals(lineEnding)) {
                output = output.replace("\n", lineEnding);
            }

            // Ensure file ends with newline
            if (!output.endsWith(lineEnding)) {
                output += lineEnding;
            }

            Files.write(pomFile.toPath(), output.getBytes(detectEncoding(document)));
        } catch (TransformerException e) {
            throw new IOException("Failed to write POM file: " + pomFile, e);
        }
    }

    // --- Private helpers ---

    private Element getDependenciesElement(boolean managed, boolean create) {
        Element root = document.getDocumentElement();

        if (managed) {
            Element depMgmt = getOrCreateChildElement(root, "dependencyManagement", create, 1);
            if (depMgmt == null) {
                return null;
            }
            return getOrCreateChildElement(depMgmt, "dependencies", create, 2);
        } else {
            return getOrCreateChildElement(root, "dependencies", create, 1);
        }
    }

    private Element getOrCreateChildElement(Element parent, String tagName, boolean create, int depth) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        if (!create) {
            return null;
        }
        String baseIndent = repeatIndent(depth);
        Element newElement = document.createElement(tagName);

        // Add newline + indent before the new element
        parent.appendChild(document.createTextNode(lineEnding + baseIndent));
        parent.appendChild(newElement);
        // Add newline after the element (for closing tag of parent)
        ensureClosingIndent(parent, depth - 1);

        return newElement;
    }

    private Element buildDependencyElement(DependencyCoordinates coords, int depth) {
        Element dep = document.createElement("dependency");
        String childIndent = repeatIndent(depth + 1);

        appendChildElement(dep, "groupId", coords.getGroupId(), childIndent);
        appendChildElement(dep, "artifactId", coords.getArtifactId(), childIndent);

        if (coords.getVersion() != null) {
            appendChildElement(dep, "version", coords.getVersion(), childIndent);
        }
        if (coords.getScope() != null) {
            appendChildElement(dep, "scope", coords.getScope(), childIndent);
        }
        if (coords.getType() != null) {
            appendChildElement(dep, "type", coords.getType(), childIndent);
        }
        if (coords.getClassifier() != null) {
            appendChildElement(dep, "classifier", coords.getClassifier(), childIndent);
        }

        // Closing indent for </dependency>
        dep.appendChild(document.createTextNode(lineEnding + repeatIndent(depth)));

        return dep;
    }

    private void appendChildElement(Element parent, String tagName, String value, String indentStr) {
        parent.appendChild(document.createTextNode(lineEnding + indentStr));
        Element child = document.createElement(tagName);
        child.setTextContent(value);
        parent.appendChild(child);
    }

    private void setOrCreateChild(Element parent, String tagName, String value) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                node.setTextContent(value);
                return;
            }
        }
        // Create new child — detect indentation depth from parent
        int depth = getElementDepth(parent);
        String childIndent = repeatIndent(depth + 1);
        appendChildElement(parent, tagName, value, childIndent);
    }

    private int getElementDepth(Node node) {
        int depth = 0;
        Node current = node;
        while (current.getParentNode() != null && current.getParentNode() != document) {
            depth++;
            current = current.getParentNode();
        }
        return depth;
    }

    private void ensureClosingIndent(Element element, int depth) {
        Node last = element.getLastChild();
        String closingIndent = lineEnding + repeatIndent(depth);
        if (last != null && last.getNodeType() == Node.TEXT_NODE) {
            last.setTextContent(closingIndent);
        } else {
            element.appendChild(document.createTextNode(closingIndent));
        }
    }

    private String repeatIndent(int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(indent);
        }
        return sb.toString();
    }

    static String getChildText(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                return node.getTextContent().trim();
            }
        }
        return null;
    }

    private static Charset detectEncoding(Document doc) {
        String encoding = doc.getXmlEncoding();
        if (encoding != null) {
            try {
                return Charset.forName(encoding);
            } catch (Exception e) {
                // fall through
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String detectLineEnding(String content) {
        if (content.contains("\r\n")) {
            return "\r\n";
        } else if (content.contains("\r")) {
            return "\r";
        }
        return "\n";
    }

    private static String detectIndent(String content) {
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            if (line.length() > 0 && (line.charAt(0) == ' ' || line.charAt(0) == '\t')) {
                // Find the leading whitespace
                int end = 0;
                char indentChar = line.charAt(0);
                while (end < line.length() && line.charAt(end) == indentChar) {
                    end++;
                }
                // Return the smallest meaningful indent unit
                String ws = line.substring(0, end);
                // If it looks like a standard indent (2 or 4 spaces, or 1 tab), use that
                if (indentChar == '\t') {
                    return "\t";
                }
                if (ws.length() <= 4) {
                    return ws;
                }
                // Try to find a common divisor (2 or 4 spaces)
                if (ws.length() % 4 == 0) {
                    return "    ";
                }
                if (ws.length() % 2 == 0) {
                    return "  ";
                }
                return ws;
            }
        }
        return "    ";
    }
}
