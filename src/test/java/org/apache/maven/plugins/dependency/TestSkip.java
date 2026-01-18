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

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.dependency.analyze.AnalyzeDepMgt;
import org.apache.maven.plugins.dependency.analyze.AnalyzeDuplicateMojo;
import org.apache.maven.plugins.dependency.analyze.AnalyzeMojo;
import org.apache.maven.plugins.dependency.analyze.AnalyzeOnlyMojo;
import org.apache.maven.plugins.dependency.analyze.AnalyzeReport;
import org.apache.maven.plugins.dependency.fromConfiguration.CopyMojo;
import org.apache.maven.plugins.dependency.fromConfiguration.UnpackMojo;
import org.apache.maven.plugins.dependency.fromDependencies.BuildClasspathMojo;
import org.apache.maven.plugins.dependency.fromDependencies.CopyDependenciesMojo;
import org.apache.maven.plugins.dependency.fromDependencies.UnpackDependenciesMojo;
import org.apache.maven.plugins.dependency.resolvers.GoOfflineMojo;
import org.apache.maven.plugins.dependency.resolvers.ListMojo;
import org.apache.maven.plugins.dependency.resolvers.OldResolveDependencySourcesMojo;
import org.apache.maven.plugins.dependency.resolvers.ResolveDependenciesMojo;
import org.apache.maven.plugins.dependency.resolvers.ResolvePluginsMojo;
import org.apache.maven.plugins.dependency.tree.TreeMojo;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MojoTest
class TestSkip {

    @Inject
    private MojoExecution mojoExecution;

    @Inject
    private Log log;

    @Test
    @InjectMojo(goal = "analyze")
    @MojoParameter(name = "skip", value = "true")
    void testSkipAnalyze(AnalyzeMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "analyze-dep-mgt")
    @MojoParameter(name = "skip", value = "true")
    void testSkipAnalyzeDepMgt(AnalyzeDepMgt mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "analyze-only")
    @MojoParameter(name = "skip", value = "true")
    void testSkipAnalyzeOnly(AnalyzeOnlyMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "analyze-report")
    @MojoParameter(name = "skip", value = "true")
    void testSkipAnalyzeReport(AnalyzeReport mojo) throws Exception {
        Plugin plugin = new Plugin();
        plugin.setArtifactId("maven-dependency-plugin");
        plugin.setVersion("1.0.0");
        when(mojoExecution.getPlugin()).thenReturn(plugin);
        when(mojoExecution.getGoal()).thenReturn("analyze-report");

        mojo.execute();
        verify(log)
                .info(contains(
                        "Skipping org.apache.maven.plugins:maven-dependency-plugin:1.0.0:analyze-report report goal"));
    }

    @Test
    @InjectMojo(goal = "analyze-duplicate")
    @MojoParameter(name = "skip", value = "true")
    void testSkipAnalyzeDuplicate(AnalyzeDuplicateMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "build-classpath")
    @MojoParameter(name = "skip", value = "true")
    void testSkipBuildClasspath(BuildClasspathMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "copy")
    @MojoParameter(name = "skip", value = "true")
    void testSkipCopy(CopyMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "copy-dependencies")
    @MojoParameter(name = "skip", value = "true")
    void testSkipCopyDependencies(CopyDependenciesMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "get")
    @MojoParameter(name = "skip", value = "true")
    void testSkipGet(GetMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "go-offline")
    @MojoParameter(name = "skip", value = "true")
    void testSkipGoOffline(GoOfflineMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "list")
    @MojoParameter(name = "skip", value = "true")
    void testSkipList(ListMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "properties")
    @MojoParameter(name = "skip", value = "true")
    void testSkipProperties(PropertiesMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "purge-local-repository")
    @MojoParameter(name = "skip", value = "true")
    void testSkipPurgeLocalRepository(PurgeLocalRepositoryMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "resolve")
    @MojoParameter(name = "skip", value = "true")
    void testSkipResolve(ResolveDependenciesMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "resolve-plugins")
    @MojoParameter(name = "skip", value = "true")
    void testSkipResolvePlugins(ResolvePluginsMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "sources")
    @MojoParameter(name = "skip", value = "true")
    void testSkipSources(OldResolveDependencySourcesMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "tree")
    @MojoParameter(name = "skip", value = "true")
    void testSkipTree(TreeMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "unpack")
    @MojoParameter(name = "skip", value = "true")
    void testSkipUnpack(UnpackMojo mojo) throws Exception {
        doTest(mojo);
    }

    @Test
    @InjectMojo(goal = "unpack-dependencies")
    @MojoParameter(name = "skip", value = "true")
    void testSkipUnpackDependencies(UnpackDependenciesMojo mojo) throws Exception {
        doTest(mojo);
    }

    private void doTest(Mojo mojo) throws Exception {
        mojo.execute();
        verify(log).info(contains("Skipping plugin"));
    }
}
