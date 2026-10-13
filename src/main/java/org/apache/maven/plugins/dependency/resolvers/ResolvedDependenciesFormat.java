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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;

/**
 * The machine-readable output types of {@link ResolveDependenciesMojo}: a JSON document with the resolved, skipped and
 * unresolved artifacts, and a POM <code>dependencies</code> fragment of the resolved artifacts.
 */
final class ResolvedDependenciesFormat {

    static final String TEXT = "text";

    static final String JSON = "json";

    static final String POM = "pom";

    private static final Comparator<Artifact> BY_COORDINATES = Comparator.comparing(Artifact::getGroupId)
            .thenComparing(Artifact::getArtifactId)
            .thenComparing(Artifact::getVersion)
            .thenComparing(Artifact::getType)
            .thenComparing(a -> Objects.toString(a.getClassifier(), ""));

    private ResolvedDependenciesFormat() {}

    static boolean isSupported(String outputType) {
        return outputType == null || TEXT.equals(outputType) || JSON.equals(outputType) || POM.equals(outputType);
    }

    /**
     * Writes the sets as <code>{"resolved": [...], "skipped": [...], "unresolved": [...]}</code>. The members of an
     * artifact follow the JSON of <code>dependency:tree</code>: <code>groupId</code>, <code>artifactId</code>,
     * <code>version</code>, <code>type</code>, <code>scope</code>, <code>classifier</code> (empty if none) and
     * <code>optional</code>, all strings. <code>file</code> is added if requested and the artifact has one.
     */
    static String json(DependencyStatusSets sets, boolean withFile, boolean sort) {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder("{").append(nl);
        jsonArray(sb, "resolved", sets.getResolvedDependencies(), withFile, sort)
                .append(",")
                .append(nl);
        jsonArray(sb, "skipped", sets.getSkippedDependencies(), withFile, sort)
                .append(",")
                .append(nl);
        jsonArray(sb, "unresolved", sets.getUnResolvedDependencies(), withFile, sort)
                .append(nl);
        return sb.append("}").append(nl).toString();
    }

    private static StringBuilder jsonArray(
            StringBuilder sb, String name, Collection<Artifact> artifacts, boolean withFile, boolean sort) {
        String nl = System.lineSeparator();
        List<Artifact> list = ordered(artifacts, sort);
        sb.append("  \"").append(name).append("\": [");
        if (list.isEmpty()) {
            return sb.append("]");
        }
        sb.append(nl);
        for (int i = 0; i < list.size(); i++) {
            Artifact artifact = list.get(i);
            List<String[]> members = new ArrayList<>();
            members.add(new String[] {"groupId", artifact.getGroupId()});
            members.add(new String[] {"artifactId", artifact.getArtifactId()});
            members.add(new String[] {"version", artifact.getVersion()});
            members.add(new String[] {"type", artifact.getType()});
            members.add(new String[] {"scope", artifact.getScope()});
            members.add(new String[] {"classifier", artifact.getClassifier()});
            members.add(new String[] {"optional", String.valueOf(artifact.isOptional())});
            if (withFile && artifact.getFile() != null) {
                members.add(new String[] {"file", artifact.getFile().getAbsolutePath()});
            }
            sb.append("    {").append(nl);
            for (int j = 0; j < members.size(); j++) {
                sb.append("      \"")
                        .append(members.get(j)[0])
                        .append("\": \"")
                        .append(jsonEscape(members.get(j)[1]))
                        .append("\"")
                        .append(j < members.size() - 1 ? "," : "")
                        .append(nl);
            }
            sb.append("    }").append(i < list.size() - 1 ? "," : "").append(nl);
        }
        return sb.append("  ]");
    }

    /**
     * Writes the resolved artifacts as a <code>dependencies</code> element that can be pasted into
     * <code>dependencyManagement</code> or <code>dependencies</code>. <code>type</code> is left out for
     * <code>jar</code>, <code>classifier</code> if there is none, and <code>scope</code> for
     * <code>compile</code>. The version is the base version, as written in a POM.
     */
    static String pom(DependencyStatusSets sets, boolean sort) {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder("<dependencies>").append(nl);
        for (Artifact artifact : ordered(sets.getResolvedDependencies(), sort)) {
            sb.append("  <dependency>").append(nl);
            element(sb, "groupId", artifact.getGroupId());
            element(sb, "artifactId", artifact.getArtifactId());
            element(sb, "version", artifact.getBaseVersion());
            if (!"jar".equals(artifact.getType())) {
                element(sb, "type", artifact.getType());
            }
            if (artifact.hasClassifier()) {
                element(sb, "classifier", artifact.getClassifier());
            }
            if (artifact.getScope() != null && !Artifact.SCOPE_COMPILE.equals(artifact.getScope())) {
                element(sb, "scope", artifact.getScope());
            }
            sb.append("  </dependency>").append(nl);
        }
        return sb.append("</dependencies>").append(nl).toString();
    }

    private static void element(StringBuilder sb, String name, String value) {
        sb.append("    <")
                .append(name)
                .append('>')
                .append(xmlEscape(value))
                .append("</")
                .append(name)
                .append('>')
                .append(System.lineSeparator());
    }

    private static List<Artifact> ordered(Collection<Artifact> artifacts, boolean sort) {
        if (artifacts == null) {
            return Collections.emptyList();
        }
        List<Artifact> list = new ArrayList<>(artifacts);
        if (sort) {
            list.sort(BY_COORDINATES);
        }
        return list;
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String xmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
