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
package org.apache.maven.plugins.dependency.analyze;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.plugins.dependency.testUtils.stubs.DuplicateDependencies2ProjectStub;
import org.apache.maven.plugins.dependency.testUtils.stubs.DuplicateDependenciesProjectStub;
import org.apache.maven.project.MavenProject;

/**
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @version $Id$
 */
public class TestAnalyzeDuplicateMojo extends AbstractDependencyMojoTestCase {
    public void testDuplicate() throws Exception {
        MavenProject project = new DuplicateDependenciesProjectStub();
        getContainer().addComponent(project, MavenProject.class.getName());

        MavenSession session = newMavenSession(project);
        getContainer().addComponent(session, MavenSession.class.getName());

        File testPom = new File(getBasedir(), "target/test-classes/unit/duplicate-dependencies/plugin-config.xml");
        AnalyzeDuplicateMojo mojo = (AnalyzeDuplicateMojo) lookupMojo("analyze-duplicate", testPom);
        assertNotNull(mojo);
        DuplicateLog log = new DuplicateLog();
        mojo.setLog(log);
        mojo.execute();

        assertTrue(log.getContent()
                .contains("List of duplicate dependencies defined in <dependencies/> in " + "your pom.xml"));
        assertTrue(log.getContent().contains("junit:junit:jar"));
    }

    public void testDuplicate2() throws Exception {
        MavenProject project = new DuplicateDependencies2ProjectStub();
        getContainer().addComponent(project, MavenProject.class.getName());

        MavenSession session = newMavenSession(project);
        getContainer().addComponent(session, MavenSession.class.getName());

        File testPom = new File(getBasedir(), "target/test-classes/unit/duplicate-dependencies/plugin-config2.xml");
        AnalyzeDuplicateMojo mojo = (AnalyzeDuplicateMojo) lookupMojo("analyze-duplicate", testPom);
        assertNotNull(mojo);
        DuplicateLog log = new DuplicateLog();
        mojo.setLog(log);
        mojo.execute();

        assertTrue(log.getContent()
                .contains("List of duplicate dependencies defined in <dependencyManagement/> in " + "your pom.xml"));
        assertTrue(log.getContent().contains("junit:junit:jar"));
    }

    static class DuplicateLog implements Log {
        StringBuilder sb = new StringBuilder();

        /** {@inheritDoc} */
        @Override
        public void debug(CharSequence content) {
            print("debug", content);
        }

        /** {@inheritDoc} */
        @Override
        public void debug(CharSequence content, Throwable error) {
            print("debug", content, error);
        }

        /** {@inheritDoc} */
        @Override
        public void debug(Throwable error) {
            print("debug", error);
        }

        /** {@inheritDoc} */
        @Override
        public void info(CharSequence content) {
            print("info", content);
        }

        /** {@inheritDoc} */
        @Override
        public void info(CharSequence content, Throwable error) {
            print("info", content, error);
        }

        /** {@inheritDoc} */
        @Override
        public void info(Throwable error) {
            print("info", error);
        }

        /** {@inheritDoc} */
        @Override
        public void warn(CharSequence content) {
            print("warn", content);
        }

        /** {@inheritDoc} */
        @Override
        public void warn(CharSequence content, Throwable error) {
            print("warn", content, error);
        }

        /** {@inheritDoc} */
        @Override
        public void warn(Throwable error) {
            print("warn", error);
        }

        /** {@inheritDoc} */
        @Override
        public void error(CharSequence content) {
            System.err.println("[error] " + content.toString());
        }

        /** {@inheritDoc} */
        @Override
        public void error(CharSequence content, Throwable error) {
            StringWriter sWriter = new StringWriter();
            PrintWriter pWriter = new PrintWriter(sWriter);

            error.printStackTrace(pWriter);

            System.err.println(
                    "[error] " + content.toString() + System.lineSeparator() + System.lineSeparator() + sWriter);
        }

        /**
         * @see org.apache.maven.plugin.logging.Log#error(java.lang.Throwable)
         */
        @Override
        public void error(Throwable error) {
            StringWriter sWriter = new StringWriter();
            PrintWriter pWriter = new PrintWriter(sWriter);

            error.printStackTrace(pWriter);

            System.err.println("[error] " + sWriter);
        }

        /**
         * @see org.apache.maven.plugin.logging.Log#isDebugEnabled()
         */
        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        /**
         * @see org.apache.maven.plugin.logging.Log#isInfoEnabled()
         */
        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        /**
         * @see org.apache.maven.plugin.logging.Log#isWarnEnabled()
         */
        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        /**
         * @see org.apache.maven.plugin.logging.Log#isErrorEnabled()
         */
        @Override
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

            sb.append("[").append(prefix).append("] ").append(sWriter).append(System.lineSeparator());
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
            sb.append(sWriter).append(System.lineSeparator());
        }

        protected String getContent() {
            return sb.toString();
        }
    }
}
