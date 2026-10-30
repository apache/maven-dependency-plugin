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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.artifact.filter.collection.AbstractArtifactFeatureFilter;

/**
 * Filters artifacts by group id. Unlike {@code org.apache.maven.shared.artifact.filter.collection.GroupIdFilter},
 * a group id such as {@code org.example} matches artifacts whose group id is {@code org.example} or is nested
 * under it, such as {@code org.example.foo}, but never an unrelated group id that merely shares the same string
 * prefix, such as {@code org.example2}.
 *
 * @since 3.12.0
 */
public class GroupIdFilter extends AbstractArtifactFeatureFilter {

    private final boolean exactMatch;

    /**
     * Creates a filter that matches a group id and every group id nested under it.
     *
     * @param include comma separated list of group ids to include, may be {@code null}
     * @param exclude comma separated list of group ids to exclude, may be {@code null}
     */
    public GroupIdFilter(final String include, final String exclude) {
        this(include, exclude, false);
    }

    /**
     * Creates a filter that matches either a group id and every group id nested under it, or, with
     * {@code exactMatch}, only the group id itself.
     *
     * @param include comma separated list of group ids to include, may be {@code null}
     * @param exclude comma separated list of group ids to exclude, may be {@code null}
     * @param exactMatch {@code true} to only match a listed group id exactly, the same way
     *            {@code includeArtifactIds}/{@code excludeArtifactIds} match artifact ids; {@code false} to also
     *            match every group id nested under a listed one, so that {@code org.example} matches
     *            {@code org.example.foo}
     */
    public GroupIdFilter(final String include, final String exclude, final boolean exactMatch) {
        super(include, exclude);
        this.exactMatch = exactMatch;
    }

    /** {@inheritDoc} */
    @Override
    protected String getArtifactFeature(final Artifact artifact) {
        return artifact.getGroupId();
    }

    /** {@inheritDoc} */
    @Override
    protected boolean compareFeatures(final String lhs, final String rhs) {
        if (exactMatch) {
            return lhs.equals(rhs);
        }
        return lhs.equals(rhs) || lhs.startsWith(rhs + ".");
    }
}
