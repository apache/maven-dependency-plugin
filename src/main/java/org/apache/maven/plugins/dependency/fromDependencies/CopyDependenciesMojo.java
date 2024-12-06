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
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.utils.CopyUtil;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.filters.DestFileFilter;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.apache.maven.shared.transfer.artifact.install.ArtifactInstaller;
import org.apache.maven.shared.transfer.artifact.install.ArtifactInstallerException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * Goal that copies the files for a project's dependencies from the repository to a directory.
 * The default location to copy to is target/dependencies.
 * Since all files are copied to the same directory by default, a dependency that
 * has the same file name as another dependency will be overwritten.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 1.0
 */
// CHECKSTYLE_OFF: LineLength
@Mojo(
        name = "copy-dependencies",
        requiresDependencyResolution = ResolutionScope.TEST,
        defaultPhase = LifecyclePhase.PROCESS_SOURCES,
        threadSafe = true)
// CHECKSTYLE_ON: LineLength
public class CopyDependenciesMojo extends AbstractFromDependenciesMojo {
    /**
     * Also copy the pom of each artifact.
     *
     * @since 2.0
     */
    @Parameter(property = "mdep.copyPom", defaultValue = "false")
    protected boolean copyPom;

    private final CopyUtil copyUtil;

    private final ArtifactInstaller installer;

    @Inject
    public CopyDependenciesMojo(CopyUtil copyUtil, ArtifactInstaller installer) {
        this.copyUtil = copyUtil;
        this.installer = installer;
    }

    /**
     * Either append the artifact's baseVersion or uniqueVersion to the filename. Will only be used if
     * {@link #isStripVersion()} is {@code false}.
     *
     * @since 2.6
     */
    @Parameter(property = "mdep.useBaseVersion", defaultValue = "true")
    protected boolean useBaseVersion = true;

    /**
     * Add parent poms to the list of copied dependencies (both current project pom parents and dependencies parents).
     *
     * @since 2.8
     */
    @Parameter(property = "mdep.addParentPoms", defaultValue = "false")
    protected boolean addParentPoms;

    /**
     * Main entry into mojo. Gets the list of dependencies and iterates through calling copyArtifact.
     *
     * @throws MojoExecutionException with a message if an error occurs
     * @see #getDependencySets(boolean, boolean)
     * @see #copyArtifact(Artifact, boolean, boolean, boolean, boolean)
     */
    @Override
    protected void doExecute() throws MojoExecutionException {
        DependencyStatusSets dss = getDependencySets(this.failOnMissingClassifierArtifact, addParentPoms);
        Set<Artifact> artifacts = dss.getResolvedDependencies();

        if (!useRepositoryLayout) {
            Map<String, Integer> copies = new HashMap<>();
            for (Artifact artifactItem : artifacts) {
                String destFileName = DependencyUtil.getFormattedFileName(
                        artifactItem, stripVersion, prependGroupId, useBaseVersion, stripClassifier);
                int numCopies = copies.getOrDefault(destFileName, 0);
                copies.put(destFileName, numCopies + 1);
            }
            for (Map.Entry<String, Integer> entry : copies.entrySet()) {
                if (entry.getValue() > 1) {
                    getLog().warn("Multiple files with the name " + entry.getKey() + " in the dependency tree.");
                    getLog().warn(
                                    "Not all JARs will be available. Consider using prependGroupId, useSubDirectoryPerArtifact, or useRepositoryLayout.");
                }
            }

            for (Artifact artifact : artifacts) {
                copyArtifact(
                        artifact, isStripVersion(), this.prependGroupId, this.useBaseVersion, this.stripClassifier);
            }
        } else {
            ProjectBuildingRequest buildingRequest = getRepositoryManager()
                    .setLocalRepositoryBasedir(session.getProjectBuildingRequest(), outputDirectory);

            artifacts.forEach(artifact -> installArtifact(artifact, buildingRequest));
        }

        Set<Artifact> skippedArtifacts = dss.getSkippedDependencies();
        for (Artifact artifact : skippedArtifacts) {
            getLog().info(artifact.getId() + " already exists in destination.");
        }

        if (isCopyPom() && !useRepositoryLayout) {
            copyPoms(getOutputDirectory(), artifacts, this.stripVersion);
            copyPoms(getOutputDirectory(), skippedArtifacts, this.stripVersion, this.stripClassifier);
            // Artifacts that already exist may not yet have poms
        }
    }

    /**
     * Install the artifact and the corresponding pom if copyPoms=true.
     */
    private void installArtifact(Artifact artifact, ProjectBuildingRequest buildingRequest) {
        try {
            installer.install(buildingRequest, Collections.singletonList(artifact));
            installBaseSnapshot(artifact, buildingRequest);

            if (!"pom".equals(artifact.getType()) && isCopyPom()) {
                Artifact pomArtifact = getResolvedPomArtifact(artifact);
                if (pomArtifact != null
                        && pomArtifact.getFile() != null
                        && pomArtifact.getFile().exists()) {
                    installer.install(buildingRequest, Collections.singletonList(pomArtifact));
                    installBaseSnapshot(pomArtifact, buildingRequest);
                }
            }
        } catch (ArtifactInstallerException e) {
            getLog().warn("unable to install " + artifact, e);
        }
    }

    private void installBaseSnapshot(Artifact artifact, ProjectBuildingRequest buildingRequest)
            throws ArtifactInstallerException {
        if (artifact.isSnapshot() && !artifact.getBaseVersion().equals(artifact.getVersion())) {
            String version = artifact.getVersion();
            try {
                artifact.setVersion(artifact.getBaseVersion());
                installer.install(buildingRequest, Collections.singletonList(artifact));
            } finally {
                artifact.setVersion(version);
            }
        }
    }

    /**
     * Copies the Artifact after building the destination file name if overridden. This method also checks if the
     * classifier is set and adds it to the destination file name if needed.
     *
     * @param artifact representing the object to be copied.
     * @param removeVersion specifies if the version should be removed from the file name when copying.
     * @param prependGroupId specifies if the groupId should be prepend to the file while copying.
     * @param theUseBaseVersion specifies if the baseVersion of the artifact should be used instead of the version.
     * @throws MojoExecutionException with a message if an error occurs.
     * @see #copyArtifact(Artifact, boolean, boolean, boolean, boolean)
     */
    protected void copyArtifact(
            Artifact artifact, boolean removeVersion, boolean prependGroupId, boolean theUseBaseVersion)
            throws MojoExecutionException {
        copyArtifact(artifact, removeVersion, prependGroupId, theUseBaseVersion, false);
    }

    /**
     * Copies the Artifact after building the destination file name if overridden. This method also checks if the
     * classifier is set and adds it to the destination file name if needed.
     *
     * @param artifact representing the object to be copied.
     * @param removeVersion specifies if the version should be removed from the file name when copying.
     * @param prependGroupId specifies if the groupId should be prepend to the file while copying.
     * @param useBaseVersion specifies if the baseVersion of the artifact should be used instead of the version.
     * @param removeClassifier specifies if the classifier should be removed from the file name when copying.
     * @throws MojoExecutionException with a message if an error occurs.
     * @see CopyUtil#copyArtifactFile(Artifact, File)
     * @see DependencyUtil#getFormattedOutputDirectory(boolean, boolean, boolean, boolean, boolean, boolean, File, Artifact)
     */
    protected void copyArtifact(
            Artifact artifact,
            boolean removeVersion,
            boolean prependGroupId,
            boolean useBaseVersion,
            boolean removeClassifier)
            throws MojoExecutionException {

        String destFileName = DependencyUtil.getFormattedFileName(
                artifact, removeVersion, prependGroupId, useBaseVersion, removeClassifier);

        File destDir = DependencyUtil.getFormattedOutputDirectory(
                useSubDirectoryPerScope,
                useSubDirectoryPerType,
                useSubDirectoryPerArtifact,
                useRepositoryLayout,
                stripVersion,
                stripType,
                outputDirectory,
                artifact);
        File destFile = new File(destDir, destFileName);
        if (destFile.exists()) {
            getLog().warn("Overwriting " + destFile);
        }
        try {
            copyUtil.copyArtifactFile(artifact, destFile);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failed to copy artifact '" + artifact + "' (" + artifact.getFile() + ") to " + destFile, e);
        }
    }

    /**
     * Copy the pom files associated with the artifacts.
     *
     * @param destDir The destination directory {@link File}.
     * @param artifacts The artifacts {@link Artifact}.
     * @param removeVersion remove version or not.
     * @throws MojoExecutionException in case of errors.
     */
    public void copyPoms(File destDir, Set<Artifact> artifacts, boolean removeVersion) throws MojoExecutionException {

        copyPoms(destDir, artifacts, removeVersion, false);
    }

    /**
     * Copy the pom files associated with the artifacts.
     *
     * @param destDir The destination directory {@link File}.
     * @param artifacts The artifacts {@link Artifact}.
     * @param removeVersion remove version or not.
     * @param removeClassifier remove the classifier or not.
     * @throws MojoExecutionException in case of errors.
     */
    public void copyPoms(File destDir, Set<Artifact> artifacts, boolean removeVersion, boolean removeClassifier)
            throws MojoExecutionException {

        for (Artifact artifact : artifacts) {
            Artifact pomArtifact = getResolvedPomArtifact(artifact);

            // Copy the pom
            if (pomArtifact != null
                    && pomArtifact.getFile() != null
                    && pomArtifact.getFile().exists()) {
                File pomDestFile = new File(
                        destDir,
                        DependencyUtil.getFormattedFileName(
                                pomArtifact, removeVersion, prependGroupId, useBaseVersion, removeClassifier));
                if (!pomDestFile.exists()) {
                    try {
                        copyUtil.copyArtifactFile(pomArtifact, pomDestFile);
                    } catch (IOException e) {
                        throw new MojoExecutionException(
                                "Failed to copy artifact '" + pomArtifact + "' (" + pomArtifact.getFile() + ") to "
                                        + pomDestFile,
                                e);
                    }
                }
            }
        }
    }

    /**
     * @param artifact {@link Artifact}
     * @return {@link Artifact}
     */
    protected Artifact getResolvedPomArtifact(Artifact artifact) {

        Artifact pomArtifact = null;
        // Resolve the pom artifact using repos
        try {
            org.eclipse.aether.artifact.Artifact aArtifact = RepositoryUtils.toArtifact(artifact);
            org.eclipse.aether.artifact.Artifact aPomArtifact = new SubArtifact(aArtifact, null, "pom");
            org.eclipse.aether.artifact.Artifact resolvedPom =
                    getResolverUtil().resolveArtifact(aPomArtifact, getProject().getRemoteProjectRepositories());
            pomArtifact = RepositoryUtils.toArtifact(resolvedPom);
        } catch (ArtifactResolutionException e) {
            getLog().info(e.getMessage());
        }
        return pomArtifact;
    }

    @Override
    protected ArtifactsFilter getMarkedArtifactFilter() {
        return new DestFileFilter(
                this.overWriteReleases,
                this.overWriteSnapshots,
                this.overWriteIfNewer,
                this.useSubDirectoryPerArtifact,
                this.useSubDirectoryPerType,
                this.useSubDirectoryPerScope,
                this.useRepositoryLayout,
                this.stripVersion,
                this.prependGroupId,
                this.useBaseVersion,
                this.outputDirectory);
    }

    /**
     * @return true, if the pom of each artifact must be copied
     */
    public boolean isCopyPom() {
        return this.copyPom;
    }

    /**
     * @param copyPom true if the pom of each artifact must be copied
     */
    public void setCopyPom(boolean copyPom) {
        this.copyPom = copyPom;
    }
}
