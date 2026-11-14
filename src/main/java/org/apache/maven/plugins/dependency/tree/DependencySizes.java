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
package org.apache.maven.plugins.dependency.tree;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;

/**
 * Computes and formats the sizes shown by <code>dependency:tree -DshowSizes</code>.
 * <p>
 * The size of a node is the length of the file of its artifact. The total of a node is the sum of the sizes of the
 * distinct artifacts that the dependency brings in as Maven resolves them: its own artifact and the artifacts of all
 * its dependencies, transitively, each counted once. Distinct means a different
 * <code>groupId:artifactId:type[:classifier]:version</code>. A dependency that Maven resolves once, below another
 * node, still counts for every node that depends on it: the totals are computed on the verbose tree, where a node
 * that is omitted for being a duplicate, or for losing to another version, stands for the node that Maven kept, and
 * adds the artifacts of that node and of its dependencies. The omitted version itself does not count. An artifact
 * without a known size counts as zero. The project, the root of the tree, has no size of its own and its total is
 * the sum over all its dependencies.
 */
final class DependencySizes {
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    private DependencySizes() {}

    /**
     * Collects the nodes whose size is needed, one per distinct artifact: those of the trees below the roots that are
     * not omitted.
     *
     * @param displayed the root of the displayed tree
     * @param complete the root of the verbose tree the totals are computed on
     * @return the nodes by {@link #key(Artifact)}, in tree order
     */
    static Map<String, DependencyNode> nodes(DependencyNode displayed, DependencyNode complete) {
        Map<String, DependencyNode> nodes = new LinkedHashMap<>();
        for (DependencyNode child : complete.getChildren()) {
            collect(child, nodes);
        }
        for (DependencyNode child : displayed.getChildren()) {
            collect(child, nodes);
        }
        return nodes;
    }

    private static void collect(DependencyNode node, Map<String, DependencyNode> nodes) {
        if (!isIncluded(node)) {
            return;
        }
        nodes.putIfAbsent(key(node.getArtifact()), node);
        for (DependencyNode child : node.getChildren()) {
            collect(child, nodes);
        }
    }

    /**
     * Sets the size and the total on every node of the displayed tree that is not omitted.
     *
     * @param displayed the root of the displayed tree
     * @param complete the root of the verbose tree, as collected, that the totals are computed on
     * @param fileSizes the size in bytes of the artifacts by {@link #key(Artifact)}; an artifact that is not in the
     *            map has an unknown size
     */
    static void annotate(DependencyNode displayed, DependencyNode complete, Map<String, Long> fileSizes) {
        Totals totals = new Totals(complete);
        Set<String> all = new LinkedHashSet<>();
        for (DependencyNode child : complete.getChildren()) {
            all.addAll(totals.brought(child));
        }
        displayed.setSizes(null, sum(all, fileSizes), true);
        for (DependencyNode child : displayed.getChildren()) {
            annotateDisplayed(child, totals, fileSizes);
        }
    }

    private static void annotateDisplayed(DependencyNode node, Totals totals, Map<String, Long> fileSizes) {
        if (!isIncluded(node)) {
            return;
        }
        Set<String> brought = totals.brought(node);
        node.setSizes(fileSizes.get(key(node.getArtifact())), sum(brought, fileSizes), brought.size() > 1);
        for (DependencyNode child : node.getChildren()) {
            annotateDisplayed(child, totals, fileSizes);
        }
    }

    private static long sum(Set<String> keys, Map<String, Long> fileSizes) {
        long total = 0;
        for (String key : keys) {
            Long size = fileSizes.get(key);
            if (size != null) {
                total += size;
            }
        }
        return total;
    }

    /**
     * The artifacts that the nodes of the verbose tree bring in, with the node that Maven kept standing for each
     * omitted one.
     */
    private static final class Totals {
        private final Map<String, DependencyNode> kept = new HashMap<>();

        private final Map<String, Set<String>> brought = new HashMap<>();

        private final Set<String> inProgress = new HashSet<>();

        Totals(DependencyNode complete) {
            index(complete);
        }

        private void index(DependencyNode node) {
            for (DependencyNode child : node.getChildren()) {
                if (isIncluded(child)) {
                    kept.putIfAbsent(key(child.getArtifact()), child);
                    index(child);
                }
            }
        }

        /**
         * @return the keys of the node itself and of everything it brings in, also for a node of another tree such
         *         as the displayed one
         */
        Set<String> brought(DependencyNode node) {
            String key = key(node.getArtifact());
            DependencyNode complete = kept.getOrDefault(key, node);
            Set<String> known = brought.get(key);
            if (known != null) {
                return known;
            }
            if (!inProgress.add(key)) {
                return Collections.singleton(key);
            }
            Set<String> result = new LinkedHashSet<>();
            result.add(key);
            for (DependencyNode child : complete.getChildren()) {
                if (isIncluded(child)) {
                    result.addAll(brought(child));
                } else {
                    DependencyNode winner = kept.get(winnerKey(child));
                    if (winner != null) {
                        result.addAll(brought(winner));
                    }
                }
            }
            inProgress.remove(key);
            brought.put(key, result);
            return result;
        }
    }

    private static String winnerKey(DependencyNode omitted) {
        Artifact artifact = omitted.getArtifact();
        return artifact.getDependencyConflictId() + ":"
                + omitted.getVerboseInfo().getWinnerVersion();
    }

    private static boolean isIncluded(DependencyNode node) {
        return node.getVerboseInfo() == null || node.getVerboseInfo().isIncluded();
    }

    /**
     * @return the identity of an artifact for the purpose of counting it once
     */
    static String key(Artifact artifact) {
        return artifact.getDependencyConflictId() + ":" + artifact.getVersion();
    }

    /**
     * The file of a node that is known without resolving it: the file of the artifact, or the <code>systemPath</code>
     * of a dependency of scope <code>system</code>.
     *
     * @return the file, or <code>null</code> if the artifact has to be resolved
     */
    static File localFile(DependencyNode node) {
        if (node.getArtifact().getFile() != null) {
            return node.getArtifact().getFile();
        }
        org.eclipse.aether.artifact.Artifact resolverArtifact = node.getResolverArtifact();
        if (resolverArtifact == null) {
            return null;
        }
        if (resolverArtifact.getFile() != null) {
            return resolverArtifact.getFile();
        }
        String localPath = resolverArtifact.getProperty(ArtifactProperties.LOCAL_PATH, null);
        return localPath != null ? new File(localPath) : null;
    }

    /**
     * The text shown after the label of a node, such as <code> (200 KB, total 1.5 MB)</code>, or the empty string for a
     * node without sizes. The total is shown for the project and for a node that brings in other artifacts.
     */
    static String describe(DependencyNode node) {
        if (!node.hasSizes()) {
            return "";
        }
        if (node.getParent() == null) {
            // the project has no size of its own
            return " (total " + format(node.getTotalSize()) + ")";
        }

        StringBuilder text = new StringBuilder(" (");
        text.append(node.getSize() != null ? format(node.getSize()) : "size unknown");
        if (node.bringsOthers()) {
            text.append(", total ").append(format(node.getTotalSize()));
        }
        return text.append(')').toString();
    }

    /**
     * Formats a size with the units B, KB, MB, GB and TB, where 1 KB is 1024 bytes, rounded to one decimal that is
     * left out when it is zero, such as <code>512 B</code>, <code>200 KB</code> or <code>1.5 MB</code>.
     *
     * @param bytes the size, not negative
     * @return the formatted size
     */
    static String format(long bytes) {
        int unit = 0;
        double value = bytes;
        while (unit < UNITS.length - 1 && round(value) >= 1024) {
            value /= 1024;
            unit++;
        }
        String number = unit == 0 ? String.valueOf(bytes) : String.format(Locale.ROOT, "%.1f", value);
        if (number.endsWith(".0")) {
            number = number.substring(0, number.length() - 2);
        }
        return number + " " + UNITS[unit];
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
