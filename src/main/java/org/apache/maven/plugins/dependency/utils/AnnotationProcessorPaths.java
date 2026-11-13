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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Reads the {@code annotationProcessorPaths} of the {@code maven-compiler-plugin} from the configuration of build
 * plugins. Maven does not resolve these artifacts as plugin dependencies, so they have to be found in the
 * configuration.
 */
public final class AnnotationProcessorPaths {

    static final String COMPILER_PLUGIN_GROUP_ID = "org.apache.maven.plugins";

    static final String COMPILER_PLUGIN_ARTIFACT_ID = "maven-compiler-plugin";

    private static final String PARAMETER = "annotationProcessorPaths";

    private AnnotationProcessorPaths() {}

    /**
     * Collects the annotation processor paths configured for the {@code maven-compiler-plugin}, both in the plugin
     * configuration and in the configuration of each of its executions.
     *
     * @param plugins the build plugins of the project, from the effective model
     * @param dependencyManagement the dependency management of the project, used for paths without a version; may be
     *                             {@code null}
     * @param warn receives a message for each path that is skipped
     * @return the paths as compile scope dependencies, without duplicates, in configuration order
     */
    public static List<Dependency> collect(
            Collection<Plugin> plugins, DependencyManagement dependencyManagement, Consumer<String> warn) {
        Map<String, Dependency> result = new LinkedHashMap<>();
        for (Plugin plugin : plugins) {
            if (!COMPILER_PLUGIN_GROUP_ID.equals(plugin.getGroupId())
                    || !COMPILER_PLUGIN_ARTIFACT_ID.equals(plugin.getArtifactId())) {
                continue;
            }
            List<Object> configurations = new ArrayList<>();
            configurations.add(plugin.getConfiguration());
            for (PluginExecution execution : plugin.getExecutions()) {
                configurations.add(execution.getConfiguration());
            }
            for (Object configuration : configurations) {
                for (Dependency dependency : parse(configuration, dependencyManagement, warn)) {
                    result.putIfAbsent(key(dependency), dependency);
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    private static List<Dependency> parse(
            Object configuration, DependencyManagement dependencyManagement, Consumer<String> warn) {
        List<Dependency> dependencies = new ArrayList<>();
        if (!(configuration instanceof Xpp3Dom)) {
            return dependencies;
        }
        Xpp3Dom paths = ((Xpp3Dom) configuration).getChild(PARAMETER);
        if (paths == null) {
            return dependencies;
        }
        for (Xpp3Dom path : paths.getChildren()) {
            Dependency dependency = new Dependency();
            dependency.setGroupId(value(path, "groupId"));
            dependency.setArtifactId(value(path, "artifactId"));
            dependency.setVersion(value(path, "version"));
            String type = value(path, "type");
            dependency.setType(type == null ? "jar" : type);
            dependency.setClassifier(value(path, "classifier"));
            dependency.setScope("compile");

            if (dependency.getGroupId() == null || dependency.getArtifactId() == null) {
                warn.accept("Skipping annotation processor path without groupId or artifactId: " + path);
                continue;
            }
            if (dependency.getVersion() == null) {
                dependency.setVersion(managedVersion(dependency, dependencyManagement));
                if (dependency.getVersion() == null) {
                    warn.accept("Skipping annotation processor path " + dependency.getGroupId() + ":"
                            + dependency.getArtifactId()
                            + ": it has no version and none is managed in dependencyManagement");
                    continue;
                }
            }
            Xpp3Dom exclusions = path.getChild("exclusions");
            if (exclusions != null) {
                for (Xpp3Dom exclusionNode : exclusions.getChildren("exclusion")) {
                    Exclusion exclusion = new Exclusion();
                    exclusion.setGroupId(value(exclusionNode, "groupId"));
                    exclusion.setArtifactId(value(exclusionNode, "artifactId"));
                    if (exclusion.getGroupId() != null && exclusion.getArtifactId() != null) {
                        dependency.addExclusion(exclusion);
                    }
                }
            }
            dependencies.add(dependency);
        }
        return dependencies;
    }

    private static String managedVersion(Dependency dependency, DependencyManagement dependencyManagement) {
        if (dependencyManagement == null) {
            return null;
        }
        for (Dependency managed : dependencyManagement.getDependencies()) {
            if (Objects.equals(managed.getGroupId(), dependency.getGroupId())
                    && Objects.equals(managed.getArtifactId(), dependency.getArtifactId())
                    && Objects.equals(managed.getType(), dependency.getType())
                    && Objects.equals(emptyToNull(managed.getClassifier()), emptyToNull(dependency.getClassifier()))) {
                return managed.getVersion();
            }
        }
        return null;
    }

    private static String value(Xpp3Dom node, String name) {
        Xpp3Dom child = node.getChild(name);
        return child == null
                ? null
                : emptyToNull(child.getValue() == null ? null : child.getValue().trim());
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String key(Dependency dependency) {
        return dependency.getManagementKey() + ":" + dependency.getVersion();
    }
}
