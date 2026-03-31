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
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Formatting-preserving POM editor using DOM-level XML parsing.
 * Preserves comments, whitespace, indentation style, XML namespaces,
 * BOM markers, and XML declarations when modifying {@code pom.xml} files.
 */
public class PomEditor {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final Document document;
    private final File pomFile;
    private final String indent;
    private final String lineEnding;
    private final boolean hadXmlDeclaration;
    private final boolean hadBom;
    private final String namespaceURI;

    private PomEditor(
            Document document,
            File pomFile,
            String indent,
            String lineEnding,
            boolean hadXmlDeclaration,
            boolean hadBom,
            String namespaceURI) {
        this.document = document;
        this.pomFile = pomFile;
        this.indent = indent;
        this.lineEnding = lineEnding;
        this.hadXmlDeclaration = hadXmlDeclaration;
        this.hadBom = hadBom;
        this.namespaceURI = namespaceURI;
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
            byte[] rawBytes = Files.readAllBytes(pomFile.toPath());
            boolean hasBom = hasBom(rawBytes);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile);

            // Verify root element is <project>
            String rootName = localName(doc.getDocumentElement());
            if (!"project".equals(rootName)) {
                throw new IOException(
                        "Not a valid POM file: expected <project> root element but found <" + rootName + ">");
            }

            Charset encoding = detectEncoding(doc);
            String content = new String(rawBytes, encoding);
            String lineEnding = detectLineEnding(content);
            String indent = detectIndent(content);
            boolean hadXmlDecl = stripBomChar(content).trim().startsWith("<?xml");

            // Capture the namespace URI from the root element
            String nsURI = doc.getDocumentElement().getNamespaceURI();

            return new PomEditor(doc, pomFile, indent, lineEnding, hadXmlDecl, hasBom, nsURI);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse POM file: " + pomFile, e);
        }
    }

    /**
     * Finds an existing dependency matching the given groupId and artifactId
     * in the specified section. Matches on groupId and artifactId only (ignoring type/classifier).
     *
     * @param groupId    the groupId to match
     * @param artifactId the artifactId to match
     * @param managed    if {@code true}, search in {@code <dependencyManagement>}; otherwise in {@code <dependencies>}
     * @return the matching {@code <dependency>} element, or {@code null} if not found
     */
    public Element findDependency(String groupId, String artifactId, boolean managed) {
        return findDependency(groupId, artifactId, null, null, managed);
    }

    /**
     * Finds a dependency element matching groupId, artifactId, type, and classifier
     * in the specified section.
     *
     * <p>Type matching: {@code null} and {@code "jar"} are treated as equivalent (Maven's default).
     * Classifier matching: {@code null} matches only dependencies without a classifier.
     *
     * @param groupId    the groupId to match
     * @param artifactId the artifactId to match
     * @param type       the type to match ({@code null} matches "jar" or absent)
     * @param classifier the classifier to match ({@code null} matches absent)
     * @param managed    if {@code true}, search in {@code <dependencyManagement>}; otherwise in {@code <dependencies>}
     * @return the matching {@code <dependency>} element, or {@code null} if not found
     */
    public Element findDependency(String groupId, String artifactId, String type, String classifier, boolean managed) {
        Element depsElement = getDependenciesElement(managed, false);
        if (depsElement == null) {
            return null;
        }
        NodeList children = depsElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "dependency".equals(localName(node))) {
                Element dep = (Element) node;
                String g = getChildText(dep, "groupId");
                String a = getChildText(dep, "artifactId");
                if (!groupId.equals(g) || !artifactId.equals(a)) {
                    continue;
                }

                String depType = getChildText(dep, "type");
                String effectiveSearchType = (type == null || type.isEmpty()) ? "jar" : type;
                String effectiveDepType = (depType == null || depType.isEmpty()) ? "jar" : depType;
                if (!effectiveSearchType.equals(effectiveDepType)) {
                    continue;
                }

                String depClassifier = getChildText(dep, "classifier");
                String effectiveSearchClassifier = (classifier == null) ? "" : classifier;
                String effectiveDepClassifier = (depClassifier == null) ? "" : depClassifier;
                if (!effectiveSearchClassifier.equals(effectiveDepClassifier)) {
                    continue;
                }

                return dep;
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
        int depthForDep = managed ? 3 : 2;
        Element dep = buildDependencyElement(coords, depthForDep);
        String baseIndent = repeatIndent(depthForDep);

        depsElement.appendChild(document.createTextNode(lineEnding + baseIndent));
        depsElement.appendChild(dep);

        // Ensure the closing tag is properly indented
        ensureClosingIndent(depsElement, managed ? 2 : 1);
    }

    /**
     * Updates an existing dependency element with the provided coordinate fields.
     * Only updates fields that are non-null in the provided coordinates.
     * A field set to an empty string signals removal of that element.
     *
     * @param existing the existing dependency element
     * @param coords   the new coordinate values
     */
    public void updateDependency(Element existing, DependencyCoordinates coords) {
        if (coords.getVersion() != null) {
            if (coords.getVersion().isEmpty()) {
                removeChild(existing, "version");
            } else {
                setOrCreateChild(existing, "version", coords.getVersion());
            }
        }
        if (coords.getScope() != null) {
            if (coords.getScope().isEmpty()) {
                removeChild(existing, "scope");
            } else {
                setOrCreateChild(existing, "scope", coords.getScope());
            }
        }
        if (coords.getType() != null) {
            if (coords.getType().isEmpty()) {
                removeChild(existing, "type");
            } else {
                setOrCreateChild(existing, "type", coords.getType());
            }
        }
        if (coords.getClassifier() != null) {
            if (coords.getClassifier().isEmpty()) {
                removeChild(existing, "classifier");
            } else {
                setOrCreateChild(existing, "classifier", coords.getClassifier());
            }
        }
        if (coords.getOptional() != null) {
            if (coords.getOptional()) {
                setOrCreateChild(existing, "optional", "true");
            } else {
                removeChild(existing, "optional");
            }
        }
    }

    /**
     * Removes a dependency element matching groupId and artifactId (any type/classifier).
     *
     * @param groupId    the groupId to match
     * @param artifactId the artifactId to match
     * @param managed    if {@code true}, remove from {@code <dependencyManagement>}
     * @return {@code true} if the dependency was found and removed
     */
    public boolean removeDependency(String groupId, String artifactId, boolean managed) {
        return removeDependency(groupId, artifactId, null, null, managed);
    }

    /**
     * Removes a dependency element from the POM, matching on groupId, artifactId, type, and classifier.
     * Also removes any immediately preceding XML comment associated with the dependency.
     *
     * @param groupId    the groupId to match
     * @param artifactId the artifactId to match
     * @param type       the type to match ({@code null} matches "jar" or absent)
     * @param classifier the classifier to match ({@code null} matches absent)
     * @param managed    if {@code true}, remove from {@code <dependencyManagement>}
     * @return {@code true} if the dependency was found and removed
     */
    public boolean removeDependency(
            String groupId, String artifactId, String type, String classifier, boolean managed) {
        Element depsElement = getDependenciesElement(managed, false);
        if (depsElement == null) {
            return false;
        }
        Element dep = findDependency(groupId, artifactId, type, classifier, managed);
        if (dep == null) {
            return false;
        }

        // Remove preceding whitespace and associated comment nodes
        removePrecedingWhitespaceAndComments(depsElement, dep);

        depsElement.removeChild(dep);
        return true;
    }

    /**
     * Writes the modified POM back to disk using an atomic write (temp file + rename).
     *
     * @throws IOException if the file cannot be written
     */
    public void save() throws IOException {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();

            if (hadXmlDeclaration) {
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
                Charset enc = detectEncoding(document);
                transformer.setOutputProperty(OutputKeys.ENCODING, enc.name());
            } else {
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            }

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));

            String output = writer.toString();

            // Remove standalone attribute if not in the original
            // The transformer may add standalone="no" which wasn't in the original
            if (hadXmlDeclaration) {
                output = output.replaceFirst("<\\?xml([^?]*) standalone=\"no\"([^?]*)\\?>", "<?xml$1$2?>");
                // Clean up any double spaces left behind
                output = output.replaceFirst("<\\?xml\\s+", "<?xml ");
                output = output.replaceFirst("\\s+\\?>", "?>");
            }

            // Normalize line endings to match original
            output = output.replace("\r\n", "\n").replace("\r", "\n");
            if (!"\n".equals(lineEnding)) {
                output = output.replace("\n", lineEnding);
            }

            // Ensure file ends with newline
            if (!output.endsWith(lineEnding)) {
                output += lineEnding;
            }

            Charset encoding = detectEncoding(document);
            byte[] bytes = output.getBytes(encoding);

            // Re-add BOM if original had one
            if (hadBom) {
                byte[] withBom = new byte[UTF8_BOM.length + bytes.length];
                System.arraycopy(UTF8_BOM, 0, withBom, 0, UTF8_BOM.length);
                System.arraycopy(bytes, 0, withBom, UTF8_BOM.length, bytes.length);
                bytes = withBom;
            }

            // Atomic write: write to temp file in same directory, then rename
            File tempFile = File.createTempFile("pom", ".xml.tmp", pomFile.getParentFile());
            try {
                Files.write(tempFile.toPath(), bytes);
                Files.move(tempFile.toPath(), pomFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // Clean up temp file on failure
                tempFile.delete();
                throw e;
            }
        } catch (TransformerException e) {
            throw new IOException("Failed to write POM file: " + pomFile, e);
        }
    }

    // --- Private helpers ---

    /**
     * Returns the local name of a node, handling both namespace-aware and non-namespace-aware cases.
     */
    private static String localName(Node node) {
        String local = node.getLocalName();
        return local != null ? local : node.getNodeName();
    }

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
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(localName(node))) {
                return (Element) node;
            }
        }
        if (!create) {
            return null;
        }
        String baseIndent = repeatIndent(depth);
        Element newElement = createElementWithNS(tagName);

        parent.appendChild(document.createTextNode(lineEnding + baseIndent));
        parent.appendChild(newElement);
        ensureClosingIndent(parent, depth - 1);

        return newElement;
    }

    /**
     * Creates an element in the same namespace as the root element, if any.
     */
    private Element createElementWithNS(String tagName) {
        if (namespaceURI != null) {
            return document.createElementNS(namespaceURI, tagName);
        }
        return document.createElement(tagName);
    }

    private Element buildDependencyElement(DependencyCoordinates coords, int depth) {
        Element dep = createElementWithNS("dependency");
        String childIndent = repeatIndent(depth + 1);

        appendChildElement(dep, "groupId", coords.getGroupId(), childIndent);
        appendChildElement(dep, "artifactId", coords.getArtifactId(), childIndent);

        if (coords.getVersion() != null && !coords.getVersion().isEmpty()) {
            appendChildElement(dep, "version", coords.getVersion(), childIndent);
        }
        if (coords.getScope() != null && !coords.getScope().isEmpty()) {
            appendChildElement(dep, "scope", coords.getScope(), childIndent);
        }
        if (coords.getType() != null && !coords.getType().isEmpty()) {
            appendChildElement(dep, "type", coords.getType(), childIndent);
        }
        if (coords.getClassifier() != null && !coords.getClassifier().isEmpty()) {
            appendChildElement(dep, "classifier", coords.getClassifier(), childIndent);
        }
        if (coords.getOptional() != null && coords.getOptional()) {
            appendChildElement(dep, "optional", "true", childIndent);
        }

        dep.appendChild(document.createTextNode(lineEnding + repeatIndent(depth)));

        return dep;
    }

    private void appendChildElement(Element parent, String tagName, String value, String indentStr) {
        parent.appendChild(document.createTextNode(lineEnding + indentStr));
        Element child = createElementWithNS(tagName);
        child.setTextContent(value);
        parent.appendChild(child);
    }

    private void setOrCreateChild(Element parent, String tagName, String value) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(localName(node))) {
                node.setTextContent(value);
                return;
            }
        }
        int depth = getElementDepth(parent);
        String childIndent = repeatIndent(depth + 1);
        appendChildElement(parent, tagName, value, childIndent);
    }

    /**
     * Removes a child element and its preceding whitespace text node, if present.
     */
    private void removeChild(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(localName(node))) {
                // Remove preceding whitespace text node
                if (i > 0 && children.item(i - 1).getNodeType() == Node.TEXT_NODE) {
                    parent.removeChild(children.item(i - 1));
                    // After removal, the element moved to index i-1
                    parent.removeChild(children.item(i - 1));
                } else {
                    parent.removeChild(node);
                }
                return;
            }
        }
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

    /**
     * Removes preceding whitespace text nodes and any immediately preceding XML comment
     * that is associated with the dependency element (no blank line separating them).
     */
    private void removePrecedingWhitespaceAndComments(Element parent, Node target) {
        Node prev = target.getPreviousSibling();

        // Walk backwards through whitespace and comment nodes
        while (prev != null) {
            if (prev.getNodeType() == Node.TEXT_NODE
                    && prev.getTextContent().trim().isEmpty()) {
                // Whitespace-only text node
                Node toRemove = prev;
                prev = prev.getPreviousSibling();
                parent.removeChild(toRemove);
            } else if (prev.getNodeType() == Node.COMMENT_NODE) {
                // Remove associated comment (directly preceding, no blank line gap)
                Node toRemove = prev;
                prev = prev.getPreviousSibling();
                parent.removeChild(toRemove);
            } else {
                break;
            }
        }
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
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(localName(node))) {
                return node.getTextContent().trim();
            }
        }
        return null;
    }

    private static boolean hasBom(byte[] bytes) {
        return bytes.length >= 3 && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2];
    }

    /**
     * Strips the Unicode BOM character (U+FEFF) from the start of a string if present.
     * Java's String.trim() only removes ASCII whitespace (chars &lt;= 0x20) and does not
     * remove the BOM character.
     */
    private static String stripBomChar(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
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

    /**
     * Detects the indentation unit by analyzing all indented lines and finding
     * the most common smallest indent unit (GCD of leading whitespace lengths).
     */
    static String detectIndent(String content) {
        String[] lines = content.split("\\r?\\n");
        Map<Character, int[]> charCounts = new HashMap<>();

        for (String line : lines) {
            if (line.isEmpty() || (line.charAt(0) != ' ' && line.charAt(0) != '\t')) {
                continue;
            }
            char indentChar = line.charAt(0);
            int end = 0;
            while (end < line.length() && line.charAt(end) == indentChar) {
                end++;
            }
            // Only count lines that have content after the indent
            if (end < line.length()) {
                int[] counts = charCounts.computeIfAbsent(indentChar, k -> new int[] {0, 0});
                counts[0]++; // frequency
                if (counts[1] == 0) {
                    counts[1] = end;
                } else {
                    counts[1] = gcd(counts[1], end);
                }
            }
        }

        if (charCounts.containsKey('\t')) {
            return "\t";
        }
        if (charCounts.containsKey(' ')) {
            int unit = charCounts.get(' ')[1];
            if (unit <= 0) {
                unit = 4;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < unit; i++) {
                sb.append(' ');
            }
            return sb.toString();
        }
        return "    ";
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a;
    }
}
