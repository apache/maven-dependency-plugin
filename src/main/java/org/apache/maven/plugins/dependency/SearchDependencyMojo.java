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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    /**
     * Enable interactive mode for browsing and selecting search results.
     * When enabled and a console is available, results are displayed as a
     * numbered list. You can select an artifact, browse its versions, and
     * get the {@code dependency:add} command to run.
     *
     * <p>Automatically disabled when no console is available (e.g., piped output)
     * or when Maven runs in batch mode ({@code -B}).</p>
     */
    @Parameter(property = "interactive", defaultValue = "true")
    private boolean interactive;

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

        String jsonResponse = performSearch(query.trim(), rows, false);

        if (isInteractive()) {
            interactiveLoop(jsonResponse);
        } else {
            displayResults(jsonResponse);
        }
    }

    String performSearch() throws MojoExecutionException, MojoFailureException {
        return performSearch(query.trim(), rows, false);
    }

    /**
     * Queries the Maven Central search API.
     *
     * @param searchQuery the Solr query string
     * @param maxRows     maximum results to return
     * @param gavMode     if true, appends {@code &core=gav} for per-version results
     */
    String performSearch(String searchQuery, int maxRows, boolean gavMode)
            throws MojoExecutionException, MojoFailureException {
        HttpURLConnection connection = null;
        try {
            String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.name());
            String urlStr = repositoryUrl + "?q=" + encodedQuery + "&rows=" + maxRows + "&wt=json";
            if (gavMode) {
                urlStr += "&core=gav";
            }
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
                String errorBody = readErrorResponse(connection);
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

            String response = readResponse(connection.getInputStream());

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

    private String readResponse(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String readErrorResponse(HttpURLConnection connection) {
        try {
            InputStream errorStream = connection.getErrorStream();
            if (errorStream == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                    if (sb.length() > 500) {
                        sb.setLength(500);
                        sb.append("...");
                        break;
                    }
                }
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    void displayResults(String jsonResponse) throws MojoExecutionException {
        try {
            int numFound = extractInt(jsonResponse, "numFound");

            getLog().info("Search results for: " + query);
            getLog().info("");

            if (numFound == 0) {
                getLog().info("No artifacts found matching '" + query + "'.");
                return;
            }

            List<String[]> artifacts = extractArtifacts(jsonResponse);

            if (artifacts.isEmpty()) {
                getLog().info("No artifacts found matching '" + query + "'.");
                return;
            }

            // Calculate column widths
            int maxGroupId = "groupId".length();
            int maxArtifactId = "artifactId".length();
            int maxVersion = "latest version".length();

            for (String[] artifact : artifacts) {
                maxGroupId = Math.max(maxGroupId, artifact[0].length());
                maxArtifactId = Math.max(maxArtifactId, artifact[1].length());
                maxVersion = Math.max(maxVersion, artifact[2].length());
            }

            String headerFmt = "  %-" + maxGroupId + "s  %-" + maxArtifactId + "s  %-" + maxVersion + "s";
            getLog().info(String.format(headerFmt, "groupId", "artifactId", "latest version"));

            int lineLen = maxGroupId + maxArtifactId + maxVersion + 6;
            StringBuilder separator = new StringBuilder("  ");
            for (int i = 0; i < lineLen; i++) {
                separator.append('\u2500');
            }
            getLog().info(separator.toString());

            String firstGav = null;
            for (String[] artifact : artifacts) {
                getLog().info(String.format(headerFmt, artifact[0], artifact[1], artifact[2]));
                if (firstGav == null) {
                    firstGav = artifact[0] + ":" + artifact[1] + ":" + artifact[2];
                }
            }

            getLog().info("");
            getLog().info(artifacts.size() + " result(s) found. Use dependency:add to add one to your project:");
            getLog().info("  mvn dependency:add -Dgav=\"" + firstGav + "\"");
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to parse search API response.", e);
        }
    }

    // ── Interactive mode ────────────────────────────────────────────────

    /**
     * Whether interactive mode should be used. Package-private for testing.
     */
    boolean isInteractive() {
        return interactive && System.console() != null;
    }

    /**
     * Reads a line of input from the user. Package-private so tests can override via spy.
     *
     * @return the user input, or {@code null} if no console is available
     */
    String readLine(String prompt) {
        if (System.console() == null) {
            return null;
        }
        return System.console().readLine("%s", prompt);
    }

    private void interactiveLoop(String initialJson) throws MojoExecutionException, MojoFailureException {
        String currentJson = initialJson;
        String currentQuery = query.trim();

        while (true) {
            List<String[]> artifacts = extractArtifacts(currentJson);
            int numFound = extractInt(currentJson, "numFound");

            getLog().info("Search results for: " + currentQuery);
            getLog().info("");

            if (artifacts.isEmpty()) {
                getLog().info("No artifacts found matching '" + currentQuery + "'.");
                getLog().info("");
                String input = readLine("Enter a new search term, or 'q' to quit: ");
                if (input == null
                        || input.trim().isEmpty()
                        || "q".equalsIgnoreCase(input.trim())
                        || "quit".equalsIgnoreCase(input.trim())) {
                    return;
                }
                currentQuery = input.trim();
                currentJson = performSearch(currentQuery, rows, false);
                continue;
            }

            displayNumberedResults(artifacts, numFound);

            String input = readLine("Enter number to select, text to search again, 'q' to quit: ");
            if (input == null
                    || input.trim().isEmpty()
                    || "q".equalsIgnoreCase(input.trim())
                    || "quit".equalsIgnoreCase(input.trim())) {
                return;
            }

            input = input.trim();

            try {
                int selection = Integer.parseInt(input);
                if (selection >= 1 && selection <= artifacts.size()) {
                    String[] selected = artifacts.get(selection - 1);
                    if (handleVersionSelection(selected[0], selected[1], selected[2])) {
                        return;
                    }
                    // User chose 'b' to go back — redisplay same results
                    continue;
                } else {
                    getLog().warn("Invalid selection: " + selection + ". Enter a number between 1 and "
                            + artifacts.size() + ".");
                    continue;
                }
            } catch (NumberFormatException e) {
                // Not a number — treat as a new search query
            }

            currentQuery = input;
            currentJson = performSearch(currentQuery, rows, false);
        }
    }

    private void displayNumberedResults(List<String[]> artifacts, int totalFound) {
        int maxGroupId = "groupId".length();
        int maxArtifactId = "artifactId".length();
        int maxVersion = "latest version".length();

        for (String[] artifact : artifacts) {
            maxGroupId = Math.max(maxGroupId, artifact[0].length());
            maxArtifactId = Math.max(maxArtifactId, artifact[1].length());
            maxVersion = Math.max(maxVersion, artifact[2].length());
        }

        int numWidth = String.valueOf(artifacts.size()).length();
        String fmt = "  %" + numWidth + "d  %-" + maxGroupId + "s  %-" + maxArtifactId + "s  %-" + maxVersion + "s";
        String headerFmt =
                "  %" + numWidth + "s  %-" + maxGroupId + "s  %-" + maxArtifactId + "s  %-" + maxVersion + "s";

        getLog().info(String.format(headerFmt, "#", "groupId", "artifactId", "latest version"));

        int lineLen = numWidth + 2 + maxGroupId + maxArtifactId + maxVersion + 6;
        StringBuilder separator = new StringBuilder("  ");
        for (int i = 0; i < lineLen; i++) {
            separator.append('\u2500');
        }
        getLog().info(separator.toString());

        for (int i = 0; i < artifacts.size(); i++) {
            String[] a = artifacts.get(i);
            getLog().info(String.format(fmt, i + 1, a[0], a[1], a[2]));
        }

        getLog().info("");
        if (totalFound > artifacts.size()) {
            getLog().info(artifacts.size() + " of " + totalFound + " result(s) shown.");
        } else {
            getLog().info(artifacts.size() + " result(s) found.");
        }
        getLog().info("");
    }

    /**
     * Handles version selection for a chosen artifact.
     *
     * @return {@code true} if the user completed selection (or quit),
     *         {@code false} if they chose to go back to the artifact list
     */
    private boolean handleVersionSelection(String groupId, String artifactId, String latestVersion)
            throws MojoExecutionException, MojoFailureException {
        getLog().info("");
        getLog().info("Fetching versions for " + groupId + ":" + artifactId + "...");

        List<String> versions;
        int totalVersions = 0;
        try {
            String versionJson = performSearch("g:\"" + groupId + "\" AND a:\"" + artifactId + "\"", 20, true);
            versions = extractVersionList(versionJson);
            totalVersions = extractInt(versionJson, "numFound");
        } catch (Exception e) {
            getLog().debug("Failed to fetch versions: " + e.getMessage());
            versions = new ArrayList<>();
        }

        // Resolve the effective default version: prefer latestVersion from initial search,
        // fall back to first version from the GAV query, or fail gracefully.
        String defaultVersion = (latestVersion != null && !latestVersion.isEmpty())
                ? latestVersion
                : (!versions.isEmpty() ? versions.get(0) : null);

        if (versions.isEmpty() && defaultVersion != null) {
            versions = new ArrayList<>();
            versions.add(defaultVersion);
        }

        if (versions.isEmpty()) {
            getLog().warn("No version information available for " + groupId + ":" + artifactId + ".");
            return false;
        }

        getLog().info("");
        getLog().info("Versions of " + groupId + ":" + artifactId + ":");
        getLog().info("");

        for (int i = 0; i < versions.size(); i++) {
            String marker = versions.get(i).equals(defaultVersion) ? " (latest)" : "";
            getLog().info("  " + (i + 1) + ") " + versions.get(i) + marker);
        }
        if (totalVersions > versions.size()) {
            getLog().info("  ... and " + (totalVersions - versions.size()) + " older version(s) not shown.");
        }
        getLog().info("");

        String input = readLine("Enter version number, 'b' to go back, or Enter for latest (" + defaultVersion + "): ");

        if (input == null || input.trim().isEmpty()) {
            printAddCommand(groupId, artifactId, defaultVersion);
            return true;
        }

        input = input.trim();

        if ("b".equalsIgnoreCase(input) || "back".equalsIgnoreCase(input)) {
            return false;
        }

        if ("q".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
            return true;
        }

        String selectedVersion = defaultVersion;
        try {
            int sel = Integer.parseInt(input);
            if (sel >= 1 && sel <= versions.size()) {
                selectedVersion = versions.get(sel - 1);
            } else {
                getLog().warn("Invalid selection, using latest version.");
            }
        } catch (NumberFormatException e) {
            getLog().warn("Invalid input, using latest version.");
        }

        printAddCommand(groupId, artifactId, selectedVersion);
        return true;
    }

    private void printAddCommand(String groupId, String artifactId, String version) {
        String gav = groupId + ":" + artifactId + ":" + version;
        getLog().info("");
        getLog().info("Selected: " + gav);
        getLog().info("");
        getLog().info("To add this dependency to your project:");
        getLog().info("  mvn dependency:add -Dgav=\"" + gav + "\"");
        getLog().info("");
        getLog().info("With a specific scope:");
        getLog().info("  mvn dependency:add -Dgav=\"" + gav + "\" -Dscope=test");
        getLog().info("");
        getLog().info("To dependencyManagement:");
        getLog().info("  mvn dependency:add -Dgav=\"" + gav + "\" -Dmanaged");
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
        Package pkg = getClass().getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            return pkg.getImplementationVersion();
        }
        return "SNAPSHOT";
    }
}
