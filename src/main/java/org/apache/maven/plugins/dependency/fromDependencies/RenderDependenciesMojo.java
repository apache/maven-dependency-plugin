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
package org.apache.maven.plugins.dependency.fromDependencies;

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.tools.generic.CollectionTool;
import org.sonatype.plexus.build.incremental.BuildContext;

import static java.util.Optional.ofNullable;

/**
 * This goal renders dependencies based on a velocity template.
 *
 * @since 3.9.0
 */
@Mojo(
        name = "render-dependencies",
        requiresDependencyResolution = ResolutionScope.TEST,
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        threadSafe = true)
public class RenderDependenciesMojo extends AbstractDependencyFilterMojo {
    /**
     * Encoding to write the rendered template.
     * @since 3.9.0
     */
    @Parameter(property = "outputEncoding", defaultValue = "${project.reporting.outputEncoding}")
    private String outputEncoding;

    /**
     * The file to write the rendered template string. If undefined, it just prints the classpath as [INFO].
     * @since 3.9.0
     */
    @Parameter(property = "mdep.outputFile")
    private File outputFile;

    /**
     * If not null or empty it will attach the artifact with this classifier.
     * @since 3.9.0
     */
    @Parameter(property = "mdep.classifier", defaultValue = "template")
    private String classifier;

    /**
     * Extension to use for the attached file if classifier is not null/empty.
     * @since 3.9.0
     */
    @Parameter(property = "mdep.extension", defaultValue = "txt")
    private String extension;

    /**
     * Velocity template to use to render the output file.
     * It can be inline or a file path.
     * @since 3.9.0
     */
    @Parameter(property = "mdep.template", required = true)
    private String template;

    private final MavenProjectHelper projectHelper;

    @Inject
    protected RenderDependenciesMojo(
            MavenSession session,
            BuildContext buildContext,
            MavenProject project,
            ResolverUtil resolverUtil,
            ProjectBuilder projectBuilder,
            ArtifactHandlerManager artifactHandlerManager,
            MavenProjectHelper projectHelper) {
        super(session, buildContext, project, resolverUtil, projectBuilder, artifactHandlerManager);
        this.projectHelper = projectHelper;
    }

    /**
     * Main entry into mojo.
     *
     * @throws MojoExecutionException with a message if an error occurs
     */
    @Override
    protected void doExecute() throws MojoExecutionException {
        // sort them to ease template work and ensure it is deterministic
        final List<Artifact> artifacts =
                ofNullable(getResolvedDependencies(true)).orElseGet(Collections::emptySet).stream()
                        .sorted(Comparator.comparing(Artifact::getGroupId)
                                .thenComparing(Artifact::getArtifactId)
                                .thenComparing(Artifact::getBaseVersion)
                                .thenComparing(orEmpty(Artifact::getClassifier))
                                .thenComparing(orEmpty(Artifact::getType)))
                        .collect(Collectors.toList());

        if (artifacts.isEmpty()) {
            getLog().warn("No dependencies found.");
        }

        final String rendered = render(artifacts);

        if (outputFile == null) {
            getLog().info(rendered);
        } else {
            store(rendered, outputFile);
        }
        if (classifier != null && !classifier.isEmpty()) {
            attachFile(rendered);
        }
    }

    /**
     * Render the template.
     *
     * @param artifacts input
     * @return the template rendered
     */
    private String render(final List<Artifact> artifacts) {
        final Path templatePath = getTemplatePath();
        final boolean fromFile = templatePath != null && Files.exists(templatePath);

        final Properties props = new Properties();
        props.setProperty("runtime.strict_mode.enable", "true");
        if (fromFile) {
            props.setProperty(
                    "resource.loader.file.path",
                    templatePath.toAbsolutePath().getParent().toString());
        }

        final VelocityEngine ve = new VelocityEngine(props);
        ve.init();

        final VelocityContext context = new VelocityContext();
        context.put("artifacts", artifacts);
        context.put("sorter", new CollectionTool());

        // Merge template + context
        try (StringWriter writer = new StringWriter()) {
            if (fromFile) {
                final Template template =
                        ve.getTemplate(templatePath.getFileName().toString());
                template.merge(context, writer);
            } else {
                ve.evaluate(context, writer, "tpl-" + Math.abs(hashCode()), template);
            }
            return writer.toString();
        } catch (final IOException e) {
            throw new UncheckedIOException("not possible", e);
        }
    }

    private Path getTemplatePath() {
        try {
            return Paths.get(template);
        } catch (final RuntimeException re) {
            return null;
        }
    }

    /**
     * Trivial null protection impl for comparing callback.
     * @param getter nominal getter.
     * @return a comparer of getter defaulting on empty if getter value is null.
     */
    private Comparator<Artifact> orEmpty(final Function<Artifact, String> getter) {
        return Comparator.comparing(a -> ofNullable(getter.apply(a)).orElse(""));
    }

    /**
     * @param content the rendered template
     * @throws MojoExecutionException in case of an error
     */
    protected void attachFile(final String content) throws MojoExecutionException {
        final File attachedFile;
        if (outputFile == null) {
            attachedFile = new File(getProject().getBuild().getDirectory(), classifier);
            store(content, attachedFile);
        } else { // already written
            attachedFile = outputFile;
        }
        projectHelper.attachArtifact(getProject(), extension, classifier, attachedFile);
    }

    /**
     * Stores the specified string into that file.
     *
     * @param content the string to write into the file
     */
    private void store(final String content, final File out) throws MojoExecutionException {
        // make sure the parent path exists.
        final Path parent = out.toPath().getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (final IOException e) {
                throw new MojoExecutionException(e);
            }
        }

        final String encoding = Objects.toString(outputEncoding, StandardCharsets.UTF_8.name());
        try (Writer w = Files.newBufferedWriter(out.toPath(), Charset.forName(encoding))) {
            w.write(content);
            getLog().info("Wrote file '" + out + "'.");
        } catch (final IOException ex) {
            throw new MojoExecutionException("Error while writing to file '" + out, ex);
        }
    }

    @Override
    protected ArtifactsFilter getMarkedArtifactFilter() {
        return null;
    }

    public void setExtension(final String extension) {
        this.extension = extension;
    }

    public void setOutputEncoding(final String outputEncoding) {
        this.outputEncoding = outputEncoding;
    }

    public void setOutputFile(final File outputFile) {
        this.outputFile = outputFile;
    }

    public void setClassifier(final String classifier) {
        this.classifier = classifier;
    }

    public void setTemplate(final String template) {
        this.template = template;
    }
}
