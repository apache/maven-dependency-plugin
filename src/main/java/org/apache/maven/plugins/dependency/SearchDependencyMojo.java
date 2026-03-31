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
 * the results in a tabular format.
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

        String jsonResponse = performSearch();
        displayResults(jsonResponse);
    }

    String performSearch() throws MojoExecutionException, MojoFailureException {
        HttpURLConnection connection = null;
        try {
            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name());
            String urlStr = repositoryUrl + "?q=" + encodedQuery + "&rows=" + rows + "&wt=json";
            URL url = new URL(urlStr);

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Apache-Maven-Dependency-Plugin/3.11.0 (dependency:search)");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new MojoFailureException("Maven Central search API returned HTTP " + status
                        + ". Check your query and network connection.");
            }

            return readResponse(connection.getInputStream());
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
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
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
}
