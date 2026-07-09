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
package org.apache.maven.plugins.dependency.resolvers;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aether {@link DependencyFilter} implementation that excludes artifacts found in the Reactor, applied to the whole
 * resolved dependency graph so transitive nodes pointing at reactor modules are skipped during artifact resolution.
 */
public class ExcludeReactorProjectsGraphDependencyFilter implements DependencyFilter {

    private final Logger log = LoggerFactory.getLogger(ExcludeReactorProjectsGraphDependencyFilter.class);
    private final Set<String> reactorArtifactKeys;

    public ExcludeReactorProjectsGraphDependencyFilter(final List<MavenProject> reactorProjects) {
        this.reactorArtifactKeys = reactorProjects.stream()
                .map(project -> ArtifactUtils.key(project.getArtifact()))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean accept(DependencyNode node, List<DependencyNode> parents) {
        if (node == null) {
            return true;
        }
        Artifact artifact = node.getArtifact();
        if (artifact == null) {
            return true;
        }
        String key = ArtifactUtils.key(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
        if (reactorArtifactKeys.contains(key)) {
            if (log.isDebugEnabled()) {
                log.debug("Skipped transitive dependency {} because it is present in the reactor", key);
            }
            return false;
        }
        return true;
    }
}
