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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.maven.artifact.Artifact;

/**
 * Computes and formats the sizes shown by <code>dependency:tree -DshowSizes</code>.
 * <p>
 * The size of a node is the length of the file of its artifact. The total of a node is the sum of the sizes of the
 * distinct artifacts in its subtree: the artifact of the node itself and the artifacts of all its descendants, each
 * counted once even when it occurs several times in the subtree. Nodes that the verbose tree marks as omitted are
 * neither shown with a size nor counted, so the totals are the same with and without <code>-Dverbose</code>. An
 * artifact without a known size counts as zero. The project itself, the root of the tree, has no size of its own and
 * its total is the sum over all its dependencies.
 */
final class DependencySizes {
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    private DependencySizes() {}

    /**
     * Collects the distinct artifacts whose size is needed: those of the nodes below the root that are not omitted.
     *
     * @param root the root of the displayed tree
     * @return the artifacts by {@link #key(Artifact)}, in tree order
     */
    static Map<String, Artifact> artifacts(DependencyNode root) {
        Map<String, Artifact> artifacts = new LinkedHashMap<>();
        for (DependencyNode child : root.getChildren()) {
            collect(child, artifacts);
        }
        return artifacts;
    }

    private static void collect(DependencyNode node, Map<String, Artifact> artifacts) {
        if (!isIncluded(node)) {
            return;
        }
        artifacts.putIfAbsent(key(node.getArtifact()), node.getArtifact());
        for (DependencyNode child : node.getChildren()) {
            collect(child, artifacts);
        }
    }

    /**
     * Sets the size and the total on every node of the tree that is not omitted.
     *
     * @param root the root of the displayed tree
     * @param fileSizes the size in bytes of the artifacts by {@link #key(Artifact)}; an artifact that is not in the
     *            map has an unknown size
     */
    static void annotate(DependencyNode root, Map<String, Long> fileSizes) {
        annotate(root, fileSizes, true);
    }

    /**
     * @return the distinct artifacts of the subtree with their sizes, those of unknown size left out
     */
    private static Map<String, Long> annotate(DependencyNode node, Map<String, Long> fileSizes, boolean isRoot) {
        Map<String, Long> subtree = new LinkedHashMap<>();
        Long own = null;
        if (!isRoot) {
            own = fileSizes.get(key(node.getArtifact()));
            if (own != null) {
                subtree.put(key(node.getArtifact()), own);
            }
        }
        for (DependencyNode child : node.getChildren()) {
            if (isIncluded(child)) {
                subtree.putAll(annotate(child, fileSizes, false));
            }
        }

        long total = 0;
        for (Long size : subtree.values()) {
            total += size;
        }
        node.setSizes(own, total);

        return subtree;
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
     * The text shown after the label of a node, such as <code> (200 KB, total 1.5 MB)</code>, or the empty string for a
     * node without sizes.
     */
    static String describe(DependencyNode node) {
        if (!node.hasSizes()) {
            return "";
        }

        StringBuilder text = new StringBuilder(" (");
        boolean hasSizedChild = false;
        for (DependencyNode child : node.getChildren()) {
            hasSizedChild |= child.hasSizes();
        }
        if (node.getParent() == null) {
            // the project has no size of its own
            return text.append("total ")
                    .append(format(node.getTotalSize()))
                    .append(')')
                    .toString();
        }

        text.append(node.getSize() != null ? format(node.getSize()) : "size unknown");
        if (hasSizedChild) {
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
