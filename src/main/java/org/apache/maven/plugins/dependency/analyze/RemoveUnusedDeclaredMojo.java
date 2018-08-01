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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.analyze.internal.CommandVerifier;
import org.apache.maven.plugins.dependency.analyze.internal.PomEditor;

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
public class RemoveUnusedDeclaredMojo extends AbstractFixDependenciesMojo {

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
     * How to remove them.
     */
    @Parameter(property = "strategies", defaultValue = "all-at-once,one-by-one")
    private String strategies;

    @Override
    public void execute() throws MojoExecutionException {

        String packaging = project.getPackaging();
        if (packaging.equals("pom")) {
            getLog().info("Skipping because packaging '" + packaging + "' is pom.");
            return;
        }

        try {
            Set<Artifact> unusedDeclaredArtifacts = filter(getAnalysis().getUnusedDeclaredArtifacts());

            if (unusedDeclaredArtifacts.isEmpty()) {
                getLog().info("Skipping because nothing to do");
                return;
            }

            PomEditor editor = getEditor(new CommandVerifier(getLog(), baseDir, command));

            if (strategies.contains("all-at-once")) {
                tryToRemoveAllUnusedDeclaredDependenciesAtOnce(editor, unusedDeclaredArtifacts);
            }
            if (strategies.contains("one-by-one")) {
                tryToRemoveUnusedDeclaredDependenciesOneByOne(editor, unusedDeclaredArtifacts);
            }

        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }


    private void tryToRemoveUnusedDeclaredDependenciesOneByOne(PomEditor editor, Set<Artifact> unusedDeclaredArtifacts) throws Exception {

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

    private void tryToRemoveAllUnusedDeclaredDependenciesAtOnce(PomEditor editor, Set<Artifact> unusedDeclaredArtifacts) throws Exception {

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

}
