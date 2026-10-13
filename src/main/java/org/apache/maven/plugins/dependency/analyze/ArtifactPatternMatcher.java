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
package org.apache.maven.plugins.dependency.analyze;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.artifact.filter.StrictPatternExcludesArtifactFilter;

/**
 * Matches artifacts against ignore patterns of the form
 * <code>[groupId]:[artifactId]:[type]:[version]:[scope]</code>.
 * <p>
 * The first four segments are matched by {@link StrictPatternExcludesArtifactFilter}, which silently never matches a
 * pattern with more than four segments. The optional fifth segment is therefore split off and matched here, with the
 * same rules: an empty segment is a wildcard, and <code>*</code> is allowed as a full or partial wildcard.
 */
final class ArtifactPatternMatcher {

    private ArtifactPatternMatcher() {}

    /**
     * Removes artifacts matching any of the given patterns from the source set and returns the removed artifacts.
     *
     * @param artifacts the source set of artifacts (mutated in place)
     * @param patterns  patterns, may be null or empty
     * @return the artifacts that were removed from the source set
     */
    static Set<Artifact> removeMatching(Set<Artifact> artifacts, String[] patterns) {
        if (patterns == null || patterns.length == 0) {
            return Collections.emptySet();
        }
        Set<Artifact> result = new LinkedHashSet<>();
        for (Iterator<Artifact> it = artifacts.iterator(); it.hasNext(); ) {
            Artifact artifact = it.next();
            if (matchesAny(artifact, patterns)) {
                it.remove();
                result.add(artifact);
            }
        }
        return result;
    }

    /**
     * @param artifact the artifact to test
     * @param patterns patterns, may be null or empty
     * @return true if the artifact matches at least one pattern
     */
    static boolean matchesAny(Artifact artifact, String[] patterns) {
        if (patterns == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (matches(artifact, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(Artifact artifact, String pattern) {
        String[] segments = pattern.split(":");
        if (segments.length <= 4) {
            return !new StrictPatternExcludesArtifactFilter(Collections.singletonList(pattern)).include(artifact);
        }
        if (segments.length > 5) {
            return false;
        }
        String coordinates = String.join(":", Arrays.copyOf(segments, 4));
        return !new StrictPatternExcludesArtifactFilter(Collections.singletonList(coordinates)).include(artifact)
                && matchesSegment(artifact.getScope(), segments[4]);
    }

    private static boolean matchesSegment(String value, String pattern) {
        if (pattern.isEmpty() || "*".equals(pattern)) {
            return true;
        }
        if (value == null) {
            return false;
        }
        boolean prefix = pattern.endsWith("*");
        boolean suffix = pattern.startsWith("*");
        String literal = pattern.substring(suffix ? 1 : 0, pattern.length() - (prefix ? 1 : 0));
        if (prefix && suffix) {
            return value.contains(literal);
        } else if (prefix) {
            return value.startsWith(literal);
        } else if (suffix) {
            return value.endsWith(literal);
        }
        return value.equals(literal);
    }
}
