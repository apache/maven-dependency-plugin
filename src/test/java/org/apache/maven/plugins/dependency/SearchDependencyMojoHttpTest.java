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

import com.sun.net.httpserver.HttpServer;
import org.apache.maven.plugin.MojoFailureException;
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
}
