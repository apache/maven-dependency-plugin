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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.utils.ParamArtifact;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.DependencyResolutionException;

/**
 * Resolves a single artifact, eventually transitively, from the specified remote repositories. Caveat: will always
 * check the central repository defined in the super pom. You could use a mirror entry in your <code>settings.xml</code>
 */
@Mojo(name = "get", requiresProject = false, threadSafe = true)
public class GetMojo extends AbstractMojo {

    private final ResolverUtil resolverUtil;

    private final ParamArtifact paramArtifact = new ParamArtifact();

    /**
     * Repositories in the format {@code id::[layout::]url} or just URLs, separated by comma. That is,
     * <code>
     * central::default::https://repo.maven.apache.org/maven2,myrepo::https://repo.acme.com,https://repo.acme2.com
     * </code>
     * <p>
     * The comma-separated form works both on the command line and as the text of a single
     * <code>&lt;remoteRepositories&gt;</code> element. In a POM the repositories may also be listed one per
     * element:
     * <pre>
     * &lt;remoteRepositories&gt;
     *   &lt;remoteRepository&gt;central::default::https://repo.maven.apache.org/maven2&lt;/remoteRepository&gt;
     *   &lt;remoteRepository&gt;https://repo.acme.com&lt;/remoteRepository&gt;
     * &lt;/remoteRepositories&gt;
     * </pre>
     */
    @Parameter(property = "remoteRepositories")
    private List<String> remoteRepositories;

    /**
     * Resolve transitively, retrieving the specified artifact and all of its dependencies.
     */
    @Parameter(property = "transitive", defaultValue = "true")
    private boolean transitive = true;

    /**
     * Skip plugin execution completely.
     *
     * @since 2.7
     */
    @Parameter(property = "mdep.skip", defaultValue = "false")
    private boolean skip;

    @Inject
    public GetMojo(ResolverUtil resolverUtil) {
        this.resolverUtil = resolverUtil;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (isSkip()) {
            getLog().info("Skipping plugin execution");
            return;
        }

        if (!paramArtifact.isDataSet()) {
            throw new MojoFailureException("You must specify an artifact OR GAV separately, "
                    + "e.g. -Dartifact=org.apache.maven.plugins:maven-downloader-plugin:1.0 OR "
                    + "-DgroupId=org.apache.maven.plugins -DartifactId=maven-downloader-plugin -Dversion=1.0");
        }

        Artifact artifact;
        List<RemoteRepository> repositories;
        try {
            artifact = resolverUtil.createArtifactFromParams(paramArtifact);
            repositories = resolverUtil.remoteRepositories(remoteRepositories);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }

        try {
            if (transitive) {
                getLog().info("Resolving " + artifact + " with transitive dependencies");
                resolverUtil.resolveDependencies(artifact, repositories);
            } else {
                getLog().info("Resolving " + artifact);
                resolverUtil.resolveArtifact(artifact, repositories);
            }
        } catch (ArtifactResolutionException | ArtifactDescriptorException | DependencyResolutionException e) {
            throw new MojoExecutionException("Couldn't download artifact: " + e.getMessage(), e);
        }
    }

    /**
     * @return {@link #skip}
     */
    protected boolean isSkip() {
        return skip;
    }

    /**
     * The groupId of the artifact to resolve. Ignored if {@code artifact} is used.
     *
     * @param groupId the groupId
     */
    @Parameter(property = "groupId")
    public void setGroupId(String groupId) {
        paramArtifact.setGroupId(groupId);
    }

    /**
     * The artifactId of the artifact to resolve. Ignored if {@code artifact} is used.
     *
     * @param artifactId the artifactId
     */
    @Parameter(property = "artifactId")
    public void setArtifactId(String artifactId) {
        paramArtifact.setArtifactId(artifactId);
    }

    /**
     * The version of the artifact to resolve. Ignored if {@code artifact} is used.
     *
     * @param version the version
     */
    @Parameter(property = "version")
    public void setVersion(String version) {
        paramArtifact.setVersion(version);
    }

    /**
     * The classifier of the artifact to resolve. Ignored if {@code artifact} is used.
     *
     * @param classifier the classifier to be used
     * @since 2.3
     */
    @Parameter(property = "classifier")
    public void setClassifier(String classifier) {
        paramArtifact.setClassifier(classifier);
    }

    /**
     * The packaging of the artifact to resolve. Ignored if {@code artifact} is used.
     *
     * @param type packaging
     */
    @Parameter(property = "packaging", defaultValue = "jar")
    public void setPackaging(String type) {
        paramArtifact.setPackaging(type);
    }

    /**
     * A string of the form groupId:artifactId:version[:packaging[:classifier]].
     *
     * @param artifact the artifact coordinates
     */
    @Parameter(property = "artifact")
    public void setArtifact(String artifact) {
        paramArtifact.setArtifact(artifact);
    }
}
