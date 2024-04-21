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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.DependencyCollectionException;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.maven.plugins.dependency.exclusion.Coordinates.coordinates;

/**
 * Analyzes the exclusions defined on dependencies in this project and reports if any of them are invalid.
 * <p>
 * Relevant use case is when an artifact in a later version has removed usage of a dependency, making the exclusion no
 * longer valid.
 * </p>
 *
 * @since 3.7.0
 */
@Mojo(name = "analyze-exclusions", requiresDependencyCollection = ResolutionScope.TEST, threadSafe = true)
public class AnalyzeExclusionsMojo extends AbstractMojo {

    @Component
    private MavenProject project;

    @Component
    private ResolverUtil resolverUtil;

    @Component
    private MavenSession session;

    /**
     * Whether to fail the build if invalid exclusions is found.
     *
     * @since 3.7.0
     */
    @Parameter(property = "mdep.exclusion.fail", defaultValue = "false")
    private boolean exclusionFail;

    /**
     * Skip plugin execution completely.
     *
     * @since 3.7.0
     */
    @Parameter(property = "mdep.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Current project modelId.
     */
    private String projectModelId;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().debug("Skipping execution");
            return;
        }

        projectModelId = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();

        Map<Coordinates, Collection<Exclusion>> dependenciesWithExclusions = new HashMap<>();

        project.getDependencyManagement().getDependencies().forEach(dependency -> {
            Collection<Exclusion> exclusions = getExclusionsForDependency(dependency);
            if (!exclusions.isEmpty()) {
                dependenciesWithExclusions
                        .computeIfAbsent(coordinates(dependency), d -> new ArrayList<>())
                        .addAll(exclusions);
            }
        });

        project.getDependencies().forEach(dependency -> {
            Collection<Exclusion> exclusions = getExclusionsForDependency(dependency);
            if (!exclusions.isEmpty()) {
                dependenciesWithExclusions
                        .computeIfAbsent(coordinates(dependency), d -> new ArrayList<>())
                        .addAll(exclusions);
            }
        });

        if (dependenciesWithExclusions.isEmpty()) {
            getLog().debug("No dependencies defined with exclusions - exiting");
            return;
        }

        ExclusionChecker checker = new ExclusionChecker();

        ArtifactTypeRegistry artifactTypeRegistry =
                session.getRepositorySession().getArtifactTypeRegistry();
        for (Map.Entry<Coordinates, Collection<Exclusion>> entry : dependenciesWithExclusions.entrySet()) {

            Coordinates currentCoordinates = entry.getKey();

            Collection<org.eclipse.aether.graph.Dependency> actualDependencies;
            try {
                actualDependencies = resolverUtil.collectDependencies(
                        RepositoryUtils.toDependency(currentCoordinates.getDependency(), artifactTypeRegistry)
                                .setExclusions(null));
            } catch (DependencyCollectionException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

            Set<Coordinates> actualCoordinates = actualDependencies.stream()
                    .map(org.eclipse.aether.graph.Dependency::getArtifact)
                    .map(a -> coordinates(a.getGroupId(), a.getArtifactId()))
                    .collect(toSet());

            Set<Coordinates> exclusions =
                    entry.getValue().stream().map(Coordinates::coordinates).collect(toSet());

            checker.check(currentCoordinates, exclusions, actualCoordinates);
        }

        if (!checker.getViolations().isEmpty()) {
            if (exclusionFail) {
                logViolations(project.getName(), checker.getViolations(), value -> getLog().error(value));
                throw new MojoExecutionException("Invalid exclusions found");
            } else {
                logViolations(project.getName(), checker.getViolations(), value -> getLog().warn(value));
            }
        } else {
            getLog().info("No problems with dependencies exclusions");
        }
    }

    private Collection<Exclusion> getExclusionsForDependency(Dependency dependency) {
        return dependency.getExclusions().stream()
                .filter(this::isExclusionInProject)
                .collect(toList());
    }

    private boolean isExclusionInProject(Exclusion exclusion) {
        String modelId = exclusion.getLocation("").getSource().getModelId();
        return projectModelId.equals(modelId);
    }

    private void logViolations(String name, Map<Coordinates, List<Coordinates>> violations, Consumer<String> logger) {
        logger.accept(name + " defines following unnecessary excludes");
        violations.forEach((dependency, invalidExclusions) -> {
            logger.accept("    " + dependency);
            invalidExclusions.forEach(invalidExclusion -> logger.accept("        - " + invalidExclusion));
        });
    }
}
