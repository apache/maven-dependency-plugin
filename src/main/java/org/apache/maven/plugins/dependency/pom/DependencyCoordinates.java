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
package org.apache.maven.plugins.dependency.pom;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents parsed Maven dependency coordinates (GAV + scope/type/classifier).
 * Supports parsing from a colon-separated string in the format:
 * {@code groupId:artifactId[:version[:scope[:type[:classifier]]]]}.
 */
public class DependencyCoordinates {

    private static final Set<String> VALID_SCOPES =
            new HashSet<>(Arrays.asList("compile", "provided", "runtime", "test", "system", "import"));

    private String groupId;
    private String artifactId;
    private String version;
    private String scope;
    private String type;
    private String classifier;
    private Boolean optional;

    public DependencyCoordinates() {}

    public DependencyCoordinates(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    /**
     * Parses a colon-separated GAV string.
     *
     * @param gav the coordinate string in format {@code groupId:artifactId[:version[:scope[:type[:classifier]]]]}
     * @return the parsed coordinates
     * @throws IllegalArgumentException if the string has fewer than 2 or more than 6 segments
     */
    public static DependencyCoordinates parse(String gav) {
        if (gav == null || gav.trim().isEmpty()) {
            throw new IllegalArgumentException("GAV string must not be null or empty");
        }
        // Use split with -1 limit to preserve trailing empty segments for validation
        String[] tokens = gav.split(":", -1);
        if (tokens.length < 2 || tokens.length > 6) {
            throw new IllegalArgumentException("Invalid GAV format: '" + gav
                    + "'. Expected groupId:artifactId[:version[:scope[:type[:classifier]]]]");
        }
        DependencyCoordinates coords = new DependencyCoordinates();
        coords.groupId = tokens[0].trim();
        coords.artifactId = tokens[1].trim();
        if (coords.groupId.isEmpty()) {
            throw new IllegalArgumentException("Invalid GAV format: '" + gav + "'. groupId must not be empty.");
        }
        if (coords.artifactId.isEmpty()) {
            throw new IllegalArgumentException("Invalid GAV format: '" + gav + "'. artifactId must not be empty.");
        }
        if (tokens.length >= 3 && !tokens[2].trim().isEmpty()) {
            coords.version = tokens[2].trim();
        }
        if (tokens.length >= 4 && !tokens[3].trim().isEmpty()) {
            coords.scope = tokens[3].trim();
        }
        if (tokens.length >= 5 && !tokens[4].trim().isEmpty()) {
            coords.type = tokens[4].trim();
        }
        if (tokens.length >= 6 && !tokens[5].trim().isEmpty()) {
            coords.classifier = tokens[5].trim();
        }
        return coords;
    }

    /**
     * Validates that required fields are present.
     *
     * @throws IllegalArgumentException if groupId or artifactId is missing
     */
    public void validate() {
        if (groupId == null || groupId.isEmpty()) {
            throw new IllegalArgumentException("groupId must not be null or empty");
        }
        if (artifactId == null || artifactId.isEmpty()) {
            throw new IllegalArgumentException("artifactId must not be null or empty");
        }
        if (scope != null && !scope.isEmpty() && !"NONE".equals(scope) && !VALID_SCOPES.contains(scope)) {
            throw new IllegalArgumentException(
                    "Invalid scope: '" + scope + "'. Valid scopes are: " + VALID_SCOPES + " (or NONE to clear)");
        }
    }

    /**
     * Returns the management key used to match dependencies: {@code groupId:artifactId:type[:classifier]}.
     * This matches how Maven's {@link org.apache.maven.model.Dependency#getManagementKey()} works.
     */
    public String getManagementKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(groupId).append(':').append(artifactId).append(':');
        sb.append(type != null ? type : "jar");
        if (classifier != null && !classifier.isEmpty()) {
            sb.append(':').append(classifier);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(groupId).append(':').append(artifactId);
        if (version != null) {
            sb.append(':').append(version);
        }
        StringBuilder details = new StringBuilder();
        if (type != null && !"jar".equals(type)) {
            details.append("type=").append(type);
        }
        if (classifier != null && !classifier.isEmpty()) {
            if (details.length() > 0) {
                details.append(", ");
            }
            details.append("classifier=").append(classifier);
        }
        if (scope != null) {
            if (details.length() > 0) {
                details.append(", ");
            }
            details.append("scope=").append(scope);
        }
        if (details.length() > 0) {
            sb.append(" [").append(details).append(']');
        }
        return sb.toString();
    }

    // Getters and setters

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public Boolean getOptional() {
        return optional;
    }

    public void setOptional(Boolean optional) {
        this.optional = optional;
    }
}
