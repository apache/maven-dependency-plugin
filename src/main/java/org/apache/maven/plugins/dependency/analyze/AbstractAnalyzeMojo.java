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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.StrictPatternExcludesArtifactFilter;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzerException;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Type;

import static java.util.Collections.list;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

/**
 * Analyzes the dependencies of this project and determines which are: used and declared; used and undeclared; unused
 * and declared; compile scoped but only used in tests.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @since 2.0-alpha-5
 */
public abstract class AbstractAnalyzeMojo extends AbstractMojo {
    // fields -----------------------------------------------------------------

    /**
     * Specify the project dependency analyzer to use (plexus component role-hint). By default,
     * <a href="/shared/maven-dependency-analyzer/">maven-dependency-analyzer</a> is used. To use this, you must declare
     * a dependency for this plugin that contains the code for the analyzer. The analyzer must have a declared Plexus
     * role name, and you specify the role name here.
     *
     * @since 2.2
     */
    @Parameter(property = "analyzer", defaultValue = "default")
    private String analyzer;

    /**
     * Whether to fail the build if a dependency warning is found.
     */
    @Parameter(property = "failOnWarning", defaultValue = "false")
    private boolean failOnWarning;

    /**
     * Output used dependencies.
     */
    @Parameter(property = "verbose", defaultValue = "false")
    private boolean verbose;

    /**
     * Ignore runtime/provided/test/system scopes for unused dependency analysis.
     * <p>
     * <code><b>Non-test scoped</b></code> list will be not affected.
     */
    @Parameter(property = "ignoreNonCompile", defaultValue = "false")
    private boolean ignoreNonCompile;

    /**
     * Ignore runtime scope for unused dependency analysis.
     *
     * @since 3.2.0
     */
    @Parameter(property = "ignoreUnusedRuntime", defaultValue = "false")
    private boolean ignoreUnusedRuntime;

    /**
     * Ignore all dependencies that are used only in test but not test-scoped. Setting
     * this flag has the same effect as adding all dependencies that have been flagged with
     * the <i>Non-test scoped test only dependencies found</i> warning to the
     * <code>&lt;ignoredNonTestScopedDependencies&gt;</code> configuration.
     *
     * @since 3.3.1-SNAPSHOT
     */
    @Parameter(property = "ignoreAllNonTestScoped", defaultValue = "false")
    private boolean ignoreAllNonTestScoped;

    /**
     * Output the XML for the missing dependencies (used but not declared).
     *
     * @since 2.0-alpha-5
     */
    @Parameter(property = "outputXML", defaultValue = "false")
    private boolean outputXML;

    /**
     * Output scriptable values for the missing dependencies (used but not declared).
     *
     * @since 2.0-alpha-5
     */
    @Parameter(property = "scriptableOutput", defaultValue = "false")
    private boolean scriptableOutput;

    /**
     * Flag to use for scriptable output.
     *
     * @since 2.0-alpha-5
     */
    @Parameter(property = "scriptableFlag", defaultValue = "$$$%%%")
    private String scriptableFlag;

    /**
     * Flag to use for scriptable output.
     *
     * @since 2.0-alpha-5
     */
    @Parameter(defaultValue = "${basedir}", readonly = true)
    private File baseDir;

    /**
     * Target folder.
     *
     * @since 2.0-alpha-5
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File outputDirectory;

    /**
     * Force dependencies as used, to override incomplete result caused by bytecode-level analysis. Dependency format is
     * <code>groupId:artifactId</code>.
     *
     * @since 2.6
     */
    @Parameter
    private String[] usedDependencies;

    /**
     * Skip plugin execution completely.
     *
     * @since 2.7
     */
    @Parameter(property = "mdep.analyze.skip", defaultValue = "false")
    private boolean skip;

    /**
     * List of dependencies that will be ignored. Any dependency on this list will be excluded from the "declared but
     * unused", the "used but undeclared", and the "non-test scoped" list. The filter syntax is:
     *
     * <pre>
     * [groupId]:[artifactId]:[type]:[version]
     * </pre>
     *
     * where each pattern segment is optional and supports full and partial <code>*</code> wildcards. An empty pattern
     * segment is treated as an implicit wildcard. *
     * <p>
     * For example, <code>org.apache.*</code> will match all artifacts whose group id starts with
     * <code>org.apache.</code>, and <code>:::*-SNAPSHOT</code> will match all snapshot artifacts.
     * </p>
     *
     * @since 2.10
     */
    @Parameter
    private String[] ignoredDependencies = new String[0];

    /**
     * List of dependencies that will be ignored if they are used but undeclared. The filter syntax is:
     *
     * <pre>
     * [groupId]:[artifactId]:[type]:[version]
     * </pre>
     *
     * where each pattern segment is optional and supports full and partial <code>*</code> wildcards. An empty pattern
     * segment is treated as an implicit wildcard. *
     * <p>
     * For example, <code>org.apache.*</code> will match all artifacts whose group id starts with
     * <code>org.apache.</code>, and <code>:::*-SNAPSHOT</code> will match all snapshot artifacts.
     * </p>
     *
     * @since 2.10
     */
    @Parameter
    private String[] ignoredUsedUndeclaredDependencies = new String[0];

    /**
     * List of dependencies that are ignored if they are declared but unused. The filter syntax is:
     *
     * <pre>
     * [groupId]:[artifactId]:[type]:[version]
     * </pre>
     *
     * where each pattern segment is optional and supports full and partial <code>*</code> wildcards. An empty pattern
     * segment is treated as an implicit wildcard. *
     * <p>
     * For example, <code>org.apache.*</code> matches all artifacts whose group id starts with
     * <code>org.apache.</code>, and <code>:::*-SNAPSHOT</code> matches all snapshot artifacts.
     * </p>
     *
     * <p>Certain dependencies that are known to be used and loaded by reflection
     * are always ignored. This includes {@code org.slf4j:slf4j-simple::}
     * and {@code org.glassfish:javax.json::}.</p>
     *
     * @since 2.10
     */
    @Parameter
    private String[] ignoredUnusedDeclaredDependencies = new String[0];

    /**
     * List of dependencies that are ignored if they are in not test scope but are only used in test classes.
     * The filter syntax is:
     *
     * <pre>
     * [groupId]:[artifactId]:[type]:[version]
     * </pre>
     *
     * where each pattern segment is optional and supports full and partial <code>*</code> wildcards. An empty pattern
     * segment is treated as an implicit wildcard. *
     * <p>
     * For example, <code>org.apache.*</code> matched all artifacts whose group id starts with
     * <code>org.apache.</code>, and <code>:::*-SNAPSHOT</code> will match all snapshot artifacts.
     * </p>
     *
     * @since 3.3.0
     */
    @Parameter
    private String[] ignoredNonTestScopedDependencies;

    /**
     * List of project packaging that will be ignored.
     * <br/>
     * <b>Default value is<b>: <code>pom, ear</code>
     *
     * @since 3.2.1
     */
    @Parameter(defaultValue = "pom,ear")
    private List<String> ignoredPackagings;

    /**
     * List of class patterns excluded from analyze. Java regular expression pattern is applied to full class name.
     *
     * @since 3.7.0
     */
    @Parameter(property = "mdep.analyze.excludedClasses")
    private Set<String> excludedClasses;

    /**
     * The plexusContainer to look up the {@link ProjectDependencyAnalyzer} implementation depending on the mojo
     * configuration.
     */
    private final PlexusContainer plexusContainer;

    /**
     * The Maven project to analyze.
     */
    private final MavenProject project;

    protected AbstractAnalyzeMojo(PlexusContainer plexusContainer, MavenProject project) {
        this.plexusContainer = plexusContainer;
        this.project = project;
    }

    // Mojo methods -----------------------------------------------------------

    /*
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (isSkip()) {
            getLog().info("Skipping plugin execution");
            return;
        }

        if (ignoredPackagings.contains(project.getPackaging())) {
            getLog().info("Skipping " + project.getPackaging() + " project");
            return;
        }

        if (outputDirectory == null || !outputDirectory.exists()) {
            getLog().info("Skipping project with no build directory");
            return;
        }

        boolean warning = checkDependencies();

        if (warning && failOnWarning) {
            throw new MojoExecutionException("Dependency problems found");
        }
    }

    /**
     * @return {@link ProjectDependencyAnalyzer}
     * @throws MojoExecutionException in case of an error
     */
    protected ProjectDependencyAnalyzer createProjectDependencyAnalyzer() throws MojoExecutionException {

        try {
            return plexusContainer.lookup(ProjectDependencyAnalyzer.class, analyzer);
        } catch (ComponentLookupException exception) {
            throw new MojoExecutionException(
                    "Failed to instantiate ProjectDependencyAnalyser" + " / role-hint " + analyzer, exception);
        }
    }

    /**
     * @return {@link #skip}
     */
    protected final boolean isSkip() {
        return skip;
    }

    // private methods --------------------------------------------------------

    private boolean checkDependencies() throws MojoExecutionException {
        ProjectDependencyAnalysis analysis;
        try {
            analysis = createProjectDependencyAnalyzer().analyze(project, excludedClasses);

            if (usedDependencies != null) {
                analysis = analysis.forceDeclaredDependenciesUsage(usedDependencies);
            }
        } catch (ProjectDependencyAnalyzerException exception) {
            throw new MojoExecutionException("Cannot analyze dependencies", exception);
        }

        if (ignoreNonCompile) {
            analysis = analysis.ignoreNonCompile();
        }

        Set<Artifact> usedDeclared = new LinkedHashSet<>(analysis.getUsedDeclaredArtifacts());
        Map<Artifact, Set<String>> usedUndeclaredWithClasses =
                new LinkedHashMap<>(analysis.getUsedUndeclaredArtifactsWithClasses());
        Set<Artifact> unusedDeclared = new LinkedHashSet<>(analysis.getUnusedDeclaredArtifacts());
        Set<Artifact> nonTestScope = new LinkedHashSet<>(analysis.getTestArtifactsWithNonTestScope());

        Set<Artifact> ignoredUsedUndeclared = new LinkedHashSet<>();
        Set<Artifact> ignoredUnusedDeclared = new LinkedHashSet<>();
        Set<Artifact> ignoredNonTestScope = new LinkedHashSet<>();

        if (ignoreUnusedRuntime) {
            filterArtifactsByScope(unusedDeclared, Artifact.SCOPE_RUNTIME);
        }

        ignoredUsedUndeclared.addAll(filterDependencies(usedUndeclaredWithClasses.keySet(), ignoredDependencies));
        ignoredUsedUndeclared.addAll(
                filterDependencies(usedUndeclaredWithClasses.keySet(), ignoredUsedUndeclaredDependencies));

        ignoredUnusedDeclared.addAll(filterDependencies(unusedDeclared, ignoredDependencies));
        ignoredUnusedDeclared.addAll(filterDependencies(unusedDeclared, ignoredUnusedDeclaredDependencies));

        if (ignoreAllNonTestScoped) {
            ignoredNonTestScope.addAll(filterDependencies(nonTestScope, new String[] {"*"}));
        } else {
            ignoredNonTestScope.addAll(filterDependencies(nonTestScope, ignoredDependencies));
            ignoredNonTestScope.addAll(filterDependencies(nonTestScope, ignoredNonTestScopedDependencies));
        }

        boolean reported = false;
        boolean warning = false;

        if (verbose && !usedDeclared.isEmpty()) {
            getLog().info("Used declared dependencies found:");

            logArtifacts(analysis.getUsedDeclaredArtifacts(), false);
            reported = true;
        }

        if (!usedUndeclaredWithClasses.isEmpty()) {
            logDependencyWarning("Used undeclared dependencies found:");

            if (verbose) {
                logArtifacts(usedUndeclaredWithClasses);
            } else {
                logArtifacts(usedUndeclaredWithClasses.keySet(), true);
            }
            reported = true;
            warning = true;
        }

        if (!unusedDeclared.isEmpty()) {
            final Set<String> declaredSpi = scanForSpiUsage(usedDeclared, usedUndeclaredWithClasses);
            cleanupUnused(declaredSpi, unusedDeclared);
            if (!unusedDeclared.isEmpty()) {
                logDependencyWarning("Unused declared dependencies found:");

                logArtifacts(unusedDeclared, true);
                reported = true;
                warning = true;
            }
        }

        if (!nonTestScope.isEmpty()) {
            logDependencyWarning("Non-test scoped test only dependencies found:");

            logArtifacts(nonTestScope, true);
            reported = true;
            warning = true;
        }

        if (verbose && !ignoredUsedUndeclared.isEmpty()) {
            getLog().info("Ignored used undeclared dependencies:");

            logArtifacts(ignoredUsedUndeclared, false);
            reported = true;
        }

        if (verbose && !ignoredUnusedDeclared.isEmpty()) {
            getLog().info("Ignored unused declared dependencies:");

            logArtifacts(ignoredUnusedDeclared, false);
            reported = true;
        }

        if (verbose && !ignoredNonTestScope.isEmpty()) {
            getLog().info("Ignored non-test scoped test only dependencies:");

            logArtifacts(ignoredNonTestScope, false);
            reported = true;
        }

        if (outputXML) {
            writeDependencyXML(usedUndeclaredWithClasses.keySet());
        }

        if (scriptableOutput) {
            writeScriptableOutput(usedUndeclaredWithClasses.keySet());
        }

        if (!reported) {
            getLog().info("No dependency problems found");
        }

        return warning;
    }

    // todo: enhance analyzer (dependency) to do it since it already visits classes
    //       will save some time
    private Set<String> scanForSpiUsage(
            final Set<Artifact> usedDeclared, final Map<Artifact, Set<String>> usedUndeclaredWithClasses) {
        return Stream.concat(
                        usedDeclared.stream().flatMap(this::findUsedSpi),
                        usedUndeclaredWithClasses.keySet().stream().flatMap(this::findUsedSpi))
                .collect(toSet());
    }

    private Stream<String> findUsedSpi(final Artifact artifact) {
        try (JarFile jar = new JarFile(artifact.getFile())) {
            return list(jar.entries()).stream()
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .flatMap(entry -> {
                        final ClassReader classReader;
                        try {
                            classReader = new ClassReader(jar.getInputStream(entry));
                        } catch (IOException e) {
                            return Stream.empty();
                        }
                        final Set<String> spi = new HashSet<>();
                        classReader.accept(
                                new ClassVisitor(ASM9) {
                                    @Override
                                    public MethodVisitor visitMethod(
                                            final int access,
                                            final String name,
                                            final String descriptor,
                                            final String signature,
                                            final String[] exceptions) {
                                        return new MethodVisitor(ASM9) {
                                            private Type lastType = null;

                                            @Override
                                            public void visitLdcInsn(final Object value) {
                                                if (value instanceof Type) {
                                                    lastType = (Type) value;
                                                }
                                            }

                                            @Override
                                            public void visitMethodInsn(
                                                    final int opcode,
                                                    final String owner,
                                                    final String name,
                                                    final String descriptor,
                                                    final boolean isInterface) {
                                                if (opcode == INVOKESTATIC
                                                        && Objects.equals(owner, "java/util/ServiceLoader")
                                                        && Objects.equals(name, "load")) {
                                                    spi.add(lastType.getClassName());
                                                }
                                                lastType = null;
                                            }
                                        };
                                    }
                                },
                                0);
                        return spi.stream();
                    })
                    .collect(toList()) // materialize before closing the jar
                    .stream();
        } catch (final IOException ioe) {
            return Stream.empty();
        }
    }

    // here we try to detect "well-known" indirect patterns to remove false warnings
    private void cleanupUnused(final Set<String> spi, final Set<Artifact> unusedDeclared) {
        unusedDeclared.removeIf(this::isSlf4jBinding);
        unusedDeclared.removeIf(it -> hasUsedSPIImpl(spi, it));
    }

    // mainly for v1.x line but doesn't hurt much for v2
    // TODO: enhance to ensure there is a single binding else just log a warning for all
    //       and maybe even handle version?
    private boolean isSlf4jBinding(final Artifact artifact) {
        try (JarFile file = new JarFile(artifact.getFile())) {
            return file.getEntry("org/slf4j/impl/StaticLoggerBinder.class") != null;
        } catch (final IOException e) {
            return false;
        }
    }

    private boolean hasUsedSPIImpl(final Set<String> usedSpi, final Artifact artifact) {
        final Set<String> spi;
        try (JarFile file = new JarFile(artifact.getFile())) {
            spi = list(file.entries()).stream()
                    .filter(it -> it.getName().startsWith("META-INF/services/") && !it.isDirectory())
                    .map(it -> it.getName().substring("META-INF/services/".length()))
                    .collect(toSet());

            // java >= 9
            final JarEntry moduleEntry = file.getJarEntry("module-info.class");
            if (moduleEntry != null) {
                try (InputStream in = file.getInputStream(moduleEntry)) {
                    final ClassReader cr = new ClassReader(in);
                    cr.accept(
                            new ClassVisitor(ASM9) {
                                @Override
                                public ModuleVisitor visitModule(String name, int access, String version) {
                                    return new ModuleVisitor(ASM9) {
                                        @Override
                                        public void visitProvide(final String service, final String[] providers) {
                                            spi.add(service.replace('/', '.'));
                                        }
                                    };
                                }
                            },
                            0);
                }
            }
        } catch (final IOException e) {
            return false;
        }
        return usedSpi.stream().anyMatch(spi::contains);
    }

    private void filterArtifactsByScope(Set<Artifact> artifacts, String scope) {
        artifacts.removeIf(artifact -> artifact.getScope().equals(scope));
    }

    private void logArtifacts(Set<Artifact> artifacts, boolean warn) {
        if (artifacts.isEmpty()) {
            getLog().info("   None");
        } else {
            for (Artifact artifact : artifacts) {
                // called because artifact will set the version to -SNAPSHOT only if I do this. MNG-2961
                artifact.isSnapshot();

                if (warn) {
                    logDependencyWarning("   " + artifact);
                } else {
                    getLog().info("   " + artifact);
                }
            }
        }
    }

    private void logArtifacts(Map<Artifact, Set<String>> artifacts) {
        if (artifacts.isEmpty()) {
            getLog().info("   None");
        } else {
            for (Map.Entry<Artifact, Set<String>> entry : artifacts.entrySet()) {
                // called because artifact will set the version to -SNAPSHOT only if I do this. MNG-2961
                entry.getKey().isSnapshot();

                logDependencyWarning("   " + entry.getKey());
                for (String clazz : entry.getValue()) {
                    logDependencyWarning("      class " + clazz);
                }
            }
        }
    }

    private void logDependencyWarning(CharSequence content) {
        if (failOnWarning) {
            getLog().error(content);
        } else {
            getLog().warn(content);
        }
    }

    private void writeDependencyXML(Set<Artifact> artifacts) {
        if (!artifacts.isEmpty()) {
            getLog().info("Add the following to your pom to correct the missing dependencies: ");

            StringWriter out = new StringWriter();
            PrettyPrintXMLWriter writer = new PrettyPrintXMLWriter(out);

            for (Artifact artifact : artifacts) {
                writer.startElement("dependency");
                writer.startElement("groupId");
                writer.writeText(artifact.getGroupId());
                writer.endElement();
                writer.startElement("artifactId");
                writer.writeText(artifact.getArtifactId());
                writer.endElement();
                writer.startElement("version");
                writer.writeText(artifact.getBaseVersion());
                String classifier = artifact.getClassifier();
                if (classifier != null && !classifier.trim().isEmpty()) {
                    writer.startElement("classifier");
                    writer.writeText(artifact.getClassifier());
                    writer.endElement();
                }
                writer.endElement();

                if (!Artifact.SCOPE_COMPILE.equals(artifact.getScope())) {
                    writer.startElement("scope");
                    writer.writeText(artifact.getScope());
                    writer.endElement();
                }
                writer.endElement();
            }

            getLog().info(System.lineSeparator() + out.getBuffer());
        }
    }

    private void writeScriptableOutput(Set<Artifact> artifacts) {
        if (!artifacts.isEmpty()) {
            getLog().info("Missing dependencies: ");
            String pomFile = baseDir.getAbsolutePath() + File.separatorChar + "pom.xml";
            StringBuilder buf = new StringBuilder();

            for (Artifact artifact : artifacts) {
                // called because artifact will set the version to -SNAPSHOT only if I do this. MNG-2961
                artifact.isSnapshot();

                buf.append(scriptableFlag)
                        .append(":")
                        .append(pomFile)
                        .append(":")
                        .append(artifact.getDependencyConflictId())
                        .append(":")
                        .append(artifact.getClassifier())
                        .append(":")
                        .append(artifact.getBaseVersion())
                        .append(":")
                        .append(artifact.getScope())
                        .append(System.lineSeparator());
            }
            getLog().info(System.lineSeparator() + buf);
        }
    }

    private List<Artifact> filterDependencies(Set<Artifact> artifacts, String[] excludes) {
        ArtifactFilter filter = new StrictPatternExcludesArtifactFilter(
                excludes == null ? Collections.emptyList() : Arrays.asList(excludes));
        List<Artifact> result = new ArrayList<>();

        for (Iterator<Artifact> it = artifacts.iterator(); it.hasNext(); ) {
            Artifact artifact = it.next();
            if (!filter.include(artifact)) {
                it.remove();
                result.add(artifact);
            }
        }

        return result;
    }
}
