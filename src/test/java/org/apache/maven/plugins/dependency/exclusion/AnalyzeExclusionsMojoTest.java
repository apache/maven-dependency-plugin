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
package org.apache.maven.plugins.dependency.exclusion;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingResult;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AnalyzeExclusionsMojoTest extends AbstractDependencyMojoTestCase {

    AnalyzeExclusionsMojo mojo;
    MavenProject project;
    private TestLog testLog;

    @Override
    public void setUp() throws Exception {
        super.setUp("analyze-exclusions", true, false);
        File testPom = new File(getBasedir(), "target/test-classes/unit/analyze-exclusions/plugin-config.xml");
        mojo = (AnalyzeExclusionsMojo) lookupMojo("analyze-exclusions", testPom);
        assertNotNull(mojo);
        project = (MavenProject) getVariableValueFromObject(mojo, "project");
        MavenSession session = newMavenSession(project);
        setVariableValueToObject(mojo, "session", session);

        LegacySupport legacySupport = lookup(LegacySupport.class);
        legacySupport.setSession(session);
        installLocalRepository(legacySupport);

        testLog = new TestLog();
        mojo.setLog(testLog);
    }

    public void test_shall_throw_exception_when_fail_on_warning() throws Exception {
        List<Dependency> projectDependencies = new ArrayList<>();
        Dependency withInvalidExclusion = dependency("a", "b");
        withInvalidExclusion.addExclusion(exclusion("invalid", "invalid"));
        projectDependencies.add(withInvalidExclusion);
        project.setDependencies(projectDependencies);
        Artifact artifact = stubFactory.createArtifact("a", "b", "1.0");
        project.setArtifacts(newHashSet(artifact));
        setVariableValueToObject(mojo, "failOnWarning", true);

        assertThatThrownBy(() -> mojo.execute())
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Invalid exclusions found");
    }

    public void test_shall_log_error_when_failOnWarning_is_true() throws Exception {
        List<Dependency> dependencies = new ArrayList<>();
        Dependency withInvalidExclusion = dependency("a", "b");
        withInvalidExclusion.addExclusion(exclusion("invalid", "invalid"));
        dependencies.add(withInvalidExclusion);
        project.setDependencies(dependencies);
        Artifact artifact = stubFactory.createArtifact("a", "b", "1.0");
        project.setArtifacts(newHashSet(artifact));
        setVariableValueToObject(mojo, "failOnWarning", true);

        try {
            mojo.execute();
        } catch (MojoExecutionException ignored) {
            // ignored
        }

        assertThat(testLog.getContent()).startsWith("[error]");
    }

    public void test_shall_log_warning_when_failOnWarning_is_false() throws Exception {
        List<Dependency> dependencies = new ArrayList<>();
        Dependency withInvalidExclusion = dependency("a", "b");
        withInvalidExclusion.addExclusion(exclusion("invalid", "invalid"));
        dependencies.add(withInvalidExclusion);
        project.setDependencies(dependencies);
        Artifact artifact = stubFactory.createArtifact("a", "b", "1.0");
        project.setArtifacts(newHashSet(artifact));
        setVariableValueToObject(mojo, "failOnWarning", false);

        mojo.execute();

        assertThat(testLog.getContent()).startsWith("[warn]");
    }

    public void test_shall_exit_without_analyze_when_no_dependency_has_exclusion() throws Exception {
        List<Dependency> dependencies = new ArrayList<>();
        dependencies.add(dependency("a", "c"));
        project.setDependencies(dependencies);
        mojo.execute();
        assertThat(testLog.getContent()).startsWith("[debug] No dependencies defined with exclusions - exiting");
    }

    public void test_shall_not_report_invalid_exclusion_for_wildcard_groupId_and_artifactId() throws Exception {
        Dependency dependencyWithWildcardExclusion = dependency("a", "b");
        dependencyWithWildcardExclusion.addExclusion(exclusion("*", "*"));
        project.setDependencies(newArrayList(dependencyWithWildcardExclusion));
        Artifact artifact = stubFactory.createArtifact("a", "b", "1.0");
        project.setArtifacts(newHashSet(artifact));

        ProjectBuilder projectBuilder = mock(ProjectBuilder.class);
        MavenProject mavenProject = new MavenProject();
        mavenProject.setArtifacts(newHashSet(stubFactory.createArtifact("whatever", "ok", "1.0")));

        ProjectBuildingResult pbr = mock(ProjectBuildingResult.class);
        when(pbr.getProject()).thenReturn(mavenProject);
        when(projectBuilder.build(any(Artifact.class), anyBoolean(), any())).thenReturn(pbr);
        setVariableValueToObject(mojo, "projectBuilder", projectBuilder);

        mojo.execute();

        assertThat(testLog.getContent())
                .doesNotContain(
                        "[warn] The following dependencies defines unnecessary excludes",
                        "[warn]     a:b:",
                        "[warn]         - *:*");
    }

    public void test_can_resolve_multiple_artifacts_with_equal_groupId_and_artifact_id() throws Exception {
        Dependency dependency1 = dependency("a", "b");
        Dependency dependency2 = dependency("a", "b", "compile", "native");
        dependency1.addExclusion(exclusion("c", "d"));
        dependency2.addExclusion(exclusion("c", "d"));
        project.setDependencies(newArrayList(dependency1, dependency2));
        Artifact artifact1 = stubFactory.createArtifact("a", "b", "1.0");
        Artifact artifact2 = stubFactory.createArtifact("a", "b", "1.0", "compile", "jar", "native");
        project.setArtifacts(newHashSet(artifact1, artifact2));

        assertThatCode(() -> mojo.execute()).doesNotThrowAnyException();
    }

    public void test_shall_not_log_when_exclusion_is_valid() throws Exception {
        List<Dependency> dependencies = new ArrayList<>();
        Dependency dependency = dependency("a", "b");
        dependency.addExclusion(exclusion("ok", "ok"));
        dependencies.add(dependency);
        project.setDependencies(dependencies);
        Artifact artifact = stubFactory.createArtifact("a", "b", "1.0");

        project.setArtifacts(newHashSet(artifact));
        setVariableValueToObject(mojo, "failOnWarning", true);

        ProjectBuilder projectBuilder = mock(ProjectBuilder.class);
        MavenProject mavenProject = new MavenProject();
        mavenProject.setArtifacts(newHashSet(stubFactory.createArtifact("ok", "ok", "1.0")));

        ProjectBuildingResult pbr = mock(ProjectBuildingResult.class);
        when(pbr.getProject()).thenReturn(mavenProject);
        when(projectBuilder.build(any(Artifact.class), anyBoolean(), any())).thenReturn(pbr);

        setVariableValueToObject(mojo, "projectBuilder", projectBuilder);
        assertThatCode(() -> mojo.execute()).doesNotThrowAnyException();
    }

    private Dependency dependency(String groupId, String artifactId) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion("1.0");
        dependency.setScope("compile");
        dependency.setType("jar");
        dependency.setClassifier("");
        return dependency;
    }

    private Dependency dependency(String groupId, String artifactId, String scope, String classifier) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion("1.0");
        dependency.setScope(scope);
        dependency.setType("jar");
        dependency.setClassifier(classifier);
        return dependency;
    }

    private Exclusion exclusion(String groupId, String artifactId) {
        Exclusion exclusion = new Exclusion();
        exclusion.setGroupId(groupId);
        exclusion.setArtifactId(artifactId);
        return exclusion;
    }

    static class TestLog implements Log {
        StringBuilder sb = new StringBuilder();

        /** {@inheritDoc} */
        public void debug(CharSequence content) {
            print("debug", content);
        }

        /** {@inheritDoc} */
        public void debug(CharSequence content, Throwable error) {
            print("debug", content, error);
        }

        /** {@inheritDoc} */
        public void debug(Throwable error) {
            print("debug", error);
        }

        /** {@inheritDoc} */
        public void info(CharSequence content) {
            print("info", content);
        }

        /** {@inheritDoc} */
        public void info(CharSequence content, Throwable error) {
            print("info", content, error);
        }

        /** {@inheritDoc} */
        public void info(Throwable error) {
            print("info", error);
        }

        /** {@inheritDoc} */
        public void warn(CharSequence content) {
            print("warn", content);
        }

        /** {@inheritDoc} */
        public void warn(CharSequence content, Throwable error) {
            print("warn", content, error);
        }

        /** {@inheritDoc} */
        public void warn(Throwable error) {
            print("warn", error);
        }

        /** {@inheritDoc} */
        public void error(CharSequence content) {
            print("error", content);
        }

        /** {@inheritDoc} */
        public void error(CharSequence content, Throwable error) {
            StringWriter sWriter = new StringWriter();
            PrintWriter pWriter = new PrintWriter(sWriter);

            error.printStackTrace(pWriter);

            System.err.println("[error] " + content.toString() + System.lineSeparator() + System.lineSeparator()
                    + sWriter.toString());
        }

        /**
         * @see org.apache.maven.plugin.logging.Log#error(java.lang.Throwable)
         */
        public void error(Throwable error) {
            StringWriter sWriter = new StringWriter();
            PrintWriter pWriter = new PrintWriter(sWriter);

            error.printStackTrace(pWriter);

            System.err.println("[error] " + sWriter.toString());
        }

        /**
         * @see org.apache.maven.plugin.logging.Log#isDebugEnabled()
         */
        public boolean isDebugEnabled() {
            // TODO: Not sure how best to set these for this implementation...
            return false;
        }

        /**
         * @see org.apache.maven.plugin.logging.Log#isInfoEnabled()
         */
        public boolean isInfoEnabled() {
            return true;
        }

        /**
         * @see org.apache.maven.plugin.logging.Log#isWarnEnabled()
         */
        public boolean isWarnEnabled() {
            return true;
        }

        /**
         * @see org.apache.maven.plugin.logging.Log#isErrorEnabled()
         */
        public boolean isErrorEnabled() {
            return true;
        }

        private void print(String prefix, CharSequence content) {
            sb.append("[")
                    .append(prefix)
                    .append("] ")
                    .append(content.toString())
                    .append(System.lineSeparator());
        }

        private void print(String prefix, Throwable error) {
            StringWriter sWriter = new StringWriter();
            PrintWriter pWriter = new PrintWriter(sWriter);

            error.printStackTrace(pWriter);

            sb.append("[")
                    .append(prefix)
                    .append("] ")
                    .append(sWriter.toString())
                    .append(System.lineSeparator());
        }

        private void print(String prefix, CharSequence content, Throwable error) {
            StringWriter sWriter = new StringWriter();
            PrintWriter pWriter = new PrintWriter(sWriter);

            error.printStackTrace(pWriter);

            sb.append("[")
                    .append(prefix)
                    .append("] ")
                    .append(content.toString())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
            sb.append(sWriter.toString()).append(System.lineSeparator());
        }

        protected String getContent() {
            return sb.toString();
        }
    }
}
