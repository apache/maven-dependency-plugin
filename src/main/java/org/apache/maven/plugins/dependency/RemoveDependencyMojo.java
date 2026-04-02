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
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

import eu.maveniverse.domtrip.Document;
import eu.maveniverse.domtrip.maven.Coordinates;
import eu.maveniverse.domtrip.maven.PomEditor;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Removes a dependency from the project's {@code pom.xml}.
 * Uses DomTrip for lossless XML editing that preserves formatting, comments, and whitespace.
 *
 * @since 3.11.0
 */
@Mojo(name = "remove", requiresProject = true, threadSafe = true)
public class RemoveDependencyMojo extends AbstractDependencyMojo {

    /**
     * The dependency GAV coordinates: {@code groupId:artifactId[:version[:classifier[:type]]]}.
     * Only groupId and artifactId are used for matching.
     */
    @Parameter(property = "gav", required = true)
    private String gav;

    /**
     * When {@code true}, remove from {@code <dependencyManagement>} instead of {@code <dependencies>}.
     */
    @Parameter(property = "managed", defaultValue = "false")
    private boolean managed;

    @Inject
    public RemoveDependencyMojo(MavenSession session, BuildContext buildContext, MavenProject project) {
        super(session, buildContext, project);
    }

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        Coordinates coords = AddDependencyMojo.parseCoordinates(gav);
        File pomFile = getProject().getFile();

        try {
            Document doc = Document.of(pomFile.toPath());
            PomEditor editor = new PomEditor(doc);

            boolean removed;
            if (managed) {
                removed = editor.dependencies().deleteManagedDependency(coords);
            } else {
                removed = editor.dependencies().deleteDependency(coords);
            }

            if (removed) {
                try (OutputStream os = Files.newOutputStream(pomFile.toPath())) {
                    doc.toXml(os);
                }
                String section = managed ? "<dependencyManagement>" : "<dependencies>";
                getLog().info("Removed " + coords.toGA() + " from " + section);
            } else {
                String section = managed ? "<dependencyManagement>" : "<dependencies>";
                throw new MojoFailureException("Dependency " + coords.toGA() + " not found in " + section + ".");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to modify POM file: " + pomFile, e);
        }
    }
}
