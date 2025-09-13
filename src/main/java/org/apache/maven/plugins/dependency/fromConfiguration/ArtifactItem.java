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

import java.io.File;
import java.util.Objects;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;
import org.codehaus.plexus.components.io.filemappers.FileMapper;

/**
 * ArtifactItem represents information specified in the plugin configuration section for each artifact.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 1.0
 */
public class ArtifactItem implements DependableCoordinate {
    /**
     * Group ID of artifact.
     */
    @Parameter(required = true)
    private String groupId;

    /**
     * Name of artifact.
     */
    @Parameter(required = true)
    private String artifactId;

    /**
     * Version of artifact.
     */
    @Parameter
    private String version = null;

    /**
     * Type of artifact (War, Jar, etc.)
     */
    @Parameter(required = true)
    private String type = "jar";

    /**
     * Classifier for artifact (tests, sources, etc.)
     */
    @Parameter
    private String classifier;

    /**
     * Location to use for this artifact. Overrides default location.
     */
    @Parameter
    private File outputDirectory;

    /**
     * Provides ability to change destination file name.
     */
    @Parameter
    private String destFileName;

    /**
     * Force Overwrite. This is the one to set in pom.
     */
    private String overWrite;

    /**
     * Encoding of artifact. Overrides default encoding.
     */
    @Parameter
    private String encoding;

    private boolean needsProcessing;

    /**
     * Artifact Item.
     */
    private Artifact artifact;

    /**
     * A comma separated list of file patterns to include when unpacking the artifact.
     */
    private String includes;

    /**
     * A comma separated list of file patterns to exclude when unpacking the artifact.
     */
    private String excludes;

    /**
     * {@link FileMapper}s to be used for rewriting each target path, or {@code null} if no rewriting shall happen.
     *
     * @since 3.1.2
     */
    @Parameter
    private FileMapper[] fileMappers;

    /**
     * Default constructor.
     */
    public ArtifactItem() {
        // default constructor
    }

    /**
     * @param artifact {@link Artifact}
     */
    public ArtifactItem(Artifact artifact) {
        this.setArtifact(artifact);
        this.setArtifactId(artifact.getArtifactId());
        this.setClassifier(artifact.getClassifier());
        this.setGroupId(artifact.getGroupId());
        this.setType(artifact.getType());
        this.setVersion(artifact.getVersion());
    }

    private String filterEmptyString(String in) {
        if ("".equals(in)) {
            return null;
        }
        return in;
    }

    /**
     * @return returns the artifact ID
     */
    @Override
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * @param theArtifact the artifact ID to set
     */
    public void setArtifactId(String theArtifact) {
        this.artifactId = filterEmptyString(theArtifact);
    }

    /**
     * @return returns the group ID
     */
    @Override
    public String getGroupId() {
        return groupId;
    }

    /**
     * @param groupId the group ID to set
     */
    public void setGroupId(String groupId) {
        this.groupId = filterEmptyString(groupId);
    }

    /**
     * @return returns the type
     */
    @Override
    public String getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = filterEmptyString(type);
    }

    /**
     * @return returns the version
     */
    @Override
    public String getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(String version) {
        this.version = filterEmptyString(version);
    }

    /**
     * @return teturns the base version
     */
    public String getBaseVersion() {
        return ArtifactUtils.toSnapshotVersion(version);
    }

    /**
     * @return classifier
     */
    @Override
    public String getClassifier() {
        return classifier;
    }

    /**
     * @param classifier classifier
     */
    public void setClassifier(String classifier) {
        this.classifier = filterEmptyString(classifier);
    }

    @Override
    public String toString() {
        if (this.classifier == null) {
            return groupId + ":" + artifactId + ":" + Objects.toString(version, "?") + ":" + type;
        } else {
            return groupId + ":" + artifactId + ":" + classifier + ":" + Objects.toString(version, "?") + ":" + type;
        }
    }

    /**
     * @return returns the location
     */
    public File getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * @param outputDirectory the outputDirectory to set
     */
    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * @return returns the location
     */
    public String getDestFileName() {
        return destFileName;
    }

    /**
     * @param destFileName the destination file name to set
     */
    public void setDestFileName(String destFileName) {
        this.destFileName = filterEmptyString(destFileName);
    }

    /**
     * @return returns the needsProcessing
     */
    public boolean isNeedsProcessing() {
        return this.needsProcessing;
    }

    /**
     * @param needsProcessing the needsProcessing to set
     */
    public void setNeedsProcessing(boolean needsProcessing) {
        this.needsProcessing = needsProcessing;
    }

    /**
     * @return teturns the overWriteSnapshots
     */
    public String getOverWrite() {
        return this.overWrite;
    }

    /**
     * @param overWrite the overWrite to set
     */
    public void setOverWrite(String overWrite) {
        this.overWrite = overWrite;
    }

    /**
     * @return returns the encoding
     * @since 3.0
     */
    public String getEncoding() {
        return this.encoding;
    }

    /**
     * @param encoding the encoding to set
     * @since 3.0
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * @return returns the artifact
     */
    public Artifact getArtifact() {
        return this.artifact;
    }

    /**
     * @param artifact the artifact to set
     */
    public void setArtifact(Artifact artifact) {
        this.artifact = artifact;
    }

    /**
     * @return returns a comma separated list of excluded items
     */
    public String getExcludes() {
        return DependencyUtil.cleanToBeTokenizedString(this.excludes);
    }

    /**
     * @param excludes a comma separated list of items to exclude; for example, <code>**\/*.xml, **\/*.properties</code>
     */
    public void setExcludes(String excludes) {
        this.excludes = excludes;
    }

    /**
     * @return returns a comma separated list of items to include
     */
    public String getIncludes() {
        return DependencyUtil.cleanToBeTokenizedString(this.includes);
    }

    /**
     * @param includes  comma separated list of items to include; for example, <code>**\/*.xml, **\/*.properties</code>
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
