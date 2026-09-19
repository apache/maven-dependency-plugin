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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.function.Consumer;

import org.slf4j.LoggerFactory;

/**
 * Read-only access to Maven's optional terminal implementations. No terminal library is linked into the plugin realm:
 * Maven 3.6.3 and its older Jansi must still be able to load the goal.
 */
final class ConsoleSupport {
    private ConsoleSupport() {}

    /** Returns an encoding only when both the logging destination and its terminal are known. */
    static Charset encoding(Consumer<String> debug) {
        try {
            Class<?> loggerFactory = LoggerFactory.getILoggerFactory().getClass();
            // Maven 3.10 uses SLF4J 2, which moved this implementation from impl to simple.
            if (!"org.slf4j.impl.MavenSimpleLoggerFactory".equals(loggerFactory.getName())
                    && !"org.slf4j.simple.MavenSimpleLoggerFactory".equals(loggerFactory.getName())) {
                // In particular, Maven 4 exposes its JLine terminal but not the logger's active sink (-l).
                debug.accept("Cannot determine the console destination of logging provider " + loggerFactory.getName());
                return null;
            }
            ClassLoader loader = loggerFactory.getClassLoader();
            Properties properties = new Properties();
            // Maven initializes its SimpleLogger in the core realm, before entering a plugin's context class loader.
            try (InputStream input = loader.getResourceAsStream("simplelogger.properties")) {
                if (input != null) {
                    properties.load(input);
                }
            }
            String destination = System.getProperty(
                    "org.slf4j.simpleLogger.logFile",
                    properties.getProperty("org.slf4j.simpleLogger.logFile", "System.err"));
            boolean stdout = "System.out".equalsIgnoreCase(destination);
            if (!stdout && !"System.err".equalsIgnoreCase(destination)) {
                debug.accept("Using standard tree tokens for a file logging destination");
                return null;
            }
            if ("dumb".equals(System.getenv("TERM"))) {
                return null;
            }
            PrintStream stream = stdout ? System.out : System.err;
            Class<?> console;
            try {
                console = Class.forName("org.jline.jansi.AnsiConsole", false, loader);
            } catch (ClassNotFoundException absent) {
                console = Class.forName("org.fusesource.jansi.AnsiConsole", false, loader);
                if (!isTerminal(console, stdout, stream)) {
                    return null;
                }
                String name = stdout ? "stdout.encoding" : "stderr.encoding";
                return jansiEncoding(
                        streamEncoding(stream),
                        System.getProperty(name),
                        System.getProperty("sun." + name),
                        System.getProperty("os.name", "").startsWith("Windows"));
            }
            // An installed JLine backend's negative result must not be overridden by a legacy Jansi adapter.
            if (!isTerminal(console, stdout, stream)) {
                return null;
            }
            return jlineEncoding(console, stdout, stream);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof VirtualMachineError) {
                throw (VirtualMachineError) cause;
            }
            if (cause instanceof ThreadDeath) {
                throw (ThreadDeath) cause;
            }
            debug.accept("Terminal metadata unavailable: " + cause);
        } catch (ReflectiveOperationException | IOException | RuntimeException | LinkageError e) {
            // Missing classes/methods, denied access and failed native initialization are all optional capabilities.
            debug.accept("Terminal metadata unavailable: " + e);
        }
        return null;
    }

    static boolean isTerminal(Class<?> console, boolean stdout, PrintStream stream)
            throws ReflectiveOperationException {
        // out()/err() may initialize native streams. Never call them before Maven has installed the console.
        // Jansi 1.x lacks this public metadata; it takes the conservative fallback above.
        if (!Boolean.TRUE.equals(console.getMethod("isInstalled").invoke(null))) {
            return false;
        }
        Method output = console.getMethod(stdout ? "out" : "err");
        if (output.invoke(null) != stream) {
            // Maven 3 replaces System.out and System.err for -l before initializing its (cached) logger streams.
            // A terminal attached to the process therefore says nothing about the log file's encoding.
            return false;
        }
        String type = String.valueOf(output.getReturnType().getMethod("getType").invoke(stream));
        // Type describes the destination; mode/color settings can be forced even when output is redirected.
        return "Native".equals(type) || "VirtualTerminal".equals(type) || "Emulation".equals(type);
    }

    private static Charset jlineEncoding(Class<?> console, boolean stdout, PrintStream stream)
            throws ReflectiveOperationException {
        Object terminal = console.getMethod("getTerminal").invoke(null);
        if (terminal == null) {
            return null;
        }
        // Maven's lazy wrapper delegates encoding(), but can inherit outputEncoding()'s default implementation.
        if ("org.apache.maven.jline.FastTerminal".equals(terminal.getClass().getName())) {
            terminal = terminal.getClass().getMethod("getTerminal").invoke(terminal);
        }
        ClassLoader loader = console.getClassLoader();
        Class<?> api = Class.forName("org.jline.terminal.Terminal", false, loader);
        Class<?> ext = Class.forName("org.jline.terminal.spi.TerminalExt", false, loader);
        String type = (String) api.getMethod("getType").invoke(terminal);
        if (type == null || type.startsWith("dumb") || !ext.isInstance(terminal)) {
            return null;
        }
        Object systemStream = ext.getMethod("getSystemStream").invoke(terminal);
        if (!(stdout ? "Output" : "Error").equals(String.valueOf(systemStream))) {
            // JLine may select stderr when stdout is piped; only the actual logging stream counts.
            return null;
        }
        Charset encoded = streamEncoding(stream);
        if (encoded == null) {
            // JLine's Jansi PrintStream is constructed with Terminal.encoding(), including on Java 8.
            encoded = (Charset) api.getMethod("encoding").invoke(terminal);
        }
        Charset output;
        try {
            output = (Charset) api.getMethod("outputEncoding").invoke(terminal);
        } catch (NoSuchMethodException absent) {
            output = (Charset) api.getMethod("encoding").invoke(terminal);
        }
        return encoded != null && encoded.equals(output) ? encoded : null;
    }

    private static Charset streamEncoding(PrintStream stream) throws ReflectiveOperationException {
        try {
            // PrintStream.charset() was added after Java 8, the plugin's minimum runtime.
            return (Charset) PrintStream.class.getMethod("charset").invoke(stream);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    static Charset jansiEncoding(Charset actual, String modern, String legacy, boolean windows) {
        Charset hint = modern != null ? Charset.forName(modern) : legacy != null ? Charset.forName(legacy) : null;
        if (windows && hint == null) {
            // file.encoding alone does not establish the Windows console's output code page.
            return null;
        }
        if (actual != null) {
            return hint == null || actual.equals(hint) ? actual : null;
        }
        // Jansi 2.x originally used sun.stdout/err.encoding, then added stdout/err.encoding precedence.
        // On older JDKs without PrintStream.charset(), only accept evidence on which those versions agree.
        if (modern != null && (legacy == null || !hint.equals(Charset.forName(legacy)))) {
            return null;
        }
        return hint != null ? hint : Charset.defaultCharset();
    }
}
