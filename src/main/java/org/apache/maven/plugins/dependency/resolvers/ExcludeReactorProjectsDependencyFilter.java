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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter implementation that excludes artifacts found in the Reactor.
 *
 * @author Maarten Mulders
 */
public class ExcludeReactorProjectsDependencyFilter implements Predicate<Dependency> {
    private final Logger log = LoggerFactory.getLogger(ExcludeReactorProjectsDependencyFilter.class);
    private final Set<String> reactorArtifactKeys;

    public ExcludeReactorProjectsDependencyFilter(final List<MavenProject> reactorProjects) {
        this.reactorArtifactKeys = reactorProjects.stream()
                .map(project -> ArtifactUtils.key(project.getArtifact()))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean test(Dependency dependency) {
        String key = ArtifactUtils.key(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
        if (reactorArtifactKeys.contains(key)) {
            if (log.isDebugEnabled()) {
                log.debug("Skipped dependency {} because it is present in the reactor", key);
            }
            return false;
        }
        return true;
    }
}
