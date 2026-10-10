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
import java.util.Map;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.dependency.graph.traversal.SerializingDependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.SerializingDependencyNodeVisitor.GraphTokens;

/** Selects text tree tokens using Maven's optional execution-request output metadata. */
final class TreeTokens {
    private TreeTokens() {}

    static GraphTokens select(String configured, boolean file, MavenSession session, Log log) {
        if (configured != null) {
            if ("extended".equals(configured)) {
                log.debug("Using explicitly configured extended tree tokens");
                return SerializingDependencyNodeVisitor.EXTENDED_TOKENS;
            }
            if ("whitespace".equals(configured)) {
                log.debug("Using explicitly configured whitespace tree tokens");
                return SerializingDependencyNodeVisitor.WHITESPACE_TOKENS;
            }
            // Preserve the historical fallback for unrecognized token names, including an empty name.
            return SerializingDependencyNodeVisitor.STANDARD_TOKENS;
        }
        if (file) {
            log.debug("Using standard tree tokens for file output");
            return SerializingDependencyNodeVisitor.STANDARD_TOKENS;
        }
        MavenExecutionRequest request = session == null ? null : session.getRequest();
        if (request == null || !request.isInteractiveMode()) {
            log.debug("Using standard tree tokens: no interactive Maven request");
            return SerializingDependencyNodeVisitor.STANDARD_TOKENS;
        }
        // This string-based contract also works for plugins compiled against older Maven APIs.
        Object value = request.getData().get("maven.logging.outputCapabilities");
        if (!(value instanceof Map)) {
            log.debug("Using standard tree tokens: Maven output capabilities are unavailable");
            return SerializingDependencyNodeVisitor.STANDARD_TOKENS;
        }
        Map<?, ?> capabilities = (Map<?, ?>) value;
        if (!"CONSOLE".equals(capabilities.get("destination"))) {
            log.debug("Using standard tree tokens: logging destination is not a known console");
            return SerializingDependencyNodeVisitor.STANDARD_TOKENS;
        }
        Object name = capabilities.get("encoding");
        if (name instanceof String) {
            try {
                Charset encoding = Charset.forName((String) name);
                if (encoding.canEncode() && encoding.newEncoder().canEncode("\u251c\u2514\u2500\u2502")) {
                    log.debug("Using extended tree tokens for console encoding " + encoding.name());
                    return SerializingDependencyNodeVisitor.EXTENDED_TOKENS;
                }
            } catch (IllegalArgumentException | UnsupportedOperationException e) {
                log.debug("Cannot use Maven's logging encoding: " + e.getMessage());
            }
        }
        log.debug("Using standard tree tokens: console encoding is unknown or cannot encode tree characters");
        return SerializingDependencyNodeVisitor.STANDARD_TOKENS;
    }
}
