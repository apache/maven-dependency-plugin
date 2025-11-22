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

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@MojoTest
public class TestPropertiesMojo {

    @Inject
    private MavenProject project;

    @Test
    @InjectMojo(goal = "properties")
    public void testSetProperties(PropertiesMojo mojo) throws Exception {

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
}
