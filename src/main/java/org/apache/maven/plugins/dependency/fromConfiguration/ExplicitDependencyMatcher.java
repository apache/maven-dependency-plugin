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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;

/**
 * Checks that artifact items correspond to dependencies declared in the project, so that Maven sees them when it
 * orders the reactor.
 * <p>
 * Items and dependencies are compared on groupId, artifactId, extension and classifier. The type is translated with
 * the artifact handler, so {@code type=test-jar} equals {@code type=jar} with {@code classifier=tests}. The type
 * defaults to {@code jar} and an empty classifier is no classifier. A declared dependency of type {@code pom}
 * matches every type and classifier. An item without a version matches regardless of the declared version. A declared
 * version range matches an item version it contains; an item version range is only matched by an identical
 * declaration, because the goal resolves such a range to the highest available version, which may not be the
 * declared one.
 */
final class ExplicitDependencyMatcher {
    private final ArtifactHandlerManager artifactHandlerManager;

    private final List<Dependency> declared;

    ExplicitDependencyMatcher(ArtifactHandlerManager artifactHandlerManager, List<Dependency> declared) {
        this.artifactHandlerManager = artifactHandlerManager;
        this.declared = declared == null ? new ArrayList<Dependency>() : declared;
    }

    /**
     * Returns one message per item that is not declared, or declared with a different version.
     *
     * @param items the items to check
     * @return the problems, empty if every item is declared
     */
    List<String> findViolations(List<ArtifactItem> items) {
        List<String> violations = new ArrayList<>();
        for (ArtifactItem item : items) {
            String violation = check(item);
            if (violation != null) {
                violations.add(violation);
            }
        }
        return violations;
    }

    private String check(ArtifactItem item) {
        String extension = extension(item.getType());
        String classifier = classifier(item.getType(), item.getClassifier());
        String version = item.getVersion() == null ? "" : item.getVersion().trim();

        List<String> declaredVersions = new ArrayList<>();
        for (Dependency dependency : declared) {
            if (!Objects.equals(item.getGroupId(), dependency.getGroupId())
                    || !Objects.equals(item.getArtifactId(), dependency.getArtifactId())) {
                continue;
            }
            // The reactor orders on groupId:artifactId:version only, so a pom declaration covers every variant.
            boolean sameVariant = "pom".equals(dependency.getType())
                    || (extension.equals(extension(dependency.getType()))
                            && Objects.equals(
                                    classifier, classifier(dependency.getType(), dependency.getClassifier())));
            if (sameVariant) {
                if (version.isEmpty() || versionsMatch(version, dependency.getVersion())) {
                    return null;
                }
                declaredVersions.add(String.valueOf(dependency.getVersion()));
            }
        }
        if (declaredVersions.isEmpty()) {
            return item + " is not declared as a dependency of the project";
        }
        return item + " is declared as a dependency with version " + String.join(", ", declaredVersions) + ", not "
                + version;
    }

    /**
     * Returns whether the project declares a dependency with the groupId and artifactId of the item, of any type,
     * classifier and version.
     *
     * @param item the item to look up
     * @return {@code true} if there is such a dependency
     */
    boolean isDeclaredInAnyVariant(ArtifactItem item) {
        for (Dependency dependency : declared) {
            if (Objects.equals(item.getGroupId(), dependency.getGroupId())
                    && Objects.equals(item.getArtifactId(), dependency.getArtifactId())) {
                return true;
            }
        }
        return false;
    }

    static boolean versionsMatch(String itemVersion, String declaredVersion) {
        if (itemVersion.equals(declaredVersion)) {
            return true;
        }
        return declaredVersion != null && contains(declaredVersion, itemVersion);
    }

    private static boolean contains(String rangeSpec, String version) {
        if (!rangeSpec.startsWith("[") && !rangeSpec.startsWith("(")) {
            return false;
        }
        try {
            return VersionRange.createFromVersionSpec(rangeSpec).containsVersion(new DefaultArtifactVersion(version));
        } catch (InvalidVersionSpecificationException e) {
            return false;
        }
    }

    private ArtifactHandler handler(String type) {
        return artifactHandlerManager.getArtifactHandler(type == null || type.isEmpty() ? "jar" : type);
    }

    private String extension(String type) {
        ArtifactHandler handler = handler(type);
        String extension = handler == null ? null : handler.getExtension();
        return extension != null ? extension : type == null || type.isEmpty() ? "jar" : type;
    }

    private String classifier(String type, String classifier) {
        if (classifier != null && !classifier.isEmpty()) {
            return classifier;
        }
        ArtifactHandler handler = handler(type);
        String fromHandler = handler == null ? null : handler.getClassifier();
        return fromHandler == null || fromHandler.isEmpty() ? null : fromHandler;
    }
}
