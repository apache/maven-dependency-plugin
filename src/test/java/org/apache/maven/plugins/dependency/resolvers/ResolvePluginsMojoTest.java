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
package org.apache.maven.plugins.dependency.resolvers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolvePluginsMojoTest {

    @Mock
    private MavenSession session;

    @Mock
    private ResolverUtil resolverUtil;

    @Mock
    private ProjectBuilder projectBuilder;

    @Mock
    private ProjectBuildingResult buildingResult;

    @TempDir
    private Path tempDir;

    private final Artifact pluginArtifact = new DefaultArtifact("g", "plugin", "jar", "1.0");

    private final Artifact dependencyArtifact = new DefaultArtifact("g", "dep", "jar", "1.0");

    private final Artifact pluginParent = new DefaultArtifact("g", "plugin-parent", "pom", "1.0");

    private final Artifact pluginGrandparent = new DefaultArtifact("g", "plugin-grandparent", "pom", "1.0");

    private final Artifact dependencyParent = new DefaultArtifact("g", "dep-parent", "pom", "1.0");

    private MavenProject project;

    @BeforeEach
    void setUp() throws Exception {
        Plugin plugin = new Plugin();
        plugin.setGroupId("g");
        plugin.setArtifactId("plugin");
        plugin.setVersion("1.0");
        Model model = new Model();
        model.setBuild(new Build());
        project = new MavenProject(model);
        when(resolverUtil.getProjectPlugins(project)).thenReturn(Collections.singletonList(plugin));
        when(resolverUtil.resolvePlugin(plugin)).thenReturn(pluginArtifact);
        when(resolverUtil.resolveDependencies(eq(plugin), any()))
                .thenReturn(Collections.singletonList(dependencyArtifact));
        lenient()
                .when(session.getProjectBuildingRequest())
                .thenReturn(new org.apache.maven.project.DefaultProjectBuildingRequest());

        lenient()
                .when(projectBuilder.build(
                        any(org.apache.maven.artifact.Artifact.class), any(ProjectBuildingRequest.class)))
                .thenAnswer(invocation -> {
                    org.apache.maven.artifact.Artifact artifact = invocation.getArgument(0);
                    MavenProject built = new MavenProject();
                    if ("plugin".equals(artifact.getArtifactId())) {
                        built.setParent(projectWithParents(pluginParent, pluginGrandparent));
                    } else if ("dep".equals(artifact.getArtifactId())) {
                        built.setParent(projectWithParents(dependencyParent));
                    }
                    when(buildingResult.getProject()).thenReturn(built);
                    return buildingResult;
                });
        lenient()
                .when(resolverUtil.resolveArtifact(any(Artifact.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MavenProject projectWithParents(Artifact... chain) {
        MavenProject parent = null;
        for (int i = chain.length - 1; i >= 0; i--) {
            MavenProject current = new MavenProject();
            current.setArtifact(RepositoryUtils.toArtifact(chain[i]));
            current.setParent(parent);
            parent = current;
        }
        return parent;
    }

    @Test
    void parentsAreListedWhenIncludeParentsIsSet() throws Exception {
        String output = execute(true);

        assertThat(output)
                .contains("g:plugin:jar:1.0")
                .contains("g:dep:jar:1.0")
                .contains("g:plugin-parent:pom:1.0")
                .contains("g:plugin-grandparent:pom:1.0")
                .contains("g:dep-parent:pom:1.0");
    }

    @Test
    void parentsAreNotListedByDefault() throws Exception {
        String output = execute(false);

        assertThat(output)
                .contains("g:plugin:jar:1.0")
                .contains("g:dep:jar:1.0")
                .doesNotContain("-parent");
    }

    @Test
    void sharedDependencyIsBuiltOnceAcrossPlugins() throws Exception {
        Plugin first = new Plugin();
        first.setGroupId("g");
        first.setArtifactId("plugin");
        first.setVersion("1.0");
        Plugin second = new Plugin();
        second.setGroupId("g");
        second.setArtifactId("plugin2");
        second.setVersion("1.0");
        Artifact secondArtifact = new DefaultArtifact("g", "plugin2", "jar", "1.0");
        when(resolverUtil.getProjectPlugins(project)).thenReturn(Arrays.asList(first, second));
        when(resolverUtil.resolvePlugin(second)).thenReturn(secondArtifact);
        when(resolverUtil.resolveDependencies(eq(second), any()))
                .thenReturn(Collections.singletonList(dependencyArtifact));

        String output = execute(true);

        assertThat(output).contains("g:plugin2:jar:1.0");
        assertThat(output.split("g:dep-parent:pom:1.0", -1)).hasSize(3);
        verify(projectBuilder, times(1))
                .build(argThat((org.apache.maven.artifact.Artifact a) -> "dep".equals(a.getArtifactId())), any());
    }

    private String execute(boolean includeParents) throws Exception {
        File outputFile = tempDir.resolve("resolved.txt").toFile();
        ResolvePluginsMojo mojo = new ResolvePluginsMojo(session, null, project, resolverUtil, projectBuilder);
        mojo.outputFile = outputFile;
        mojo.excludeReactor = false;
        mojo.includeGroupIds = Collections.emptyList();
        mojo.excludeGroupIds = Collections.emptyList();
        mojo.includeArtifactIds = Collections.emptyList();
        mojo.excludeArtifactIds = Collections.emptyList();
        mojo.includeParents = includeParents;
        mojo.execute();
        return new String(Files.readAllBytes(outputFile.toPath()));
    }
}
