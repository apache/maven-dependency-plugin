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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyNode;

/**
 * Tests <code>TreeMojo</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @since 2.0
 */
public class TestTreeMojo extends AbstractDependencyMojoTestCase {
    // TestCase methods -------------------------------------------------------

    /*
     * @see org.apache.maven.plugin.testing.AbstractMojoTestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        // required for mojo lookups to work
        super.setUp("tree", false);

        MavenProject project = new MavenProjectStub();
        getContainer().addComponent(project, MavenProject.class.getName());

        MavenSession session = newMavenSession(project);
        getContainer().addComponent(session, MavenSession.class.getName());

        LegacySupport legacySupport = lookup(LegacySupport.class);
        legacySupport.setSession(session);
        installLocalRepository(legacySupport);
    }

    // tests ------------------------------------------------------------------

    /**
     * Tests the proper discovery and configuration of the mojo.
     *
     * @throws Exception in case of an error.
     */
    public void testTreeTestEnvironment() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/tree-test/plugin-config.xml");
        TreeMojo mojo = (TreeMojo) lookupMojo("tree", testPom);

        assertNotNull(mojo);
        assertNotNull(mojo.getProject());
        MavenProject project = mojo.getProject();
        project.setArtifact(this.stubFactory.createArtifact("testGroupId", "project", "1.0"));

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);

        project.setArtifacts(artifacts);
        project.setDependencyArtifacts(directArtifacts);

        mojo.execute();

        DependencyNode rootNode = mojo.getDependencyGraph();
        assertNodeEquals("testGroupId:project:jar:1.0:compile", rootNode);
        assertEquals(2, rootNode.getChildren().size());

        List<String> actualNodes = Arrays.asList(
                createArtifactCoordinateString(rootNode.getChildren().get(0)),
                createArtifactCoordinateString(rootNode.getChildren().get(1)));
        List<String> expectedNodes =
                Arrays.asList("testGroupId:release:jar:1.0:compile", "testGroupId:snapshot:jar:2.0-SNAPSHOT:compile");

        assertTrue(expectedNodes.containsAll(actualNodes));
    }

    /**
     * Test the DOT format serialization
     *
     * @throws Exception in case of an error.
     */
    public void testTreeDotSerializing() throws Exception {
        List<String> contents = runTreeMojo("tree1.dot", "dot");
        assertTrue(findString(contents, "digraph \"testGroupId:project:jar:1.0:compile\" {"));
        assertTrue(findString(
                contents,
                "\"testGroupId:project:jar:1.0:compile\" -> \"testGroupId:snapshot:jar:2.0-SNAPSHOT:compile\""));
        assertTrue(findString(
                contents, "\"testGroupId:project:jar:1.0:compile\" -> \"testGroupId:release:jar:1.0:compile\""));
    }

    /**
     * Test the GraphML format serialization
     *
     * @throws Exception in case of an error.
     */
    public void testTreeGraphMLSerializing() throws Exception {
        List<String> contents = runTreeMojo("tree1.graphml", "graphml");

        assertTrue(findString(contents, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(findString(contents, "<y:NodeLabel>testGroupId:project:jar:1.0:compile</y:NodeLabel>"));
        assertTrue(findString(contents, "<y:NodeLabel>testGroupId:snapshot:jar:2.0-SNAPSHOT:compile</y:NodeLabel>"));
        assertTrue(findString(contents, "<y:NodeLabel>testGroupId:release:jar:1.0:compile</y:NodeLabel>"));
        assertTrue(findString(contents, "<key for=\"node\" id=\"d0\" yfiles.type=\"nodegraphics\"/>"));
        assertTrue(findString(contents, "<key for=\"edge\" id=\"d1\" yfiles.type=\"edgegraphics\"/>"));
    }

    /**
     * Test the TGF format serialization
     *
     * @throws Exception in case of an error.
     */
    public void testTreeTGFSerializing() throws Exception {
        List<String> contents = runTreeMojo("tree1.tgf", "tgf");
        assertTrue(findString(contents, "testGroupId:project:jar:1.0:compile"));
        assertTrue(findString(contents, "testGroupId:snapshot:jar:2.0-SNAPSHOT:compile"));
        assertTrue(findString(contents, "testGroupId:release:jar:1.0:compile"));
    }

    /**
     * Test the JSON format serialization on DependencyNodes with circular dependence
     */
    public void testTreeJsonCircularDependency() throws IOException {
        String outputFileName = testDir.getAbsolutePath() + File.separator + "tree1.json";
        File outputFile = new File(outputFileName);
        Files.createDirectories(outputFile.getParentFile().toPath());
        outputFile.createNewFile();

        Artifact artifact1 = this.stubFactory.createArtifact("testGroupId", "project1", "1.0");
        Artifact artifact2 = this.stubFactory.createArtifact("testGroupId", "project2", "1.0");
        DefaultDependencyNode node1 = new DefaultDependencyNode(artifact1);
        DefaultDependencyNode node2 = new DefaultDependencyNode(artifact2);

        node1.setChildren(new ArrayList<DependencyNode>());
        node2.setChildren(new ArrayList<DependencyNode>());

        node1.getChildren().add(node2);
        node2.getChildren().add(node1);

        try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(outputFile))) {
            JsonDependencyNodeVisitor jsonDependencyNodeVisitor = new JsonDependencyNodeVisitor(outputStreamWriter);

            jsonDependencyNodeVisitor.visit(node1);
        }
    }

    /*
     * Test parsing of Json output and verify all key-value pairs
     */
    public void testTreeJsonParsing() throws Exception {
        List<String> contents = runTreeMojo("tree2.json", "json");

        System.setProperty("jakarta.json.provider", "org.glassfish.json.JsonProviderImpl");
        try (JsonReader reader = Json.createReader(new StringReader(String.join(System.lineSeparator(), contents)))) {
            JsonObject root = reader.readObject();

            assertEquals(root.getString("groupId"), "testGroupId");
            assertEquals(root.getString("artifactId"), "project");
            assertEquals(root.getString("version"), "1.0");
            assertEquals(root.getString("type"), "jar");
            assertEquals(root.getString("scope"), "compile");
            assertEquals(root.getString("classifier"), "");
            assertEquals(root.getString("optional"), "false");

            JsonArray children = root.getJsonArray("children");
            assertEquals(children.size(), 2);

            List<JsonObject> sortedChildren = children.stream()
                    .map(JsonObject.class::cast)
                    .sorted(Comparator.comparing(child -> child.getString("artifactId")))
                    .collect(Collectors.toList());

            // Now that children are sorted, we can assert their values in a fixed order
            JsonObject child0 = sortedChildren.get(0);
            assertEquals(child0.getString("groupId"), "testGroupId");
            assertEquals(child0.getString("artifactId"), "release");
            assertEquals(child0.getString("version"), "1.0");
            assertEquals(child0.getString("type"), "jar");
            assertEquals(child0.getString("scope"), "compile");
            assertEquals(child0.getString("classifier"), "");
            assertEquals(child0.getString("optional"), "false");

            JsonObject child1 = sortedChildren.get(1);
            assertEquals(child1.getString("groupId"), "testGroupId");
            assertEquals(child1.getString("artifactId"), "snapshot");
            assertEquals(child1.getString("version"), "2.0-SNAPSHOT");
            assertEquals(child1.getString("type"), "jar");
            assertEquals(child1.getString("scope"), "compile");
            assertEquals(child1.getString("classifier"), "");
            assertEquals(child1.getString("optional"), "false");
        }
    }

    /**
     * Help finding content in the given list of string
     *
     * @param outputFile the outputFile.
     * @param format The format.
     * @throws Exception in case of an error.
     * @return list of strings in the output file
     */
    private List<String> runTreeMojo(String outputFile, String format) throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/tree-test/plugin-config.xml");
        Path outputFilePath = Paths.get(testDir.getAbsolutePath(), outputFile);
        TreeMojo mojo = (TreeMojo) lookupMojo("tree", testPom);
        setVariableValueToObject(mojo, "outputEncoding", "UTF-8");
        setVariableValueToObject(mojo, "outputType", format);
        setVariableValueToObject(mojo, "outputFile", outputFilePath.toFile());

        assertNotNull(mojo);
        assertNotNull(mojo.getProject());
        MavenProject project = mojo.getProject();
        project.setArtifact(this.stubFactory.createArtifact("testGroupId", "project", "1.0"));

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll(directArtifacts);

        project.setArtifacts(artifacts);
        project.setDependencyArtifacts(directArtifacts);

        mojo.execute();

        List<String> contents = Files.readAllLines(outputFilePath);

        return contents;
    }

    /**
     * Help finding content in the given list of string
     *
     * @param contents The contents.
     * @param str The content which should be checked for.
     */
    private boolean findString(List<String> contents, String str) {
        for (String line : contents) {
            if (line.contains(str)) {
                // if match then return here
                return true;
            }
        }

        // in case no match for the whole list
        return false;
    }

    // private methods --------------------------------------------------------

    private void assertNodeEquals(String expectedNode, DependencyNode actualNode) {
        String[] tokens = expectedNode.split(":");

        assertNodeEquals(tokens[0], tokens[1], tokens[2], tokens[3], tokens[4], actualNode);
    }

    private void assertNodeEquals(
            String expectedGroupId,
            String expectedArtifactId,
            String expectedType,
            String expectedVersion,
            String expectedScope,
            DependencyNode actualNode) {
        Artifact actualArtifact = actualNode.getArtifact();

        assertEquals("group id", expectedGroupId, actualArtifact.getGroupId());
        assertEquals("artifact id", expectedArtifactId, actualArtifact.getArtifactId());
        assertEquals("type", expectedType, actualArtifact.getType());
        assertEquals("version", expectedVersion, actualArtifact.getVersion());
        assertEquals("scope", expectedScope, actualArtifact.getScope());
    }

    private String createArtifactCoordinateString(DependencyNode actualNode) {
        return actualNode.getArtifact().getGroupId() + ":"
                + actualNode.getArtifact().getArtifactId() + ":"
                + actualNode.getArtifact().getType() + ":"
                + actualNode.getArtifact().getVersion() + ":"
                + actualNode.getArtifact().getScope();
    }
}
