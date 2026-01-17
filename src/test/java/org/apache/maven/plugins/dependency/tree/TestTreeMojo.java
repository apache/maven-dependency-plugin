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

import javax.inject.Inject;

import java.io.File;
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
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Tests <code>TreeMojo</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @since 2.0
 */
@MojoTest(realRepositorySession = true)
class TestTreeMojo {

    @TempDir
    private File tempDir;

    @Inject
    private MavenSession session;

    private DependencyArtifactStubFactory stubFactory;

    @BeforeEach
    void setUp() {
        stubFactory = new DependencyArtifactStubFactory(tempDir, true, false);
        session.getRequest().setLocalRepositoryPath(new File(tempDir, "localTestRepo"));

        DefaultProjectBuildingRequest pbr = spy(new DefaultProjectBuildingRequest());
        doAnswer(__ -> session.getRepositorySession()).when(pbr).getRepositorySession();
        when(session.getProjectBuildingRequest()).thenReturn(pbr);
    }

    // tests ------------------------------------------------------------------

    /**
     * Tests the proper discovery and configuration of the mojo.
     *
     * @throws Exception in case of an error.
     */
    @Test
    @InjectMojo(goal = "tree")
    void testTreeTestEnvironment(TreeMojo mojo) throws Exception {
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
    @Test
    @InjectMojo(goal = "tree")
    @MojoParameter(name = "outputType", value = "dot")
    void testTreeDotSerializing(TreeMojo mojo) throws Exception {
        List<String> contents = runTreeMojo(mojo);
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
    @Test
    @InjectMojo(goal = "tree")
    @MojoParameter(name = "outputType", value = "graphml")
    void testTreeGraphMLSerializing(TreeMojo mojo) throws Exception {
        List<String> contents = runTreeMojo(mojo);

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
    @Test
    @InjectMojo(goal = "tree")
    @MojoParameter(name = "outputType", value = "tgf")
    void testTreeTGFSerializing(TreeMojo mojo) throws Exception {
        List<String> contents = runTreeMojo(mojo);
        assertTrue(findString(contents, "testGroupId:project:jar:1.0:compile"));
        assertTrue(findString(contents, "testGroupId:snapshot:jar:2.0-SNAPSHOT:compile"));
        assertTrue(findString(contents, "testGroupId:release:jar:1.0:compile"));
    }

    /**
     * Test the JSON format serialization on DependencyNodes with circular dependence
     */
    @Test
    void testTreeJsonCircularDependency() throws IOException {
        File outputFile = new File(tempDir, "tree1.json");
        Files.createDirectories(outputFile.getParentFile().toPath());
        outputFile.createNewFile();

        Artifact artifact1 = this.stubFactory.createArtifact("testGroupId", "project1", "1.0");
        Artifact artifact2 = this.stubFactory.createArtifact("testGroupId", "project2", "1.0");
        DefaultDependencyNode node1 = new DefaultDependencyNode(artifact1);
        DefaultDependencyNode node2 = new DefaultDependencyNode(artifact2);

        node1.setChildren(new ArrayList<>());
        node2.setChildren(new ArrayList<>());

        node1.getChildren().add(node2);
        node2.getChildren().add(node1);

        try (OutputStreamWriter outputStreamWriter =
                new OutputStreamWriter(Files.newOutputStream(outputFile.toPath()))) {
            JsonDependencyNodeVisitor jsonDependencyNodeVisitor = new JsonDependencyNodeVisitor(outputStreamWriter);

            jsonDependencyNodeVisitor.visit(node1);
        }
    }

    /*
     * Test parsing of Json output and verify all key-value pairs
     */
    @Test
    @InjectMojo(goal = "tree")
    @MojoParameter(name = "outputType", value = "json")
    void testTreeJsonParsing(TreeMojo mojo) throws Exception {
        List<String> contents = runTreeMojo(mojo);

        System.setProperty("jakarta.json.provider", "org.glassfish.json.JsonProviderImpl");
        try (JsonReader reader = Json.createReader(new StringReader(String.join(System.lineSeparator(), contents)))) {
            JsonObject root = reader.readObject();

            assertEquals("testGroupId", root.getString("groupId"));
            assertEquals("project", root.getString("artifactId"));
            assertEquals("1.0", root.getString("version"));
            assertEquals("jar", root.getString("type"));
            assertEquals("compile", root.getString("scope"));
            assertEquals("", root.getString("classifier"));
            assertEquals("false", root.getString("optional"));

            JsonArray children = root.getJsonArray("children");
            assertEquals(2, children.size());

            List<JsonObject> sortedChildren = children.stream()
                    .map(JsonObject.class::cast)
                    .sorted(Comparator.comparing(child -> child.getString("artifactId")))
                    .collect(Collectors.toList());

            // Now that children are sorted, we can assert their values in a fixed order
            JsonObject child0 = sortedChildren.get(0);
            assertEquals("testGroupId", child0.getString("groupId"));
            assertEquals("release", child0.getString("artifactId"));
            assertEquals("1.0", child0.getString("version"));
            assertEquals("jar", child0.getString("type"));
            assertEquals("compile", child0.getString("scope"));
            assertEquals("", child0.getString("classifier"));
            assertEquals("false", child0.getString("optional"));

            JsonObject child1 = sortedChildren.get(1);
            assertEquals("testGroupId", child1.getString("groupId"));
            assertEquals("snapshot", child1.getString("artifactId"));
            assertEquals("2.0-SNAPSHOT", child1.getString("version"));
            assertEquals("jar", child1.getString("type"));
            assertEquals("compile", child1.getString("scope"));
            assertEquals("", child1.getString("classifier"));
            assertEquals("false", child1.getString("optional"));
        }
    }

    /**
     * Help finding content in the given list of string
     *
     * @throws Exception in case of an error.
     * @return list of strings in the output file
     */
    private List<String> runTreeMojo(TreeMojo mojo) throws Exception {
        Path outputFilePath = Paths.get(tempDir.getPath(), "outputFile.txt");
        setVariableValueToObject(mojo, "outputEncoding", "UTF-8");
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

        return Files.readAllLines(outputFilePath);
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

        assertEquals(expectedGroupId, actualArtifact.getGroupId(), "group id");
        assertEquals(expectedArtifactId, actualArtifact.getArtifactId(), "artifact id");
        assertEquals(expectedType, actualArtifact.getType(), "type");
        assertEquals(expectedVersion, actualArtifact.getVersion(), "version");
        assertEquals(expectedScope, actualArtifact.getScope(), "scope");
    }

    private String createArtifactCoordinateString(DependencyNode actualNode) {
        return actualNode.getArtifact().getGroupId() + ":"
                + actualNode.getArtifact().getArtifactId() + ":"
                + actualNode.getArtifact().getType() + ":"
                + actualNode.getArtifact().getVersion() + ":"
                + actualNode.getArtifact().getScope();
    }
}
