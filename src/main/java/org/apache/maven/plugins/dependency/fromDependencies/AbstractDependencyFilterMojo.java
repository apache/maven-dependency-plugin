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
package org.apache.maven.plugins.dependency.fromDependencies;

import javax.inject.Inject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.AbstractDependencyMojo;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.plugins.dependency.utils.translators.ArtifactTranslator;
import org.apache.maven.plugins.dependency.utils.translators.ClassifierTypeTranslator;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.apache.maven.shared.artifact.filter.collection.ClassifierFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.GroupIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Class that encapsulates the plugin parameters, and contains methods that handle dependency filtering.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @see org.apache.maven.plugins.dependency.AbstractDependencyMojo
 */
public abstract class AbstractDependencyFilterMojo extends AbstractDependencyMojo {

    /**
     * Overwrite release artifacts.
     *
     * @since 1.0
     */
    @Parameter(property = "overWriteReleases", defaultValue = "false")
    protected boolean overWriteReleases;

    /**
     * Overwrite snapshot artifacts.
     *
     * @since 1.0
     */
    @Parameter(property = "overWriteSnapshots", defaultValue = "false")
    protected boolean overWriteSnapshots;

    /**
     * Overwrite artifacts that don't exist or are older than the source.
     *
     * @since 2.0
     */
    @Parameter(property = "overWriteIfNewer", defaultValue = "true")
    protected boolean overWriteIfNewer;

    /**
     * If we should exclude transitive dependencies.
     *
     * @since 2.0
     */
    @Parameter(property = "excludeTransitive", defaultValue = "false")
    protected boolean excludeTransitive;

    /**
     * Comma-separated list of Types to include. Empty String indicates include everything (default).
     *
     * @since 2.0
     */
    @Parameter(property = "includeTypes", defaultValue = "")
    protected String includeTypes;

    /**
     * Comma-separated list of Types to exclude. Empty String indicates don't exclude anything (default).
     *
     * @since 2.0
     */
    @Parameter(property = "excludeTypes", defaultValue = "")
    protected String excludeTypes;

    /**
     * Scope threshold to include. An empty string indicates include all dependencies (default).<br>
     * The scope threshold value being interpreted is the scope as
     * Maven filters for creating a classpath, not as specified in the pom. In summary:
     * <ul>
     * <li><code>runtime</code> include scope gives runtime and compile dependencies,</li>
     * <li><code>compile</code> include scope gives compile, provided, and system dependencies,</li>
     * <li><code>test</code> include scope gives all dependencies (equivalent to default),</li>
     * <li><code>provided</code> include scope just gives provided dependencies,</li>
     * <li><code>system</code> include scope just gives system dependencies.</li>
     * </ul>
     *
     * @since 2.0
     */
    @Parameter(property = "includeScope", defaultValue = "")
    protected String includeScope;

    /**
     * Scope threshold to exclude, if no value is defined for include.
     * An empty string indicates no dependencies (default).  Unlike the other
     * exclusion parameters, this property does not support a comma-delimited
     * list of scope exclusions. Just one scope may be excluded at a time.<br>
     * The scope threshold value being interpreted is the scope as
     * Maven filters for creating a classpath, not as specified in the pom. In summary:
     * <ul>
     * <li><code>runtime</code> exclude scope excludes runtime and compile dependencies,</li>
     * <li><code>compile</code> exclude scope excludes compile, provided, and system dependencies,</li>
     * <li><code>test</code> exclude scope excludes all dependencies, then not really a legitimate option: it will
     * fail, you probably meant to configure includeScope = compile</li>
     * <li><code>provided</code> exclude scope just excludes provided dependencies,</li>
     * <li><code>system</code> exclude scope just excludes system dependencies.</li>
     * </ul>
     *
     * @since 2.0
     */
    @Parameter(property = "excludeScope", defaultValue = "")
    protected String excludeScope;

    /**
     * Comma-separated list of Classifiers to include. Empty string indicates include everything (default).
     *
     * @since 2.0
     */
    @Parameter(property = "includeClassifiers", defaultValue = "")
    protected String includeClassifiers;

    /**
     * Comma-separated list of Classifiers to exclude. Empty String indicates don't exclude anything (default).
     *
     * @since 2.0
     */
    @Parameter(property = "excludeClassifiers", defaultValue = "")
    protected String excludeClassifiers;

    /**
     * Specify classifier to look for. Example: sources.
     *
     * @since 2.0
     */
    @Parameter(property = "classifier", defaultValue = "")
    protected String classifier;

    /**
     * Specify type to look for when constructing artifact based on classifier. Example: java-source,jar,war.
     *
     * @since 2.0
     */
    @Parameter(property = "type", defaultValue = "")
    protected String type;

    /**
     * Comma-separated list of artifact IDs to exclude.
     *
     * @since 2.0
     */
    @Parameter(property = "excludeArtifactIds", defaultValue = "")
    protected String excludeArtifactIds;

    /**
     * Comma-separated list of artifact IDs to include. Empty String indicates include everything (default).
     *
     * @since 2.0
     */
    @Parameter(property = "includeArtifactIds", defaultValue = "")
    protected String includeArtifactIds;

    /**
     * Comma-separated list of group IDs to exclude.
     *
     * @since 2.0
     */
    @Parameter(property = "excludeGroupIds", defaultValue = "")
    protected String excludeGroupIds;

    /**
     * Comma-separated list of group IDs to include. Empty string indicates include everything (default).
     *
     * @since 2.0
     */
    @Parameter(property = "includeGroupIds", defaultValue = "")
    protected String includeGroupIds;

    /**
     * Directory to store flag files.
     *
     * @since 2.0
     */
    @Parameter(
            property = "markersDirectory",
            defaultValue = "${project.build.directory}/dependency-maven-plugin-markers")
    protected File markersDirectory;

    /**
     * Prepend the group ID during copy.
     *
     * @since 2.2
     */
    @Parameter(property = "mdep.prependGroupId", defaultValue = "false")
    protected boolean prependGroupId = false;

    /**
     * By default, this goal uses the project itself as the root of the dependency tree.
     * With graphRoots, you can select a subtree of dependencies based on groupId and artifactId.
     * After that, the general include/exclude filters can be applied.
     *
     * @since 3.10.0
     */
    @Parameter
    private List<GraphRoot> graphRoots;

    private final ResolverUtil resolverUtil;

    private final ProjectBuilder projectBuilder;

    private final ArtifactHandlerManager artifactHandlerManager;

    @Inject
    protected AbstractDependencyFilterMojo(
            MavenSession session,
            BuildContext buildContext,
            MavenProject project,
            ResolverUtil resolverUtil,
            ProjectBuilder projectBuilder,
            ArtifactHandlerManager artifactHandlerManager) {
        super(session, buildContext, project);
        this.resolverUtil = resolverUtil;
        this.projectBuilder = projectBuilder;
        this.artifactHandlerManager = artifactHandlerManager;
    }

    /**
     * Return an {@link ArtifactsFilter} indicating which artifacts must be filtered out.
     *
     * @return an {@link ArtifactsFilter} indicating which artifacts must be filtered out
     */
    protected abstract ArtifactsFilter getMarkedArtifactFilter();

    /**
     * Tells whether this goal asks Maven for the dependency graph only ({@code requiresDependencyCollection}) and
     * resolves the artifacts that are left after filtering itself. This way a dependency that the filters drop, for
     * example a {@code provided} module of the same reactor that has not been built, is never resolved.
     * <p>
     * Goals that ask Maven to resolve the dependencies ({@code requiresDependencyResolution}) keep the default.
     *
     * @return {@code true} if the artifacts of {@link MavenProject#getArtifacts()} are not resolved yet
     */
    protected boolean isResolvingAfterFiltering() {
        return false;
    }

    /**
     * Retrieves dependencies, either direct only or all including transitive.
     *
     * @param stopOnFailure true to fail if resolution does not work or false not to fail
     * @return a set of artifacts
     * @throws MojoExecutionException in case of errors
     */
    protected Set<Artifact> getResolvedDependencies(boolean stopOnFailure) throws MojoExecutionException {

        DependencyStatusSets status = getDependencySets(stopOnFailure);

        return status.getResolvedDependencies();
    }

    /**
     * @param stopOnFailure true/false
     * @return {@link DependencyStatusSets}
     * @throws MojoExecutionException in case of an error
     */
    protected DependencyStatusSets getDependencySets(boolean stopOnFailure) throws MojoExecutionException {
        return getDependencySets(stopOnFailure, false);
    }

    /**
     * Method creates filters and filters the projects dependencies. This method also transforms the dependencies if
     * classifier is set. The dependencies are filtered in least specific to most specific order.
     *
     * @param stopOnFailure true to fail if artifacts can't be resolved false otherwise
     * @param includeParents <code>true</code> if parents should be included or not <code>false</code>
     * @return DependencyStatusSets - Bean of TreeSets that contains information on the projects dependencies
     * @throws MojoExecutionException in case of errors
     */
    protected DependencyStatusSets getDependencySets(boolean stopOnFailure, boolean includeParents)
            throws MojoExecutionException {

        // add filters in well known order, least specific to most specific
        FilterArtifacts filter = new FilterArtifacts();

        filter.addFilter(new ProjectTransitivityFilter(getProject().getDependencyArtifacts(), this.excludeTransitive));

        if ("test".equals(this.excludeScope)) {
            throw new MojoExecutionException("Excluding every artifact inside 'test' resolution scope means "
                    + "excluding everything: you probably want includeScope='compile', "
                    + "read parameters documentation for detailed explanations");
        }
        filter.addFilter(new ScopeFilter(
                DependencyUtil.cleanToBeTokenizedString(this.includeScope),
                DependencyUtil.cleanToBeTokenizedString(this.excludeScope)));

        filter.addFilter(new TypeFilter(
                DependencyUtil.cleanToBeTokenizedString(this.includeTypes),
                DependencyUtil.cleanToBeTokenizedString(this.excludeTypes)));

        filter.addFilter(new ClassifierFilter(
                DependencyUtil.cleanToBeTokenizedString(this.includeClassifiers),
                DependencyUtil.cleanToBeTokenizedString(this.excludeClassifiers)));

        filter.addFilter(new GroupIdFilter(
                DependencyUtil.cleanToBeTokenizedString(this.includeGroupIds),
                DependencyUtil.cleanToBeTokenizedString(this.excludeGroupIds)));

        filter.addFilter(new ArtifactIdFilter(
                DependencyUtil.cleanToBeTokenizedString(this.includeArtifactIds),
                DependencyUtil.cleanToBeTokenizedString(this.excludeArtifactIds)));

        // start with all artifacts.
        Set<Artifact> artifacts;

        try {
            artifacts = collectArtifacts(getProject());
        } catch (DependencyResolutionException e) {
            throw new MojoExecutionException("Failed to collect artifacts", e);
        }

        // A POM can be both a dependency to translate and a parent to copy unchanged.
        Set<Artifact> parentArtifacts = new LinkedHashSet<>();
        if (includeParents) {
            // add dependencies parents
            for (Artifact dep : artifacts) {
                addParentArtifacts(buildProjectFromArtifact(dep), parentArtifacts);
            }

            // add current project parent
            addParentArtifacts(getProject(), parentArtifacts);
        }

        // perform filtering
        try {
            artifacts = filter.filter(artifacts);
            parentArtifacts = filter.filter(parentArtifacts);
        } catch (ArtifactFilterException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        // with a classifier the originals only seed the translation and only the translated artifacts are resolved
        if (isResolvingAfterFiltering() && (classifier == null || classifier.isEmpty())) {
            resolveFilteredArtifacts(artifacts);
        }

        // transform artifacts if classifier is set
        DependencyStatusSets status;
        if (classifier != null && !classifier.isEmpty()) {
            status = getClassifierTranslatedDependencies(artifacts, stopOnFailure);
            DependencyStatusSets parentStatus = filterMarkedDependencies(parentArtifacts);
            status.getResolvedDependencies().addAll(parentStatus.getResolvedDependencies());
            status.getSkippedDependencies().addAll(parentStatus.getSkippedDependencies());
        } else {
            artifacts.addAll(parentArtifacts);
            status = filterMarkedDependencies(artifacts);
        }

        return status;
    }

    /**
     * Resolves the artifacts that are still without a file and records the file on the artifact itself, so scope,
     * trail and the other properties collected by Maven are kept.
     */
    private void resolveFilteredArtifacts(Set<Artifact> artifacts) throws MojoExecutionException {
        List<Artifact> unresolved = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            if (artifact.getFile() != null) {
                continue;
            }
            if (Artifact.SCOPE_SYSTEM.equals(artifact.getScope())) {
                // never a repository artifact: Maven takes the file from the systemPath of the dependency
                artifact.setFile(getSystemPathFile(artifact));
            } else {
                unresolved.add(artifact);
            }
        }
        if (unresolved.isEmpty()) {
            return;
        }

        List<ArtifactResult> results;
        try {
            results = resolverUtil.resolveArtifacts(
                    unresolved.stream().map(RepositoryUtils::toArtifact).collect(Collectors.toList()),
                    getProject().getRemoteProjectRepositories());
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(
                    "Could not resolve dependencies for project " + getProject().getId() + ": " + e.getMessage(), e);
        }

        for (int i = 0; i < unresolved.size(); i++) {
            ArtifactResult result = results.get(i);
            if (result.isResolved()) {
                Artifact artifact = unresolved.get(i);
                // same as Maven when it resolves a project: a snapshot gets its timestamped version
                if (!result.getArtifact().getVersion().equals(artifact.getVersion())) {
                    artifact.setVersion(result.getArtifact().getVersion());
                }
                artifact.setFile(result.getArtifact().getFile());
                artifact.setResolved(true);
            }
        }
    }

    private File getSystemPathFile(Artifact artifact) throws MojoExecutionException {
        String key = artifact.getGroupId()
                + ':'
                + artifact.getArtifactId()
                + ':'
                + artifact.getType()
                + (artifact.hasClassifier() ? ':' + artifact.getClassifier() : "");
        List<Dependency> candidates = new ArrayList<>(getProject().getDependencies());
        if (getProject().getDependencyManagement() != null) {
            candidates.addAll(getProject().getDependencyManagement().getDependencies());
        }
        for (Dependency dependency : candidates) {
            if (key.equals(dependency.getManagementKey()) && dependency.getSystemPath() != null) {
                File file = new File(dependency.getSystemPath());
                if (!file.isFile()) {
                    throw new MojoExecutionException(
                            "The systemPath of " + artifact.getId() + " does not exist: " + file);
                }
                return file;
            }
        }
        throw new MojoExecutionException(
                "No systemPath is declared for the system scoped dependency " + artifact.getId());
    }

    private MavenProject buildProjectFromArtifact(Artifact artifact) throws MojoExecutionException {
        try {
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setProcessPlugins(false);
            buildingRequest.setRemoteRepositories(getProject().getRemoteArtifactRepositories());
            return projectBuilder.build(artifact, buildingRequest).getProject();
        } catch (ProjectBuildingException e) {
            throw new MojoExecutionException("Could not build project for " + artifact.getId(), e);
        }
    }

    private void addParentArtifacts(MavenProject project, Set<Artifact> artifacts) throws MojoExecutionException {
        while (project.hasParent()) {
            project = project.getParent();

            if (artifacts.contains(project.getArtifact())) {
                // artifact already in the set
                break;
            }
            try {
                org.eclipse.aether.artifact.Artifact resolvedArtifact = resolverUtil.resolveArtifact(
                        RepositoryUtils.toArtifact(project.getArtifact()), project.getRemoteProjectRepositories());

                artifacts.add(RepositoryUtils.toArtifact(resolvedArtifact));
            } catch (ArtifactResolutionException | ArtifactDescriptorException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    /**
     * Transform artifacts.
     *
     * @param artifacts set of artifacts {@link Artifact}
     * @param stopOnFailure true/false
     * @return DependencyStatusSets - Bean of TreeSets that contains information on the projects dependencies
     * @throws MojoExecutionException in case of an error
     */
    private DependencyStatusSets getClassifierTranslatedDependencies(Set<Artifact> artifacts, boolean stopOnFailure)
            throws MojoExecutionException {
        Set<Artifact> unResolvedArtifacts = new LinkedHashSet<>();
        Set<Artifact> resolvedArtifacts = artifacts;
        DependencyStatusSets status = new DependencyStatusSets();

        // possibly translate artifacts into a new set of artifacts based on the
        // classifier and type
        // if this did something, we need to resolve the new artifacts
        if (classifier != null && !classifier.isEmpty()) {
            ArtifactTranslator translator =
                    new ClassifierTypeTranslator(artifactHandlerManager, this.classifier, this.type);
            Collection<org.eclipse.aether.artifact.Artifact> coordinates = translator.translate(artifacts, getLog());

            // resolve the translated artifacts
            resolvedArtifacts = resolve(new LinkedHashSet<>(coordinates), stopOnFailure, unResolvedArtifacts);

            // skip the translated artifacts that are already up to date in the destination: the destination file
            // name depends on the classifier, so the decision cannot be taken on the original artifacts
            status = filterMarkedDependencies(resolvedArtifacts);
            resolvedArtifacts = status.getResolvedDependencies();
        }

        // return a bean of all 3 sets.
        status.setResolvedDependencies(resolvedArtifacts);
        status.setUnResolvedDependencies(unResolvedArtifacts);

        return status;
    }

    /**
     * Filter the marked dependencies.
     *
     * @param artifacts the artifacts set {@link Artifact}
     * @return status set {@link DependencyStatusSets}
     * @throws MojoExecutionException in case of an error
     */
    protected DependencyStatusSets filterMarkedDependencies(Set<Artifact> artifacts) throws MojoExecutionException {
        // remove files that have markers already
        FilterArtifacts filter = new FilterArtifacts();
        filter.clearFilters();
        filter.addFilter(getMarkedArtifactFilter());

        Set<Artifact> unMarkedArtifacts;
        try {
            unMarkedArtifacts = filter.filter(artifacts);
        } catch (ArtifactFilterException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        // calculate the skipped artifacts
        Set<Artifact> skippedArtifacts = new LinkedHashSet<>(artifacts);
        skippedArtifacts.removeAll(unMarkedArtifacts);

        return new DependencyStatusSets(unMarkedArtifacts, null, skippedArtifacts);
    }

    /**
     * @param artifacts the set of artifacts
     * @param stopOnFailure <code>true</code> if we should fail with exception if an artifact couldn't be resolved
     *            <code>false</code> otherwise
     * @param unresolved receives the artifacts that could not be resolved
     * @return the resolved artifacts. {@link Artifact}.
     * @throws MojoExecutionException in case of error
     */
    private Set<Artifact> resolve(
            Set<org.eclipse.aether.artifact.Artifact> artifacts, boolean stopOnFailure, Set<Artifact> unresolved)
            throws MojoExecutionException {

        Set<Artifact> resolvedArtifacts = new LinkedHashSet<>();
        for (org.eclipse.aether.artifact.Artifact artifact : artifacts) {
            try {
                org.eclipse.aether.artifact.Artifact resolveArtifact =
                        resolverUtil.resolveArtifact(artifact, getProject().getRemoteProjectRepositories());
                resolvedArtifacts.add(RepositoryUtils.toArtifact(resolveArtifact));
            } catch (ArtifactResolutionException | ArtifactDescriptorException ex) {
                // an error occurred during resolution, log it and continue
                getLog().debug("error resolving: " + artifact, ex);
                unresolved.add(RepositoryUtils.toArtifact(artifact));
                if (stopOnFailure) {
                    throw new MojoExecutionException("error resolving: " + artifact, ex);
                }
            }
        }
        return resolvedArtifacts;
    }

    private Set<Artifact> collectArtifacts(MavenProject project) throws DependencyResolutionException {
        if (graphRoots == null || graphRoots.isEmpty()) {
            // artifact have already been resolved here due to
            // @Mojo(requiresDependencyResolution = ResolutionScope.TEST) on final Mojo
            return project.getArtifacts();
        } else {
            // MavenProject doesn't provide access to the graph of dependencies(only the direct dependencies)
            // Hence we need to re-resolve artifacts, but only for the matching graphnodes
            List<DependencyMatcher> filterMatchers =
                    graphRoots.stream().map(GraphRootMatcher::new).collect(Collectors.toList());

            DependencyMatcher subTreeMatcher = new OrDependencyMatcher(filterMatchers);

            Set<Artifact> artifacts = new HashSet<>();
            for (Dependency dep : project.getDependencies()) {
                if (subTreeMatcher.matches(dep)) {
                    artifacts.addAll(resolveDependencyArtifacts(dep));
                }
            }
            return artifacts;
        }
    }

    private Set<Artifact> resolveDependencyArtifacts(Dependency root) throws DependencyResolutionException {
        org.eclipse.aether.graph.Dependency dependency = RepositoryUtils.toDependency(
                root, session.getRepositorySession().getArtifactTypeRegistry());

        List<RemoteRepository> remoteRepositories = getProject().getRemoteProjectRepositories();

        Collection<org.eclipse.aether.artifact.Artifact> depArtifacts =
                resolverUtil.resolveDependencies(dependency.getArtifact(), remoteRepositories);

        return depArtifacts.stream().map(RepositoryUtils::toArtifact).collect(Collectors.toSet());
    }

    /**
     * @return returns the markersDirectory
     */
    public File getMarkersDirectory() {
        return this.markersDirectory;
    }

    /**
     * @param theMarkersDirectory the markersDirectory to set
     */
    public void setMarkersDirectory(File theMarkersDirectory) {
        this.markersDirectory = theMarkersDirectory;
    }

    // TODO: Set marker files.

    /**
     * @return true, if the groupId should be prepended to the filename
     */
    public boolean isPrependGroupId() {
        return prependGroupId;
    }

    /**
     * @param prependGroupId true if the groupId must be prepended during the copy
     */
    public void setPrependGroupId(boolean prependGroupId) {
        this.prependGroupId = prependGroupId;
    }

    /**
     * @return {@link #resolverUtil}
     */
    protected final ResolverUtil getResolverUtil() {
        return resolverUtil;
    }
}
