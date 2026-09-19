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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleSupportTest {
    @BeforeEach
    void resetConsole() {
        Console.installed = true;
        Console.output = new ConsoleStream();
        Console.error = new ConsoleStream();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Native", "VirtualTerminal", "Emulation"})
    void acceptsInstalledTerminalRegardlessOfAnsiMode(String type) throws Exception {
        Console.output.type = type;
        assertTrue(ConsoleSupport.isTerminal(Console.class, true, Console.output));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Redirected", "Unsupported", "unknown"})
    void rejectsNonTerminalEvenIfColorWasForced(String type) throws Exception {
        Console.output.type = type;
        assertFalse(ConsoleSupport.isTerminal(Console.class, true, Console.output));
    }

    @Test
    void logFileReplacementIsNotTheInstalledTerminal() throws Exception {
        PrintStream logFile = new PrintStream(new ByteArrayOutputStream());
        assertFalse(ConsoleSupport.isTerminal(Console.class, true, logFile));
        assertFalse(ConsoleSupport.isTerminal(Console.class, false, logFile));
    }

    @Test
    void checksTheStreamSelectedByTheLogger() throws Exception {
        Console.output.type = "Redirected";
        assertFalse(ConsoleSupport.isTerminal(Console.class, true, Console.output));
        assertTrue(ConsoleSupport.isTerminal(Console.class, false, Console.error));
        assertFalse(ConsoleSupport.isTerminal(Console.class, false, Console.output));
    }

    @Test
    void doesNotInitializeAnUninstalledConsole() throws Exception {
        Console.installed = false;
        assertFalse(ConsoleSupport.isTerminal(Console.class, true, Console.output));
    }

    @Test
    void legacyJansiWithoutMetadataIsNotProbed() {
        assertThrows(
                NoSuchMethodException.class,
                () -> ConsoleSupport.isTerminal(LegacyConsole.class, true, Console.output));
    }

    @Test
    void unknownLoggerCannotEstablishTheDestination() {
        assertNull(ConsoleSupport.encoding(message -> {}));
    }

    @Test
    void oldJdkUsesTheJansiStreamEncodingRatherThanTheDefaultCharset() {
        Charset cp437 = Charset.forName("IBM437");
        assertSame(cp437, ConsoleSupport.jansiEncoding(null, null, "IBM437", true));
        assertSame(StandardCharsets.US_ASCII, ConsoleSupport.jansiEncoding(null, null, "US-ASCII", false));
    }

    @Test
    void ambiguousJansiVersionsOrConflictingStreamEncodingsStayAscii() {
        assertNull(ConsoleSupport.jansiEncoding(null, "UTF-8", null, false));
        assertNull(ConsoleSupport.jansiEncoding(null, "UTF-8", "IBM437", true));
        assertNull(ConsoleSupport.jansiEncoding(StandardCharsets.UTF_8, "IBM437", null, true));
    }

    @Test
    void windowsNeedsEvidenceOfTheConsoleEncoding() {
        assertNull(ConsoleSupport.jansiEncoding(null, null, null, true));
        assertNull(ConsoleSupport.jansiEncoding(StandardCharsets.UTF_8, null, null, true));
    }

    @Test
    void modernJdkExposesTheActualPrintStreamCharset() {
        assertSame(StandardCharsets.UTF_8, ConsoleSupport.jansiEncoding(StandardCharsets.UTF_8, "UTF-8", null, true));
        assertSame(StandardCharsets.UTF_8, ConsoleSupport.jansiEncoding(StandardCharsets.UTF_8, null, null, false));
    }

    public static class Console {
        static boolean installed;
        static ConsoleStream output;
        static ConsoleStream error;

        public static boolean isInstalled() {
            return installed;
        }

        public static ConsoleStream out() {
            if (!installed) {
                throw new AssertionError("Must not initialize a terminal");
            }
            return output;
        }

        public static ConsoleStream err() {
            if (!installed) {
                throw new AssertionError("Must not initialize a terminal");
            }
            return error;
        }
    }

    public static class ConsoleStream extends PrintStream {
        String type = "Native";

        ConsoleStream() {
            super(new ByteArrayOutputStream());
        }

        public String getType() {
            return type;
        }
    }

    public static class LegacyConsole {
        public static PrintStream out() {
            throw new AssertionError("Must not initialize legacy Jansi");
        }
    }
}
