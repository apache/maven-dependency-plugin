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
import java.util.function.Supplier;

import org.apache.maven.shared.dependency.graph.traversal.SerializingDependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.SerializingDependencyNodeVisitor.GraphTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertSame;

class TreeTokensTest {
    private static final Supplier<Charset> NO_PROBE = () -> {
        throw new AssertionError("Terminal detection must be skipped");
    };

    @ParameterizedTest
    @ValueSource(strings = {"standard", "extended", "whitespace", "", "invalid"})
    void explicitStyleWinsEvenForFilesAndBatchMode(String tokens) {
        GraphTokens expected = "extended".equals(tokens)
                ? SerializingDependencyNodeVisitor.EXTENDED_TOKENS
                : "whitespace".equals(tokens)
                        ? SerializingDependencyNodeVisitor.WHITESPACE_TOKENS
                        : SerializingDependencyNodeVisitor.STANDARD_TOKENS;
        assertSame(expected, select(tokens, true, false, NO_PROBE));
        assertSame(expected, select(tokens, false, true, NO_PROBE));
    }

    @Test
    void filesAndBatchModeDoNotProbeTheTerminal() {
        assertSame(SerializingDependencyNodeVisitor.STANDARD_TOKENS, select(null, true, true, NO_PROBE));
        assertSame(SerializingDependencyNodeVisitor.STANDARD_TOKENS, select(null, false, false, NO_PROBE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "IBM437", "IBM850"})
    void encodingsWithAllTreeCharactersUseExtendedTokens(String encoding) {
        assertSame(
                SerializingDependencyNodeVisitor.EXTENDED_TOKENS,
                select(null, false, true, () -> Charset.forName(encoding)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"US-ASCII", "ISO-8859-1", "windows-1252", "ISO-2022-CN"})
    void encodingsMissingTreeCharactersUseStandardTokens(String encoding) {
        assertSame(
                SerializingDependencyNodeVisitor.STANDARD_TOKENS,
                select(null, false, true, () -> Charset.forName(encoding)));
    }

    @Test
    void unknownConsoleUsesStandardTokens() {
        assertSame(SerializingDependencyNodeVisitor.STANDARD_TOKENS, select(null, false, true, () -> null));
    }

    private static GraphTokens select(String tokens, boolean file, boolean interactive, Supplier<Charset> probe) {
        return TreeTokens.select(tokens, file, interactive, probe, message -> {});
    }
}
