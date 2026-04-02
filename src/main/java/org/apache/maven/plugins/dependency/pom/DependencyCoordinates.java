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
 * {@code groupId:artifactId[:version]} or
 * {@code groupId:artifactId[:extension[:classifier]]:version}.
 *
 * <p>This follows the standard Maven coordinate convention used by
 * {@code org.eclipse.aether.artifact.DefaultArtifact}. Scope and optional
 * are not part of coordinates and must be specified as separate parameters.</p>
 *
 * @since 3.11.0
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
     * Parses a colon-separated coordinate string following Maven conventions.
     *
     * <p>Supported formats:</p>
     * <ul>
     *   <li>{@code groupId:artifactId} — minimum</li>
     *   <li>{@code groupId:artifactId:version}</li>
     *   <li>{@code groupId:artifactId:extension:version}</li>
     *   <li>{@code groupId:artifactId:extension:classifier:version}</li>
     * </ul>
     *
     * @param gav the coordinate string
     * @return the parsed coordinates
     * @throws IllegalArgumentException if the string has fewer than 2 or more than 5 segments
     */
    public static DependencyCoordinates parse(String gav) {
        if (gav == null || gav.trim().isEmpty()) {
            throw new IllegalArgumentException("GAV string must not be null or empty");
        }
        String[] tokens = gav.split(":", -1);
        if (tokens.length < 2 || tokens.length > 5) {
            throw new IllegalArgumentException(
                    "Invalid GAV format: '" + gav
                            + "'. Expected groupId:artifactId[:version] or groupId:artifactId[:extension[:classifier]]:version");
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
        switch (tokens.length) {
            case 3:
                // g:a:v
                if (!tokens[2].trim().isEmpty()) {
                    coords.version = tokens[2].trim();
                }
                break;
            case 4:
                // g:a:ext:v
                if (!tokens[2].trim().isEmpty()) {
                    coords.type = tokens[2].trim();
                }
                if (!tokens[3].trim().isEmpty()) {
                    coords.version = tokens[3].trim();
                }
                break;
            case 5:
                // g:a:ext:cls:v
                if (!tokens[2].trim().isEmpty()) {
                    coords.type = tokens[2].trim();
                }
                if (!tokens[3].trim().isEmpty()) {
                    coords.classifier = tokens[3].trim();
                }
                if (!tokens[4].trim().isEmpty()) {
                    coords.version = tokens[4].trim();
                }
                break;
            default:
                break;
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
        if (scope != null && !scope.isEmpty() && !VALID_SCOPES.contains(scope)) {
            throw new IllegalArgumentException("Invalid scope: '" + scope + "'. Valid scopes are: " + VALID_SCOPES);
        }
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
