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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecution.Source;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.resolve.AbstractFilter;
import org.apache.maven.shared.artifact.filter.resolve.AndFilter;
import org.apache.maven.shared.artifact.filter.resolve.Node;
import org.apache.maven.shared.artifact.filter.resolve.PatternExclusionsFilter;
import org.apache.maven.shared.artifact.filter.resolve.PatternInclusionsFilter;
import org.apache.maven.shared.artifact.filter.resolve.ScopeFilter;
import org.apache.maven.shared.artifact.filter.resolve.TransformableFilter;
import org.apache.maven.shared.artifact.filter.resolve.transform.ArtifactIncludeFilterTransformer;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.TransferUtils;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;
import org.apache.maven.shared.utils.logging.MessageBuilder;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.codehaus.plexus.util.FileUtils;

/**
 * When run on a project, remove the project dependencies from the local repository, and optionally re-resolve them.
 * Outside of a project, remove the manually given dependencies.
 *
 * @author jdcasey
 * @since 2.0
 */
@Mojo(name = "purge-local-repository", threadSafe = true, requiresProject = false)
public class PurgeLocalRepositoryMojo extends AbstractMojo {

    private static final String VERSION_FUZZINESS = "version";

    private static final String ARTIFACT_ID_FUZZINESS = "artifactId";

    private static final String GROUP_ID_FUZZINESS = "groupId";

    /**
     * The current Maven project.
     */
    private final MavenProject project;

    private final MavenSession session;

    /**
     * Artifact handler manager.
     */
    private final ArtifactHandlerManager artifactHandlerManager;

    /**
     * The dependency resolver
     */
    private final DependencyResolver dependencyResolver;

    /**
     * The artifact resolver used to re-resolve dependencies, if that option is enabled.
     */
    private final ArtifactResolver artifactResolver;

    /**
     * The Maven projects in the reactor.
     */
    @Parameter(defaultValue = "${reactorProjects}", readonly = true, required = true)
    private List<MavenProject> reactorProjects;

    /**
     * This mojo execution, used to determine if it was launched from the lifecycle or the command-line.
     */
    @Parameter(defaultValue = "${mojo}", required = true, readonly = true)
    private MojoExecution mojoExecution;

    /**
     * The list of dependencies in the form of groupId:artifactId which should BE deleted/purged from the local
     * repository. Note that using this parameter will deactivate the normal process for purging the current project
     * dependency tree. If this parameter is used, only the included artifacts will be purged. The manualIncludes
     * parameter should not be used in combination with the includes/excludes parameters.
     *
     * @since 2.6
     */
    @Parameter
    private List<String> manualIncludes;

    /**
     * Comma-separated list of groupId:artifactId entries, which should be used to manually include artifacts for
     * deletion. This is a command-line alternative to the <code>manualIncludes</code> parameter, since List parameters
     * are not currently compatible with CLI specification.
     *
     * @since 2.6
     */
    @Parameter(property = "manualInclude")
    private String manualInclude;

    /**
     * The list of dependencies in the form of groupId:artifactId which should BE deleted/refreshed.
     *
     * @since 2.6
     */
    @Parameter
    private List<String> includes;

    /**
     * Comma-separated list of groupId:artifactId entries, which should be used to include artifacts for
     * deletion/refresh. This is a command-line alternative to the <code>includes</code> parameter, since List
     * parameters are not currently compatible with CLI specification.
     *
     * @since 2.6
     */
    @Parameter(property = "include")
    private String include;

    /**
     * The list of dependencies in the form of groupId:artifactId which should NOT be deleted/refreshed.
     */
    @Parameter
    private List<String> excludes;

    /**
     * Comma-separated list of groupId:artifactId entries, which should be used to exclude artifacts from
     * deletion/refresh. This is a command-line alternative to the <code>excludes</code> parameter, since List
     * parameters are not currently compatible with CLI specification.
     */
    @Parameter(property = "exclude")
    private String exclude;

    /**
     * Whether to re-resolve the artifacts once they have been deleted from the local repository. If you are running
     * this mojo from the command-line, you may want to disable this. By default, artifacts will be re-resolved.
     */
    @Parameter(property = "reResolve", defaultValue = "true")
    private boolean reResolve;

    /**
     * The local repository, from which to delete artifacts.
     */
    @Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
    private ArtifactRepository localRepository;

    /**
     * Determines how liberally the plugin will delete an artifact from the local repository. Values are: <br/>
     * <ul>
     * <li><b>file</b> - Eliminate only the artifact's file.</li>
     * <li><b>version</b> <i>(default)</i> - Eliminate all files associated with the version of the artifact.</li>
     * <li><b>artifactId</b> - Eliminate all files associated with the artifact's artifactId.</li>
     * <li><b>groupId</b> - Eliminate all files associated with the artifact's groupId.</li>
     * </ul>
     */
    @Parameter(property = "resolutionFuzziness", defaultValue = "version")
    private String resolutionFuzziness;

    /**
     * Whether this mojo should act on all transitive dependencies. Default value is true.
     */
    @Parameter(property = "actTransitively", defaultValue = "true")
    private boolean actTransitively;

    /**
     * Whether this plugin should output verbose messages. Default is false.
     */
    @Parameter(property = "verbose", defaultValue = "false")
    private boolean verbose;

    /**
     * Whether to purge only snapshot artifacts.
     *
     * @since 2.4
     */
    @Parameter(property = "snapshotsOnly", defaultValue = "false")
    private boolean snapshotsOnly;

    /**
     * Skip plugin execution completely.
     *
     * @since 2.7
     */
    @Parameter(property = "skip", defaultValue = "false")
    private boolean skip;

    @Inject
    public PurgeLocalRepositoryMojo(
            MavenProject project,
            MavenSession session,
            ArtifactHandlerManager artifactHandlerManager,
            DependencyResolver dependencyResolver,
            ArtifactResolver artifactResolver) {
        this.session = session;
        this.project = project;
        this.artifactHandlerManager = artifactHandlerManager;
        this.dependencyResolver = dependencyResolver;
        this.artifactResolver = artifactResolver;
    }

    /**
     * Includes only direct project dependencies.
     */
    private class DirectDependencyFilter extends AbstractFilter {
        private final Artifact projectArtifact;

        private final List<Dependency> directDependencies;

        /**
         * Default constructor
         *
         * @param directDependencies Set of dependencies objects which represent the direct dependencies of the project
         */
        DirectDependencyFilter(Artifact projectArtifact, List<Dependency> directDependencies) {
            this.projectArtifact = projectArtifact;
            this.directDependencies = directDependencies;
        }

        @Override
        public boolean accept(Node node, List<Node> parents) {

            if (artifactsGAMatch(node, projectArtifact.getGroupId(), projectArtifact.getArtifactId())) {
                return true;
            }
            for (Dependency dep : directDependencies) {
                if (this.artifactsGAMatch(node, dep.getGroupId(), dep.getArtifactId())) {
                    return true;
                }
            }
            return false;
        }

        /*
         * Compare the groupId:artifactId of two artifacts.
         */
        private boolean artifactsGAMatch(Node node, String groupId, String artifactId) {
            if (node.getDependency() == null) {
                return false;
            }

            if (!node.getDependency().getGroupId().equals(groupId)) {
                getLog().debug("Different groupId: " + node.getDependency() + "  " + groupId);
                return false;
            }
            if (!node.getDependency().getArtifactId().equals(artifactId)) {
                getLog().debug("Different artifactId: " + node.getDependency() + "  " + artifactId);
                return false;
            }
            return true;
        }
    }

    /**
     * Includes only snapshot artifacts
     */
    private static class SnapshotsFilter extends AbstractFilter {
        @Override
        public boolean accept(Node node, List<Node> parents) {
            if (node.getDependency() == null) {
                return false;
            } else {
                return ArtifactUtils.isSnapshot(node.getDependency().getVersion());
            }
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (isSkip()) {
            getLog().info("Skipping plugin execution");
            return;
        }

        if (!(manualInclude == null || manualInclude.isEmpty())) {
            manualIncludes = this.parseIncludes(manualInclude);
        }
        // If it's a manual purge, the only step is to delete from the local repo
        if (manualIncludes != null && !manualIncludes.isEmpty()) {
            manualPurge(manualIncludes);
            return;
        }

        Set<Artifact> purgedArtifacts = new HashSet<>();
        if (shouldPurgeAllProjectsInReactor()) {
            for (MavenProject reactorProject : reactorProjects) {
                purgeLocalRepository(reactorProject, purgedArtifacts);
            }
        } else {
            purgeLocalRepository(project, purgedArtifacts);
        }
    }

    /**
     * Determines if all projects in the reactor should be purged from their dependencies. When this goal is started on
     * the command-line, it is always the case. When it is bound to a phase in the lifecycle, it is never the case.
     *
     * @return <code>true</code> if all projects in the reactor should be purged, <code>false</code> otherwise.
     */
    private boolean shouldPurgeAllProjectsInReactor() {
        Source source = mojoExecution.getSource();
        return reactorProjects.size() > 1 && source == Source.CLI;
    }

    /**
     * Purges the local repository for the dependencies in the given Maven project.
     *
     * @param theProject Maven project.
     * @param purgedArtifacts The artifacts that were already purged.
     * @throws MojoFailureException in case of errors during the purge.
     */
    private void purgeLocalRepository(MavenProject theProject, Set<Artifact> purgedArtifacts)
            throws MojoFailureException {
        List<Dependency> dependencies = theProject.getDependencies();

        TransformableFilter dependencyFilter = createPurgeArtifactsFilter(theProject, dependencies, purgedArtifacts);

        Set<Artifact> resolvedArtifactsToPurge =
                getFilteredResolvedArtifacts(theProject, dependencies, dependencyFilter);

        if (resolvedArtifactsToPurge.isEmpty()) {
            getLog().info("No artifacts included for purge for project: " + getProjectKey(theProject));
            return;
        }

        purgeArtifacts(theProject, resolvedArtifactsToPurge);
        purgedArtifacts.addAll(resolvedArtifactsToPurge);

        if (reResolve) {
            getLog().info("Re-resolving dependencies");
            try {
                reResolveArtifacts(theProject, resolvedArtifactsToPurge);
            } catch (ArtifactResolutionException e) {
                String failureMessage = "Failed to refresh project dependencies for: " + theProject.getId();
                throw new MojoFailureException(failureMessage, e);
            }
        }
    }

    /**
     * Purge/Delete artifacts from the local repository according to the given patterns.
     *
     * @param theIncludes The includes.
     * @throws MojoExecutionException in case of an error.
     */
    private void manualPurge(List<String> theIncludes) throws MojoExecutionException {
        MessageBuilder messageBuilder = MessageUtils.buffer();

        getLog().info(messageBuilder
                .a("Deleting ")
                .strong(theIncludes.size())
                .a(" manual ")
                .a(theIncludes.size() != 1 ? "dependencies" : "dependency")
                .a(" from ")
                .strong(localRepository.getBasedir())
                .build());

        for (String gavPattern : theIncludes) {
            if (gavPattern == null || gavPattern.isEmpty()) {
                getLog().debug("Skipping empty gav pattern");
                continue;
            }

            String relativePath = gavToPath(gavPattern);
            if (relativePath == null || relativePath.isEmpty()) {
                getLog().debug("Skipping empty relative path for gav pattern: " + gavPattern);
                continue;
            }

            File purgeDir = new File(localRepository.getBasedir(), relativePath);
            if (purgeDir.exists()) {
                getLog().debug("Deleting directory: " + purgeDir);
                try {
                    FileUtils.deleteDirectory(purgeDir);
                } catch (IOException e) {
                    throw new MojoExecutionException("Unable to purge directory: " + purgeDir);
                }
            } else {
                getLog().debug("Directory: " + purgeDir + " doesn't exist");
            }
        }
    }

    /**
     * Convert a groupId:artifactId:version to a file system path
     *
     * @param gav the groupId:artifactId:version string
     * @return the corresponding path
     */
    private String gavToPath(String gav) {
        if (gav == null || gav.isEmpty()) {
            return null;
        }

        String[] pathComponents = gav.split(":");

        StringBuilder path = new StringBuilder(pathComponents[0].replace('.', '/'));

        for (int i = 1; i < pathComponents.length; ++i) {
            path.append("/").append(pathComponents[i]);
        }

        return path.toString();
    }

    /**
     * Create the includes exclude filter to use when resolving and purging dependencies Also excludes any "system"
     * scope dependencies
     *
     * @param theProject The Maven project.
     * @param dependencies The dependencies to use as a reference if we're excluding transitive dependencies
     * @param purgedArtifacts The artifacts already purged.
     * @return the created filter
     */
    private TransformableFilter createPurgeArtifactsFilter(
            MavenProject theProject, List<Dependency> dependencies, Set<Artifact> purgedArtifacts) {
        List<TransformableFilter> subFilters = new ArrayList<>();

        // System dependencies should never be purged
        subFilters.add(ScopeFilter.excluding(Artifact.SCOPE_SYSTEM));

        if (this.snapshotsOnly) {
            subFilters.add(new SnapshotsFilter());
        }

        // The CLI includes/excludes overrides configuration in the pom
        if (!(this.include == null || this.include.isEmpty())) {
            this.includes = parseIncludes(this.include);
        }
        if (this.includes != null) {
            subFilters.add(new PatternInclusionsFilter(includes));
        }

        if (!(this.exclude == null || this.exclude.isEmpty())) {
            this.excludes = parseIncludes(this.exclude);
        }
        if (this.excludes != null) {
            subFilters.add(new PatternExclusionsFilter(excludes));
        }

        if (!actTransitively) {
            subFilters.add(new DirectDependencyFilter(theProject.getArtifact(), dependencies));
        }

        List<String> exclusions = new ArrayList<>(reactorProjects.size());
        // It doesn't make sense to include projects from the reactor here since they're likely not able to be resolved
        for (MavenProject reactorProject : reactorProjects) {
            exclusions.add(toPatternExcludes(reactorProject.getArtifact()));
        }
        // There is no need to consider a second time artifacts that were already purged (re-resolved or not)
        for (Artifact purgedArtifact : purgedArtifacts) {
            exclusions.add(toPatternExcludes(purgedArtifact));
        }
        subFilters.add(new PatternExclusionsFilter(exclusions));

        return new AndFilter(subFilters);
    }

    /**
     * Returns a string that represents a pattern for an exclude filter for the given artifact.
     *
     * @param artifact Artifact.
     * @return String representation of a pattern for an exclude filter for the given artifact.
     */
    private String toPatternExcludes(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
                + artifact.getArtifactHandler().getExtension() + ":" + artifact.getVersion();
    }

    /**
     * Convert comma separated list of includes to List object
     *
     * @param theInclude The list of includes
     * @return the includes list
     */
    private List<String> parseIncludes(String theInclude) {
        List<String> theIncludes = new ArrayList<>();

        if (theInclude != null) {
            String[] elements = theInclude.split(",");
            theIncludes.addAll(Arrays.asList(elements));
        }

        return theIncludes;
    }

    private Set<Artifact> getFilteredResolvedArtifacts(
            MavenProject theProject, List<Dependency> dependencies, TransformableFilter filter) {
        try {
            Iterable<ArtifactResult> results = dependencyResolver.resolveDependencies(
                    session.getProjectBuildingRequest(), theProject.getModel(), filter);

            Set<Artifact> resolvedArtifacts = new LinkedHashSet<>();

            for (ArtifactResult artResult : results) {
                resolvedArtifacts.add(artResult.getArtifact());
            }

            return resolvedArtifacts;
        } catch (DependencyResolverException e) {
            getLog().info("Unable to resolve all dependencies for: " + getProjectKey(theProject)
                    + ". Falling back to non-transitive mode for initial artifact resolution.");
        }

        Set<Artifact> resolvedArtifacts = new LinkedHashSet<>();

        ArtifactFilter artifactFilter = filter.transform(new ArtifactIncludeFilterTransformer());

        for (Dependency dependency : dependencies) {
            DefaultArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
            coordinate.setGroupId(dependency.getGroupId());
            coordinate.setArtifactId(dependency.getArtifactId());
            coordinate.setVersion(dependency.getVersion());
            coordinate.setExtension(artifactHandlerManager
                    .getArtifactHandler(dependency.getType())
                    .getExtension());
            try {
                Artifact artifact = artifactResolver
                        .resolveArtifact(session.getProjectBuildingRequest(), coordinate)
                        .getArtifact();
                if (artifactFilter.include(artifact)) {
                    resolvedArtifacts.add(artifact);
                }
            } catch (ArtifactResolverException e) {
                getLog().debug("Unable to resolve artifact: " + coordinate);
            }
        }
        return resolvedArtifacts;
    }

    private void purgeArtifacts(MavenProject theProject, Set<Artifact> artifacts) {
        MessageBuilder messageBuilder = MessageUtils.buffer();

        getLog().info(messageBuilder
                .a("Deleting ")
                .strong(artifacts.size())
                .a(" ")
                .strong(actTransitively ? "transitive" : "direct")
                .a(artifacts.size() != 1 ? " dependencies" : " dependency")
                .a(" for project ")
                .strong(getProjectKey(theProject))
                .a(" from ")
                .strong(localRepository.getBasedir())
                .a(" with artifact ")
                .strong(resolutionFuzziness)
                .a(" resolution fuzziness")
                .build());

        for (Artifact artifact : artifacts) {
            verbose("Purging artifact: " + artifact.getId());

            File deleteTarget = findDeleteTarget(artifact);

            verbose("Deleting: " + deleteTarget);

            if (deleteTarget.isDirectory()) {
                try {
                    FileUtils.deleteDirectory(deleteTarget);
                } catch (IOException e) {
                    getLog().warn("Unable to purge local repository location: " + deleteTarget, e);
                }
            } else {
                if (!deleteTarget.delete()) {
                    deleteTarget.deleteOnExit();
                    getLog().warn("Unable to purge local repository location immediately: " + deleteTarget);
                }
            }
            artifact.setResolved(false);
        }
    }

    private void reResolveArtifacts(MavenProject theProject, Set<Artifact> artifacts)
            throws ArtifactResolutionException {
        // Always need to re-resolve the poms in case they were purged along with the artifact
        // because Maven 2 will not automatically re-resolve them when resolving the artifact
        for (Artifact artifact : artifacts) {
            verbose("Resolving artifact: " + artifact.getId());

            try {
                // CHECKSTYLE_OFF: LineLength
                artifactResolver.resolveArtifact(
                        session.getProjectBuildingRequest(), TransferUtils.toArtifactCoordinate(artifact));
                // CHECKSTYLE_ON: LineLength
            } catch (ArtifactResolverException e) {
                verbose(e.getMessage());
            }
        }

        List<Artifact> missingArtifacts = new ArrayList<>();

        for (Artifact artifact : artifacts) {
            try {
                artifactResolver.resolveArtifact(session.getProjectBuildingRequest(), artifact);
            } catch (ArtifactResolverException e) {
                verbose(e.getMessage());
                missingArtifacts.add(artifact);
            }
        }

        if (!missingArtifacts.isEmpty()) {
            StringBuilder message = new StringBuilder("required artifacts missing:");
            message.append(System.lineSeparator());
            for (Artifact missingArtifact : missingArtifacts) {
                message.append("  ").append(missingArtifact.getId()).append(System.lineSeparator());
            }
            message.append(System.lineSeparator());
            message.append("for the artifact:");

            throw new ArtifactResolutionException(
                    message.toString(), theProject.getArtifact(), theProject.getRemoteArtifactRepositories());
        }
    }

    private File findDeleteTarget(Artifact artifact) {
        // Use localRepository.pathOf() in case artifact.getFile() is not set
        File deleteTarget = new File(localRepository.getBasedir(), localRepository.pathOf(artifact));

        if (GROUP_ID_FUZZINESS.equals(resolutionFuzziness)) {
            // get the groupId dir.
            deleteTarget = deleteTarget.getParentFile().getParentFile().getParentFile();
        } else if (ARTIFACT_ID_FUZZINESS.equals(resolutionFuzziness)) {
            // get the artifactId dir.
            deleteTarget = deleteTarget.getParentFile().getParentFile();
        } else if (VERSION_FUZZINESS.equals(resolutionFuzziness)) {
            // get the version dir.
            deleteTarget = deleteTarget.getParentFile();
        }
        // else it's file fuzziness.
        return deleteTarget;
    }

    private void verbose(String message) {
        if (verbose || getLog().isDebugEnabled()) {
            getLog().info(message);
        }
    }

    private String getProjectKey(MavenProject project) {
        return project.getArtifactId();
    }

    /**
     * @return {@link #skip}
     */
    public boolean isSkip() {
        return skip;
    }

    /**
     * @param skip {@link #skip}
     */
    public void setSkip(boolean skip) {
        this.skip = skip;
    }
}
