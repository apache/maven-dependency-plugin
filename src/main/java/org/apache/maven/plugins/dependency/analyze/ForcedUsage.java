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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;

/**
 * Splits the <code>usedDependencies</code> entries into those the analysis already reports as used and declared, which
 * {@link ProjectDependencyAnalysis#forceDeclaredDependenciesUsage(String[])} rejects with an exception, and the rest.
 */
final class ForcedUsage {

    private final List<String> unneeded = new ArrayList<>();

    private final List<String> toForce = new ArrayList<>();

    ForcedUsage(ProjectDependencyAnalysis analysis, String[] usedDependencies) {
        Set<String> usedDeclared = new HashSet<>();
        for (Artifact artifact : analysis.getUsedDeclaredArtifacts()) {
            usedDeclared.add(key(artifact));
        }
        // the analyzer first matches an entry against the unused declared artifacts, so such an entry is needed
        for (Artifact artifact : analysis.getUnusedDeclaredArtifacts()) {
            usedDeclared.remove(key(artifact));
        }
        for (String entry : usedDependencies) {
            (usedDeclared.contains(entry) ? unneeded : toForce).add(entry);
        }
    }

    /**
     * @return the entries that are already detected as used
     */
    List<String> getUnneeded() {
        return unneeded;
    }

    /**
     * @return the entries that must still be passed to the analyzer
     */
    String[] getToForce() {
        return toForce.toArray(new String[0]);
    }

    static String message(String entry) {
        return "Unneeded forced usage of " + entry + ": the dependency is already detected as used";
    }

    private static String key(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId();
    }
}
