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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.transfer.artifact.ArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;

/**
 * Resolves a single artifact, eventually transitively, from the specified remote repositories. Caveat: will always
 * check the central repository defined in the super pom. You could use a mirror entry in your <code>settings.xml</code>
 */
@Mojo(name = "get", requiresProject = false, threadSafe = true)
public class GetMojo extends AbstractMojo {
    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.*)::(.+)");

    private final MavenSession session;

    private final ArtifactResolver artifactResolver;

    private final DependencyResolver dependencyResolver;

    private final ArtifactHandlerManager artifactHandlerManager;

    /**
     * Map that contains the layouts.
     */
    private final Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    /**
     * The repository system.
     */
    private final RepositorySystem repositorySystem;

    private final DefaultDependableCoordinate coordinate = new DefaultDependableCoordinate();

    /**
     * Repositories in the format id::[layout]::url or just url, separated by comma. ie.
     * central::default::https://repo.maven.apache.org/maven2,myrepo::::https://repo.acme.com,https://repo.acme2.com
     */
    @Parameter(property = "remoteRepositories")
    private String remoteRepositories;

    /**
     * A string of the form groupId:artifactId:version[:packaging[:classifier]].
     */
    @Parameter(property = "artifact")
    private String artifact;

    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true)
    private List<ArtifactRepository> pomRemoteRepositories;

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
    public GetMojo(
            MavenSession session,
            ArtifactResolver artifactResolver,
            DependencyResolver dependencyResolver,
            ArtifactHandlerManager artifactHandlerManager,
            Map<String, ArtifactRepositoryLayout> repositoryLayouts,
            RepositorySystem repositorySystem) {
        this.session = session;
        this.artifactResolver = artifactResolver;
        this.dependencyResolver = dependencyResolver;
        this.artifactHandlerManager = artifactHandlerManager;
        this.repositoryLayouts = repositoryLayouts;
        this.repositorySystem = repositorySystem;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (isSkip()) {
            getLog().info("Skipping plugin execution");
            return;
        }

        if (coordinate.getArtifactId() == null && artifact == null) {
            throw new MojoFailureException("You must specify an artifact, "
                    + "e.g. -Dartifact=org.apache.maven.plugins:maven-downloader-plugin:1.0");
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
                coordinate.setType(tokens[3]);
            }
            if (tokens.length == 5) {
                coordinate.setClassifier(tokens[4]);
            }
        }

        ArtifactRepositoryPolicy always = new ArtifactRepositoryPolicy(
                true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);

        List<ArtifactRepository> repoList = new ArrayList<>();

        if (pomRemoteRepositories != null) {
            repoList.addAll(pomRemoteRepositories);
        }

        if (remoteRepositories != null) {
            // Use the same format as in the deploy plugin id::layout::url
            String[] repos = remoteRepositories.split(",");
            for (String repo : repos) {
                repoList.add(parseRepository(repo, always));
            }
        }

        try {
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

            Settings settings = session.getSettings();
            repositorySystem.injectMirror(repoList, settings.getMirrors());
            repositorySystem.injectProxy(repoList, settings.getProxies());
            repositorySystem.injectAuthentication(repoList, settings.getServers());

            buildingRequest.setRemoteRepositories(repoList);

            if (transitive) {
                getLog().info("Resolving " + coordinate + " with transitive dependencies");
                dependencyResolver.resolveDependencies(buildingRequest, coordinate, null);
            } else {
                getLog().info("Resolving " + coordinate);
                artifactResolver.resolveArtifact(buildingRequest, toArtifactCoordinate(coordinate));
            }
        } catch (ArtifactResolverException | DependencyResolverException e) {
            throw new MojoExecutionException("Couldn't download artifact: " + e.getMessage(), e);
        }
    }

    private ArtifactCoordinate toArtifactCoordinate(DependableCoordinate dependableCoordinate) {
        ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler(dependableCoordinate.getType());
        DefaultArtifactCoordinate artifactCoordinate = new DefaultArtifactCoordinate();
        artifactCoordinate.setGroupId(dependableCoordinate.getGroupId());
        artifactCoordinate.setArtifactId(dependableCoordinate.getArtifactId());
        artifactCoordinate.setVersion(dependableCoordinate.getVersion());
        artifactCoordinate.setClassifier(dependableCoordinate.getClassifier());
        artifactCoordinate.setExtension(artifactHandler.getExtension());
        return artifactCoordinate;
    }

    ArtifactRepository parseRepository(String repo, ArtifactRepositoryPolicy policy) throws MojoFailureException {
        // if it's a simple url
        String id = "temp";
        ArtifactRepositoryLayout layout = getLayout("default");
        String url = repo;

        // if it's an extended repo URL of the form id::layout::url
        if (repo.contains("::")) {
            Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(repo);
            if (!matcher.matches()) {
                throw new MojoFailureException(
                        repo,
                        "Invalid syntax for repository: " + repo,
                        "Invalid syntax for repository. Use \"id::layout::url\" or \"URL\".");
            }

            id = matcher.group(1).trim();
            if (matcher.group(2) != null && !matcher.group(2).isEmpty()) {
                layout = getLayout(matcher.group(2).trim());
            }
            url = matcher.group(3).trim();
        }
        return new MavenArtifactRepository(id, url, layout, policy, policy);
    }

    private ArtifactRepositoryLayout getLayout(String id) throws MojoFailureException {
        ArtifactRepositoryLayout layout = repositoryLayouts.get(id);

        if (layout == null) {
            throw new MojoFailureException(id, "Invalid repository layout", "Invalid repository layout: " + id);
        }

        return layout;
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
     * @param groupId The groupId.
     */
    @Parameter(property = "groupId")
    public void setGroupId(String groupId) {
        this.coordinate.setGroupId(groupId);
    }

    /**
     * The artifactId of the artifact to resolve. Ignored if {@link #artifact} is used.
     *
     * @param artifactId The artifactId.
     */
    @Parameter(property = "artifactId")
    public void setArtifactId(String artifactId) {
        this.coordinate.setArtifactId(artifactId);
    }

    /**
     * The version of the artifact to resolve. Ignored if {@link #artifact} is used.
     *
     * @param version The version.
     */
    @Parameter(property = "version")
    public void setVersion(String version) {
        this.coordinate.setVersion(version);
    }

    /**
     * The classifier of the artifact to resolve. Ignored if {@link #artifact} is used.
     *
     * @param classifier The classifier to be used.
     *
     * @since 2.3
     */
    @Parameter(property = "classifier")
    public void setClassifier(String classifier) {
        this.coordinate.setClassifier(classifier);
    }

    /**
     * The packaging of the artifact to resolve. Ignored if {@link #artifact} is used.
     *
     * @param type packaging.
     */
    @Parameter(property = "packaging", defaultValue = "jar")
    public void setPackaging(String type) {
        this.coordinate.setType(type);
    }
}
