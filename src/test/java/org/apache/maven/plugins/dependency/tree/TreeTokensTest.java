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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.dependency.graph.traversal.SerializingDependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.SerializingDependencyNodeVisitor.GraphTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TreeTokensTest {
    @ParameterizedTest
    @ValueSource(strings = {"standard", "extended", "whitespace", "", "invalid"})
    void explicitStyleSkipsMetadataAndWinsForFileOutput(String configured) {
        MavenSession session = mock(MavenSession.class);
        GraphTokens expected = "extended".equals(configured)
                ? SerializingDependencyNodeVisitor.EXTENDED_TOKENS
                : "whitespace".equals(configured)
                        ? SerializingDependencyNodeVisitor.WHITESPACE_TOKENS
                        : SerializingDependencyNodeVisitor.STANDARD_TOKENS;
        assertSame(expected, TreeTokens.select(configured, false, session, mock(Log.class)));
        assertSame(expected, TreeTokens.select(configured, true, session, mock(Log.class)));
        verifyNoInteractions(session);
    }

    @Test
    void fileOutputSkipsMetadata() {
        MavenSession session = mock(MavenSession.class);
        assertSame(
                SerializingDependencyNodeVisitor.STANDARD_TOKENS,
                TreeTokens.select(null, true, session, mock(Log.class)));
        verifyNoInteractions(session);
    }

    @Test
    void batchModeSkipsMetadata() {
        MavenExecutionRequest request = spy(new DefaultMavenExecutionRequest().setInteractiveMode(false));
        MavenSession session = mock(MavenSession.class);
        when(session.getRequest()).thenReturn(request);
        assertSame(
                SerializingDependencyNodeVisitor.STANDARD_TOKENS,
                TreeTokens.select(null, false, session, mock(Log.class)));
        verify(request, never()).getData();
    }

    @Test
    void absentSessionOrRequestUsesStandardTokens() {
        assertSame(
                SerializingDependencyNodeVisitor.STANDARD_TOKENS,
                TreeTokens.select(null, false, null, mock(Log.class)));
        assertSame(
                SerializingDependencyNodeVisitor.STANDARD_TOKENS,
                TreeTokens.select(null, false, mock(MavenSession.class), mock(Log.class)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "IBM437", "IBM850"})
    void compatibleConsoleEncodingUsesExtendedTokens(String encoding) {
        Map<Object, Object> capabilities = capabilities("CONSOLE", encoding);
        capabilities.put("future-key", "ignored");
        assertSame(SerializingDependencyNodeVisitor.EXTENDED_TOKENS, select(capabilities));
    }

    @ParameterizedTest
    @ValueSource(strings = {"US-ASCII", "ISO-8859-1", "windows-1252", "ISO-2022-CN", "", "not a charset", "x-unknown"})
    void unusableConsoleEncodingUsesStandardTokens(String encoding) {
        assertSame(SerializingDependencyNodeVisitor.STANDARD_TOKENS, select(capabilities("CONSOLE", encoding)));
    }

    @ParameterizedTest
    @MethodSource("unusableMetadata")
    void missingOrMalformedMetadataUsesStandardTokens(Object metadata) {
        assertSame(SerializingDependencyNodeVisitor.STANDARD_TOKENS, select(metadata));
    }

    static Stream<Object> unusableMetadata() {
        return Stream.of(
                null,
                "not a map",
                42,
                Collections.emptyMap(),
                capabilities(null, "UTF-8"),
                capabilities(42, "UTF-8"),
                capabilities("console", "UTF-8"),
                capabilities("FILE", "UTF-8"),
                capabilities("REDIRECTED", "UTF-8"),
                capabilities("UNKNOWN", "UTF-8"),
                capabilities("FUTURE_DESTINATION", "UTF-8"),
                capabilities("CONSOLE", null),
                capabilities("CONSOLE", 42),
                capabilities("CONSOLE", Charset.forName("UTF-8")));
    }

    private static Map<Object, Object> capabilities(Object destination, Object encoding) {
        Map<Object, Object> capabilities = new HashMap<>();
        if (destination != null) {
            capabilities.put("destination", destination);
        }
        if (encoding != null) {
            capabilities.put("encoding", encoding);
        }
        return capabilities;
    }

    private static GraphTokens select(Object metadata) {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest().setInteractiveMode(true);
        if (metadata != null) {
            request.getData().put("maven.logging.outputCapabilities", metadata);
        }
        MavenSession session = mock(MavenSession.class);
        when(session.getRequest()).thenReturn(request);
        return TreeTokens.select(null, false, session, mock(Log.class));
    }
}
