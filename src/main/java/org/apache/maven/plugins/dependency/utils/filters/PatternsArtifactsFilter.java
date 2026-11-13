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
package org.apache.maven.plugins.dependency.utils.filters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.artifact.filter.StrictPatternExcludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.StrictPatternIncludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.collection.AbstractArtifactsFilter;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;

/**
 * Filter that keeps the artifacts matching the {@code includes} patterns and not matching the {@code excludes}
 * patterns. The patterns have the syntax {@code [groupId]:[artifactId]:[type]:[version]} of
 * {@link StrictPatternIncludesArtifactFilter}, the same as the {@code includes} and {@code excludes} parameters of
 * {@code dependency:tree}. An empty or {@code null} pattern list does not filter.
 *
 * @since 3.12.0
 */
public class PatternsArtifactsFilter extends AbstractArtifactsFilter {

    private final StrictPatternIncludesArtifactFilter includesFilter;

    private final StrictPatternExcludesArtifactFilter excludesFilter;

    /**
     * @param includes the patterns an artifact must match, or {@code null} or empty to keep every artifact
     * @param excludes the patterns an artifact must not match, or {@code null} or empty to remove no artifact
     */
    public PatternsArtifactsFilter(Collection<String> includes, Collection<String> excludes) {
        this.includesFilter = includes == null || includes.isEmpty()
                ? null
                : new StrictPatternIncludesArtifactFilter(new ArrayList<>(includes));
        this.excludesFilter = excludes == null || excludes.isEmpty()
                ? null
                : new StrictPatternExcludesArtifactFilter(new ArrayList<>(excludes));
    }

    @Override
    public Set<Artifact> filter(Set<Artifact> artifacts) throws ArtifactFilterException {
        Set<Artifact> result = new LinkedHashSet<>();
        for (Artifact artifact : artifacts) {
            if ((excludesFilter == null || excludesFilter.include(artifact))
                    && (includesFilter == null || includesFilter.include(artifact))) {
                result.add(artifact);
            }
        }
        return result;
    }
}
