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
package org.apache.maven.plugins.dependency.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.logging.Log;

/**
 * Utility class with static helper methods.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public final class DependencyUtil {

    /**
     * Builds the file name. If removeVersion is set, then the file name must be reconstructed from the artifactId,
     * Classifier (if used) and Type. Otherwise, this method returns the artifact file name.
     *
     * @param artifact File to be formatted.
     * @param removeVersion Specifies if the version should be removed from the file name.
     * @return Formatted file name in the format artifactId-[version]-[classifier].[type]
     * @see #getFormattedFileName(Artifact, boolean, boolean)
     */
    public static String getFormattedFileName(Artifact artifact, boolean removeVersion) {
        return getFormattedFileName(artifact, removeVersion, false);
    }

    /**
     * Builds the file name. If removeVersion is set, then the file name must be reconstructed from the groupId (if
     * <b>prependGroupId</b> is true) artifactId, Classifier (if used) and Type. Otherwise, this method returns the
     * artifact file name.
     *
     * @param artifact File to be formatted.
     * @param removeVersion Specifies if the version should be removed from the file name.
     * @param prependGroupId Specifies if the groupId should be prepended to the file name.
     * @return Formatted file name in the format [groupId].artifactId-[version]-[classifier].[type]
     */
    public static String getFormattedFileName(Artifact artifact, boolean removeVersion, boolean prependGroupId) {
        return getFormattedFileName(artifact, removeVersion, prependGroupId, false);
    }

    /**
     * Builds the file name. If removeVersion is set, then the file name must be reconstructed from the groupId (if
     * <b>prependGroupId</b> is true) artifactId, Classifier (if used), and Type. Otherwise, this method returns the
     * artifact file name.
     *
     * @param artifact file to be formatted
     * @param removeVersion Specifies if the version should be removed from the file name
     * @param prependGroupId Specifies if the groupId should be prepended to the file name
     * @param useBaseVersion Specifies if the baseVersion of the artifact should be used instead of the version
     * @return Formatted file name in the format [groupId].artifactId-[version]-[classifier].[type]
     */
    public static String getFormattedFileName(
            Artifact artifact, boolean removeVersion, boolean prependGroupId, boolean useBaseVersion) {
        return getFormattedFileName(artifact, removeVersion, prependGroupId, useBaseVersion, false);
    }

    /**
     * Builds the file name. If removeVersion is set, then the file name must be reconstructed from the groupId (if
     * <b>prependGroupId</b> is true) artifactId, Classifier (if used) and Type. Otherwise, this method returns the
     * artifact file name.
     *
     * @param artifact File to be formatted.
     * @param removeVersion Specifies if the version should be removed from the file name.
     * @param prependGroupId Specifies if the groupId should be prepended to the file name.
     * @param useBaseVersion Specifies if the baseVersion of the artifact should be used instead of the version.
     * @param removeClassifier Specifies if the classifier of the artifact should be remved from the file name.
     * @return Formatted file name in the format [groupId].artifactId-[version]-[classifier].[type]
     */
    public static String getFormattedFileName(
            Artifact artifact,
            boolean removeVersion,
            boolean prependGroupId,
            boolean useBaseVersion,
            boolean removeClassifier) {
        StringBuilder destFileName = new StringBuilder();

        if (prependGroupId) {
            destFileName.append(artifact.getGroupId()).append(".");
        }

        String versionString = "";
        if (!removeVersion) {
            if (useBaseVersion) {
                versionString = "-" + ArtifactUtils.toSnapshotVersion(artifact.getVersion());
            } else {
                versionString = "-" + artifact.getVersion();
            }
        }

        String classifierString = "";

        if (!removeClassifier && StringUtils.isNotEmpty(artifact.getClassifier())) {
            classifierString = "-" + artifact.getClassifier();
        }
        destFileName.append(artifact.getArtifactId()).append(versionString);
        destFileName.append(classifierString).append(".");
        destFileName.append(artifact.getArtifactHandler().getExtension());

        return destFileName.toString();
    }

    /**
     * Formats the outputDirectory based on type.
     *
     * @param useSubdirsPerScope if a new sub directory should be used for each scope.
     * @param useSubdirsPerType if a new sub directory should be used for each type.
     * @param useSubdirPerArtifact if a new sub directory should be used for each artifact.
     * @param useRepositoryLayout if dependencies must be moved into a Maven repository layout, if set, other
     *         settings
     *         will be ignored.
     * @param removeVersion if the version must not be mentioned in the filename
     * @param removeType if the type must not be mentioned in the filename
     * @param outputDirectory base outputDirectory.
     * @param artifact information about the artifact.
     * @return a formatted File object to use for output.
     */
    public static File getFormattedOutputDirectory(
            boolean useSubdirsPerScope,
            boolean useSubdirsPerType,
            boolean useSubdirPerArtifact,
            boolean useRepositoryLayout,
            boolean removeVersion,
            boolean removeType,
            File outputDirectory,
            Artifact artifact) {
        StringBuilder sb = new StringBuilder(128);
        if (useRepositoryLayout) {
            // group id
            sb.append(artifact.getGroupId().replace('.', File.separatorChar)).append(File.separatorChar);
            // artifact id
            sb.append(artifact.getArtifactId()).append(File.separatorChar);
            // version
            sb.append(artifact.getBaseVersion()).append(File.separatorChar);
        } else {
            if (useSubdirsPerScope) {
                sb.append(artifact.getScope()).append(File.separatorChar);
            }
            if (useSubdirsPerType) {
                sb.append(artifact.getType()).append("s").append(File.separatorChar);
            }
            if (useSubdirPerArtifact) {
                String artifactString = getDependencyId(artifact, removeVersion, removeType);
                sb.append(artifactString).append(File.separatorChar);
            }
        }
        return new File(outputDirectory, sb.toString());
    }

    private static String getDependencyId(Artifact artifact, boolean removeVersion, boolean removeType) {
        StringBuilder sb = new StringBuilder();

        sb.append(artifact.getArtifactId());

        if (!removeVersion) {
            sb.append("-");
            sb.append(artifact.getVersion());
        }

        if (StringUtils.isNotEmpty(artifact.getClassifier())) {
            sb.append("-");
            sb.append(artifact.getClassifier());
        }

        // if the classifier and type are the same (sources), then don't
        // repeat.
        // avoids names like foo-sources-sources
        if (!removeType && !Objects.equals(artifact.getClassifier(), artifact.getType())) {
            sb.append("-");
            sb.append(artifact.getType());
        }

        return sb.toString();
    }

    /**
     * Writes the specified string to the specified file.
     *
     * @param string the string to write
     * @param file the file to write to
     * @param append append to existing file or not
     * @param log ignored
     * @throws IOException if an I/O error occurs
     * @deprecated specify an encoding instead of a log
     */
    @Deprecated
    public static synchronized void write(String string, File file, boolean append, Log log) throws IOException {
        write(string, file, append, "UTF-8");
    }

    /**
     * Writes the specified string to the specified file.
     *
     * @param string the string to write
     * @param file the file to write to
     * @param append append to existing file or not
     * @param encoding character set name
     * @throws IOException if an I/O error occurs
     */
    public static synchronized void write(String string, File file, boolean append, String encoding)
            throws IOException {
        Files.createDirectories(file.getParentFile().toPath());

        OpenOption appendOption = append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING;

        try (Writer writer = Files.newBufferedWriter(
                file.toPath(),
                Charset.forName(encoding),
                appendOption,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE)) {
            writer.write(string);
        }
    }

    /**
     * Writes each line in the specified string to the log at info level.
     * The difference between calling
     * {@code DependencyUtil.log(s, log)} and {@code log.info(s)} is that the latter
     * will put "[INFO]" in front of each line in the string whereas the former only
     * outputs it once at the front of the string.
     *
     * @param string the string to write
     * @param log where to log information
     * @throws IOException if an I/O error occurs
     */
    public static synchronized void log(String string, Log log) throws IOException {
        try (BufferedReader reader = new BufferedReader(new StringReader(string))) {
            reader.lines().forEach(log::info);
        }
    }

    /**
     * Mainly used to parse excludes, includes configuration.
     *
     * @param str the string to split
     * @return the result items
     */
    public static String[] tokenizer(String str) {
        String s = cleanToBeTokenizedString(str);
        if (s.isEmpty()) {
            return new String[0];
        }
        return cleanToBeTokenizedString(str).split(",");
    }

    /**
     * Clean up configuration string before it can be tokenized.
     *
     * @param str the string which should be cleaned
     * @return cleaned up string
     */
    public static String cleanToBeTokenizedString(String str) {
        String ret = "";
        if (!(str == null || str.isEmpty())) {
            // remove initial and ending spaces, plus all spaces next to commas
            ret = str.trim().replaceAll("\\s*,\\s*", ",");
        }

        return ret;
    }
}
