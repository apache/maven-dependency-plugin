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
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Utility class for editing poms in-place.
 */
public class PomEditor {
    private final Properties properties;
    private final File pom;
    private final File pomBackup;
    private final Verifier verifier;
    private Document doc;
    private final String indent;
    private final boolean dependencyManaged;
    private Node dependencies;

    public PomEditor(Properties properties, File baseDir, String indent, Verifier verifier, boolean dependencyManaged) {
        this.properties = properties;
        this.pom = new File(baseDir, "pom.xml");
        this.pomBackup = new File(baseDir, "pom.xml.backup");
        this.indent = indent;
        this.verifier = verifier;
        this.dependencyManaged = dependencyManaged;
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

    public void addDependency(Artifact artifact) {
        append2Indents(dependencies);
        Element dependency = doc.createElement("dependency");
        dependencies.appendChild(dependency);
        appendNewLine(dependency);

        append3Indents(dependency);
        Element groupId = doc.createElement("groupId");
        groupId.appendChild(doc.createTextNode(artifact.getGroupId()));
        dependency.appendChild(groupId);
        appendNewLine(dependency);

        append3Indents(dependency);
        Element artifactId = doc.createElement("artifactId");
        artifactId.appendChild(doc.createTextNode(artifact.getArtifactId()));
        dependency.appendChild(artifactId);
        appendNewLine(dependency);

        boolean reactor = artifact.getVersion().equals(subst("${project.version}"));

        if (reactor || !dependencyManaged) {
            append3Indents(dependency);
            Element version = doc.createElement("version");
            version.appendChild(doc.createTextNode(reactor ? "${project.version}" : artifact.getVersion()));
            dependency.appendChild(version);
            appendNewLine(dependency);
        }

        if (!"compile".equals(artifact.getScope())) {
            append3Indents(dependency);
            Element scope = doc.createElement("scope");
            scope.appendChild(doc.createTextNode(artifact.getScope()));
            dependency.appendChild(scope);
            appendNewLine(dependency);
        }

        append2Indents(dependency);
        appendNewLine(dependencies);
    }

    private void append2Indents(Node element) {
        element.appendChild(doc.createTextNode(indent + indent));
    }

    private void append3Indents(Node element) {
        element.appendChild(doc.createTextNode(indent + indent + indent));
    }

    private void appendNewLine(Node element) {
        element.appendChild(doc.createTextNode("\n"));
    }


    public void start() throws IOException {
        Files.copy(pom.toPath(), pomBackup.toPath(), REPLACE_EXISTING);
        try {
            this.doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(this.pom);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        this.dependencies = dependenciesFor(doc);
    }

    public void end() throws Exception {
        try {
            TransformerFactory.newInstance().newTransformer()
                    .transform(new DOMSource(doc), new StreamResult(pom));

            verifier.verify();

            Files.delete(pomBackup.toPath());
        } catch (Exception e) {
            Files.move(pomBackup.toPath(), pom.toPath(), REPLACE_EXISTING);
            throw e;
        } finally {
            this.doc = null;
            this.dependencies = null;
        }
    }

    public void removeDependency(Artifact artifact) {
        NodeList childNodes = dependencies.getChildNodes();
        boolean found = false;
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node dependency = childNodes.item(i);
            if (dependency.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) dependency;


                String groupId = subst(element.getElementsByTagName("groupId").item(0).getTextContent());
                String artifactId = subst(element.getElementsByTagName("artifactId").item(0).getTextContent());

                if (artifact.getGroupId().equals(groupId) && artifact.getArtifactId().equals(artifactId)) {
                    dependencies.removeChild(dependency);
                    found = true;
                }

            }
        }
        if (!found) {
            throw new IllegalStateException("dependency " + artifact + " not found");
        }
    }

    private String subst(String textContent) {

        for (String key : properties.stringPropertyNames()) {
            textContent = textContent.replace("${" + key + "}", properties.getProperty(key));
        }

        return textContent;
    }
}

