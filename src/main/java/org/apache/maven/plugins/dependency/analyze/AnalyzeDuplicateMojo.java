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
package org.apache.maven.plugins.dependency.analyze;

import javax.inject.Inject;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Analyzes the <code>&lt;dependencies/&gt;</code> and <code>&lt;dependencyManagement/&gt;</code> tags in the
 * <code>pom.xml</code> and determines the duplicate declared dependencies.
 *
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 */
@Mojo(name = "analyze-duplicate", aggregator = false, threadSafe = true)
public class AnalyzeDuplicateMojo extends AbstractMojo {
    public static final String MESSAGE_DUPLICATE_DEP_IN_DEPENDENCIES =
            "List of duplicate dependencies defined in <dependencies/> in your pom.xml:\n";

    public static final String MESSAGE_DUPLICATE_DEP_IN_DEPMGMT =
            "List of duplicate dependencies defined in <dependencyManagement/> in your pom.xml:\n";

    /**
     * Skip plugin execution completely.
     *
     * @since 2.7
     */
    @Parameter(property = "mdep.analyze.skip", defaultValue = "false")
    private boolean skip;

    /**
     * The Maven project to analyze.
     */
    private MavenProject project;

    @Inject
    public AnalyzeDuplicateMojo(MavenProject project) {
        this.project = project;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping plugin execution");
            return;
        }

        MavenXpp3Reader pomReader = new MavenXpp3Reader();
        Model model;
        try (Reader reader = new XmlStreamReader(project.getFile())) {
            model = pomReader.read(reader);
        } catch (IOException | XmlPullParserException e) {
            throw new MojoExecutionException("Exception: " + e.getMessage(), e);
        }

        Set<String> duplicateDependencies = Collections.emptySet();
        if (model.getDependencies() != null) {
            duplicateDependencies = findDuplicateDependencies(model.getDependencies());
        }

        Set<String> duplicateDependenciesManagement = Collections.emptySet();
        if (model.getDependencyManagement() != null
                && model.getDependencyManagement().getDependencies() != null) {
            duplicateDependenciesManagement =
                    findDuplicateDependencies(model.getDependencyManagement().getDependencies());
        }

        if (getLog().isInfoEnabled()) {
            StringBuilder sb = new StringBuilder();

            createMessage(duplicateDependencies, sb, MESSAGE_DUPLICATE_DEP_IN_DEPENDENCIES);
            createMessage(duplicateDependenciesManagement, sb, MESSAGE_DUPLICATE_DEP_IN_DEPMGMT);

            if (sb.length() > 0) {
                getLog().info(sb.toString());
            } else {
                getLog().info("No duplicate dependencies found in <dependencies/> or in <dependencyManagement/>");
            }
        }
    }

    private void createMessage(
            Set<String> duplicateDependencies, StringBuilder sb, String messageDuplicateDepInDependencies) {
        if (!duplicateDependencies.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(System.lineSeparator());
            }
            sb.append(messageDuplicateDepInDependencies);
            for (Iterator<String> it = duplicateDependencies.iterator(); it.hasNext(); ) {
                String dup = it.next();

                sb.append("\to ").append(dup);
                if (it.hasNext()) {
                    sb.append(System.lineSeparator());
                }
            }
        }
    }

    private Set<String> findDuplicateDependencies(List<Dependency> modelDependencies) {
        List<String> modelDependencies2 =
                modelDependencies.stream().map(Dependency::getManagementKey).collect(Collectors.toList());
        // remove one instance of each element from the list
        modelDependencies2.removeIf(new HashSet<>(modelDependencies2)::remove);
        // keep a single instance of each duplicate
        return new LinkedHashSet<>(modelDependencies2);
    }
}
