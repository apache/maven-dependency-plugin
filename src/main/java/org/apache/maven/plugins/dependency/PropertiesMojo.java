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

import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.utils.ParamArtifact;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.resolution.ArtifactResolutionException;

/**
 * Goal that sets a property pointing to the artifact file for each project dependency. For each dependency (direct and
 * transitive) a project property will be set which follows the <code>groupId:artifactId:type:[classifier]</code> form
 * and contains the path to the resolved artifact.
 *
 * @author Paul Gier
 * @since 2.2
 */
// CHECKSTYLE_OFF: LineLength
@Mojo(
        name = "properties",
        requiresDependencyResolution = ResolutionScope.TEST,
        defaultPhase = LifecyclePhase.INITIALIZE,
        threadSafe = true)
// CHECKSTYLE_ON: LineLength
public class PropertiesMojo extends AbstractMojo {

    /**
     * The current Maven project.
     */
    private final MavenProject project;

    private final ResolverUtil resolverUtil;
    
    @Inject
    public PropertiesMojo(MavenProject project, ResolverUtil resolverUtil) {
        this.project = project;
        this.resolverUtil = resolverUtil;
    }

    /**
     * Skip plugin execution completely.
     *
     * @since 2.7
     */
    @Parameter(property = "mdep.skip", defaultValue = "false")
    private boolean skip;
    
    /**
     * Extra artifacts that can be provided to the plugin. For each artifact in this list a property will be set
     * similar to the project artifacts discovered. This allows callers to supply additional
     * resolved dependencies for which properties pointing to the artifact file shall be created.
     *
     * Example usage in plugin configuration:
     * <pre>
     * &lt;extraArtifacts&gt;
     *   &lt;extraArtifact&gt;
     *      &lt;artifact&gt;org.example:my-artifact:jar:1.0&lt;/artifact&gt;
     *   &lt;/extraArtifact&gt;
     *   &lt;extraArtifact&gt;
     *      &lt;groupId&gt;org.example&lt;/groupId&gt;
     *      &lt;artifactId&gt;my-artifact&lt;/artifactId&gt;
     *      &lt;version&gt;1.0&lt;/version&gt;
     *   &lt;/extraArtifact&gt;
     * &lt;/extraArtifacts&gt;
     * </pre>
     * @since 3.10.1
     */
    @Parameter
    private List<ParamArtifact> extraArtifacts;

    /**
     * Main entry into mojo. Gets the list of dependencies and iterates through setting a property for each artifact.
     * Gets the list of declared plugin's extra artifacts and iterates through setting a property for each artifact.
     * @throws MojoExecutionException with a message if an error occurs
     */
    @Override
    public void execute() throws MojoExecutionException {
        if (isSkip()) {
            getLog().info("Skipping plugin execution");
            return;
        }

        Set<Artifact> artifacts = project.getArtifacts();

        for (Artifact artifact : artifacts) {
            project.getProperties()
                    .setProperty(
                            artifact.getDependencyConflictId(),
                            artifact.getFile().getAbsolutePath());
        }

        if (extraArtifacts != null) {
            try {
                for (ParamArtifact paramArtifact : extraArtifacts) {

                    if (!paramArtifact.isDataSet()) {
                        throw new MojoExecutionException("You must specify an artifact OR GAV separately");
                    }

                    org.eclipse.aether.artifact.Artifact artifact = resolverUtil.createArtifactFromParams(paramArtifact);
                    artifact = resolverUtil.resolveArtifact(artifact, project.getRemoteProjectRepositories());

                    this.project
                            .getProperties()
                            .setProperty(
                                    toConflictId(artifact),
                                    artifact.getFile().getAbsolutePath());
                }
            } catch (ArtifactResolutionException e) {
                throw new MojoExecutionException("Couldn't download artifact: " + e.getMessage(), e);
            }
        }
    }

    /**
     * @return {@link #skip}
     */
    public boolean isSkip() {
        return skip;
    }

    private String toConflictId(org.eclipse.aether.artifact.Artifact artifact) {
        // The conflict ID is the same as the one used by Maven's Artifact class, which is groupId:artifactId:type[:classifier]
        StringBuilder sb = new StringBuilder();
        sb.append(artifact.getGroupId()).append(":").append(artifact.getArtifactId()).append(":").append(artifact.getExtension());
        if (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) {
            sb.append(":").append(artifact.getClassifier());
        }
        return sb.toString();
    }
}
