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

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisplayAncestorsMojoTest {

    private final Log log = mock(Log.class);

    private static MavenProject project(String groupId, String artifactId, String version, MavenProject parent) {
        MavenProject project = mock(MavenProject.class);
        when(project.getGroupId()).thenReturn(groupId);
        when(project.getArtifactId()).thenReturn(artifactId);
        when(project.getVersion()).thenReturn(version);
        when(project.getParent()).thenReturn(parent);
        return project;
    }

    private void execute(MavenProject project) throws Exception {
        DisplayAncestorsMojo mojo = new DisplayAncestorsMojo(project);
        mojo.setLog(log);
        mojo.execute();
    }

    @Test
    void listsASingleParent() throws Exception {
        MavenProject apache = project("org.apache", "apache", "5", null);

        execute(project("org.example", "app", "1.0", apache));

        verify(log).info("Ancestor POMs: org.apache:apache:5");
    }

    @Test
    void listsTheWholeParentChainNearestFirst() throws Exception {
        MavenProject apache = project("org.apache", "apache", "5", null);
        MavenProject parent = project("org.example", "parent", "2", apache);

        execute(project("org.example", "app", "1.0", parent));

        verify(log).info("Ancestor POMs: org.example:parent:2 <- org.apache:apache:5");
    }

    @Test
    void reportsAProjectWithoutParent() throws Exception {
        execute(project("org.example", "app", "1.0", null));

        verify(log).info("No Ancestor POMs!");
    }
}
