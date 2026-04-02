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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Queries Maven Central for artifacts matching a search term and displays
 * the results in a tabular format. Does not require a project context.
 *
 * <p>Response validation includes Content-Type checking, JSON structure verification,
 * and detailed error messages for HTTP failures (including rate limiting).</p>
 *
 * @since 3.11.0
 */
@Mojo(name = "search", requiresProject = false, threadSafe = true)
public class SearchDependencyMojo extends AbstractMojo {

    /**
     * Free-text search term, or a structured query (e.g., {@code g:com.google.adk}, {@code a:google-adk}).
     */
    @Parameter(property = "query", required = true)
    private String query;

    /**
     * Maximum number of results to return.
     */
    @Parameter(property = "rows", defaultValue = "10")
    private int rows;

    /**
     * Maven Central Search API endpoint.
     */
    @Parameter(property = "repositoryUrl", defaultValue = "https://search.maven.org/solrsearch/select")
    private String repositoryUrl;

    /**
     * Skip plugin execution.
     */
    @Parameter(property = "mdep.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping plugin execution");
            return;
        }

        if (query == null || query.trim().isEmpty()) {
            throw new MojoFailureException("Search query must not be empty.");
        }

        if (rows < 1) {
            throw new MojoFailureException("The 'rows' parameter must be a positive integer, got: " + rows);
        }

        String jsonResponse = performSearch(query.trim(), rows);
        displayResults(jsonResponse);
    }

    /**
     * Queries the Maven Central search API.
     *
     * @param searchQuery the Solr query string
     * @param maxRows     maximum results to return
     */
    String performSearch(String searchQuery, int maxRows) throws MojoExecutionException, MojoFailureException {
        HttpURLConnection connection = null;
        try {
            String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.name());
            String urlStr = repositoryUrl + "?q=" + encodedQuery + "&rows=" + maxRows + "&wt=json";
            URL url = new URL(urlStr);

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty(
                    "User-Agent", "Apache-Maven-Dependency-Plugin/" + getPluginVersion() + " (dependency:search)");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                String errorBody = readStream(connection.getErrorStream());
                if (status == 429) {
                    throw new MojoFailureException("Maven Central search API rate limit exceeded (HTTP 429). "
                            + "Please wait a moment and try again."
                            + (errorBody.isEmpty() ? "" : " Server response: " + errorBody));
                }
                throw new MojoFailureException("Maven Central search API returned HTTP " + status + "."
                        + (errorBody.isEmpty() ? "" : " Server response: " + errorBody));
            }

            String contentType = connection.getContentType();
            if (contentType != null && !contentType.contains("json")) {
                throw new MojoFailureException("Maven Central search API returned unexpected content type: "
                        + contentType + ". Expected a JSON response.");
            }

            String response = readStream(connection.getInputStream());

            if (response.isEmpty() || response.charAt(0) != '{') {
                throw new MojoFailureException("Maven Central search API returned a non-JSON response. "
                        + "The API endpoint may have changed or returned an error page.");
            }

            return response;
        } catch (MojoFailureException e) {
            throw e;
        } catch (java.net.UnknownHostException | java.net.ConnectException e) {
            throw new MojoFailureException(
                    "Unable to reach Maven Central search API. Check your network connection.", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to query Maven Central search API: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    void displayResults(String jsonResponse) throws MojoExecutionException {
        try {
            int numFound = extractInt(jsonResponse, "numFound");

            if (numFound == 0) {
                getLog().info("No artifacts found matching '" + query + "'.");
                return;
            }

            List<String[]> artifacts = extractArtifacts(jsonResponse);

            if (artifacts.isEmpty()) {
                getLog().info("No artifacts found matching '" + query + "'.");
                return;
            }

            int[] colWidths = calculateColumnWidths(artifacts);
            String headerFmt = "  %-" + colWidths[0] + "s  %-" + colWidths[1] + "s  %-" + colWidths[2] + "s";

            StringBuilder sb = new StringBuilder();
            sb.append("Search results for: ").append(query).append('\n');
            sb.append('\n');
            sb.append(String.format(headerFmt, "groupId", "artifactId", "latest version"))
                    .append('\n');
            sb.append(buildSeparator(colWidths[0] + colWidths[1] + colWidths[2] + 6))
                    .append('\n');

            String firstGav = null;
            for (String[] artifact : artifacts) {
                sb.append(String.format(headerFmt, artifact[0], artifact[1], artifact[2]))
                        .append('\n');
                if (firstGav == null) {
                    String version = artifact[2];
                    if (version != null && !version.isEmpty()) {
                        firstGav = artifact[0] + ":" + artifact[1] + ":" + version;
                    }
                }
            }

            sb.append('\n');
            if (firstGav != null) {
                sb.append(artifacts.size())
                        .append(" result(s) found. Use dependency:add to add one to your project:\n");
                sb.append("  mvn dependency:add -Dgav=\"").append(firstGav).append('"');
            } else {
                sb.append(artifacts.size()).append(" result(s) found.");
            }

            getLog().info(sb.toString());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to parse search API response.", e);
        }
    }

    /**
     * Calculates minimum column widths for groupId, artifactId, and version columns.
     */
    private static int[] calculateColumnWidths(List<String[]> artifacts) {
        int maxGroupId = "groupId".length();
        int maxArtifactId = "artifactId".length();
        int maxVersion = "latest version".length();
        for (String[] artifact : artifacts) {
            maxGroupId = Math.max(maxGroupId, artifact[0].length());
            maxArtifactId = Math.max(maxArtifactId, artifact[1].length());
            maxVersion = Math.max(maxVersion, artifact[2].length());
        }
        return new int[] {maxGroupId, maxArtifactId, maxVersion};
    }

    /**
     * Builds a horizontal separator line of box-drawing characters.
     */
    private static String buildSeparator(int length) {
        StringBuilder sb = new StringBuilder("  ");
        for (int i = 0; i < length; i++) {
            sb.append('\u2500');
        }
        return sb.toString();
    }

    /**
     * Extracts artifact info (groupId, artifactId, latestVersion) from the Solr JSON response.
     */
    static List<String[]> extractArtifacts(String json) {
        List<String[]> results = new ArrayList<>();
        int docsStart = json.indexOf("\"docs\":");
        if (docsStart < 0) {
            return results;
        }

        int arrayStart = json.indexOf('[', docsStart);
        if (arrayStart < 0) {
            return results;
        }

        int pos = arrayStart + 1;
        while (pos < json.length()) {
            int objStart = json.indexOf('{', pos);
            if (objStart < 0) {
                break;
            }
            int objEnd = findMatchingBrace(json, objStart);
            if (objEnd < 0) {
                break;
            }

            String docJson = json.substring(objStart, objEnd + 1);
            String g = extractStringField(docJson, "g");
            String a = extractStringField(docJson, "a");
            String v = extractStringField(docJson, "latestVersion");
            if (g != null && a != null) {
                results.add(new String[] {g, a, v != null ? v : ""});
            }

            pos = objEnd + 1;
            int nextChar = skipWhitespace(json, pos);
            if (nextChar < json.length() && json.charAt(nextChar) == ']') {
                break;
            }
        }
        return results;
    }

    /**
     * Extracts individual version strings from a GAV-mode Solr JSON response
     * (uses the {@code "v"} field instead of {@code "latestVersion"}).
     */
    static List<String> extractVersionList(String json) {
        List<String> versions = new ArrayList<>();
        int docsStart = json.indexOf("\"docs\":");
        if (docsStart < 0) {
            return versions;
        }
        int arrayStart = json.indexOf('[', docsStart);
        if (arrayStart < 0) {
            return versions;
        }

        int pos = arrayStart + 1;
        while (pos < json.length()) {
            int objStart = json.indexOf('{', pos);
            if (objStart < 0) {
                break;
            }
            int objEnd = findMatchingBrace(json, objStart);
            if (objEnd < 0) {
                break;
            }

            String docJson = json.substring(objStart, objEnd + 1);
            String v = extractStringField(docJson, "v");
            if (v != null && !v.isEmpty()) {
                versions.add(v);
            }

            pos = objEnd + 1;
            int nextChar = skipWhitespace(json, pos);
            if (nextChar < json.length() && json.charAt(nextChar) == ']') {
                break;
            }
        }
        return versions;
    }

    private static int findMatchingBrace(String json, int openPos) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openPos; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int skipWhitespace(String s, int pos) {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
        if (pos < s.length() && s.charAt(pos) == ',') {
            pos++;
        }
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    static String extractStringField(String json, String field) {
        String key = "\"" + field + "\"";
        int keyPos = json.indexOf(key);
        if (keyPos < 0) {
            return null;
        }
        // Find the colon after the key
        int colonPos = json.indexOf(':', keyPos + key.length());
        if (colonPos < 0) {
            return null;
        }
        // Find the opening quote of the value
        int openQuote = json.indexOf('"', colonPos + 1);
        if (openQuote < 0) {
            return null;
        }
        // Find the closing quote, handling escaped quotes
        StringBuilder value = new StringBuilder();
        for (int i = openQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == '"' || next == '\\') {
                    value.append(next);
                    i++;
                    continue;
                }
            }
            if (c == '"') {
                return value.toString();
            }
            value.append(c);
        }
        return null;
    }

    static int extractInt(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    private String getPluginVersion() {
        java.util.Map<String, Object> ctx = getPluginContext();
        if (ctx != null) {
            PluginDescriptor pluginDescriptor = (PluginDescriptor) ctx.get("pluginDescriptor");
            if (pluginDescriptor != null && pluginDescriptor.getVersion() != null) {
                return pluginDescriptor.getVersion();
            }
        }
        return "SNAPSHOT";
    }
}
