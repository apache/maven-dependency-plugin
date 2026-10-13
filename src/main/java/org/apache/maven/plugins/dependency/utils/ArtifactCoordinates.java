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

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The coordinates given as a single string in the <code>artifact</code> parameter of <code>dependency:get</code>,
 * <code>copy</code> and <code>unpack</code>. Two forms are accepted:
 * <dl>
 * <dt>legacy</dt>
 * <dd><code>groupId:artifactId:version[:packaging[:classifier[:destFileName]]]</code>, where
 * <code>destFileName</code> is only accepted where the caller asks for it (<code>copy</code>)</dd>
 * <dt>Maven</dt>
 * <dd><code>groupId:artifactId:type[:classifier]:version[:scope]</code>, which is how Maven prints artifacts in its
 * error messages and how <code>dependency:list</code> prints them</dd>
 * </dl>
 * The forms share their shapes, so the form is decided by which tokens look like a version. A token looks like a
 * version if it starts with a digit, is <code>LATEST</code> or <code>RELEASE</code>, or starts with <code>[</code> or
 * <code>(</code> as a version range does. The string is split at every colon, empty parts included.
 * <ul>
 * <li>3 parts are the legacy form.</li>
 * <li>6 parts are the Maven form <code>groupId:artifactId:type:classifier:version:scope</code> if the sixth is a
 * scope and the fifth looks like a version. Otherwise they are the legacy form with a <code>destFileName</code> if
 * that is allowed and the third looks like a version. Otherwise they are rejected.</li>
 * <li>4 or 5 parts are the Maven form if the part where that form has the version looks like a version:
 * <code>groupId:artifactId:type:version</code> (4 parts), <code>groupId:artifactId:type:classifier:version</code> or
 * <code>groupId:artifactId:type:version:scope</code> (5 parts, told apart by the last part being a scope). They are
 * also the Maven form if the third part is a well-known type (<code>jar</code>, <code>pom</code>,
 * <code>war</code>, ...) and does not look like a version, so that <code>groupId:artifactId:jar:v1.2</code> works.
 * Otherwise they are the legacy form, whatever the parts look like, as it always was.</li>
 * <li>If the third part looks like a version and the Maven form fits as well, the string is rejected, for example
 * <code>groupId:artifactId:1.0:jar:2.0</code>.</li>
 * </ul>
 * The Maven form needs a type and a version. Its scope is checked and then ignored.
 */
public final class ArtifactCoordinates {

    private static final List<String> SCOPES =
            Arrays.asList("compile", "provided", "runtime", "test", "system", "import");

    private static final List<String> WELL_KNOWN_TYPES = Arrays.asList(
            "jar",
            "pom",
            "war",
            "ear",
            "test-jar",
            "maven-plugin",
            "ejb",
            "ejb-client",
            "rar",
            "zip",
            "java-source",
            "javadoc");

    private static final Pattern VERSION_LIKE = Pattern.compile("^(?:\\d.*|LATEST|RELEASE|[\\[(].*)$");

    private final String groupId;

    private final String artifactId;

    private final String version;

    private final String packaging;

    private final String classifier;

    private final String destFileName;

    private ArtifactCoordinates(
            String groupId,
            String artifactId,
            String version,
            String packaging,
            String classifier,
            String destFileName) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packaging = packaging;
        this.classifier = classifier;
        this.destFileName = destFileName;
    }

    /**
     * Parses a string without <code>destFileName</code>.
     *
     * @param tokens the artifact string split at the colons, empty parts included
     * @param artifact the artifact string, for the message
     * @return the coordinates
     * @throws IllegalArgumentException if the tokens fit neither form, or both
     */
    public static ArtifactCoordinates parse(String[] tokens, String artifact) {
        return parse(tokens, artifact, false);
    }

    /**
     * @param tokens the artifact string split at the colons, empty parts included
     * @param artifact the artifact string, for the message
     * @param allowDestFileName whether the legacy form may have a sixth part, the destination file name
     * @return the coordinates
     * @throws IllegalArgumentException if the tokens fit neither form, or both
     */
    public static ArtifactCoordinates parse(String[] tokens, String artifact, boolean allowDestFileName) {
        int n = tokens.length;
        if (n < 3 || n > 6 || tokens[0].isEmpty() || tokens[1].isEmpty()) {
            throw invalid(artifact, "", allowDestFileName);
        }
        if (n == 3) {
            return legacy(tokens, artifact, allowDestFileName);
        }
        if (n == 6) {
            if (isScope(tokens[5]) && isVersionLike(tokens[4])) {
                return maven(tokens, artifact, 2, 3, 4, allowDestFileName);
            }
            if (allowDestFileName && isVersionLike(tokens[2])) {
                return new ArtifactCoordinates(tokens[0], tokens[1], tokens[2], tokens[3], tokens[4], tokens[5]);
            }
            throw invalid(artifact, ", the sixth part is not a scope", allowDestFileName);
        }
        boolean legacyVersion = isVersionLike(tokens[2]);
        int versionIndex = n == 4 || isScope(tokens[4]) ? 3 : 4;
        boolean mavenVersion = isVersionLike(tokens[versionIndex]);
        if (legacyVersion && mavenVersion) {
            throw invalid(artifact, ", both the third and the last part look like the version", allowDestFileName);
        }
        if (mavenVersion || (!legacyVersion && WELL_KNOWN_TYPES.contains(tokens[2]))) {
            return maven(tokens, artifact, 2, versionIndex == 4 ? 3 : -1, versionIndex, allowDestFileName);
        }
        return legacy(tokens, artifact, allowDestFileName);
    }

    private static ArtifactCoordinates maven(
            String[] tokens, String artifact, int typeIndex, int classifierIndex, int versionIndex, boolean dest) {
        if (tokens[typeIndex].isEmpty() || tokens[versionIndex].isEmpty()) {
            throw invalid(artifact, ", the type and the version must not be empty", dest);
        }
        return new ArtifactCoordinates(
                tokens[0],
                tokens[1],
                tokens[versionIndex],
                tokens[typeIndex],
                classifierIndex < 0 ? null : tokens[classifierIndex],
                null);
    }

    private static ArtifactCoordinates legacy(String[] tokens, String artifact, boolean dest) {
        if (tokens[2].isEmpty()) {
            throw invalid(artifact, ", the version must not be empty", dest);
        }
        return new ArtifactCoordinates(
                tokens[0],
                tokens[1],
                tokens[2],
                tokens.length > 3 ? tokens[3] : null,
                tokens.length > 4 ? tokens[4] : null,
                tokens.length > 5 ? tokens[5] : null);
    }

    private static boolean isVersionLike(String token) {
        return VERSION_LIKE.matcher(token).matches();
    }

    private static boolean isScope(String token) {
        return SCOPES.contains(token);
    }

    private static IllegalArgumentException invalid(String artifact, String reason, boolean dest) {
        return new IllegalArgumentException("Invalid artifact format: " + artifact + reason + ", expected "
                + (dest
                        ? "groupId:artifactId:version[:packaging[:classifier[:destFileName]]]"
                        : "groupId:artifactId:version[:packaging[:classifier]]")
                + " or groupId:artifactId:type[:classifier]:version[:scope]");
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    /**
     * @return the packaging or type, or <code>null</code> if the string did not have one
     */
    public String getPackaging() {
        return packaging;
    }

    /**
     * @return the classifier, or <code>null</code> if the string did not have one
     */
    public String getClassifier() {
        return classifier;
    }

    /**
     * @return the destination file name, or <code>null</code> if the string did not have one
     */
    public String getDestFileName() {
        return destFileName;
    }
}
