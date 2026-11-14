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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.ParamArtifact;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.DependencyResolutionException;

/**
 * Resolves a single artifact, given by its coordinates, together with its transitive runtime dependencies, and outputs
 * the classpath string of the resolved files from the local repository to a file or log. It combines
 * <code>dependency:get</code>, which needs no project, with <code>dependency:build-classpath</code>. Use it to get
 * the classpath of an ad-hoc artifact from the command line:
 * <pre>
 * mvn dependency:get-classpath -Dartifact=org.apache.maven:maven-model:3.9.9 -Dmdep.outputFile=cp.txt
 * </pre>
 * The artifact itself comes first in the classpath, followed by its dependencies in the order in which the resolver
 * of the running Maven returns them, which may differ between Maven versions. Test and provided dependencies, and
 * optional ones, are not part of the classpath. As <code>dependency:get</code> does, this goal always checks the
 * central repository defined in the super POM. You could use a mirror entry in your <code>settings.xml</code>.
 *
 * @since 3.12.0
 */
@Mojo(name = "get-classpath", requiresProject = false, threadSafe = true)
public class GetClasspathMojo extends AbstractMojo {

    private final ResolverUtil resolverUtil;

    private final ParamArtifact paramArtifact = new ParamArtifact();

    /**
     * Repositories in the format {@code id::[layout::]url} or just URLs, separated by comma. That is,
     * <code>
     * central::default::https://repo.maven.apache.org/maven2,myrepo::https://repo.acme.com,https://repo.acme2.com
     * </code>
     * <p>
     * The comma-separated form works both on the command line and as the text of a single
     * <code>&lt;remoteRepositories&gt;</code> element. In a POM the repositories may also be listed one per element.
     */
    @Parameter(property = "remoteRepositories")
    private List<String> remoteRepositories;

    /**
     * Resolve transitively, putting the artifact and all of its runtime dependencies on the classpath. If
     * {@code false}, the classpath holds the artifact only.
     */
    @Parameter(property = "transitive", defaultValue = "true")
    private boolean transitive = true;

    /**
     * If defined, the file to which the classpath string will be written. If not set, the classpath will be logged at
     * INFO level.
     */
    @Parameter(property = "mdep.outputFile")
    private File outputFile;

    /**
     * The prefix to prepend on each file name of the classpath. If undefined, the paths refer to the actual files
     * stored in the local repository.
     */
    @Parameter(property = "mdep.prefix")
    private String prefix;

    /**
     * Override the char used between the paths. The default is the system-dependent path-separator character: ':' on
     * UNIX systems, ';' on Microsoft Windows systems.
     */
    @Parameter(property = "mdep.pathSeparator", defaultValue = "")
    private String pathSeparator;

    /**
     * Override the char used between path folders. The default is the system-dependent file-separator character: '/'
     * on UNIX systems, '\' on Microsoft Windows systems.
     */
    @Parameter(property = "mdep.fileSeparator", defaultValue = "")
    private String fileSeparator;

    /**
     * Write out the classpath in a format compatible with filtering (<code>classpath=xxxxx</code>).
     */
    @Parameter(property = "mdep.outputFilterFile", defaultValue = "false")
    private boolean outputFilterFile;

    /**
     * Skip plugin execution completely.
     */
    @Parameter(property = "mdep.skip", defaultValue = "false")
    private boolean skip;

    @Inject
    public GetClasspathMojo(ResolverUtil resolverUtil) {
        this.resolverUtil = resolverUtil;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping plugin execution");
            return;
        }

        if (paramArtifact.getArtifact() == null) {
            throw new MojoFailureException("You must specify an artifact, "
                    + "e.g. -Dartifact=org.apache.maven.plugins:maven-downloader-plugin:1.0");
        }

        List<Artifact> artifacts;
        try {
            Artifact artifact = resolverUtil.createArtifactFromParams(paramArtifact);
            List<RemoteRepository> repositories =
                    resolverUtil.remoteRepositories(remoteRepositories, RepositoryPolicy.UPDATE_POLICY_ALWAYS);
            getLog().info("Resolving " + artifact + (transitive ? " with transitive dependencies" : ""));
            artifacts = resolverUtil.resolveArtifactAndDependencies(artifact, repositories, transitive);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage(), e);
        } catch (ArtifactResolutionException | ArtifactDescriptorException | DependencyResolutionException e) {
            throw new MojoExecutionException("Couldn't resolve artifact: " + e.getMessage(), e);
        }

        String classpath = toClasspath(artifacts);
        if (outputFilterFile) {
            classpath = "classpath=" + classpath;
        }

        if (outputFile == null) {
            getLog().info("Classpath:" + System.lineSeparator() + classpath);
        } else {
            writeClasspathFile(classpath);
        }
    }

    private String toClasspath(List<Artifact> artifacts) throws MojoExecutionException {
        String separator = pathSeparator != null && !pathSeparator.isEmpty() ? pathSeparator : File.pathSeparator;

        List<String> paths = new ArrayList<>(artifacts.size());
        for (Artifact artifact : artifacts) {
            paths.add(toPath(artifact));
        }
        return String.join(separator, paths);
    }

    private String toPath(Artifact artifact) throws MojoExecutionException {
        String path;
        if (prefix == null) {
            File file = artifact.getFile();
            if (file == null) {
                throw new MojoExecutionException(artifact + " has no file");
            }
            path = file.getPath();
        } else {
            path = prefix
                    + File.separator
                    + DependencyUtil.getFormattedFileName(
                            RepositoryUtils.toArtifact(artifact), false, false, true, false);
        }
        if (fileSeparator != null && !fileSeparator.isEmpty()) {
            path = path.replaceAll(Pattern.quote(File.separator), Matcher.quoteReplacement(fileSeparator));
        }
        return path;
    }

    private void writeClasspathFile(String classpath) throws MojoExecutionException {
        File parent = outputFile.getAbsoluteFile().getParentFile();
        try {
            Files.createDirectories(parent.toPath());
            Files.write(outputFile.toPath(), classpath.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MojoExecutionException("Error while writing to classpath file '" + outputFile + "'", e);
        }
        getLog().info("Wrote classpath file '" + outputFile + "'.");
    }

    /**
     * A string of the form <code>groupId:artifactId:version[:packaging[:classifier]]</code>, or the form that
     * Maven's error messages and <code>dependency:list</code> print,
     * <code>groupId:artifactId:type[:classifier]:version[:scope]</code>; see the <code>artifact</code> parameter of
     * <code>dependency:get</code> for how the two are told apart. The scope is ignored. The goal fails if it is not
     * set.
     *
     * @param artifact the artifact coordinates
     */
    @Parameter(property = "artifact")
    public void setArtifact(String artifact) {
        paramArtifact.setArtifact(artifact);
    }
}
