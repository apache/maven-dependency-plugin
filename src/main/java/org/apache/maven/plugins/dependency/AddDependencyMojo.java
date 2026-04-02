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
import eu.maveniverse.domtrip.Element;
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
 * Adds or updates a dependency in the project's {@code pom.xml}.
 * Uses DomTrip for lossless XML editing that preserves formatting, comments, and whitespace.
 *
 * <p>If the dependency already exists (matched by groupId, artifactId, type, and classifier),
 * its version is updated. If the version is a property reference ({@code ${...}}), the property
 * value is updated instead.</p>
 *
 * <p>Examples:</p>
 * <pre>
 * mvn dependency:add -Dgav=com.google.guava:guava:33.0.0-jre
 * mvn dependency:add -Dgav=com.google.guava:guava:33.0.0-jre -Dscope=compile
 * mvn dependency:add -Dgav=com.google.guava:guava:33.0.0-jre -Dmanaged
 * mvn dependency:add -Dgav=com.google.guava:guava:33.0.0-jre -DuseProperty
 * mvn dependency:add -Dgav=com.google.guava:guava:33.0.0-jre -DpropertyName=guava.version
 * </pre>
 *
 * @since 3.11.0
 */
@Mojo(name = "add", requiresProject = true, threadSafe = true)
public class AddDependencyMojo extends AbstractDependencyMojo {

    /**
     * The dependency coordinates: {@code groupId:artifactId[:version[:classifier[:type]]]}.
     */
    @Parameter(property = "gav", required = true)
    private String gav;

    /**
     * When {@code true}, add to {@code <dependencyManagement>} instead of {@code <dependencies>}.
     */
    @Parameter(property = "managed", defaultValue = "false")
    private boolean managed;

    /**
     * The dependency scope (e.g., {@code compile}, {@code test}, {@code provided}, {@code runtime}, {@code system}).
     * If not specified, no {@code <scope>} element is added (Maven defaults to {@code compile}).
     */
    @Parameter(property = "scope")
    private String scope;

    /**
     * When {@code true}, store the version in a property (e.g., {@code ${artifactId.version}})
     * and reference it from the dependency's {@code <version>} element.
     * The property name is derived from the artifactId unless {@link #propertyName} is set.
     */
    @Parameter(property = "useProperty", defaultValue = "false")
    private boolean useProperty;

    /**
     * Explicit property name to use for the version (implies {@code useProperty=true}).
     * For example, {@code -DpropertyName=guava.version} creates {@code <guava.version>33.0.0-jre</guava.version>}
     * in {@code <properties>} and sets the dependency version to {@code ${guava.version}}.
     */
    @Parameter(property = "propertyName")
    private String propertyName;

    @Inject
    public AddDependencyMojo(MavenSession session, BuildContext buildContext, MavenProject project) {
        super(session, buildContext, project);
    }

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        Coordinates coords = parseCoordinates(gav);
        File pomFile = getProject().getFile();

        boolean wantProperty = useProperty || propertyName != null;
        if (wantProperty && (coords.version() == null || coords.version().isEmpty())) {
            throw new MojoFailureException("Version is required when using property-based versioning");
        }

        try {
            Document doc = Document.of(pomFile.toPath());
            PomEditor editor = new PomEditor(doc);

            Coordinates effectiveCoords = coords;
            if (wantProperty) {
                String propName = propertyName != null ? propertyName : coords.artifactId() + ".version";
                // Use property reference as the version in the dependency element
                effectiveCoords = Coordinates.of(
                        coords.groupId(),
                        coords.artifactId(),
                        "${" + propName + "}",
                        coords.classifier(),
                        coords.type());
                // Create or update the property with the actual version value
                editor.properties().updateProperty(true, propName, coords.version());
            }

            boolean changed;
            if (managed) {
                changed = editor.dependencies().updateManagedDependency(true, effectiveCoords);
            } else {
                changed = editor.dependencies().updateDependency(true, effectiveCoords);
            }

            if (scope != null && !scope.isEmpty()) {
                setDependencyScope(editor, effectiveCoords);
                changed = true;
            }

            if (changed || wantProperty) {
                try (OutputStream os = Files.newOutputStream(pomFile.toPath())) {
                    doc.toXml(os);
                }
                String section = managed ? "<dependencyManagement>" : "<dependencies>";
                getLog().info("Added/updated " + coords.toGAV() + " in " + section);
            } else {
                getLog().info("No changes needed for " + coords.toGAV());
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to modify POM file: " + pomFile, e);
        }
    }

    private void setDependencyScope(PomEditor editor, Coordinates coords) {
        Element root = editor.document().root();
        Element depsContainer;
        if (managed) {
            Element dm = editor.findChildElement(root, "dependencyManagement");
            depsContainer = dm != null ? editor.findChildElement(dm, "dependencies") : null;
        } else {
            depsContainer = editor.findChildElement(root, "dependencies");
        }
        if (depsContainer != null) {
            depsContainer
                    .childElements("dependency")
                    .filter(coords.predicateGATC())
                    .findFirst()
                    .ifPresent(dep -> {
                        editor.updateOrCreateChildElement(dep, "scope", scope);
                    });
        }
    }

    static Coordinates parseCoordinates(String gav) throws MojoFailureException {
        if (gav == null || gav.trim().isEmpty()) {
            throw new MojoFailureException("GAV must not be empty. Use -Dgav=groupId:artifactId[:version]");
        }
        String[] parts = gav.split(":");
        if (parts.length < 2 || parts.length > 5) {
            throw new MojoFailureException(
                    "Invalid GAV format: '" + gav + "'. Expected groupId:artifactId[:version[:classifier[:type]]]");
        }
        String groupId = parts[0].trim();
        String artifactId = parts[1].trim();
        String version = parts.length >= 3 ? parts[2].trim() : null;
        String classifier = parts.length >= 4 ? parts[3].trim() : null;
        String type = parts.length >= 5 ? parts[4].trim() : null;
        if (groupId.isEmpty() || artifactId.isEmpty()) {
            throw new MojoFailureException("groupId and artifactId must not be empty");
        }
        if (version != null && version.isEmpty()) {
            version = null;
        }
        if (classifier != null && classifier.isEmpty()) {
            classifier = null;
        }
        if (type != null && type.isEmpty()) {
            type = null;
        }
        return Coordinates.of(groupId, artifactId, version, classifier, type != null ? type : "jar");
    }
}
