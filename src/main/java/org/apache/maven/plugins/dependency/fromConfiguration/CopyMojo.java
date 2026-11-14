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
package org.apache.maven.plugins.dependency.fromConfiguration;

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.utils.CopyUtil;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.plugins.dependency.utils.filters.ArtifactItemFilter;
import org.apache.maven.plugins.dependency.utils.filters.DestFileFilter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Goal that copies a list of artifacts from the repository to defined locations.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 1.0
 */
@Mojo(name = "copy", defaultPhase = LifecyclePhase.PROCESS_SOURCES, requiresProject = false, threadSafe = true)
public class CopyMojo extends AbstractFromConfigurationMojo {

    private final CopyUtil copyUtil;

    /**
     * Strip artifact version during copy.
     */
    @Parameter(property = "mdep.stripVersion", defaultValue = "false")
    private boolean stripVersion = false;

    /**
     * Strip artifact classifier during copy.
     */
    @Parameter(property = "mdep.stripClassifier", defaultValue = "false")
    private boolean stripClassifier = false;

    /**
     * Prepend artifact groupId during copy.
     *
     * @since 2.7
     */
    @Parameter(property = "mdep.prependGroupId", defaultValue = "false")
    private boolean prependGroupId = false;

    /**
     * Use artifact baseVersion during copy.
     *
     * @since 2.7
     */
    @Parameter(property = "mdep.useBaseVersion", defaultValue = "false")
    private boolean useBaseVersion = false;

    /**
     * The artifact to copy from command line. A string of the form
     * <code>groupId:artifactId:version[:packaging[:classifier[:destFileName]]]</code>. The optional
     * <code>destFileName</code> (since 3.12.0) is the name of the copied file; leave the classifier empty to give a
     * <code>destFileName</code> without one, as in <code>g:a:1.0:jar::renamed.jar</code>. Since 3.12.0 the form that
     * Maven's error messages and <code>dependency:list</code> print,
     * <code>groupId:artifactId:type[:classifier]:version[:scope]</code>, is accepted as well. The form is told apart
     * by which parts look like a version, as described for the <code>artifact</code> parameter of
     * <code>dependency:get</code>; <code>destFileName</code> is only part of the first form. Use
     * {@link #artifactItems} within the POM configuration.
     */
    @SuppressWarnings("unused") // marker-field, setArtifact(String) does the magic
    @Parameter(property = "artifact")
    private String artifact;

    /**
     * Resolve the artifact identified by {@link #artifact} (or by the {@link #artifactItems}) together with its
     * transitive dependencies, directly through the repository system, and copy all of them. With a single artifact
     * this goal works like {@code dependency:get}, but copies the resolved files instead of only downloading them
     * into the local repository.
     * <p>
     * With several artifact items, all of them are resolved in one dependency graph, as if a project declared them
     * as dependencies, so a dependency they share is copied once, in the version chosen by Maven's usual mediation
     * rules. Their own test, provided and optional dependencies are then left out, whereas a single artifact,
     * being the root of the graph, keeps its optional dependencies. No project dependency management applies. All
     * the artifact items must use the same output directory, and no two of them may name the same artifact in
     * different versions. The {@code destFileName} and {@code overWrite} settings of the artifact items are not
     * used: every resolved file is copied under the name built from {@code stripVersion}, {@code stripClassifier},
     * {@code prependGroupId} and {@code useBaseVersion}.
     *
     * @since 3.12.0
     */
    @Parameter(property = "transitive", defaultValue = "false")
    private boolean transitive = false;

    private final ResolverUtil resolverUtil;

    @Inject
    public CopyMojo(
            MavenSession session,
            BuildContext buildContext,
            MavenProject project,
            ArtifactHandlerManager artifactHandlerManager,
            CopyUtil copyUtil,
            RepositorySystem repositorySystem,
            ResolverUtil resolverUtil) {
        super(session, buildContext, project, artifactHandlerManager, repositorySystem, resolverUtil);
        this.copyUtil = copyUtil;
        this.resolverUtil = resolverUtil;
    }

    /**
     * Main entry into mojo. This method gets the ArtifactItems and iterates through each one passing it to
     * copyArtifact.
     *
     * @throws MojoExecutionException with a message if an error occurs
     * @see ArtifactItem
     * @see #getArtifactItems
     * @see #copyArtifact(ArtifactItem)
     */
    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        verifyRequirements();
        verifyExplicitDependencies();

        List<ArtifactItem> theArtifactItems = getProcessedArtifactItems(
                new ProcessArtifactItemsRequest(stripVersion, prependGroupId, useBaseVersion, stripClassifier));

        if (transitive) {
            if (theArtifactItems.size() == 1) {
                copyWithTransitiveDependencies(theArtifactItems.get(0));
            } else {
                copyAllWithTransitiveDependencies(theArtifactItems);
            }
            return;
        }

        for (ArtifactItem artifactItem : theArtifactItems) {
            if (artifactItem.isNeedsProcessing()) {
                copyArtifact(artifactItem);
            } else {
                getLog().info(artifactItem + " already exists in " + artifactItem.getOutputDirectory());
            }
        }
    }

    /**
     * Resolves {@code rootItem}'s artifact together with its transitive dependencies, directly through the
     * repository system, and copies every one of them to {@code rootItem}'s output directory.
     *
     * @param rootItem the single configured artifact
     * @throws MojoExecutionException if the artifact or one of its dependencies cannot be resolved, or if one of
     *                                 them cannot be copied
     */
    private void copyWithTransitiveDependencies(ArtifactItem rootItem) throws MojoExecutionException {
        org.eclipse.aether.artifact.Artifact rootArtifact = RepositoryUtils.toArtifact(rootItem.getArtifact());
        List<RemoteRepository> repositories = getRemoteRepositories();

        List<org.eclipse.aether.artifact.Artifact> resolved;
        try {
            resolved = resolverUtil.resolveDependencies(rootArtifact, repositories);
        } catch (DependencyResolutionException e) {
            throw new MojoExecutionException("Failed to resolve " + rootArtifact + " with its dependencies", e);
        }

        copyResolved(resolved, rootItem.getOutputDirectory());
    }

    /**
     * Resolves the artifacts of all {@code items} together with their transitive dependencies in one dependency
     * graph, and copies every resolved artifact to the items' common output directory.
     *
     * @param items the configured artifact items, more than one
     * @throws MojoFailureException   if the items use different output directories, or name one artifact in two
     *                                 versions
     * @throws MojoExecutionException if an artifact or one of its dependencies cannot be resolved, or if one of them
     *                                 cannot be copied
     */
    private void copyAllWithTransitiveDependencies(List<ArtifactItem> items)
            throws MojoExecutionException, MojoFailureException {
        File outputDirectory = items.get(0).getOutputDirectory();
        Map<String, ArtifactItem> byKey = new HashMap<>();
        List<org.eclipse.aether.artifact.Artifact> roots = new ArrayList<>();
        for (ArtifactItem item : items) {
            if (!normalized(outputDirectory).equals(normalized(item.getOutputDirectory()))) {
                throw new MojoFailureException("transitive requires all artifactItems to use the same outputDirectory,"
                        + " but " + items.get(0) + " uses " + outputDirectory + " and " + item + " uses "
                        + item.getOutputDirectory());
            }
            org.eclipse.aether.artifact.Artifact root = RepositoryUtils.toArtifact(item.getArtifact());
            String key = root.getGroupId()
                    + ':'
                    + root.getArtifactId()
                    + ':'
                    + root.getExtension()
                    + ':'
                    + root.getClassifier();
            ArtifactItem previous = byKey.putIfAbsent(key, item);
            if (previous == null) {
                roots.add(root);
            } else if (!previous.getArtifact()
                    .getVersion()
                    .equals(item.getArtifact().getVersion())) {
                throw new MojoFailureException("transitive cannot copy " + previous + " and " + item
                        + ": they name the same artifact in different versions");
            }
        }

        List<org.eclipse.aether.artifact.Artifact> resolved;
        try {
            resolved = resolverUtil.resolveArtifactsAndDependencies(
                    roots, getRemoteRepositories(), null, session.getRepositorySession());
        } catch (DependencyResolutionException e) {
            throw new MojoExecutionException("Failed to resolve " + roots + " with their dependencies", e);
        }

        copyResolved(resolved, outputDirectory);
    }

    private static Path normalized(File directory) {
        return directory.toPath().toAbsolutePath().normalize();
    }

    private void copyResolved(List<org.eclipse.aether.artifact.Artifact> resolved, File outputDirectory)
            throws MojoExecutionException {
        for (org.eclipse.aether.artifact.Artifact resolvedArtifact : resolved) {
            Artifact artifactToCopy = RepositoryUtils.toArtifact(resolvedArtifact);
            String destFileName = DependencyUtil.getFormattedFileName(
                    artifactToCopy, stripVersion, prependGroupId, useBaseVersion, stripClassifier);
            File destFile = new File(outputDirectory, destFileName);
            if (destFile.exists()) {
                getLog().warn("Overwriting " + destFile);
            }
            try {
                copyUtil.copyArtifactFile(artifactToCopy, destFile);
            } catch (IOException e) {
                throw new MojoExecutionException(
                        "Failed to copy artifact '" + artifactToCopy + "' (" + artifactToCopy.getFile() + ") to "
                                + destFile,
                        e);
            }
        }
    }

    /**
     * Resolves the artifact from the repository and copies it to the specified location.
     *
     * @param artifactItem containing the information about the artifact to copy
     * @throws MojoExecutionException with a message if an error occurs
     * @see CopyUtil#copyArtifactFile(Artifact, File)
     */
    protected void copyArtifact(ArtifactItem artifactItem) throws MojoExecutionException {
        File destFile = new File(artifactItem.getOutputDirectory(), artifactItem.getDestFileName());
        if (destFile.exists()) {
            getLog().warn("Overwriting " + destFile);
        }
        try {
            copyUtil.copyArtifactFile(artifactItem.getArtifact(), destFile);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failed to copy artifact '" + artifactItem.getArtifact() + "' ("
                            + artifactItem.getArtifact().getFile() + ") to " + destFile,
                    e);
        }
    }

    @Override
    protected ArtifactItemFilter getMarkedArtifactFilter(ArtifactItem item) {
        return new DestFileFilter(
                this.isOverWriteReleases(),
                this.isOverWriteSnapshots(),
                this.isOverWriteIfNewer(),
                false,
                false,
                false,
                false,
                this.stripVersion,
                prependGroupId,
                useBaseVersion,
                item.getOutputDirectory());
    }

    /**
     * @return returns the stripVersion
     */
    public boolean isStripVersion() {
        return this.stripVersion;
    }

    /**
     * @param stripVersion the stripVersion to set
     */
    public void setStripVersion(boolean stripVersion) {
        this.stripVersion = stripVersion;
    }

    /**
     * @return returns the stripClassifier
     */
    public boolean isStripClassifier() {
        return this.stripClassifier;
    }

    /**
     * @param stripClassifier the stripClassifier to set
     */
    public void setStripClassifier(boolean stripClassifier) {
        this.stripClassifier = stripClassifier;
    }

    /**
     * @param useBaseVersion the useBaseVersion to set
     */
    public void setUseBaseVersion(boolean useBaseVersion) {
        this.useBaseVersion = useBaseVersion;
    }

    /**
     * @return returns the transitive
     */
    public boolean isTransitive() {
        return this.transitive;
    }

    /**
     * @param transitive the transitive to set
     */
    public void setTransitive(boolean transitive) {
        this.transitive = transitive;
    }
}
