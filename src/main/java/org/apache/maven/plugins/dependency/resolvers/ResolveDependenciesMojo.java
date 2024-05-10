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
package org.apache.maven.plugins.dependency.resolvers;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.filters.ResolveFileFilter;
import org.apache.maven.plugins.dependency.utils.markers.SourcesFileMarkerHandler;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.apache.maven.shared.utils.logging.MessageBuilder;
import org.apache.maven.shared.utils.logging.MessageUtils;

/**
 * Goal that resolves the project dependencies from the repository. When using this goal while running on Java 9 the
 * module names will be visible as well.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 2.0
 */
@Mojo(
        name = "resolve",
        requiresDependencyResolution = ResolutionScope.TEST,
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        threadSafe = true)
public class ResolveDependenciesMojo extends AbstractResolveMojo {

    @Parameter(property = "outputEncoding", defaultValue = "${project.reporting.outputEncoding}")
    private String outputEncoding;

    /**
     * If we should display the scope when resolving
     *
     * @since 2.0-alpha-2
     */
    @Parameter(property = "mdep.outputScope", defaultValue = "true")
    protected boolean outputScope;

    /**
     * Output absolute filename for resolved artifacts
     *
     * @since 2.0
     */
    @Parameter(property = "outputAbsoluteArtifactFilename", defaultValue = "false")
    private boolean outputAbsoluteArtifactFilename;

    /**
     * Only used to store results for integration test validation
     */
    DependencyStatusSets results;

    /**
     * Sort the output list of resolved artifacts alphabetically. The default ordering matches the classpath order.
     *
     * @since 2.8
     */
    @Parameter(property = "sort", defaultValue = "false")
    boolean sort;

    /**
     * Include parent poms in the dependency resolution list.
     *
     * @since 2.8
     */
    @Parameter(property = "includeParents", defaultValue = "false")
    boolean includeParents;

    /**
     * Main entry into mojo. Gets the list of dependencies and iterates through displaying the resolved version.
     *
     * @throws MojoExecutionException with a message if an error occurs
     */
    @Override
    protected void doExecute() throws MojoExecutionException {
        // get sets of dependencies
        results = this.getDependencySets(false, includeParents);

        String output = getOutput(outputAbsoluteArtifactFilename, outputScope, sort);
        try {
            if (outputFile == null) {
                DependencyUtil.log(output, getLog());
            } else {
                String encoding = Objects.toString(outputEncoding, "UTF-8");
                DependencyUtil.write(output, outputFile, appendOutput, encoding);
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * @return returns the results
     */
    public DependencyStatusSets getResults() {
        return this.results;
    }

    @Override
    protected ArtifactsFilter getMarkedArtifactFilter() {
        return new ResolveFileFilter(new SourcesFileMarkerHandler(this.markersDirectory));
    }

    /**
     * @param outputAbsoluteArtifactFilename absolute artifact filename
     * @param theOutputScope the output scope
     * @param theSort sort yes/no
     * @return the output
     */
    public String getOutput(boolean outputAbsoluteArtifactFilename, boolean theOutputScope, boolean theSort) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator());
        sb.append("The following files have been resolved:");
        sb.append(System.lineSeparator());
        if (results.getResolvedDependencies() == null
                || results.getResolvedDependencies().isEmpty()) {
            sb.append("   none");
            sb.append(System.lineSeparator());
        } else {
            sb.append(buildArtifactListOutput(
                    results.getResolvedDependencies(), outputAbsoluteArtifactFilename, theOutputScope, theSort));
        }

        if (results.getSkippedDependencies() != null
                && !results.getSkippedDependencies().isEmpty()) {
            sb.append(System.lineSeparator());
            sb.append("The following files were skipped:");
            sb.append(System.lineSeparator());
            Set<Artifact> skippedDependencies = new LinkedHashSet<>(results.getSkippedDependencies());
            sb.append(buildArtifactListOutput(
                    skippedDependencies, outputAbsoluteArtifactFilename, theOutputScope, theSort));
        }

        if (results.getUnResolvedDependencies() != null
                && !results.getUnResolvedDependencies().isEmpty()) {
            sb.append(System.lineSeparator());
            sb.append("The following files have NOT been resolved:");
            sb.append(System.lineSeparator());
            Set<Artifact> unResolvedDependencies = new LinkedHashSet<>(results.getUnResolvedDependencies());
            sb.append(buildArtifactListOutput(
                    unResolvedDependencies, outputAbsoluteArtifactFilename, theOutputScope, theSort));
        }
        sb.append(System.lineSeparator());

        return sb.toString();
    }

    private StringBuilder buildArtifactListOutput(
            Set<Artifact> artifacts, boolean outputAbsoluteArtifactFilename, boolean theOutputScope, boolean theSort) {
        StringBuilder sb = new StringBuilder();
        List<String> artifactStringList = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            MessageBuilder messageBuilder = MessageUtils.buffer();

            messageBuilder.a("   ");

            if (theOutputScope) {
                messageBuilder.a(artifact.toString());
            } else {
                messageBuilder.a(artifact.getId());
            }

            if (outputAbsoluteArtifactFilename) {
                try {
                    // we want to print the absolute file name here
                    String artifactFilename =
                            artifact.getFile().getAbsoluteFile().getPath();

                    messageBuilder.a(':').a(artifactFilename);
                } catch (NullPointerException e) {
                    // ignore the null pointer, we'll output a null string
                }
            }

            if (theOutputScope && artifact.isOptional()) {
                messageBuilder.a(" (optional)");
            }

            // dependencies:collect won't download jars
            if (artifact.getFile() != null) {
                ModuleDescriptor moduleDescriptor = getModuleDescriptor(artifact.getFile());
                if (moduleDescriptor != null) {
                    messageBuilder.project(" -- module " + moduleDescriptor.name);

                    if (moduleDescriptor.automatic) {
                        if ("MANIFEST".equals(moduleDescriptor.moduleNameSource)) {
                            messageBuilder.strong(" [auto]");
                        } else {
                            messageBuilder.warning(" (auto)");
                        }
                    }
                }
            }
            artifactStringList.add(messageBuilder + System.lineSeparator());
        }
        if (theSort) {
            Collections.sort(artifactStringList);
        }
        for (String artifactString : artifactStringList) {
            sb.append(artifactString);
        }
        return sb;
    }

    private ModuleDescriptor getModuleDescriptor(File artifactFile) {
        ModuleDescriptor moduleDescriptor = null;
        try {
            // Use Java9 code to get moduleName, don't try to do it better with own implementation
            Class<?> moduleFinderClass = Class.forName("java.lang.module.ModuleFinder");

            java.nio.file.Path path = artifactFile.toPath();

            Method ofMethod = moduleFinderClass.getMethod("of", java.nio.file.Path[].class);
            Object moduleFinderInstance = ofMethod.invoke(null, new Object[] {new java.nio.file.Path[] {path}});

            Method findAllMethod = moduleFinderClass.getMethod("findAll");
            Set<Object> moduleReferences = (Set<Object>) findAllMethod.invoke(moduleFinderInstance);

            // moduleReferences can be empty when referring to target/classes without module-info.class
            if (!moduleReferences.isEmpty()) {
                Object moduleReference = moduleReferences.iterator().next();
                Method descriptorMethod = moduleReference.getClass().getMethod("descriptor");
                Object moduleDescriptorInstance = descriptorMethod.invoke(moduleReference);

                Method nameMethod = moduleDescriptorInstance.getClass().getMethod("name");
                String name = (String) nameMethod.invoke(moduleDescriptorInstance);

                moduleDescriptor = new ModuleDescriptor();
                moduleDescriptor.name = name;

                Method isAutomaticMethod = moduleDescriptorInstance.getClass().getMethod("isAutomatic");
                moduleDescriptor.automatic = (Boolean) isAutomaticMethod.invoke(moduleDescriptorInstance);

                if (moduleDescriptor.automatic) {
                    if (artifactFile.isFile()) {
                        try (JarFile jarFile = new JarFile(artifactFile)) {
                            Manifest manifest = jarFile.getManifest();

                            if (manifest != null
                                    && manifest.getMainAttributes().getValue("Automatic-Module-Name") != null) {
                                moduleDescriptor.moduleNameSource = "MANIFEST";
                            } else {
                                moduleDescriptor.moduleNameSource = "FILENAME";
                            }
                        } catch (IOException e) {
                            // noop
                        }
                    }
                }
            }
        } catch (ClassNotFoundException | SecurityException | IllegalAccessException | IllegalArgumentException e) {
            // do nothing
        } catch (NoSuchMethodException e) {
            getLog().warn(e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            getLog().info("Can't extract module name from " + artifactFile.getName() + ": " + cause.getMessage());
        }
        return moduleDescriptor;
    }

    private static class ModuleDescriptor {
        String name;

        boolean automatic = true;

        String moduleNameSource;
    }
}
