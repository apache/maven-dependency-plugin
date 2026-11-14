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
package org.apache.maven.plugins.dependency;

import javax.inject.Inject;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.dependency.utils.ParamArtifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.ReflectionUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MojoTest(realRepositorySession = true)
class TestPropertiesMojo {

    @Inject
    private MavenProject project;

    @Test
    @InjectMojo(goal = "properties")
    void testSetProperties(PropertiesMojo mojo) throws Exception {

        Artifact artifact1 = Mockito.mock(Artifact.class);
        Artifact artifact2 = Mockito.mock(Artifact.class);

        when(artifact1.getDependencyConflictId()).thenReturn("group1:artifact1");
        when(artifact1.getFile()).thenReturn(new File(MojoExtension.getBasedir(), "artifact1.jar"));

        when(artifact2.getDependencyConflictId()).thenReturn("group2:artifact2");
        when(artifact2.getFile()).thenReturn(new File(MojoExtension.getBasedir(), "artifact2.jar"));

        when(artifact2.getVersion()).thenReturn("2.0.0");
        when(this.project.getArtifacts()).thenReturn(new HashSet<>(Arrays.asList(artifact1, artifact2)));

        mojo.execute();

        // Verify that properties are set correctly
        assertTrue(project.getProperties().getProperty("group1:artifact1").endsWith("artifact1.jar"));
        assertTrue(project.getProperties().getProperty("group2:artifact2").endsWith("artifact2.jar"));
    }

    /**
     * tests the proper discovery and configuration of the mojo for extra artifacts
     * Each artifact should be resolved and set a property in the project
     * @throws Exception in case of errors
     */
    @Test
    @InjectMojo(goal = "properties")
    void testSetPropertiesForExtractArtifacts(PropertiesMojo mojo) throws Exception {

        String depId1 = "org.apache.commons:commons-lang3:jar";
        String depId2 = "org.apache.commons:commons-collections4:jar";

        ParamArtifact a1 = new ParamArtifact();
        a1.setGroupId("org.apache.commons");
        a1.setArtifactId("commons-lang3");
        a1.setVersion("3.6");

        ParamArtifact a2 = new ParamArtifact();
        a2.setGroupId("org.apache.commons");
        a2.setArtifactId("commons-collections4");
        a2.setVersion("4.4");

        List<ParamArtifact> artifacts = Arrays.asList(a1, a2);

        // 3. Inject into private field
        ReflectionUtils.setVariableValueInObject(mojo, "extraArtifacts", artifacts);

        mojo.execute();

        assertTrue(project.getProperties().containsKey(depId1));
        assertTrue(new File(project.getProperties().getProperty(depId1)).exists());

        assertTrue(project.getProperties().containsKey(depId2));
        assertTrue(new File(project.getProperties().getProperty(depId1)).exists());
    }

    @Test
    @InjectMojo(goal = "properties")
    void testNullFileSkipsProperty(PropertiesMojo mojo) throws Exception {
        Artifact artifact = Mockito.mock(Artifact.class);
        when(artifact.getDependencyConflictId()).thenReturn("group:artifact");
        when(artifact.getFile()).thenReturn(null);
        when(this.project.getArtifacts()).thenReturn(new HashSet<>(Arrays.asList(artifact)));
        Log log = Mockito.mock(Log.class);
        mojo.setLog(log);

        mojo.execute();

        assertFalse(project.getProperties().containsKey("group:artifact"));
        verify(log).warn(contains("group:artifact"));
    }

    @Test
    @InjectMojo(goal = "properties")
    void testVersionsNotSetByDefault(PropertiesMojo mojo) throws Exception {
        Artifact artifact = mockArtifact("group:artifact:jar", "1.0", "1.0");
        when(this.project.getArtifacts()).thenReturn(new HashSet<>(Arrays.asList(artifact)));

        mojo.execute();

        assertTrue(project.getProperties().containsKey("group:artifact:jar"));
        assertFalse(project.getProperties().containsKey("group:artifact:jar.version"));
    }

    @Test
    @InjectMojo(goal = "properties")
    void testIncludeVersionsUsesBaseVersion(PropertiesMojo mojo) throws Exception {
        Artifact release = mockArtifact("group:release:jar", "2.0.16", "2.0.16");
        Artifact snapshot = mockArtifact("group:snapshot:jar:tests", "1.0-20260101.101010-1", "1.0-SNAPSHOT");
        when(this.project.getArtifacts()).thenReturn(new HashSet<>(Arrays.asList(release, snapshot)));
        ReflectionUtils.setVariableValueInObject(mojo, "includeVersions", true);

        mojo.execute();

        assertEquals("2.0.16", project.getProperties().getProperty("group:release:jar.version"));
        assertEquals("1.0-SNAPSHOT", project.getProperties().getProperty("group:snapshot:jar:tests.version"));
        assertTrue(project.getProperties().getProperty("group:release:jar").endsWith("group-release.jar"));
    }

    @Test
    @InjectMojo(goal = "properties")
    void testIncludeVersionsSetForArtifactWithoutFile(PropertiesMojo mojo) throws Exception {
        Artifact artifact = mockArtifact("group:artifact:jar", "1.0", "1.0");
        when(artifact.getFile()).thenReturn(null);
        when(this.project.getArtifacts()).thenReturn(new HashSet<>(Arrays.asList(artifact)));
        mojo.setLog(Mockito.mock(Log.class));
        ReflectionUtils.setVariableValueInObject(mojo, "includeVersions", true);

        mojo.execute();

        assertFalse(project.getProperties().containsKey("group:artifact:jar"));
        assertEquals("1.0", project.getProperties().getProperty("group:artifact:jar.version"));
    }

    @Test
    @InjectMojo(goal = "properties")
    void testIncludeVersionsForExtraArtifacts(PropertiesMojo mojo) throws Exception {
        ParamArtifact a1 = new ParamArtifact();
        a1.setGroupId("org.apache.commons");
        a1.setArtifactId("commons-lang3");
        a1.setVersion("3.6");
        ReflectionUtils.setVariableValueInObject(mojo, "extraArtifacts", Arrays.asList(a1));
        ReflectionUtils.setVariableValueInObject(mojo, "includeVersions", true);

        mojo.execute();

        assertEquals("3.6", project.getProperties().getProperty("org.apache.commons:commons-lang3:jar.version"));
    }

    private static Artifact mockArtifact(String conflictId, String version, String baseVersion) {
        Artifact artifact = Mockito.mock(Artifact.class);
        when(artifact.getDependencyConflictId()).thenReturn(conflictId);
        when(artifact.getFile())
                .thenReturn(new File(
                        MojoExtension.getBasedir(),
                        conflictId.split(":")[0] + "-" + conflictId.split(":")[1] + ".jar"));
        when(artifact.getVersion()).thenReturn(version);
        when(artifact.getBaseVersion()).thenReturn(baseVersion);
        return artifact;
    }
}
