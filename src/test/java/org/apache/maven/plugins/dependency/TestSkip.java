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

import java.io.File;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.dependency.testUtils.stubs.DependencyProjectStub;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TestSkip extends AbstractDependencyMojoTestCase {

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        MavenProject project = new DependencyProjectStub();
        getContainer().addComponent(project, MavenProject.class.getName());

        MavenSession session = newMavenSession(project);
        getContainer().addComponent(session, MavenSession.class.getName());
    }

    @Test
    public void testSkipAnalyze() throws Exception {
        doTest("analyze");
    }

    @Test
    public void testSkipAnalyzeDepMgt() throws Exception {
        doTest("analyze-dep-mgt");
    }

    @Test
    public void testSkipAnalyzeOnly() throws Exception {
        doTest("analyze-only");
    }

    @Test
    public void testSkipAnalyzeReport() throws Exception {
        doSpecialTest("analyze-report", true);
    }

    @Test
    public void testSkipAnalyzeDuplicate() throws Exception {
        doTest("analyze-duplicate");
    }

    @Test
    public void testSkipBuildClasspath() throws Exception {
        doTest("build-classpath");
    }

    @Test
    public void testSkipCopy() throws Exception {
        doTest("copy");
    }

    @Test
    public void testSkipCopyDependencies() throws Exception {
        doTest("copy-dependencies");
    }

    @Test
    public void testSkipGet() throws Exception {
        doSpecialTest("get");
    }

    @Test
    public void testSkipGoOffline() throws Exception {
        doTest("go-offline");
    }

    @Test
    public void testSkipList() throws Exception {
        doTest("list");
    }

    @Test
    public void testSkipProperties() throws Exception {
        doTest("properties");
    }

    @Test
    public void testSkipPurgeLocalRepository() throws Exception {
        doSpecialTest("purge-local-repository");
    }

    @Test
    public void testSkipResolve() throws Exception {
        doTest("resolve");
    }

    @Test
    public void testSkipResolvePlugins() throws Exception {
        doTest("resolve-plugins");
    }

    @Test
    public void testSkipSources() throws Exception {
        doTest("sources");
    }

    @Test
    public void testSkipTree() throws Exception {
        doTest("tree");
    }

    @Test
    public void testSkipUnpack() throws Exception {
        doTest("unpack");
    }

    @Test
    public void testSkipUnpackDependencies() throws Exception {
        doTest("unpack-dependencies");
    }

    protected void doTest(String mojoName) throws Exception {
        doConfigTest(mojoName, "plugin-config.xml");
    }

    protected void doSpecialTest(String mojoName) throws Exception {
        doConfigTest(mojoName, "plugin-" + mojoName + "-config.xml", false);
    }

    protected void doSpecialTest(String mojoName, boolean addMojoExecution) throws Exception {
        doConfigTest(mojoName, "plugin-" + mojoName + "-config.xml", addMojoExecution);
    }

    private void doConfigTest(String mojoName, String configFile) throws Exception {
        doConfigTest(mojoName, configFile, false);
    }

    private void doConfigTest(String mojoName, String configFile, boolean addMojoExecution) throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/skip-test/" + configFile);
        Mojo mojo = lookupMojo(mojoName, testPom);
        assertNotNull(mojo, "Mojo not found.");

        if (addMojoExecution) {
            setVariableValueToObject(mojo, "mojoExecution", getMockMojoExecution(mojoName));
        }
        Log log = mock(Log.class);
        mojo.setLog(log);
        mojo.execute();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(log, atLeastOnce()).info(captor.capture());
        String skipMessage;
        if (addMojoExecution) {
            MojoExecution me = getMockMojoExecution(mojoName);
            String reportMojoInfo = me.getPlugin().getId() + ":" + me.getGoal();
            skipMessage = "Skipping " + reportMojoInfo + " report goal";
        } else {
            skipMessage = "Skipping plugin execution";
        }
        assertTrue(captor.getValue().contains(skipMessage));
    }

    private MojoExecution getMockMojoExecution(String goal) {
        MojoDescriptor md = new MojoDescriptor();
        md.setGoal(goal);

        MojoExecution me = new MojoExecution(md);

        PluginDescriptor pd = new PluginDescriptor();
        Plugin p = new Plugin();
        p.setGroupId("org.apache.maven.plugins");
        p.setArtifactId("maven-dependency-plugin");
        pd.setPlugin(p);
        md.setPluginDescriptor(pd);

        return me;
    }
}
