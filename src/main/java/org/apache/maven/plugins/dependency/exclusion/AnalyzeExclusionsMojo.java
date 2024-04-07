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
package org.apache.maven.plugins.dependency.exclusion;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.stripToEmpty;
import static org.apache.maven.plugins.dependency.exclusion.Coordinates.coordinates;

/**
 * Analyzes the exclusions defined on dependencies in this project and reports if any of them are invalid.
 * <p>
 * Relevant use case is when an artifact in a later version has removed usage of a dependency, making the exclusion no
 * longer valid.
 * </p>
 *
 * @since 3.6.2
 */
@Mojo(name = "analyze-exclusions", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class AnalyzeExclusionsMojo extends AbstractMojo {

    @Component
    private MavenProject project;

    @Component
    private ProjectBuilder projectBuilder;

    @Component
    private MavenSession session;

    /**
     * Whether to fail the build if invalid exclusions is found.
     */
    @Parameter(property = "failOnWarning", defaultValue = "false")
    private boolean failOnWarning;

    /**
     * Skip plugin execution completely.
     */
    @Parameter(property = "mdep.exclusion.fail", defaultValue = "false")
    private boolean exclusionFail;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (exclusionFail) {
            getLog().debug("Skipping execution");
            return;
        }
        List<Dependency> dependenciesWithExclusions = project.getDependencies().stream()
                .filter(dep -> !dep.getExclusions().isEmpty())
                .collect(toList());

        if (dependenciesWithExclusions.isEmpty()) {
            getLog().debug("No dependencies defined with exclusions - exiting");
            return;
        }

        ExclusionChecker checker = new ExclusionChecker();

        for (final Dependency dependency : dependenciesWithExclusions) {
            Coordinates currentCoordinates = coordinates(dependency.getGroupId(), dependency.getArtifactId());
            Artifact matchingArtifact = project.getArtifacts().stream()
                    .filter(artifact -> matchesDependency(artifact, dependency))
                    .findFirst()
                    .orElseThrow(() -> new MojoExecutionException(
                            format("Error finding Artifact for given Dependency [%s]", dependency)));

            ProjectBuildingResult result = buildProject(matchingArtifact);

            Set<Coordinates> actualDependencies = result.getProject().getArtifacts().stream()
                    .map(a -> coordinates(a.getGroupId(), a.getArtifactId()))
                    .collect(toSet());

            Set<Coordinates> exclusions = dependency.getExclusions().stream()
                    .map(e -> coordinates(e.getGroupId(), e.getArtifactId()))
                    .collect(toSet());

            checker.check(currentCoordinates, exclusions, actualDependencies);
        }

        if (!checker.getViolations().isEmpty()) {
            if (failOnWarning) {
                logViolations(project.getName(), checker.getViolations(), (value) -> getLog().error(value));
                throw new MojoExecutionException("Invalid exclusions found");
            } else {
                logViolations(project.getName(), checker.getViolations(), (value) -> getLog().warn(value));
            }
        }
    }

    private boolean matchesDependency(Artifact artifact, Dependency dependency) {
        return Objects.equals(artifact.getGroupId(), dependency.getGroupId())
                && Objects.equals(artifact.getArtifactId(), dependency.getArtifactId())
                && Objects.equals(artifact.getType(), dependency.getType())
                && Objects.equals(stripToEmpty(artifact.getClassifier()), stripToEmpty(dependency.getClassifier()));
    }

    private void logViolations(String name, Map<Coordinates, List<Coordinates>> violations, Consumer<String> logger) {
        logger.accept(name + " defines following unnecessary excludes");
        violations.forEach((dependency, invalidExclusions) -> {
            logger.accept("    " + dependency + ":");
            invalidExclusions.forEach(invalidExclusion -> {
                logger.accept("        - " + invalidExclusion);
            });
        });
    }

    private ProjectBuildingResult buildProject(Artifact artifact) throws MojoExecutionException {
        try {
            ProjectBuildingRequest projectBuildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            projectBuildingRequest.setResolveDependencies(true);
            return projectBuilder.build(artifact, true, projectBuildingRequest);
        } catch (ProjectBuildingException e) {
            throw new MojoExecutionException(
                    format("Failed to build project for %s:%s", artifact.getGroupId(), artifact.getArtifactId()), e);
        }
    }
}
