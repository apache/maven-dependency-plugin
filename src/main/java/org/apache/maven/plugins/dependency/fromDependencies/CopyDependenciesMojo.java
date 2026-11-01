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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.utils.CopyUtil;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.ParamArtifact;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.plugins.dependency.utils.filters.DestFileFilter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Goal that copies the files for a project's dependencies from the repository to a directory.
 * The default location to copy to is target/dependencies.
 * Since all files are copied to the same directory by default, a dependency that
 * has the same file name as another dependency will be overwritten.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 1.0
 */
@Mojo(
        name = "copy-dependencies",
        requiresProject = false,
        requiresDependencyCollection = ResolutionScope.TEST,
        defaultPhase = LifecyclePhase.PROCESS_SOURCES,
        threadSafe = true)
public class CopyDependenciesMojo extends AbstractFromDependenciesMojo {
    /**
     * Also copy the pom of each artifact.
     *
     * @since 2.0
     */
    @Parameter(property = "mdep.copyPom", defaultValue = "false")
    protected boolean copyPom;

    private final CopyUtil copyUtil;

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
     * The {@code classifier} and {@code type} parameters do not change parent POM coordinates.
     * Inclusion and exclusion filters still apply.
     *
     * @since 2.8
     */
    @Parameter(property = "mdep.addParentPoms", defaultValue = "false")
    protected boolean addParentPoms;

    /**
     * Add the POMs of the BOMs that the project and its parents import with {@code <scope>import</scope>} in their
     * {@code dependencyManagement} (and of the parents and imports of those BOMs) to the list of copied dependencies,
     * so that the copied POMs stay resolvable. Use it together with {@link #addParentPoms}; it is a separate parameter
     * so that the output of {@code addParentPoms} does not change. Inclusion and exclusion filters still apply. An import whose coordinates cannot be interpolated is skipped with a
     * warning.
     *
     * @since 3.12.0
     */
    @Parameter(property = "mdep.addImportedBoms", defaultValue = "false")
    protected boolean addImportedBoms;

    /**
     * Also copy the signature files (.asc) of each artifact.
     *
     * @since 3.2.0
     */
    @Parameter(property = "mdep.copySignatures", defaultValue = "false")
    protected boolean copySignatures;

    /**
     * Also copy the checksum files ({@code .sha1}, {@code .md5}, {@code .sha256} and {@code .sha512}) that sit next
     * to each artifact and its POM in the local repository. Only used together with {@link #useRepositoryLayout};
     * checksum files that do not exist in the local repository are skipped.
     *
     * @since 3.12.0
     */
    @Parameter(property = "mdep.copyChecksums", defaultValue = "false")
    protected boolean copyChecksums;

    private final ParamArtifact paramArtifact = new ParamArtifact();

    @Inject
    public CopyDependenciesMojo(
            MavenSession session,
            BuildContext buildContext,
            MavenProject project,
            ResolverUtil resolverUtil,
            ProjectBuilder projectBuilder,
            ArtifactHandlerManager artifactHandlerManager,
            CopyUtil copyUtil) {
        super(session, buildContext, project, resolverUtil, projectBuilder, artifactHandlerManager);
        this.copyUtil = copyUtil;
    }

    @Override
    protected boolean isResolvingAfterFiltering() {
        return true;
    }

    /**
     * A string of the form {@code groupId:artifactId:version[:packaging[:classifier]]} identifying a single
     * artifact to resolve, together with its transitive dependencies, instead of this project's own dependencies.
     * When set, the goal needs no {@code pom.xml} at all and can be run directly from the command line in any
     * directory, for example {@code mvn dependency:copy-dependencies -Dartifact=groupId:artifactId:version}.
     * Ignored when not set, in which case this project's dependencies are copied as before.
     *
     * @param artifact the artifact coordinates
     * @since 3.12.0
     */
    @Parameter(property = "artifact")
    public void setArtifact(String artifact) {
        paramArtifact.setArtifact(artifact);
    }

    /**
     * Main entry into mojo. Gets the list of dependencies and iterates through calling copyArtifact.
     *
     * @throws MojoExecutionException with a message if an error occurs
     * @see #getDependencySets(boolean, boolean)
     * @see #copyArtifact(Artifact, boolean, boolean, boolean, boolean)
     */
    @Override
    protected void doExecute() throws MojoExecutionException {
        if (copyChecksums && !useRepositoryLayout) {
            getLog().warn(
                            "copyChecksums only applies together with useRepositoryLayout, no checksum files are copied.");
        }

        Set<Artifact> artifacts;
        Set<Artifact> skippedArtifacts;
        if (paramArtifact.isDataSet()) {
            artifacts = resolveArtifactWithTransitiveDependencies();
            skippedArtifacts = Collections.emptySet();
        } else {
            DependencyStatusSets dss =
                    getDependencySets(this.failOnMissingClassifierArtifact, addParentPoms, addImportedBoms);
            artifacts = dss.getResolvedDependencies();
            skippedArtifacts = dss.getSkippedDependencies();
        }

        copyOrInstall(artifacts, skippedArtifacts);
    }

    /**
     * Resolves the artifact identified by {@link #setArtifact(String)} together with its transitive dependencies,
     * via the repository system directly, without relying on this project's own dependencies.
     *
     * @return the resolved artifact and its transitive dependencies
     * @throws MojoExecutionException if the artifact or one of its dependencies cannot be resolved
     */
    private Set<Artifact> resolveArtifactWithTransitiveDependencies() throws MojoExecutionException {
        org.eclipse.aether.artifact.Artifact rootArtifact = getResolverUtil().createArtifactFromParams(paramArtifact);
        List<RemoteRepository> repositories = getProject().getRemoteProjectRepositories();

        List<org.eclipse.aether.artifact.Artifact> resolved;
        try {
            resolved = getResolverUtil().resolveDependencies(rootArtifact, repositories);
        } catch (DependencyResolutionException e) {
            throw new MojoExecutionException("Failed to resolve " + rootArtifact + " with its dependencies", e);
        }

        if (excludeTransitive) {
            resolved = resolved.stream()
                    .filter(resolvedArtifact -> isSameArtifact(resolvedArtifact, rootArtifact))
                    .collect(Collectors.toList());
        }

        return resolved.stream().map(RepositoryUtils::toArtifact).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean isSameArtifact(
            org.eclipse.aether.artifact.Artifact artifact, org.eclipse.aether.artifact.Artifact other) {
        return artifact.getGroupId().equals(other.getGroupId())
                && artifact.getArtifactId().equals(other.getArtifactId())
                && artifact.getVersion().equals(other.getVersion())
                && artifact.getExtension().equals(other.getExtension())
                && artifact.getClassifier().equals(other.getClassifier());
    }

    /**
     * Copies, or installs in repository layout, the resolved artifacts, then copies their POMs if requested.
     *
     * @param artifacts the artifacts to copy or install
     * @param skippedArtifacts artifacts that already exist in the destination and were not re-copied
     * @throws MojoExecutionException with a message if an error occurs
     */
    private void copyOrInstall(Set<Artifact> artifacts, Set<Artifact> skippedArtifacts) throws MojoExecutionException {
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
            RepositorySystemSession repositorySystemSession = getResolverUtil().localRepositorySession(outputDirectory);

            artifacts.forEach(artifact -> installArtifact(artifact, repositorySystemSession));
            if (isCopyPom()) {
                // Artifacts that already exist may not yet have poms
                skippedArtifacts.forEach(artifact -> installMissingPom(artifact, repositorySystemSession));
            }
        }

        for (Artifact artifact : skippedArtifacts) {
            getLog().info(artifact.getId() + " already exists in destination.");
        }

        if (isCopyPom() && !useRepositoryLayout) {
            copyPoms(getOutputDirectory(), artifacts, this.stripVersion, this.stripClassifier, true);
            // Artifacts that already exist may not yet have poms
            copyPoms(getOutputDirectory(), skippedArtifacts, this.stripVersion, this.stripClassifier, false);
        }
    }

    /**
     * Install the artifact and the corresponding pom if copyPoms=true.
     */
    private void installArtifact(Artifact artifact, RepositorySystemSession repositorySystemSession) {
        try {
            getResolverUtil().installArtifact(artifact, repositorySystemSession);
            installBaseSnapshot(artifact, repositorySystemSession);
            copyChecksumFiles(artifact, repositorySystemSession);

            if (!"pom".equals(artifact.getType()) && isCopyPom()) {
                Artifact pomArtifact = getResolvedPomArtifact(artifact);
                if (pomArtifact != null
                        && pomArtifact.getFile() != null
                        && pomArtifact.getFile().exists()) {
                    getResolverUtil().installArtifact(pomArtifact, repositorySystemSession);
                    installBaseSnapshot(pomArtifact, repositorySystemSession);
                    copyChecksumFiles(pomArtifact, repositorySystemSession);
                }
            }
        } catch (InstallationException e) {
            getLog().warn("unable to install " + artifact, e);
        }
    }

    /**
     * Install the pom of an artifact that was skipped because it already exists in the destination, unless the pom
     * exists there too.
     */
    private void installMissingPom(Artifact artifact, RepositorySystemSession repositorySystemSession) {
        if ("pom".equals(artifact.getType())) {
            return;
        }
        // Check the destination first, resolving the POM may mean a remote request.
        LocalRepositoryManager localRepositoryManager = repositorySystemSession.getLocalRepositoryManager();
        File pomDestFile = new File(
                localRepositoryManager.getRepository().getBasedir(),
                localRepositoryManager.getPathForLocalArtifact(
                        new SubArtifact(RepositoryUtils.toArtifact(artifact), null, "pom")));
        if (pomDestFile.exists()) {
            return;
        }
        Artifact pomArtifact = getResolvedPomArtifact(artifact);
        if (pomArtifact == null
                || pomArtifact.getFile() == null
                || !pomArtifact.getFile().exists()) {
            return;
        }
        try {
            getResolverUtil().installArtifact(pomArtifact, repositorySystemSession);
            installBaseSnapshot(pomArtifact, repositorySystemSession);
        } catch (InstallationException e) {
            getLog().warn("unable to install " + pomArtifact, e);
        }
    }

    /**
     * Copies the checksum files next to the artifact in the source local repository to the matching location in the
     * target repository layout. The local repository manager names a timestamped snapshot by its base version, as
     * the installed artifact is.
     */
    private void copyChecksumFiles(Artifact artifact, RepositorySystemSession repositorySystemSession) {
        if (!copyChecksums || artifact.getFile() == null) {
            return;
        }
        LocalRepositoryManager localRepositoryManager = repositorySystemSession.getLocalRepositoryManager();
        File baseDir = localRepositoryManager.getRepository().getBasedir();
        org.eclipse.aether.artifact.Artifact aArtifact = RepositoryUtils.toArtifact(artifact);
        copyChecksumFiles(
                artifact.getFile(), new File(baseDir, localRepositoryManager.getPathForLocalArtifact(aArtifact)));
    }

    private void copyChecksumFiles(File source, File destination) {
        for (String extension : CHECKSUM_EXTENSIONS) {
            File checksum = new File(source.getPath() + extension);
            if (checksum.isFile()) {
                try {
                    copyUtil.copyFile(checksum, new File(destination.getPath() + extension));
                } catch (IOException e) {
                    getLog().warn("Failed to copy checksum file: " + checksum, e);
                }
            }
        }
    }

    private void installBaseSnapshot(Artifact artifact, RepositorySystemSession repositorySystemSession)
            throws InstallationException {
        if (artifact.isSnapshot() && !artifact.getBaseVersion().equals(artifact.getVersion())) {
            String version = artifact.getVersion();
            try {
                artifact.setVersion(artifact.getBaseVersion());
                getResolverUtil().installArtifact(artifact, repositorySystemSession);
            } finally {
                artifact.setVersion(version);
            }
        }
    }

    /**
     * Copies the Artifact after building the destination file name if overridden. This method also checks if the
     * classifier is set and adds it to the destination file name if needed.
     *
     * @param artifact the object to be copied
     * @param removeVersion specifies if the version should be removed from the file name when copying
     * @param prependGroupId specifies if the group ID should be prefixed to the file while copying
     * @param theUseBaseVersion specifies if the baseVersion of the artifact should be used instead of the version
     * @throws MojoExecutionException with a message if an error occurs
     * @see #copyArtifact(Artifact, boolean, boolean, boolean, boolean)
     */
    protected void copyArtifact(
            Artifact artifact, boolean removeVersion, boolean prependGroupId, boolean theUseBaseVersion)
            throws MojoExecutionException {
        copyArtifact(artifact, removeVersion, prependGroupId, theUseBaseVersion, false);
    }

    private static final String SIGNATURE_EXTENSION = ".asc";

    private static final String[] CHECKSUM_EXTENSIONS = {".sha1", ".md5", ".sha256", ".sha512"};

    /**
     * Copies the Artifact after building the destination file name if overridden. This method also checks if the
     * classifier is set and adds it to the destination file name if needed.
     *
     * @param artifact the object to be copied
     * @param removeVersion specifies if the version should be removed from the file name when copying
     * @param prependGroupId specifies if the groupId should be prefixed to the file while copying
     * @param useBaseVersion specifies if the baseVersion of the artifact should be used instead of the version
     * @param removeClassifier specifies if the classifier should be removed from the file name when copying
     * @throws MojoExecutionException with a message if an error occurs
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

            // Copy the signature file if the copySignatures flag is true
            if (copySignatures) {
                copySignatureFile(artifact, destDir, destFileName);
            }

        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failed to copy artifact '" + artifact + "' (" + artifact.getFile() + ") to " + destFile, e);
        }
    }

    /**
     * Copies the signature file of the artifact to the destination directory, if it exists or can be resolved.
     * If the signature file does not exist and cannot be resolved, a warning is logged.
     *
     * @param artifact the artifact whose signature file should be copied
     * @param destDir the destination directory
     * @param destFileName the destination file name without the extension
     */
    private void copySignatureFile(Artifact artifact, File destDir, String destFileName) {
        File signatureFile = new File(artifact.getFile().getAbsolutePath() + SIGNATURE_EXTENSION);

        if (!signatureFile.exists()) {
            try {
                org.eclipse.aether.artifact.Artifact aArtifact = RepositoryUtils.toArtifact(artifact);
                org.eclipse.aether.artifact.Artifact aSignatureArtifact =
                        new SubArtifact(aArtifact, null, "jar" + SIGNATURE_EXTENSION);
                org.eclipse.aether.artifact.Artifact resolvedSignature = getResolverUtil()
                        .resolveArtifact(aSignatureArtifact, getProject().getRemoteProjectRepositories());
                signatureFile = resolvedSignature.getFile();
            } catch (ArtifactResolutionException | ArtifactDescriptorException e) {
                getLog().warn("Failed to resolve signature file for artifact: " + artifact, e);
            }
        }

        if (signatureFile != null && signatureFile.exists()) {
            File signatureDestFile = new File(destDir, destFileName + SIGNATURE_EXTENSION);
            try {
                copyUtil.copyFile(signatureFile, signatureDestFile);
            } catch (IOException e) {
                getLog().warn("Failed to copy signature file: " + signatureFile, e);
            }
        } else {
            getLog().warn("Signature file for artifact " + artifact + " not found and could not be resolved.");
        }
    }

    /**
     * Copy the pom files associated with the artifacts.
     *
     * @param destDir the destination directory {@link File}
     * @param artifacts the artifacts {@link Artifact}
     * @param removeVersion remove version or not
     * @throws MojoExecutionException in case of errors
     */
    public void copyPoms(File destDir, Set<Artifact> artifacts, boolean removeVersion) throws MojoExecutionException {

        copyPoms(destDir, artifacts, removeVersion, false);
    }

    /**
     * Copy the pom files associated with the artifacts.
     *
     * @param destDir the destination directory {@link File}
     * @param artifacts the artifacts {@link Artifact}
     * @param removeVersion remove version or not
     * @param removeClassifier remove the classifier or not
     * @throws MojoExecutionException in case of errors
     */
    public void copyPoms(File destDir, Set<Artifact> artifacts, boolean removeVersion, boolean removeClassifier)
            throws MojoExecutionException {
        copyPoms(destDir, artifacts, removeVersion, removeClassifier, false);
    }

    private void copyPoms(
            File destDir, Set<Artifact> artifacts, boolean removeVersion, boolean removeClassifier, boolean overwrite)
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
                if (overwrite || !pomDestFile.exists()) {
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
        } catch (ArtifactResolutionException | ArtifactDescriptorException e) {
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
