package org.apache.maven.plugins.dependency.analyze;
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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
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
import java.io.*;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Attempts to fix the warnings identified by analysis.
 *
 * @author <a href="mailto:alex@alexecollins.com">Alex Collins</a>
 * @since 3.1.2
 */
@Mojo(name = "fix-warnings", requiresDependencyResolution = ResolutionScope.TEST)
public class FixWarningsMojo extends AbstractMojo implements Contextualizable {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    private Context context;

    @Parameter(defaultValue = "${basedir}", readonly = true)
    private File baseDir;


    @Parameter(property = "analyzer", defaultValue = "default")
    private String analyzer;

    @Parameter(property = "indent", defaultValue = "    ")
    private String indent;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (project.getPackaging().equals("pom")) {
            getLog().info("skipping - pom packaging");
            return;
        }

        try {
            executeAux();
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void executeAux() throws Exception {

        getLog().info("Step 1 - verify project");
        verify();
        ProjectDependencyAnalysis analysis = getAnalyzer().analyze(project);

        getLog().info("Step 2 - add used un-declared artifacts");
        addUnusedDependencies(analysis.getUsedUndeclaredArtifacts());
    }

    private void addUnusedDependencies(Set<Artifact> usedUndeclaredArtifacts) throws SAXException, IOException, ParserConfigurationException, TransformerException {
        File pom = new File(baseDir, "pom.xml");
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(pom);
        Node dependencies = getDependencies(doc);
        for (Artifact artifact : new TreeSet<>(usedUndeclaredArtifacts)) {
            getLog().info("+ " + artifact);
            append2Indents(doc, dependencies);
            Element dependency = doc.createElement("dependency");
            dependencies.appendChild(dependency);
            appendNewLine(doc, dependency);

            append4Indents(doc, dependency);
            dependency.appendChild(doc.createComment("automatically added"));
            appendNewLine(doc, dependency);

            append4Indents(doc, dependency);
            Element groupId = doc.createElement("groupId");
            groupId.appendChild(doc.createTextNode(artifact.getGroupId()));
            dependency.appendChild(groupId);
            appendNewLine(doc, dependency);

            append4Indents(doc, dependency);
            Element artifactId = doc.createElement("artifactId");
            artifactId.appendChild(doc.createTextNode(artifact.getGroupId()));
            dependency.appendChild(artifactId);
            appendNewLine(doc, dependency);

            append4Indents(doc, dependency);
            Element version = doc.createElement("version");
            version.appendChild(doc.createTextNode(artifact.getVersion()));
            dependency.appendChild(version);
            appendNewLine(doc, dependency);
            append2Indents(doc, dependency);

            dependencies.appendChild(doc.createTextNode("\n"));
        }


        TransformerFactory.newInstance().newTransformer()
                .transform(new DOMSource(doc), new StreamResult(pom));
    }

    private Node append2Indents(Document doc, Node dependencies) {
        return dependencies.appendChild(doc.createTextNode(indent + indent));
    }

    private Node append4Indents(Document doc, Element dependency) {
        return dependency.appendChild(doc.createTextNode(indent + indent + indent));
    }

    private Node appendNewLine(Document doc, Element dependency) {
        return dependency.appendChild(doc.createTextNode("\n"));
    }

    private Node getDependencies(Document doc) {
        NodeList childNodes = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (item.getNodeName().equals("dependencies")) {
                return item;
            }
        }
        return null;
    }

    private void verify() throws IOException, InterruptedException {

        getLog().info("verifying " + baseDir);

        Process process = new ProcessBuilder()
                .directory(baseDir)
                .command("mvn", "-q", "clean", "test-compile")
                .start();

        log(process.getInputStream(), new LogConsumer() {
            @Override
            public void consume(String line) {
                getLog().info(line);
            }
        });
        log(process.getErrorStream(), new LogConsumer() {
            @Override
            public void consume(String line) {
                getLog().error(line);
            }
        });

        if (process.waitFor() != 0) {
            throw new IllegalStateException(String.valueOf(process.exitValue()));
        }
    }

    private interface LogConsumer {
        void consume(String line);

    }


    private void log(final InputStream inputStream, final LogConsumer log) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))) {

                    String line;
                    while ((line = in.readLine()) != null) {
                        log.consume(line);
                    }

                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }).start();
    }

    private ProjectDependencyAnalyzer getAnalyzer() throws ComponentLookupException, ContextException {
        return (ProjectDependencyAnalyzer) ((PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY)).lookup(ProjectDependencyAnalyzer.ROLE, this.analyzer);
    }


    @Override
    public void contextualize(Context context) {
        this.context = context;
    }
}
