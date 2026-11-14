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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoFailureException;

/**
 * Records the files written by one execution of {@code unpack} or {@code unpack-dependencies}, to find entries that
 * several archives, or one archive twice, deliver to the same target path. Files that already existed before the
 * execution are not known to the tracker.
 *
 * @since 3.12.0
 */
public class DuplicateTargetTracker {

    private final Map<Path, List<String>> sources = new LinkedHashMap<>();

    private final Map<Path, List<String>> duplicates = new LinkedHashMap<>();

    /**
     * Registers a target path. Paths are compared as given, after normalization, so the caller should pass the
     * real path of the output directory. In addition, when the target already exists on disk, its real path is
     * looked up, which finds names that differ only by case on a case-insensitive file system and paths reached
     * through symbolic links. Names are not case-folded, as that would be wrong on a case-sensitive file system.
     *
     * @param target the target path, after the file mappers were applied
     * @param source describes where the entry comes from, for the error message
     * @return {@code true} if no other entry of this execution targets the same path; {@code false} if it is a
     *         duplicate, which is then reported by {@link #verify()}
     */
    public boolean register(File target, String source) {
        Path key = target.toPath().toAbsolutePath().normalize();
        List<String> existing = sources.get(key);
        if (existing == null && Files.exists(key)) {
            try {
                Path real = key.toRealPath();
                existing = sources.get(real);
                if (existing != null) {
                    key = real;
                }
            } catch (IOException e) {
                // cannot resolve: treat as a new path
            }
        }
        if (existing == null) {
            List<String> list = new ArrayList<>();
            list.add(source);
            sources.put(key, list);
            return true;
        }
        existing.add(source);
        duplicates.put(key, existing);
        return false;
    }

    /**
     * @throws MojoFailureException if at least one duplicate was registered, listing each path with all its sources
     */
    public void verify() throws MojoFailureException {
        if (duplicates.isEmpty()) {
            return;
        }
        StringBuilder msg = new StringBuilder("Several entries are unpacked to the same path (failOnDuplicates):");
        for (Map.Entry<Path, List<String>> entry : duplicates.entrySet()) {
            msg.append(System.lineSeparator())
                    .append("  ")
                    .append(entry.getKey())
                    .append(" <- ");
            msg.append(String.join(", ", entry.getValue()));
        }
        msg.append(System.lineSeparator())
                .append("Only the first entry was written to each path; the others were skipped.");
        throw new MojoFailureException(msg.toString());
    }
}
