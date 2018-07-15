package org.apache.maven.plugins.dependency.analyze.internal;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;

/**
 * Utility class for editing poms in-place.
 */
class PomEditor {
    private final File pom;
    private final Document doc;
    private final Node dependencies;
    private final String indent;

    PomEditor(File pom, String indent) {
        this.pom = pom;
        this.indent = indent;
        try {
            this.doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(this.pom);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        this.dependencies = dependenciesFor(doc);
    }


    private static Node dependenciesFor(Document doc) {
        NodeList childNodes = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (item.getNodeName().equals("dependencies")) {
                return item;
            }
        }
        throw new IllegalStateException("no dependencies node");
    }

    void addDependency(Artifact artifact) {
        append2Indents(dependencies);
        Element dependency = doc.createElement("dependency");
        dependencies.appendChild(dependency);
        appendNewLine(dependency);

        append4Indents(dependency);
        Element groupId = doc.createElement("groupId");
        groupId.appendChild(doc.createTextNode(artifact.getGroupId()));
        dependency.appendChild(groupId);
        appendNewLine(dependency);

        append4Indents(dependency);
        Element artifactId = doc.createElement("artifactId");
        artifactId.appendChild(doc.createTextNode(artifact.getArtifactId()));
        dependency.appendChild(artifactId);
        appendNewLine(dependency);

        append4Indents(dependency);
        Element version = doc.createElement("version");
        version.appendChild(doc.createTextNode(artifact.getVersion()));
        dependency.appendChild(version);
        appendNewLine(dependency);
        append2Indents(dependency);

        dependencies.appendChild(doc.createTextNode("\n"));
    }

    private void append2Indents(Node dependencies) {
        dependencies.appendChild(doc.createTextNode(indent + indent));
    }

    private void append4Indents(Element dependency) {
        dependency.appendChild(doc.createTextNode(indent + indent + indent));
    }

    private void appendNewLine(Element dependency) {
        dependency.appendChild(doc.createTextNode("\n"));
    }

    void save() {
        try {
            TransformerFactory.newInstance().newTransformer()
                    .transform(new DOMSource(doc), new StreamResult(pom));
        } catch (TransformerException e) {
            throw new IllegalStateException(e);
        }
    }

    void removeArtifact(Artifact artifact) {
        NodeList childNodes = dependencies.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node dependency = childNodes.item(i);
            if (dependency.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) dependency;

                String groupId = element.getElementsByTagName("groupId").item(0).getTextContent();
                String artifactId = element.getElementsByTagName("artifactId").item(0).getTextContent();

                if (artifact.getGroupId().equals(groupId) && artifact.getArtifactId().equals(artifactId)) {
                    dependencies.removeChild(dependency);
                    return;
                }

            }
        }
        throw new IllegalStateException("dependency " + artifact + " not found in " + childNodes);
    }
}

