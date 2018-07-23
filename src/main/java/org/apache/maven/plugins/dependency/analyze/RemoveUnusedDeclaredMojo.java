package org.apache.maven.plugins.dependency.analyze;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.analyze.internal.CommandVerifier;
import org.apache.maven.plugins.dependency.analyze.internal.PomEditor;
import org.apache.maven.plugins.dependency.analyze.internal.PropertiesFactory;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Attempts to remove un-used dependencies.
 * <p>
 * You must have compiled your main and test sources before you run this using {@code mvn clean test-compile} or
 * similar.
 * <p>
 * Further more, this assumes that the project is correctly formed and will build when it starts. So you probably want
 * to run {@code mvn clean install}.
 *
 * @author <a href="mailto:alex@alexecollins.com">Alex Collins</a>
 * @since 3.1.2
 */
@Mojo(name = "remove-unused-declared", requiresDependencyResolution = ResolutionScope.TEST)
public class RemoveUnusedDeclaredMojo extends AbstractMojo implements Contextualizable {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    private Context context;

    @Parameter(defaultValue = "${basedir}", readonly = true)
    private File baseDir;

    @Parameter(property = "analyzer", defaultValue = "default")
    private String analyzer;

    /**
     * This command is run in the project's directory after every change to verify that the change worked OK.
     * <p>
     * Simply removing dependencies can break a module, for example:
     * <p>
     * - The dependency generates code than is referenced.
     * - The dependency is used by class-path scanning, and therefore not directly referenced.
     * <p>
     * This assumes that the command will catch the overwhelming majority of issues, but it quite possible for projects
     * to not have adaquate tests in place.
     */
    @Parameter(property = "command", defaultValue = "mvn -q clean install")
    private String command;

    /**
     * The indent used in your pom.xml.
     */
    @Parameter(property = "indent", defaultValue = "    ")
    private String indent;

    /**
     * Large projects typically use a {@code dependencyManagement} section in their pom.xml to managed dependencies.
     * When this is the case, you can set this to {@code true} and no {@code version} tags will be added to you pom.xml.
     */
    @Parameter(property = "dependencyManaged", defaultValue = "false")
    private boolean dependencyManaged;

    /**
     * The list of dependencies in the form of groupId:artifactId which should be removed.
     * Lower precedence than exclusion.
     */
    @Parameter(property = "include")
    private String include;

    /**
     * The list of dependencies in the form of groupId:artifactId which should NOT be removed.
     * Higher precedence than inclusion.
     */
    @Parameter(property = "exclude")
    private String exclude;

    /**
     * How to remove them.
     */
    @Parameter(property = "strategies", defaultValue = "all-at-once,one-by-one")
    private String strategies;

    private PomEditor editor;

    @Override
    public void execute() throws MojoExecutionException {

        String packaging = project.getPackaging();
        if (packaging.equals("pom")) {
            getLog().info("Skipping because packaging '" + packaging + "' is pom.");
            return;
        }

        try {
            Set<Artifact> unusedDeclaredArtifacts = filter(getAnalyzer().analyze(project).getUnusedDeclaredArtifacts());

            if (unusedDeclaredArtifacts.isEmpty()) {
                getLog().info("Skipping because nothing to do");
                return;
            }

            editor = new PomEditor(PropertiesFactory.getProperties(project), baseDir, indent, new CommandVerifier(getLog(), baseDir, command), dependencyManaged);

            if (strategies.contains("all-at-once")) {
                tryToRemoveAllUnusedDeclaredDependenciesAtOnce(unusedDeclaredArtifacts);
            }
            if (strategies.contains("one-by-one")) {
                tryToRemoveUnusedDeclaredDependenciesOneByOne(unusedDeclaredArtifacts);
            }

        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private Set<Artifact> filter(Set<Artifact> in) {
        Set<Artifact> out = new HashSet<>();

        for (Artifact artifact : in) {
            if (!exclude(artifact) && include(artifact)) {
                out.add(artifact);
            } else {
                getLog().info("x " + artifact);
            }
        }
        return out;
    }

    private boolean exclude(Artifact artifact) {
        if (exclude == null) {
            return false;
        }

        for (String coordinate : exclude.split(",")) {
            if (artifact.toString().contains(coordinate)) {
                return true;
            }
        }

        return false;
    }

    private boolean include(Artifact artifact) {
        if (include == null) {
            return true;
        }

        for (String coordinate : include.split(",")) {
            if (artifact.toString().contains(coordinate)) {
                return true;
            }
        }

        return false;
    }

    private ProjectDependencyAnalyzer getAnalyzer() throws ContextException, ComponentLookupException {
        return (ProjectDependencyAnalyzer) ((PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY))
                .lookup(ProjectDependencyAnalyzer.ROLE, analyzer);
    }

    private void tryToRemoveUnusedDeclaredDependenciesOneByOne(Set<Artifact> unusedDeclaredArtifacts) throws Exception {

        getLog().info("Removing " + unusedDeclaredArtifacts.size() + " unused declared dependencies one-by-one.");

        for (final Artifact artifact : unusedDeclaredArtifacts) {
            getLog().info("- " + artifact);
            editor.start();
            editor.removeDependency(artifact);
            try {
                editor.end();
            } catch (Exception ignored) {
                // noop
            }
        }
    }

    private void tryToRemoveAllUnusedDeclaredDependenciesAtOnce(Set<Artifact> unusedDeclaredArtifacts) throws Exception {

        getLog().info("Removing all " + unusedDeclaredArtifacts.size() + " unused declared dependencies all at once.");

        editor.start();
        for (final Artifact artifact : unusedDeclaredArtifacts) {
            getLog().info("- " + artifact);
            editor.removeDependency(artifact);

        }
        try {
            editor.end();
        } catch (Exception ignored) {
            // noop
        }
    }

    @Override
    public void contextualize(Context context) {
        this.context = context;
    }
}
