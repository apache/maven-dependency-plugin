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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchDependencyMojoTest {

    private static final String SAMPLE_RESPONSE = "{\"responseHeader\":{\"status\":0,\"QTime\":1},"
            + "\"response\":{\"numFound\":2,\"start\":0,\"docs\":["
            + "{\"id\":\"com.google.adk:google-adk\",\"g\":\"com.google.adk\","
            + "\"a\":\"google-adk\",\"latestVersion\":\"1.0.0\",\"p\":\"jar\"},"
            + "{\"id\":\"com.google.adk:google-adk-spring\",\"g\":\"com.google.adk\","
            + "\"a\":\"google-adk-spring\",\"latestVersion\":\"0.5.0\",\"p\":\"jar\"}"
            + "]}}";

    private static final String EMPTY_RESPONSE =
            "{\"responseHeader\":{\"status\":0},\"response\":{\"numFound\":0,\"start\":0,\"docs\":[]}}";

    @Test
    void extractArtifactsFromResponse() {
        List<String[]> artifacts = SearchDependencyMojo.extractArtifacts(SAMPLE_RESPONSE);
        assertEquals(2, artifacts.size());

        assertEquals("com.google.adk", artifacts.get(0)[0]);
        assertEquals("google-adk", artifacts.get(0)[1]);
        assertEquals("1.0.0", artifacts.get(0)[2]);

        assertEquals("com.google.adk", artifacts.get(1)[0]);
        assertEquals("google-adk-spring", artifacts.get(1)[1]);
        assertEquals("0.5.0", artifacts.get(1)[2]);
    }

    @Test
    void extractArtifactsFromEmptyResponse() {
        List<String[]> artifacts = SearchDependencyMojo.extractArtifacts(EMPTY_RESPONSE);
        assertEquals(0, artifacts.size());
    }

    @Test
    void extractNumFound() {
        assertEquals(2, SearchDependencyMojo.extractInt(SAMPLE_RESPONSE, "numFound"));
        assertEquals(0, SearchDependencyMojo.extractInt(EMPTY_RESPONSE, "numFound"));
    }

    @Test
    void extractStringField() {
        String json = "{\"g\":\"com.example\",\"a\":\"my-lib\",\"latestVersion\":\"2.0.0\"}";
        assertEquals("com.example", SearchDependencyMojo.extractStringField(json, "g"));
        assertEquals("my-lib", SearchDependencyMojo.extractStringField(json, "a"));
        assertEquals("2.0.0", SearchDependencyMojo.extractStringField(json, "latestVersion"));
        assertNull(SearchDependencyMojo.extractStringField(json, "nonexistent"));
    }

    @Test
    void extractIntFieldMissing() {
        assertEquals(0, SearchDependencyMojo.extractInt("{}", "numFound"));
    }

    @Test
    void extractArtifactsNoDocsKey() {
        List<String[]> artifacts = SearchDependencyMojo.extractArtifacts("{\"response\":{}}");
        assertEquals(0, artifacts.size());
    }

    @Test
    void extractStringFieldWithEscapedQuotes() {
        String json = "{\"desc\":\"A \\\"special\\\" library\",\"g\":\"com.example\"}";
        assertEquals("A \"special\" library", SearchDependencyMojo.extractStringField(json, "desc"));
        assertEquals("com.example", SearchDependencyMojo.extractStringField(json, "g"));
    }

    @Test
    void extractStringFieldWithEscapedBackslash() {
        String json = "{\"path\":\"C:\\\\Users\\\\test\"}";
        assertEquals("C:\\Users\\test", SearchDependencyMojo.extractStringField(json, "path"));
    }

    @Test
    void extractArtifactsFromHtmlResponse() {
        // Simulates an HTML error page returned with HTTP 200
        String html = "<html><body><h1>Service Unavailable</h1></body></html>";
        List<String[]> artifacts = SearchDependencyMojo.extractArtifacts(html);
        assertEquals(0, artifacts.size());
    }

    @Test
    void extractArtifactsFromMalformedJson() {
        // Missing docs array but has numFound
        String malformed = "{\"response\":{\"numFound\":5}}";
        List<String[]> artifacts = SearchDependencyMojo.extractArtifacts(malformed);
        assertEquals(0, artifacts.size());
    }
}
