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
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.utils.DuplicateTargetTracker;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.plugins.dependency.utils.UnpackUtil;
import org.apache.maven.plugins.dependency.utils.filters.ArtifactItemFilter;
import org.apache.maven.plugins.dependency.utils.filters.MarkerFileFilter;
import org.apache.maven.plugins.dependency.utils.markers.MarkerHandler;
import org.apache.maven.plugins.dependency.utils.markers.UnpackFileMarkerHandler;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.components.io.filemappers.FileMapper;
import org.eclipse.aether.RepositorySystem;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Goal that retrieves a list of artifacts from the repository and unpacks them in a defined location.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 1.0
 */
@Mojo(name = "unpack", defaultPhase = LifecyclePhase.PROCESS_SOURCES, requiresProject = false, threadSafe = true)
public class UnpackMojo extends AbstractFromConfigurationMojo {

    private final UnpackUtil unpackUtil;

    /**
     * Directory to store flag files after unpack.
     */
    @Parameter(
            property = "markersDirectory",
            defaultValue = "${project.build.directory}/dependency-maven-plugin-markers")
    private File markersDirectory;

    /**
     * A comma separated list of file patterns to include when unpacking the artifact. i.e.
     * <code>**&#47;*.xml,**&#47;*.properties</code> NOTE: Excludes patterns override the includes. (component code =
     * <code>return isIncluded( name ) AND !isExcluded( name );</code>)
     *
     * @since 2.0-alpha-5
     */
    @Parameter(property = "mdep.unpack.includes")
    private String includes;

    /**
     * A comma separated list of file patterns to exclude when unpacking the artifact. i.e. **\/*.xml,**\/*.properties
     * <code>**&#47;*.xml,**&#47;*.properties</code> NOTE: Excludes patterns override the includes. (component code =
     * <code>return isIncluded( name ) AND !isExcluded( name );</code>)
     *
     * @since 2.0-alpha-5
     */
    @Parameter(property = "mdep.unpack.excludes")
    private String excludes;

    /**
     * Whether to apply the default excludes (for example {@code .svn}, {@code CVS} and {@code .git}) when excludes
     * are set. Set to {@code false} to unpack such files and directories as well.
     *
     * @since 3.12.0
     */
    @Parameter(property = "mdep.unpack.useDefaultExcludes", defaultValue = "true")
    private boolean useDefaultExcludes = true;

    /**
     * Whether unpacking may replace files that already exist in the output directory. This is decided for each file.
     * With {@code true} (the default) every entry of the archive is written. With {@code false} an entry is skipped
     * whenever a file already exists at its target path, after the {@code fileMappers} have been applied, however old
     * that file is; only the missing files are written, so when several entries or dependencies map to the same path,
     * the first one wins. Directories are not affected. The option has no effect on
     * single-file compression formats such as gzip.
     * <p>
     * This differs from {@code overWriteReleases}, {@code overWriteSnapshots} and {@code overWriteIfNewer}, and from
     * the {@code overWrite} of an {@code artifactItem}, which are decided per artifact (together with the marker
     * files) and only determine whether an artifact is unpacked at all. {@code overWriteFiles} applies to the files of
     * an artifact that is unpacked.
     * </p>
     *
     * Can also be set for a single {@code artifactItem}, which then takes precedence.
     *
     * @since 3.12.0
     */
    @Parameter(property = "mdep.unpack.overWriteFiles", defaultValue = "true")
    private boolean overWriteFiles = true;

    /**
     * Whether to fail when, within this execution, two entries are unpacked to the same path of the output directory:
     * from two different artifacts, or twice from one archive. Paths are compared after the {@code fileMappers} have
     * been applied; directories are not compared. The first entry is written, a later one is not, and the build fails
     * after all artifacts are processed, naming each path and the entries that target it. This also applies with
     * {@code overWriteFiles} set to {@code false}. The marker files are then not written, so a repeated execution
     * unpacks again.
     * Names that differ only by case are found on file systems that ignore case, such as on macOS and Windows, and so
     * are paths that reach the output directory through a symbolic link. Every jar delivers {@code META-INF/MANIFEST.MF},
     * and often {@code module-info.class} and {@code META-INF/LICENSE} and {@code NOTICE}, so with
     * {@code unpack-dependencies} use excludes such as {@code META-INF/**,module-info.class}.
     * <p>
     * Files that already exist in the output directory before the execution are not duplicates; use
     * {@code overWriteFiles} for those. Artifacts skipped because they are already unpacked (see the marker files)
     * are not compared, and the option has no effect on single-file compression formats such as gzip. With the
     * option set, a failed execution leaves none of its artifacts marked, as a repeated execution has to detect the
     * duplicate again.
     * </p>
     *
     * @since 3.12.0
     */
    @Parameter(property = "mdep.unpack.failOnDuplicates", defaultValue = "false")
    private boolean failOnDuplicates;

    /**
     * Ignore to set file permissions when unpacking a dependency.
     *
     * @since 2.7
     */
    @Parameter(property = "dependency.ignorePermissions", defaultValue = "false")
    private boolean ignorePermissions;

    /**
     * {@code FileMapper} to be used for rewriting each target path, or {@code null} if no rewriting shall happen.
     *
     * @since 3.1.2
     */
    @Parameter(property = "mdep.unpack.filemappers")
    private FileMapper[] fileMappers;

    /**
     * The artifact to unpack from command line. A string of the form
     * <code>groupId:artifactId:version[:packaging[:classifier]]</code>, or, since 3.12.0, the form that Maven's error
     * messages and <code>dependency:list</code> print, <code>groupId:artifactId:type[:classifier]:version[:scope]</code>.
     * The form is told apart by which parts look like a version, as described for the <code>artifact</code>
     * parameter of <code>dependency:get</code>. The goal shares the parser of <code>dependency:copy</code>, which also
     * accepts a trailing <code>:destFileName</code>; unpack ignores it. Use {@link #artifactItems} within the POM
     * configuration.
     */
    @SuppressWarnings("unused") // marker-field, setArtifact(String) does the magic
    @Parameter(property = "artifact")
    private String artifact;

    @Inject
    public UnpackMojo(
            MavenSession session,
            BuildContext buildContext,
            MavenProject project,
            ArtifactHandlerManager artifactHandlerManager,
            UnpackUtil unpackUtil,
            RepositorySystem repositorySystem,
            ResolverUtil resolverUtil) {
        super(session, buildContext, project, artifactHandlerManager, repositorySystem, resolverUtil);
        this.unpackUtil = unpackUtil;
    }

    /**
     * Main entry into mojo. This method gets the ArtifactItems and iterates through each one passing it to
     * unpackArtifact.
     *
     * @throws MojoExecutionException with a message if an error occurs
     * @see ArtifactItem
     * @see #getArtifactItems
     * @see #unpackArtifact(ArtifactItem, DuplicateTargetTracker)
     */
    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        if (isSkip()) {
            return;
        }

        verifyRequirements();
        verifyExplicitDependencies();

        DuplicateTargetTracker tracker = failOnDuplicates ? new DuplicateTargetTracker() : null;
        List<MarkerHandler> handlers = new ArrayList<>();
        List<ArtifactItem> processedItems = getProcessedArtifactItems(false);
        for (ArtifactItem artifactItem : processedItems) {
            if (artifactItem.isNeedsProcessing()) {
                MarkerHandler handler = unpackArtifact(artifactItem, tracker);
                if (tracker == null) {
                    handler.setMarker();
                } else {
                    // only mark as unpacked once it is known that no entry was skipped as a duplicate
                    handlers.add(handler);
                }
            } else {
                this.getLog().info(artifactItem.getArtifact().getFile().getName() + " already unpacked.");
            }
        }

        if (tracker != null) {
            tracker.verify();
            for (MarkerHandler handler : handlers) {
                handler.setMarker();
            }
        }
    }

    /**
     * This method gets the Artifact object and calls DependencyUtil.unpackFile.
     *
     * @param artifactItem containing the information about the Artifact to unpack
     * @param tracker records the unpacked paths, or {@code null} if duplicates are not checked
     * @return the handler for the marker file of the artifact, which the caller sets
     * @throws MojoExecutionException with a message if an error occurs
     * @see #getArtifact
     */
    private MarkerHandler unpackArtifact(ArtifactItem artifactItem, DuplicateTargetTracker tracker)
            throws MojoExecutionException {
        MarkerHandler handler = new UnpackFileMarkerHandler(artifactItem, this.markersDirectory);

        unpackUtil.unpack(
                artifactItem.getArtifact().getFile(),
                artifactItem.getType(),
                artifactItem.getOutputDirectory(),
                artifactItem.getIncludes(),
                artifactItem.getExcludes(),
                useDefaultExcludes,
                artifactItem.getOverWriteFiles() != null ? artifactItem.getOverWriteFiles() : overWriteFiles,
                artifactItem.getEncoding(),
                ignorePermissions,
                artifactItem.getFileMappers(),
                tracker,
                artifactItem.getArtifact().getId(),
                getLog());
        return handler;
    }

    @Override
    ArtifactItemFilter getMarkedArtifactFilter(ArtifactItem item) {
        MarkerHandler handler = new UnpackFileMarkerHandler(item, this.markersDirectory);

        return new MarkerFileFilter(
                this.isOverWriteReleases(), this.isOverWriteSnapshots(), this.isOverWriteIfNewer(), handler);
    }

    /**
     * @param removeVersion removeVersion
     * @return list of {@link ArtifactItem}
     * @throws MojoExecutionException in case of an error
     */
    protected List<ArtifactItem> getProcessedArtifactItems(boolean removeVersion) throws MojoExecutionException {
        return super.getProcessedArtifactItems(new ProcessArtifactItemsRequest(removeVersion, false, false, false));
    }

    @Override
    void applyMojoConfiguration(ArtifactItem artifactItem) {
        if (artifactItem.getIncludes().isEmpty()) {
            artifactItem.setIncludes(getIncludes());
        }
        if (artifactItem.getExcludes().isEmpty()) {
            artifactItem.setExcludes(getExcludes());
        }
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

    /**
     * @return returns a comma separated list of excluded items
     */
    public String getExcludes() {
        return this.excludes;
    }

    /**
     * @param excludes a comma separated list of items to exclude i.e. **\/*.xml, **\/*.properties
     */
    public void setExcludes(String excludes) {
        this.excludes = excludes;
    }

    /**
     * @param overWriteFiles whether files that already exist in the output directory may be replaced
     * @since 3.12.0
     */
    public void setOverWriteFiles(boolean overWriteFiles) {
        this.overWriteFiles = overWriteFiles;
    }

    /**
     * @param failOnDuplicates whether to fail when several entries are unpacked to the same path
     * @since 3.12.0
     */
    public void setFailOnDuplicates(boolean failOnDuplicates) {
        this.failOnDuplicates = failOnDuplicates;
    }

    /**
     * @param useDefaultExcludes whether the default excludes are applied when excludes are set
     */
    public void setUseDefaultExcludes(boolean useDefaultExcludes) {
        this.useDefaultExcludes = useDefaultExcludes;
    }

    /**
     * @return returns a comma separated list of included items
     */
    public String getIncludes() {
        return this.includes;
    }

    /**
     * @param includes a comma separated list of items to include i.e. **\/*.xml, **\/*.properties
     */
    public void setIncludes(String includes) {
        this.includes = includes;
    }

    /**
     * @return {@link FileMapper}s to be used for rewriting each target path, or {@code null} if no rewriting shall
     *         happen
     * @since 3.1.2
     */
    public FileMapper[] getFileMappers() {
        return this.fileMappers;
    }

    /**
     * @param fileMappers {@link FileMapper}s to be used for rewriting each target path, or {@code null} if no
     * rewriting shall happen
     * @since 3.1.2
     */
    public void setFileMappers(FileMapper[] fileMappers) {
        this.fileMappers = fileMappers;
    }
}
