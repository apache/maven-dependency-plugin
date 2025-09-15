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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;

/**
 * A filter to exclude plugins that are part of the current reactor build.
 */
class PluginsReactorExcludeFilter implements Predicate<Plugin> {

    private final Set<String> reactorArtifactKeys;

    PluginsReactorExcludeFilter(List<MavenProject> reactorProjects) {
        this.reactorArtifactKeys = new LinkedHashSet<>(reactorProjects.size());
        for (final MavenProject project : reactorProjects) {
            this.reactorArtifactKeys.add(ArtifactUtils.key(project.getArtifact()));
        }
    }

    @Override
    public boolean test(Plugin plugin) {
        String pluginKey = ArtifactUtils.key(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion());
        return !reactorArtifactKeys.contains(pluginKey);
    }
}
