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

import javax.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Searches Maven Central for artifacts matching a query.
 *
 * <p>Uses the Sonatype Central Search API (Solr). Supports free-text search
 * and field-based queries using Solr syntax.</p>
 *
 * <p>Examples:</p>
 * <pre>
 * mvn dependency:search -Dquery=guava
 * mvn dependency:search -Dquery="g:com.google.guava AND a:guava"
 * mvn dependency:search -Dquery=guava -Drows=5
 * </pre>
 *
 * @since 3.11.0
 */
@Mojo(name = "search", requiresProject = false, threadSafe = true)
public class SearchDependencyMojo extends AbstractDependencyMojo {

    private static final String SEARCH_URL = "https://central.sonatype.com/solrsearch/select";

    /**
     * The search query. Supports free-text (e.g., {@code guava}) or Solr field syntax
     * (e.g., {@code g:com.google.guava AND a:guava}).
     */
    @Parameter(property = "query", required = true)
    private String query;

    /**
     * Maximum number of results to return.
     */
    @Parameter(property = "rows", defaultValue = "20")
    private int rows;

    @Inject
    public SearchDependencyMojo(MavenSession session, BuildContext buildContext, MavenProject project) {
        super(session, buildContext, project);
    }

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Searching Maven Central for: " + query);

        try {
            String url = buildSearchUrl();
            JsonObject response = executeSearch(url);
            JsonObject responseBody = response.getJsonObject("response");
            int totalHits = responseBody.getInt("numFound");
            JsonArray docs = responseBody.getJsonArray("docs");

            if (docs.isEmpty()) {
                getLog().info("No results found.");
                return;
            }

            getLog().info("Found " + totalHits + " result(s), showing " + docs.size() + ":");
            getLog().info("");

            // Print header
            String format = "%-40s %-30s %-15s";
            getLog().info(String.format(format, "GroupId", "ArtifactId", "Version"));
            getLog().info(String.format(format, dashes(40), dashes(30), dashes(15)));

            for (int i = 0; i < docs.size(); i++) {
                JsonObject doc = docs.getJsonObject(i);
                String groupId = doc.getString("g", "");
                String artifactId = doc.getString("a", "");
                String version = doc.getString("latestVersion", doc.getString("v", ""));
                getLog().info(String.format(format, groupId, artifactId, version));
            }

            if (totalHits > docs.size()) {
                getLog().info("");
                getLog().info("... and " + (totalHits - docs.size()) + " more. Use -Drows=N to see more results.");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Search failed: " + e.getMessage(), e);
        }
    }

    private String buildSearchUrl() throws UnsupportedEncodingException {
        return SEARCH_URL + "?q=" + URLEncoder.encode(query, "UTF-8") + "&rows=" + rows + "&wt=json";
    }

    private JsonObject executeSearch(String url) throws IOException, MojoExecutionException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);

        int status = connection.getResponseCode();
        if (status != 200) {
            throw new MojoExecutionException("Search API returned HTTP " + status);
        }

        try (InputStream is = connection.getInputStream();
                JsonReader reader = Json.createReader(is)) {
            return reader.readObject();
        }
    }

    private static String dashes(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append('-');
        }
        return sb.toString();
    }
}
