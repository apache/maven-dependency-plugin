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

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.utils.ParamArtifact;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.DependencyResolutionException;

/**
 * Retrieves and lists all classes contained in the specified artifact from the specified remote repositories.
 *
 * @since 3.1.3
 */
@Mojo(name = "list-classes", requiresProject = false, threadSafe = true)
public class ListClassesMojo extends AbstractMojo {

    private ResolverUtil resolverUtil;

    private ParamArtifact paramArtifact = new ParamArtifact();

    @Inject
    public ListClassesMojo(ResolverUtil resolverUtil) {
        this.resolverUtil = resolverUtil;
    }

    /**
     * The group ID of the artifact to download. Ignored if {@code artifact} is used.
     *
     * @since 3.1.3
     */
    @Parameter(property = "groupId")
    public void setGroupId(String groupId) {
        paramArtifact.setGroupId(groupId);
    }

    /**
     * The artifact ID of the artifact to download. Ignored if {@code artifact} is used.
     *
     * @since 3.1.3
     */
    @Parameter(property = "artifactId")
    public void setArtifactId(String artifactId) {
        paramArtifact.setArtifactId(artifactId);
    }

    /**
     * The version of the artifact to download. Ignored if {@code artifact} is used.
     *
     * @since 3.1.3
     */
    @Parameter(property = "version")
    public void setVersion(String version) {
        paramArtifact.setVersion(version);
    }

    /**
     * The classifier of the artifact to download. Ignored if {@code artifact} is used.
     *
     * @since 3.1.3
     */
    @Parameter(property = "classifier")
    public void setClassifier(String classifier) {
        paramArtifact.setClassifier(classifier);
    }

    /**
     * The packaging of the artifact to download. Ignored if {@code artifact} is used.
     *
     * @since 3.1.3
     */
    @Parameter(property = "packaging", defaultValue = "jar")
    public void setPackaging(String packaging) {
        paramArtifact.setPackaging(packaging);
    }

    /**
     * A string of the form {@code groupId:artifactId:version[:packaging[:classifier]]}.
     *
     * @since 3.1.3
     */
    @Parameter(property = "artifact")
    public void setArtifact(String artifact) {
        paramArtifact.setArtifact(artifact);
    }

    /**
     * Repositories in the format {@code id::[layout::]url} or just URLs. That is,
     * <code>
     * central::default::https://repo.maven.apache.org/maven2,myrepo::https://repo.acme.com,https://repo.acme2.com
     * </code>
     *
     * @since 3.1.3
     */
    @Parameter(property = "remoteRepositories")
    private List<String> remoteRepositories;

    /**
     * Download transitively, retrieving the specified artifact and all of its dependencies.
     *
     * @since 3.1.3
     */
    @Parameter(property = "transitive", defaultValue = "false")
    private boolean transitive = false;

    /**
     * Skip plugin execution completely.
     *
     * @since 3.6.0
     */
    @Parameter(property = "mdep.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping plugin execution");
            return;
        }

        if (!paramArtifact.isDataSet()) {
            throw new MojoExecutionException("You must specify an artifact OR GAV separately, "
                    + "e.g. -Dartifact=org.apache.maven.plugins:maven-downloader-plugin:1.0 OR "
                    + "-DgroupId=org.apache.maven.plugins -DartifactId=maven-downloader-plugin -Dversion=1.0");
        }

        Artifact artifact = resolverUtil.createArtifactFromParams(paramArtifact);

        try {
            if (transitive) {
                List<Artifact> artifacts =
                        resolverUtil.resolveDependencies(artifact, resolverUtil.remoteRepositories(remoteRepositories));

                for (Artifact a : artifacts) {
                    printClassesFromArtifactResult(a.getFile());
                }
            } else {
                Artifact a =
                        resolverUtil.resolveArtifact(artifact, resolverUtil.remoteRepositories(remoteRepositories));
                printClassesFromArtifactResult(a.getFile());
            }
        } catch (IOException | ArtifactResolutionException | DependencyResolutionException e) {
            throw new MojoExecutionException("Couldn't download artifact: " + e.getMessage(), e);
        }
    }

    private void printClassesFromArtifactResult(File file) throws IOException {
        // open jar file in try-with-resources statement to guarantee the file closes after use regardless of errors
        try (JarFile jarFile = new JarFile(file)) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // filter out files that do not end in .class
                if (!entryName.endsWith(".class")) {
                    continue;
                }

                // remove .class from the end and change format to use periods instead of forward slashes
                String className =
                        entryName.substring(0, entryName.length() - 6).replace('/', '.');
                getLog().info(className);
            }
        }
    }
}
