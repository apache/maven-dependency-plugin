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

import java.nio.charset.Charset;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.maven.shared.dependency.graph.traversal.SerializingDependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.SerializingDependencyNodeVisitor.GraphTokens;

/** Selects text tree tokens without inspecting the console when the user has already made the choice. */
final class TreeTokens {
    private TreeTokens() {}

    static GraphTokens select(
            String configured, boolean file, boolean interactive, Supplier<Charset> console, Consumer<String> debug) {
        if (configured != null) {
            if ("extended".equals(configured)) {
                debug.accept("Using explicitly configured extended tree tokens");
                return SerializingDependencyNodeVisitor.EXTENDED_TOKENS;
            }
            if ("whitespace".equals(configured)) {
                debug.accept("Using explicitly configured whitespace tree tokens");
                return SerializingDependencyNodeVisitor.WHITESPACE_TOKENS;
            }
            // Preserve the historical fallback for unrecognized token names, including an empty name.
            return SerializingDependencyNodeVisitor.STANDARD_TOKENS;
        }
        if (file || !interactive) {
            debug.accept("Using standard tree tokens for file output or batch mode");
            return SerializingDependencyNodeVisitor.STANDARD_TOKENS;
        }
        Charset encoding = console.get();
        if (encoding != null && encoding.canEncode() && encoding.newEncoder().canEncode("\u251c\u2514\u2500\u2502")) {
            debug.accept("Using extended tree tokens for console encoding " + encoding.name());
            return SerializingDependencyNodeVisitor.EXTENDED_TOKENS;
        }
        debug.accept("Using standard tree tokens: console encoding is unknown or cannot encode tree characters");
        return SerializingDependencyNodeVisitor.STANDARD_TOKENS;
    }
}
