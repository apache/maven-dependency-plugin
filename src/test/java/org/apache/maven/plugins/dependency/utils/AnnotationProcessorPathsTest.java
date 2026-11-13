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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationProcessorPathsTest {

    private final List<String> warnings = new ArrayList<>();

    private static Plugin compiler(String pluginConfig, String... executionConfigs) throws Exception {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-compiler-plugin");
        if (pluginConfig != null) {
            plugin.setConfiguration(Xpp3DomBuilder.build(new StringReader(pluginConfig)));
        }
        int i = 0;
        for (String config : executionConfigs) {
            PluginExecution execution = new PluginExecution();
            execution.setId("e" + i++);
            execution.setConfiguration(Xpp3DomBuilder.build(new StringReader(config)));
            plugin.addExecution(execution);
        }
        return plugin;
    }

    private static String paths(String... paths) {
        return "<configuration><annotationProcessorPaths>" + String.join("", paths)
                + "</annotationProcessorPaths></configuration>";
    }

    private static String path(String groupId, String artifactId, String version) {
        return path(groupId, artifactId, version, "");
    }

    private static String path(String groupId, String artifactId, String version, String extra) {
        return "<path><groupId>" + groupId + "</groupId><artifactId>" + artifactId + "</artifactId>"
                + (version == null ? "" : "<version>" + version + "</version>") + extra + "</path>";
    }

    private List<AnnotationProcessorPaths.Group> groups(DependencyManagement management, Plugin... plugins) {
        List<Plugin> list = new ArrayList<>();
        Collections.addAll(list, plugins);
        return AnnotationProcessorPaths.collect(list, management, warnings::add);
    }

    private List<Dependency> collect(DependencyManagement management, Plugin... plugins) {
        List<Dependency> result = new ArrayList<>();
        groups(management, plugins).forEach(g -> result.addAll(g.getPaths()));
        return result;
    }

    @Test
    void readsPluginLevelConfiguration() throws Exception {
        List<Dependency> result = collect(null, compiler(paths(path("g", "a", "1.0"), path("g", "b", "2.0"))));

        assertEquals(2, result.size());
        assertEquals(
                "g:a:jar:1.0",
                result.get(0).getManagementKey() + ":" + result.get(0).getVersion());
        assertEquals("compile", result.get(0).getScope());
        assertEquals("b", result.get(1).getArtifactId());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void yieldsOneGroupPerConfiguration() throws Exception {
        List<AnnotationProcessorPaths.Group> groups = groups(
                null,
                compiler(
                        paths(path("g", "a", "1.0")),
                        paths(path("g", "a", "1.0"), path("g", "c", "3.0")),
                        paths(path("g", "a", "1.1")),
                        paths(path("g", "a", "1.0")),
                        "<configuration/>"));

        assertEquals(3, groups.size());
        assertEquals(1, groups.get(0).getPaths().size());
        assertEquals(2, groups.get(1).getPaths().size());
        assertEquals("1.1", groups.get(2).getPaths().get(0).getVersion());
    }

    @Test
    void doesNotDeduplicatePathsDifferingInExclusions() throws Exception {
        String exclusion =
                "<exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>";

        List<Dependency> result = collect(
                null, compiler(paths(path("g", "a", "1.0"), path("g", "a", "1.0", exclusion), path("g", "a", "1.0"))));

        assertEquals(2, result.size());
        assertTrue(result.get(0).getExclusions().isEmpty());
        assertEquals(1, result.get(1).getExclusions().size());
    }

    @Test
    void readsUseDepMgmtFromTheConfiguration() throws Exception {
        String on = "<annotationProcessorPathsUseDepMgmt>true</annotationProcessorPathsUseDepMgmt>";
        String off = "<annotationProcessorPathsUseDepMgmt>false</annotationProcessorPathsUseDepMgmt>";
        String pathsOnly = paths(path("g", "a", "1.0"));

        List<AnnotationProcessorPaths.Group> groups = groups(
                null,
                compiler(
                        pathsOnly.replace("<configuration>", "<configuration>" + on),
                        // inherits the plugin level flag, so it equals the plugin level group
                        pathsOnly,
                        pathsOnly.replace("<configuration>", "<configuration>" + off)));

        assertEquals(2, groups.size());
        assertTrue(groups.get(0).isUseDependencyManagement());
        assertFalse(groups.get(1).isUseDependencyManagement());
    }

    @Test
    void readsTypeAndClassifier() throws Exception {
        List<Dependency> result = collect(
                null, compiler(paths(path("g", "a", "1.0", "<type>test-jar</type><classifier>proc</classifier>"))));

        assertEquals(1, result.size());
        assertEquals("test-jar", result.get(0).getType());
        assertEquals("proc", result.get(0).getClassifier());
    }

    @Test
    void takesMissingVersionFromDependencyManagement() throws Exception {
        DependencyManagement management = new DependencyManagement();
        Dependency managed = new Dependency();
        managed.setGroupId("g");
        managed.setArtifactId("a");
        managed.setVersion("4.2");
        management.addDependency(managed);

        List<Dependency> result = collect(management, compiler(null, paths(path("g", "a", null))));

        assertEquals(1, result.size());
        assertEquals("4.2", result.get(0).getVersion());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void skipsMissingVersionWithoutManagedVersion() throws Exception {
        DependencyManagement management = new DependencyManagement();
        Dependency managed = new Dependency();
        managed.setGroupId("g");
        managed.setArtifactId("a");
        managed.setClassifier("other");
        managed.setVersion("4.2");
        management.addDependency(managed);

        List<Dependency> result = collect(management, compiler(paths(path("g", "a", null), path("g", "b", "1"))));

        assertEquals(1, result.size());
        assertEquals("b", result.get(0).getArtifactId());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("g:a"));
        assertTrue(warnings.get(0).contains("compilation will fail"));
    }

    @Test
    void readsExclusions() throws Exception {
        String exclusions = "<exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion>"
                + "<exclusion><groupId>*</groupId><artifactId>z</artifactId></exclusion></exclusions>";

        List<Dependency> result = collect(null, compiler(paths(path("g", "a", "1.0", exclusions))));

        assertEquals(2, result.get(0).getExclusions().size());
        assertEquals("x", result.get(0).getExclusions().get(0).getGroupId());
        assertEquals("y", result.get(0).getExclusions().get(0).getArtifactId());
        assertEquals("*", result.get(0).getExclusions().get(1).getGroupId());
    }

    @Test
    void ignoresOtherPluginsAndMissingConfiguration() throws Exception {
        Plugin other = compiler(paths(path("g", "a", "1.0")));
        other.setArtifactId("maven-surefire-plugin");

        assertTrue(collect(null).isEmpty());
        assertTrue(collect(null, other).isEmpty());
        assertTrue(collect(null, compiler(null)).isEmpty());
        assertTrue(collect(null, compiler("<configuration><release>17</release></configuration>"))
                .isEmpty());
        assertTrue(warnings.isEmpty());
    }
}
