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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.AbstractDependencyMojo;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.plugins.dependency.utils.translators.ArtifactTranslator;
import org.apache.maven.plugins.dependency.utils.translators.ClassifierTypeTranslator;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.apache.maven.shared.artifact.filter.collection.ClassifierFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.GroupIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.transfer.repository.RepositoryManager;
import org.eclipse.aether.resolution.ArtifactResolutionException;

/**
 * Class that encapsulates the plugin parameters, and contains methods that handle dependency filtering
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @see org.apache.maven.plugins.dependency.AbstractDependencyMojo
 */
public abstract class AbstractDependencyFilterMojo extends AbstractDependencyMojo {

    @Component
    private ResolverUtil resolverUtil;

    @Component
    private DependencyResolver dependencyResolver;

    @Component
    private RepositoryManager repositoryManager;

    /**
     * Overwrite release artifacts
     *
     * @since 1.0
     */
    @Parameter(property = "overWriteReleases", defaultValue = "false")
    protected boolean overWriteReleases;

    /**
     * Overwrite snapshot artifacts
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
     * If we should exclude transitive dependencies
     *
     * @since 2.0
     */
    @Parameter(property = "excludeTransitive", defaultValue = "false")
    protected boolean excludeTransitive;

    /**
     * Comma Separated list of Types to include. Empty String indicates include everything (default).
     *
     * @since 2.0
     */
    @Parameter(property = "includeTypes", defaultValue = "")
    protected String includeTypes;

    /**
     * Comma Separated list of Types to exclude. Empty String indicates don't exclude anything (default).
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
     * Comma Separated list of Classifiers to include. Empty String indicates include everything (default).
     *
     * @since 2.0
     */
    @Parameter(property = "includeClassifiers", defaultValue = "")
    protected String includeClassifiers;

    /**
     * Comma Separated list of Classifiers to exclude. Empty String indicates don't exclude anything (default).
     *
     * @since 2.0
     */
    @Parameter(property = "excludeClassifiers", defaultValue = "")
    protected String excludeClassifiers;

    /**
     * Specify classifier to look for. Example: sources
     *
     * @since 2.0
     */
    @Parameter(property = "classifier", defaultValue = "")
    protected String classifier;

    /**
     * Specify type to look for when constructing artifact based on classifier. Example: java-source,jar,war
     *
     * @since 2.0
     */
    @Parameter(property = "type", defaultValue = "")
    protected String type;

    /**
     * Comma separated list of Artifact names to exclude.
     *
     * @since 2.0
     */
    @Parameter(property = "excludeArtifactIds", defaultValue = "")
    protected String excludeArtifactIds;

    /**
     * Comma separated list of Artifact names to include. Empty String indicates include everything (default).
     *
     * @since 2.0
     */
    @Parameter(property = "includeArtifactIds", defaultValue = "")
    protected String includeArtifactIds;

    /**
     * Comma separated list of GroupId Names to exclude.
     *
     * @since 2.0
     */
    @Parameter(property = "excludeGroupIds", defaultValue = "")
    protected String excludeGroupIds;

    /**
     * Comma separated list of GroupIds to include. Empty String indicates include everything (default).
     *
     * @since 2.0
     */
    @Parameter(property = "includeGroupIds", defaultValue = "")
    protected String includeGroupIds;

    /**
     * Directory to store flag files
     *
     * @since 2.0
     */
    // CHECKSTYLE_OFF: LineLength
    @Parameter(
            property = "markersDirectory",
            defaultValue = "${project.build.directory}/dependency-maven-plugin-markers")
    // CHECKSTYLE_ON: LineLength
    protected File markersDirectory;

    /**
     * Prepend the groupId during copy.
     *
     * @since 2.2
     */
    @Parameter(property = "mdep.prependGroupId", defaultValue = "false")
    protected boolean prependGroupId = false;

    @Component
    private ProjectBuilder projectBuilder;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    /**
     * Return an {@link ArtifactsFilter} indicating which artifacts must be filtered out.
     *
     * @return an {@link ArtifactsFilter} indicating which artifacts must be filtered out.
     */
    protected abstract ArtifactsFilter getMarkedArtifactFilter();

    /**
     * Retrieves dependencies, either direct only or all including transitive.
     *
     * @param stopOnFailure true to fail if resolution does not work or false not to fail.
     * @return A set of artifacts
     * @throws MojoExecutionException in case of errors.
     */
    protected Set<Artifact> getResolvedDependencies(boolean stopOnFailure) throws MojoExecutionException {

        DependencyStatusSets status = getDependencySets(stopOnFailure);

        return status.getResolvedDependencies();
    }

    /**
     * @param stopOnFailure true/false.
     * @return {@link DependencyStatusSets}
     * @throws MojoExecutionException in case of an error.
     */
    protected DependencyStatusSets getDependencySets(boolean stopOnFailure) throws MojoExecutionException {
        return getDependencySets(stopOnFailure, false);
    }

    /**
     * Method creates filters and filters the projects dependencies. This method also transforms the dependencies if
     * classifier is set. The dependencies are filtered in least specific to most specific order
     *
     * @param stopOnFailure true to fail if artifacts can't be resolved false otherwise.
     * @param includeParents <code>true</code> if parents should be included or not <code>false</code>.
     * @return DependencyStatusSets - Bean of TreeSets that contains information on the projects dependencies
     * @throws MojoExecutionException in case of errors.
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
        Set<Artifact> artifacts = getProject().getArtifacts();

        if (includeParents) {
            // add dependencies parents
            for (Artifact dep : new ArrayList<>(artifacts)) {
                addParentArtifacts(buildProjectFromArtifact(dep), artifacts);
            }

            // add current project parent
            addParentArtifacts(getProject(), artifacts);
        }

        // perform filtering
        try {
            artifacts = filter.filter(artifacts);
        } catch (ArtifactFilterException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        // transform artifacts if classifier is set
        DependencyStatusSets status;
        if (classifier != null && !classifier.isEmpty()) {
            status = getClassifierTranslatedDependencies(artifacts, stopOnFailure);
        } else {
            status = filterMarkedDependencies(artifacts);
        }

        return status;
    }

    private MavenProject buildProjectFromArtifact(Artifact artifact) throws MojoExecutionException {
        try {
            return projectBuilder
                    .build(artifact, session.getProjectBuildingRequest().setProcessPlugins(false))
                    .getProject();
        } catch (ProjectBuildingException e) {
            throw new MojoExecutionException("Coud not build project for " + artifact.getId(), e);
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
            } catch (ArtifactResolutionException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    /**
     * Transform artifacts
     *
     * @param artifacts set of artifacts {@link Artifact}.
     * @param stopOnFailure true/false.
     * @return DependencyStatusSets - Bean of TreeSets that contains information on the projects dependencies
     * @throws MojoExecutionException in case of an error.
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

            status = filterMarkedDependencies(artifacts);

            // the unskipped artifacts are in the resolved set.
            artifacts = status.getResolvedDependencies();

            // resolve the rest of the artifacts
            resolvedArtifacts = resolve(new LinkedHashSet<>(coordinates), stopOnFailure);

            // calculate the artifacts not resolved.
            unResolvedArtifacts.addAll(artifacts);
            unResolvedArtifacts.removeAll(resolvedArtifacts);
        }

        // return a bean of all 3 sets.
        status.setResolvedDependencies(resolvedArtifacts);
        status.setUnResolvedDependencies(unResolvedArtifacts);

        return status;
    }

    /**
     * Filter the marked dependencies
     *
     * @param artifacts The artifacts set {@link Artifact}.
     * @return status set {@link DependencyStatusSets}.
     * @throws MojoExecutionException in case of an error.
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
     * @param artifacts The set of artifacts
     * @param stopOnFailure <code>true</code> if we should fail with exception if an artifact couldn't be resolved
     *            <code>false</code> otherwise.
     * @return the resolved artifacts. {@link Artifact}.
     * @throws MojoExecutionException in case of error.
     */
    private Set<Artifact> resolve(Set<org.eclipse.aether.artifact.Artifact> artifacts, boolean stopOnFailure)
            throws MojoExecutionException {

        Set<Artifact> resolvedArtifacts = new LinkedHashSet<>();
        for (org.eclipse.aether.artifact.Artifact artifact : artifacts) {
            try {
                org.eclipse.aether.artifact.Artifact resolveArtifact =
                        resolverUtil.resolveArtifact(artifact, getProject().getRemoteProjectRepositories());
                resolvedArtifacts.add(RepositoryUtils.toArtifact(resolveArtifact));
            } catch (ArtifactResolutionException ex) {
                // an error occurred during resolution, log it an continue
                getLog().debug("error resolving: " + artifact, ex);
                if (stopOnFailure) {
                    throw new MojoExecutionException("error resolving: " + artifact, ex);
                }
            }
        }
        return resolvedArtifacts;
    }

    /**
     * @return Returns the markersDirectory.
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

    /**
     * @return {@link #dependencyResolver}
     */
    protected final DependencyResolver getDependencyResolver() {
        return dependencyResolver;
    }

    /**
     * @return {@link #repositoryManager}
     */
    protected final RepositoryManager getRepositoryManager() {
        return repositoryManager;
    }
}
