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

import jakarta.json.spi.JsonProvider;
import org.apache.johnzon.core.JsonProviderImpl;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.ResolverExpressionEvaluatorStub;
import org.apache.maven.plugins.dependency.analyze.main.JsonpMain;
import org.apache.maven.plugins.dependency.analyze.main.Slf4jMain;
import org.apache.maven.plugins.dependency.testUtils.TestLog;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.DefaultClassAnalyzer;
import org.apache.maven.shared.dependency.analyzer.DefaultProjectDependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.asm.ASMDependencyAnalyzer;
import org.codehaus.plexus.component.configurator.BasicComponentConfigurator;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.tomitribe.util.JarLocation.jarLocation;

class TestAnalyzeIndirectDependency {
    @Test
    void jakartaJavaxEE(@TempDir final Path work) throws Exception {
        final String glassfishVersion = System.getProperty("glassfishVersion", "2.0.1");
        final String johnzonVersion = System.getProperty("johnzonVersion", "2.0.1");

        assertEquals(
                "[info] No dependency problems found",
                exec(
                        work,
                        base -> {
                            final String resource = JsonpMain.class.getName().replace('.', '/') + ".class";
                            final Path target = base.resolve(resource);
                            Files.createDirectories(target.getParent());
                            try (final InputStream is = Thread.currentThread()
                                    .getContextClassLoader()
                                    .getResourceAsStream(resource)) {
                                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                        },
                        artifact("org.glassfish", "jakarta.json", glassfishVersion, JsonProvider.class),
                        artifact("org.apache.johnzon", "johnzon-core", johnzonVersion, JsonProviderImpl.class)));
    }

    /* Ensure we do not have
    [warn] Unused declared dependencies found:
    [warn]    org.slf4j:slf4j-simple:jar:1.7.36:compile
     */
    @Test
    void slf4j(@TempDir final Path work) throws Exception {
        final String slf4jVersion = System.getProperty("slf4jVersion", "1.7.36");
        assertEquals(
                "[info] No dependency problems found",
                exec(
                        work,
                        base -> {
                            final String resource = Slf4jMain.class.getName().replace('.', '/') + ".class";
                            final Path target = base.resolve(resource);
                            Files.createDirectories(target.getParent());
                            try (final InputStream is = Thread.currentThread()
                                    .getContextClassLoader()
                                    .getResourceAsStream(resource)) {
                                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                        },
                        artifact("org.slf4j", "slf4j-api", slf4jVersion, LoggerFactory.class),
                        artifact("org.slf4j", "slf4j-simple", slf4jVersion, SimpleLoggerFactory.class)));
    }

    private String exec(final Path work, final IOConsumer<Path> classesFiller, final Artifact... artifacts) throws Exception {
        final Path classes = Files.createDirectories(work.resolve("target/classes"));
        final Build build = new Build();
        build.setOutputDirectory(classes.toString());
        build.setTestOutputDirectory(Files.createDirectories(work.resolve("target/test-classes")).toString());

        final MavenProject project = new MavenProject();
        project.setGroupId("g");
        project.setArtifactId("test");
        project.setVersion("build");
        project.setBuild(build);
        project.setArtifacts(new HashSet<>(asList(artifacts)));
        project.setDependencyArtifacts(project.getArtifacts());
        classesFiller.accept(classes);

        final Log log = new TestLog();

        final DefaultProjectDependencyAnalyzer analyzer = new DefaultProjectDependencyAnalyzer();
        final Field classAnalyzer = DefaultProjectDependencyAnalyzer.class.getDeclaredField("classAnalyzer");
        classAnalyzer.setAccessible(true);
        classAnalyzer.set(analyzer, new DefaultClassAnalyzer());
        final Field dependencyAnalyzer = DefaultProjectDependencyAnalyzer.class.getDeclaredField("dependencyAnalyzer");
        dependencyAnalyzer.setAccessible(true);
        dependencyAnalyzer.set(analyzer, new ASMDependencyAnalyzer());

        final AnalyzeMojo mojo = new AnalyzeMojo(null, project) {
            @Override
            protected ProjectDependencyAnalyzer createProjectDependencyAnalyzer() throws MojoExecutionException {
                return analyzer;
            }

            @Override
            public Log getLog() {
                return log;
            }
        };

        final ExpressionEvaluator evaluator = new ResolverExpressionEvaluatorStub();

        final DefaultPlexusConfiguration configuration = new DefaultPlexusConfiguration("configuration");
        configuration.addChild("ignoredPackagings", "pom");
        configuration.addChild("outputDirectory", project.getBuild().getOutputDirectory());

        new BasicComponentConfigurator()
                .configureComponent(mojo, configuration, evaluator, null);

        mojo.execute();

        return log.toString().trim();
    }

    private DefaultArtifact artifact(final String groupId, final String artifactId, final String slf4jVersion, final Class<?> marker) {
        final DefaultArtifact artifact = new DefaultArtifact(
                groupId, artifactId, slf4jVersion,
                "compile", "jar", "", new DefaultArtifactHandler());
        artifact.setFile(jarLocation(marker));
        return artifact;
    }

    private interface IOConsumer<T> {
        void accept(T arg) throws IOException;
    }
}
