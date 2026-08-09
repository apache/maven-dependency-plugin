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

import java.util.Arrays;
import java.util.Collections;
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
import org.eclipse.aether.repository.RepositoryPolicy;
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

    private final ParamArtifact coordinate = new ParamArtifact();

    /**
     * Repositories in the format {@code id::[layout::]url} or just URLs, separated by comma. That is,
     * {@code central::default::https://repo.maven.apache.org/maven2,myrepo::https://repo.acme.com,https://repo.acme2.com}.
     */
    @Parameter(property = "remoteRepositories")
    private String remoteRepositories;

    /**
     * A string of the form groupId:artifactId:version[:packaging[:classifier]].
     */
    @Parameter(property = "artifact")
    private String artifact;

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

        if (artifact != null) {
            String[] tokens = artifact.split(":");
            if (tokens.length < 3 || tokens.length > 5) {
                throw new MojoFailureException("Invalid artifact, you must specify "
                        + "groupId:artifactId:version[:packaging[:classifier]] " + artifact);
            }
            coordinate.setGroupId(tokens[0]);
            coordinate.setArtifactId(tokens[1]);
            coordinate.setVersion(tokens[2]);
            if (tokens.length >= 4) {
                coordinate.setPackaging(tokens[3]);
            }
            if (tokens.length == 5) {
                coordinate.setClassifier(tokens[4]);
            }
        }

        if (!coordinate.isDataSet()) {
            throw new MojoFailureException("You must specify an artifact, "
                    + "e.g. -Dartifact=org.apache.maven.plugins:maven-downloader-plugin:1.0");
        }

        List<RemoteRepository> resolverRepositories;
        try {
            resolverRepositories = resolverUtil.remoteRepositories(
                    remoteRepositories == null ? Collections.emptyList() : Arrays.asList(remoteRepositories.split(",")),
                    RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException("Invalid remote repository: " + e.getMessage(), e);
        }

        try {
            Artifact targetArtifact = resolverUtil.createArtifactFromParams(coordinate);

            if (transitive) {
                getLog().info("Resolving " + targetArtifact + " with transitive dependencies");
                resolverUtil.resolveDependencies(targetArtifact, resolverRepositories);
            } else {
                getLog().info("Resolving " + targetArtifact);
                resolverUtil.resolveArtifact(targetArtifact, resolverRepositories);
            }
        } catch (ArtifactDescriptorException | ArtifactResolutionException | DependencyResolutionException e) {
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
     * The groupId of the artifact to resolve. Ignored if {@link #artifact} is used.
     *
     * @param groupId the groupId
     */
    @Parameter(property = "groupId")
    public void setGroupId(String groupId) {
        this.coordinate.setGroupId(groupId);
    }

    /**
     * The artifactId of the artifact to resolve. Ignored if {@link #artifact} is used.
     *
     * @param artifactId the artifactId
     */
    @Parameter(property = "artifactId")
    public void setArtifactId(String artifactId) {
        this.coordinate.setArtifactId(artifactId);
    }

    /**
     * The version of the artifact to resolve. Ignored if {@link #artifact} is used.
     *
     * @param version the version
     */
    @Parameter(property = "version")
    public void setVersion(String version) {
        this.coordinate.setVersion(version);
    }

    /**
     * The classifier of the artifact to resolve. When {@link #artifact} is used, this value supplies the classifier if
     * it is omitted from the artifact string.
     *
     * @param classifier the classifier to be used
     * @since 2.3
     */
    @Parameter(property = "classifier")
    public void setClassifier(String classifier) {
        this.coordinate.setClassifier(classifier);
    }

    /**
     * The packaging of the artifact to resolve. When {@link #artifact} is used, this value supplies the packaging if it
     * is omitted from the artifact string.
     *
     * @param type packaging
     */
    @Parameter(property = "packaging", defaultValue = "jar")
    public void setPackaging(String type) {
        this.coordinate.setPackaging(type);
    }
}
