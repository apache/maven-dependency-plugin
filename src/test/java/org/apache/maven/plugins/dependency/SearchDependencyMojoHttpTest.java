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
package org.apache.maven.plugins.dependency;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.sun.net.httpserver.HttpServer;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchDependencyMojoHttpTest {

    private HttpServer server;
    private int port;
    private SearchDependencyMojo mojo;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();

        mojo = new SearchDependencyMojo();
        setVariableValueToObject(mojo, "query", "test");
        setVariableValueToObject(mojo, "rows", 10);
        setVariableValueToObject(mojo, "skip", false);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void setUrl(String path) throws Exception {
        setVariableValueToObject(mojo, "repositoryUrl", "http://localhost:" + port + path);
    }

    @Test
    void httpErrorIncludesResponseBody() throws Exception {
        server.createContext("/search", exchange -> {
            String body = "Internal Server Error: database unavailable";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        setUrl("/search");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("HTTP 500"));
        assertTrue(ex.getMessage().contains("database unavailable"));
    }

    @Test
    void rateLimitReturnsSpecificMessage() throws Exception {
        server.createContext("/search", exchange -> {
            String body = "Rate limit exceeded";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        setUrl("/search");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("rate limit"));
    }

    @Test
    void htmlResponseWithHttp200IsRejected() throws Exception {
        server.createContext("/search", exchange -> {
            String html = "<html><body><h1>Service Unavailable</h1></body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        setUrl("/search");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("unexpected content type"));
    }

    @Test
    void nonJsonBodyWithHttp200IsRejected() throws Exception {
        server.createContext("/search", exchange -> {
            String notJson = "This is not JSON at all";
            byte[] bytes = notJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        setUrl("/search");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("non-JSON response"));
    }

    @Test
    void validJsonResponseIsAccepted() throws Exception {
        server.createContext("/search", exchange -> {
            String json =
                    "{\"responseHeader\":{\"status\":0}," + "\"response\":{\"numFound\":0,\"start\":0,\"docs\":[]}}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        setUrl("/search");

        // Should not throw — valid empty response
        mojo.execute();
    }

    @Test
    void dynamicUserAgentIsNotHardcoded() throws Exception {
        final String[] capturedUserAgent = {null};
        server.createContext("/search", exchange -> {
            capturedUserAgent[0] = exchange.getRequestHeaders().getFirst("User-Agent");
            String json =
                    "{\"responseHeader\":{\"status\":0}," + "\"response\":{\"numFound\":0,\"start\":0,\"docs\":[]}}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        setUrl("/search");

        mojo.execute();

        assertTrue(capturedUserAgent[0] != null);
        assertTrue(capturedUserAgent[0].startsWith("Apache-Maven-Dependency-Plugin/"));
        assertTrue(capturedUserAgent[0].contains("dependency:search"));
    }

    @Test
    void negativeRowsIsRejected() throws Exception {
        setVariableValueToObject(mojo, "rows", -1);
        setUrl("/search");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("positive integer"));
    }

    @Test
    void zeroRowsIsRejected() throws Exception {
        setVariableValueToObject(mojo, "rows", 0);
        setUrl("/search");

        MojoFailureException ex = assertThrows(MojoFailureException.class, () -> mojo.execute());
        assertTrue(ex.getMessage().contains("positive integer"));
    }

    // ── Interactive mode tests ──────────────────────────────────────────

    private static final String SEARCH_RESPONSE = "{\"responseHeader\":{\"status\":0},"
            + "\"response\":{\"numFound\":2,\"start\":0,\"docs\":["
            + "{\"g\":\"com.example\",\"a\":\"my-lib\",\"latestVersion\":\"2.0.0\"},"
            + "{\"g\":\"com.example\",\"a\":\"other-lib\",\"latestVersion\":\"1.0.0\"}"
            + "]}}";

    private static final String VERSION_RESPONSE = "{\"responseHeader\":{\"status\":0},"
            + "\"response\":{\"numFound\":3,\"start\":0,\"docs\":["
            + "{\"g\":\"com.example\",\"a\":\"my-lib\",\"v\":\"2.0.0\"},"
            + "{\"g\":\"com.example\",\"a\":\"my-lib\",\"v\":\"1.5.0\"},"
            + "{\"g\":\"com.example\",\"a\":\"my-lib\",\"v\":\"1.0.0\"}"
            + "]}}";

    /**
     * Creates a SearchDependencyMojo that returns predetermined responses to readLine()
     * and reports itself as interactive.
     */
    private SearchDependencyMojo createInteractiveMojo(Queue<String> answers) throws Exception {
        SearchDependencyMojo interactiveMojo = new SearchDependencyMojo() {
            @Override
            boolean isInteractive() {
                return true;
            }

            @Override
            String readLine(String prompt) {
                return answers.isEmpty() ? null : answers.poll();
            }
        };
        setVariableValueToObject(interactiveMojo, "query", "test");
        setVariableValueToObject(interactiveMojo, "rows", 10);
        setVariableValueToObject(interactiveMojo, "skip", false);
        setVariableValueToObject(interactiveMojo, "interactive", true);
        return interactiveMojo;
    }

    @Test
    void interactiveSelectArtifactAndAcceptLatestVersion() throws Exception {
        server.createContext("/search", exchange -> {
            String uri = exchange.getRequestURI().toString();
            String json = uri.contains("core=gav") ? VERSION_RESPONSE : SEARCH_RESPONSE;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        Queue<String> answers = new LinkedList<>();
        answers.add("1"); // select first artifact
        answers.add(""); // accept latest version

        SearchDependencyMojo interactiveMojo = createInteractiveMojo(answers);
        setUrl(interactiveMojo, "/search");

        CapturingLog log = new CapturingLog();
        interactiveMojo.setLog(log);
        interactiveMojo.execute();

        String output = String.join("\n", log.messages);
        assertTrue(output.contains("Selected: com.example:my-lib:2.0.0"), "should show selected GAV");
        assertTrue(output.contains("dependency:add"), "should show add command");
    }

    @Test
    void interactiveSelectSpecificVersion() throws Exception {
        server.createContext("/search", exchange -> {
            String uri = exchange.getRequestURI().toString();
            String json = uri.contains("core=gav") ? VERSION_RESPONSE : SEARCH_RESPONSE;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        Queue<String> answers = new LinkedList<>();
        answers.add("1"); // select first artifact
        answers.add("2"); // select version 1.5.0

        SearchDependencyMojo interactiveMojo = createInteractiveMojo(answers);
        setUrl(interactiveMojo, "/search");

        CapturingLog log = new CapturingLog();
        interactiveMojo.setLog(log);
        interactiveMojo.execute();

        String output = String.join("\n", log.messages);
        assertTrue(output.contains("Selected: com.example:my-lib:1.5.0"), "should show selected version");
    }

    @Test
    void interactiveBackFromVersionGoesToArtifactList() throws Exception {
        server.createContext("/search", exchange -> {
            String uri = exchange.getRequestURI().toString();
            String json = uri.contains("core=gav") ? VERSION_RESPONSE : SEARCH_RESPONSE;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        Queue<String> answers = new LinkedList<>();
        answers.add("1"); // select first artifact
        answers.add("b"); // go back
        answers.add(""); // quit

        SearchDependencyMojo interactiveMojo = createInteractiveMojo(answers);
        setUrl(interactiveMojo, "/search");

        CapturingLog log = new CapturingLog();
        interactiveMojo.setLog(log);
        interactiveMojo.execute();

        String output = String.join("\n", log.messages);
        // The search results header should appear twice (initial + after going back)
        int firstOccurrence = output.indexOf("Search results for:");
        int secondOccurrence = output.indexOf("Search results for:", firstOccurrence + 1);
        assertTrue(secondOccurrence > firstOccurrence, "should redisplay results after going back");
    }

    @Test
    void interactiveReSearchWithText() throws Exception {
        final int[] requestCount = {0};
        server.createContext("/search", exchange -> {
            requestCount[0]++;
            String json = SEARCH_RESPONSE;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        Queue<String> answers = new LinkedList<>();
        answers.add("newquery"); // text → re-search
        answers.add(""); // quit

        SearchDependencyMojo interactiveMojo = createInteractiveMojo(answers);
        setUrl(interactiveMojo, "/search");

        CapturingLog log = new CapturingLog();
        interactiveMojo.setLog(log);
        interactiveMojo.execute();

        // Initial search + re-search = 2 requests
        assertTrue(requestCount[0] >= 2, "should have made at least 2 HTTP requests");
    }

    @Test
    void interactiveInvalidNumberShowsWarning() throws Exception {
        server.createContext("/search", exchange -> {
            byte[] bytes = SEARCH_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        Queue<String> answers = new LinkedList<>();
        answers.add("99"); // invalid number
        answers.add(""); // quit

        SearchDependencyMojo interactiveMojo = createInteractiveMojo(answers);
        setUrl(interactiveMojo, "/search");

        CapturingLog log = new CapturingLog();
        interactiveMojo.setLog(log);
        interactiveMojo.execute();

        assertTrue(
                log.warnings.stream().anyMatch(w -> w.contains("Invalid selection")),
                "should warn about invalid selection");
    }

    @Test
    void interactiveQuitImmediately() throws Exception {
        server.createContext("/search", exchange -> {
            byte[] bytes = SEARCH_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        Queue<String> answers = new LinkedList<>();
        answers.add(""); // quit immediately

        SearchDependencyMojo interactiveMojo = createInteractiveMojo(answers);
        setUrl(interactiveMojo, "/search");

        CapturingLog log = new CapturingLog();
        interactiveMojo.setLog(log);
        interactiveMojo.execute();

        String output = String.join("\n", log.messages);
        // Should show results but not show "Selected:"
        assertTrue(output.contains("result(s)"), "should show results");
        assertTrue(!output.contains("Selected:"), "should not select anything");
    }

    @Test
    void interactiveQuitWithQ() throws Exception {
        server.createContext("/search", exchange -> {
            byte[] bytes = SEARCH_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        Queue<String> answers = new LinkedList<>();
        answers.add("q"); // quit with 'q'

        SearchDependencyMojo interactiveMojo = createInteractiveMojo(answers);
        setUrl(interactiveMojo, "/search");

        CapturingLog log = new CapturingLog();
        interactiveMojo.setLog(log);
        interactiveMojo.execute();

        String output = String.join("\n", log.messages);
        assertTrue(output.contains("result(s)"), "should show results before quitting");
        assertTrue(!output.contains("Selected:"), "should not select anything");
    }

    @Test
    void interactiveEmptyLatestVersionUsesFirstFromVersionList() throws Exception {
        // Artifact with empty latestVersion
        String searchNoVersion = "{\"responseHeader\":{\"status\":0},"
                + "\"response\":{\"numFound\":1,\"start\":0,\"docs\":["
                + "{\"g\":\"com.example\",\"a\":\"no-latest\",\"latestVersion\":\"\"}"
                + "]}}";

        server.createContext("/search", exchange -> {
            String uri = exchange.getRequestURI().toString();
            String json = uri.contains("core=gav") ? VERSION_RESPONSE : searchNoVersion;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        Queue<String> answers = new LinkedList<>();
        answers.add("1"); // select artifact
        answers.add(""); // accept default version (should be 2.0.0, not empty)

        SearchDependencyMojo interactiveMojo = createInteractiveMojo(answers);
        setUrl(interactiveMojo, "/search");

        CapturingLog log = new CapturingLog();
        interactiveMojo.setLog(log);
        interactiveMojo.execute();

        String output = String.join("\n", log.messages);
        assertTrue(
                output.contains("Selected: com.example:no-latest:2.0.0"),
                "should use first version from GAV query, not empty string");
    }

    @Test
    void interactiveNoVersionsAvailableGoesBack() throws Exception {
        // Both latestVersion empty AND version query returns nothing
        String searchNoVersion = "{\"responseHeader\":{\"status\":0},"
                + "\"response\":{\"numFound\":1,\"start\":0,\"docs\":["
                + "{\"g\":\"com.example\",\"a\":\"ghost\",\"latestVersion\":\"\"}"
                + "]}}";
        String emptyVersions =
                "{\"responseHeader\":{\"status\":0}," + "\"response\":{\"numFound\":0,\"start\":0,\"docs\":[]}}";

        server.createContext("/search", exchange -> {
            String uri = exchange.getRequestURI().toString();
            String json = uri.contains("core=gav") ? emptyVersions : searchNoVersion;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        Queue<String> answers = new LinkedList<>();
        answers.add("1"); // select artifact
        // handleVersionSelection returns false (no versions) → back to list
        answers.add("q"); // quit

        SearchDependencyMojo interactiveMojo = createInteractiveMojo(answers);
        setUrl(interactiveMojo, "/search");

        CapturingLog log = new CapturingLog();
        interactiveMojo.setLog(log);
        interactiveMojo.execute();

        assertTrue(
                log.warnings.stream().anyMatch(w -> w.contains("No version information")),
                "should warn about missing versions");
    }

    @Test
    void interactiveVersionPagingHint() throws Exception {
        // Version response with numFound > docs returned
        String manyVersions = "{\"responseHeader\":{\"status\":0},"
                + "\"response\":{\"numFound\":50,\"start\":0,\"docs\":["
                + "{\"g\":\"com.example\",\"a\":\"my-lib\",\"v\":\"3.0.0\"},"
                + "{\"g\":\"com.example\",\"a\":\"my-lib\",\"v\":\"2.0.0\"}"
                + "]}}";

        server.createContext("/search", exchange -> {
            String uri = exchange.getRequestURI().toString();
            String json = uri.contains("core=gav") ? manyVersions : SEARCH_RESPONSE;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        Queue<String> answers = new LinkedList<>();
        answers.add("1"); // select first artifact
        answers.add("1"); // select first version

        SearchDependencyMojo interactiveMojo = createInteractiveMojo(answers);
        setUrl(interactiveMojo, "/search");

        CapturingLog log = new CapturingLog();
        interactiveMojo.setLog(log);
        interactiveMojo.execute();

        String output = String.join("\n", log.messages);
        assertTrue(output.contains("48 older version(s) not shown"), "should indicate more versions exist");
    }

    @Test
    void interactiveQuitFromVersionPrompt() throws Exception {
        server.createContext("/search", exchange -> {
            String uri = exchange.getRequestURI().toString();
            String json = uri.contains("core=gav") ? VERSION_RESPONSE : SEARCH_RESPONSE;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        Queue<String> answers = new LinkedList<>();
        answers.add("1"); // select artifact
        answers.add("q"); // quit from version prompt

        SearchDependencyMojo interactiveMojo = createInteractiveMojo(answers);
        setUrl(interactiveMojo, "/search");

        CapturingLog log = new CapturingLog();
        interactiveMojo.setLog(log);
        interactiveMojo.execute();

        String output = String.join("\n", log.messages);
        assertTrue(!output.contains("Selected:"), "should not select when quitting with q");
    }

    private void setUrl(SearchDependencyMojo target, String path) throws Exception {
        setVariableValueToObject(target, "repositoryUrl", "http://localhost:" + port + path);
    }

    /** Simple Log implementation that captures messages for assertions. */
    private static class CapturingLog implements Log {
        final List<String> messages = new java.util.ArrayList<>();
        final List<String> warnings = new java.util.ArrayList<>();

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(CharSequence content) {}

        @Override
        public void debug(CharSequence content, Throwable error) {}

        @Override
        public void debug(Throwable error) {}

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
            messages.add(content.toString());
        }

        @Override
        public void info(CharSequence content, Throwable error) {
            messages.add(content.toString());
        }

        @Override
        public void info(Throwable error) {}

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(CharSequence content) {
            warnings.add(content.toString());
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
            warnings.add(content.toString());
        }

        @Override
        public void warn(Throwable error) {}

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(CharSequence content) {
            messages.add(content.toString());
        }

        @Override
        public void error(CharSequence content, Throwable error) {
            messages.add(content.toString());
        }

        @Override
        public void error(Throwable error) {}
    }
}
