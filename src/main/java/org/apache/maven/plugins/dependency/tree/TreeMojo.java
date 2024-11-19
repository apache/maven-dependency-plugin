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
package org.apache.maven.plugins.dependency.tree;

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.filter.StrictPatternExcludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.StrictPatternIncludesArtifactFilter;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.filter.AncestorOrSelfDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.AndDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.ArtifactDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.DependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.FilteringDependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.SerializingDependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.SerializingDependencyNodeVisitor.GraphTokens;

/**
 * Displays the dependency tree for this project. Multiple formats are supported: text (by default), but also
 * <a href="https://en.wikipedia.org/wiki/DOT_language">DOT</a>,
 * <a href="https://en.wikipedia.org/wiki/GraphML">GraphML</a>,
 * <a href="https://en.wikipedia.org/wiki/Trivial_Graph_Format">TGF</a> and
 * <a href="https://en.wikipedia.org/wiki/JSON">JSON</a>.
 *
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @since 2.0-alpha-5
 */
@Mojo(name = "tree", requiresDependencyCollection = ResolutionScope.TEST, threadSafe = true)
public class TreeMojo extends AbstractMojo {
    // fields -----------------------------------------------------------------

    /**
     * The Maven project.
     */
    private final MavenProject project;

    private final MavenSession session;

    /**
     * The dependency collector builder to use.
     */
    private final DependencyCollectorBuilder dependencyCollectorBuilder;

    /**
     * The dependency graph builder to use.
     */
    private final DependencyGraphBuilder dependencyGraphBuilder;

    @Parameter(property = "outputEncoding", defaultValue = "${project.reporting.outputEncoding}")
    private String outputEncoding;

    /**
     * If specified, this parameter will cause the dependency tree to be written to the path specified, instead of
     * writing to the console.
     *
     * @since 2.0-alpha-5
     */
    @Parameter(property = "outputFile")
    private File outputFile;

    /**
     * If specified, this parameter will cause the dependency tree to be written using the specified format. Currently
     * supported formats are: <code>text</code> (default), <code>dot</code>, <code>graphml</code>, <code>tgf</code>
     * and <code>json</code>.
     * These additional formats can be plotted to image files.
     *
     * @since 2.2
     */
    @Parameter(property = "outputType", defaultValue = "text")
    private String outputType;

    /**
     * The scope to filter by when resolving the dependency tree, or <code>null</code> to include dependencies from all
     * scopes.
     *
     * @since 2.0-alpha-5
     */
    @Parameter(property = "scope")
    private String scope;

    /**
     * Whether to include omitted nodes in the serialized dependency tree.
     *
     * @since 2.0-alpha-6
     */
    @Parameter(property = "verbose", defaultValue = "false")
    private boolean verbose;

    /**
     * The token set name to use when outputting the dependency tree. Possible values are <code>whitespace</code>,
     * <code>standard</code> or <code>extended</code>, which use whitespace, standard (ie ASCII) or extended character
     * sets respectively.
     *
     * @since 2.0-alpha-6
     */
    @Parameter(property = "tokens", defaultValue = "standard")
    private String tokens;

    /**
     * A comma-separated list of artifacts to filter the serialized dependency tree by, or <code>null</code> not to
     * filter the dependency tree. The filter syntax is:
     *
     * <pre>
     * [groupId]:[artifactId]:[type]:[version]
     * </pre>
     *
     * where each pattern segment is optional and supports full and partial <code>*</code> wildcards. An empty pattern
     * segment is treated as an implicit wildcard.
     * <p>
     * For example, <code>org.apache.*</code> will match all artifacts whose group id starts with
     * <code>org.apache.</code>, and <code>:::*-SNAPSHOT</code> will match all snapshot artifacts.
     * </p>
     *
     * @see StrictPatternIncludesArtifactFilter
     * @since 2.0-alpha-6
     */
    @Parameter(property = "includes")
    private List<String> includes;

    /**
     * A comma-separated list of artifacts to filter from the serialized dependency tree, or <code>null</code> not to
     * filter any artifacts from the dependency tree. The filter syntax is:
     *
     * <pre>
     * [groupId]:[artifactId]:[type]:[version]
     * </pre>
     *
     * where each pattern segment is optional and supports full and partial <code>*</code> wildcards. An empty pattern
     * segment is treated as an implicit wildcard.
     * <p>
     * For example, <code>org.apache.*</code> will match all artifacts whose group id starts with
     * <code>org.apache.</code>, and <code>:::*-SNAPSHOT</code> will match all snapshot artifacts.
     * </p>
     *
     * @see StrictPatternExcludesArtifactFilter
     * @since 2.0-alpha-6
     */
    @Parameter(property = "excludes")
    private List<String> excludes;

    /**
     * The computed dependency tree root node of the Maven project.
     */
    private DependencyNode rootNode;

    /**
     * Whether to append outputs into the output file or overwrite it.
     *
     * @since 2.2
     */
    @Parameter(property = "appendOutput", defaultValue = "false")
    private boolean appendOutput;

    /**
     * Skip plugin execution completely.
     *
     * @since 2.7
     */
    @Parameter(property = "skip", defaultValue = "false")
    private boolean skip;

    @Inject
    public TreeMojo(
            MavenProject project,
            MavenSession session,
            DependencyCollectorBuilder dependencyCollectorBuilder,
            DependencyGraphBuilder dependencyGraphBuilder) {
        this.project = project;
        this.session = session;
        this.dependencyCollectorBuilder = dependencyCollectorBuilder;
        this.dependencyGraphBuilder = dependencyGraphBuilder;
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

        try {
            String dependencyTreeString;

            // TODO: note that filter does not get applied due to MSHARED-4
            ArtifactFilter artifactFilter = createResolvingArtifactFilter();

            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

            buildingRequest.setProject(project);

            if (verbose) {
                rootNode = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, artifactFilter);
                dependencyTreeString = serializeDependencyTree(rootNode);
            } else {
                // non-verbose mode use dependency graph component, which gives consistent results with Maven version
                // running
                rootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, artifactFilter);

                dependencyTreeString = serializeDependencyTree(rootNode);
            }

            if (outputFile != null) {
                String encoding = Objects.toString(outputEncoding, "UTF-8");
                DependencyUtil.write(dependencyTreeString, outputFile, this.appendOutput, encoding);

                getLog().info("Wrote dependency tree to: " + outputFile);
            } else {
                DependencyUtil.log(dependencyTreeString, getLog());
            }
        } catch (DependencyGraphBuilderException | DependencyCollectorBuilderException exception) {
            throw new MojoExecutionException("Cannot build project dependency graph", exception);
        } catch (IOException exception) {
            throw new MojoExecutionException("Cannot serialize project dependency graph", exception);
        }
    }

    // public methods ---------------------------------------------------------

    /**
     * Gets the Maven project used by this mojo.
     *
     * @return the Maven project
     */
    public MavenProject getProject() {
        return project;
    }

    /**
     * Gets the computed dependency graph root node for the Maven project.
     *
     * @return the dependency tree root node
     */
    public DependencyNode getDependencyGraph() {
        return rootNode;
    }

    /**
     * @return {@link #skip}
     */
    public boolean isSkip() {
        return skip;
    }

    /**
     * @param skip {@link #skip}
     */
    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    // private methods --------------------------------------------------------

    /**
     * Gets the artifact filter to use when resolving the dependency tree.
     *
     * @return the artifact filter
     */
    private ArtifactFilter createResolvingArtifactFilter() {
        ArtifactFilter filter;

        // filter scope
        if (scope != null) {
            getLog().debug("+ Resolving dependency tree for scope '" + scope + "'");

            filter = new ScopeArtifactFilter(scope);
        } else {
            filter = null;
        }

        return filter;
    }

    /**
     * Serializes the specified dependency tree to a string.
     *
     * @param theRootNode the dependency tree root node to serialize
     * @return the serialized dependency tree
     */
    private String serializeDependencyTree(DependencyNode theRootNode) {
        StringWriter writer = new StringWriter();

        DependencyNodeVisitor visitor = getSerializingDependencyNodeVisitor(writer);

        // TODO: remove the need for this when the serializer can calculate last nodes from visitor calls only
        visitor = new BuildingDependencyNodeVisitor(visitor);

        DependencyNodeFilter filter = createDependencyNodeFilter();

        if (filter != null) {
            CollectingDependencyNodeVisitor collectingVisitor = new CollectingDependencyNodeVisitor();
            DependencyNodeVisitor firstPassVisitor = new FilteringDependencyNodeVisitor(collectingVisitor, filter);
            theRootNode.accept(firstPassVisitor);

            DependencyNodeFilter secondPassFilter =
                    new AncestorOrSelfDependencyNodeFilter(collectingVisitor.getNodes());
            visitor = new FilteringDependencyNodeVisitor(visitor, secondPassFilter);
        }

        theRootNode.accept(visitor);

        return writer.toString();
    }

    /**
     * @param writer {@link Writer}
     * @return {@link DependencyNodeVisitor}
     */
    public DependencyNodeVisitor getSerializingDependencyNodeVisitor(Writer writer) {
        if ("graphml".equals(outputType)) {
            return new GraphmlDependencyNodeVisitor(writer);
        } else if ("tgf".equals(outputType)) {
            return new TGFDependencyNodeVisitor(writer);
        } else if ("dot".equals(outputType)) {
            return new DOTDependencyNodeVisitor(writer);
        } else if ("json".equals(outputType)) {
            return new JsonDependencyNodeVisitor(writer);
        } else {
            return new SerializingDependencyNodeVisitor(writer, toGraphTokens(tokens));
        }
    }

    /**
     * Gets the graph tokens instance for the specified name.
     *
     * @param theTokens the graph tokens name
     * @return the <code>GraphTokens</code> instance
     */
    private GraphTokens toGraphTokens(String theTokens) {
        GraphTokens graphTokens;

        if ("whitespace".equals(theTokens)) {
            getLog().debug("+ Using whitespace tree tokens");

            graphTokens = SerializingDependencyNodeVisitor.WHITESPACE_TOKENS;
        } else if ("extended".equals(theTokens)) {
            getLog().debug("+ Using extended tree tokens");

            graphTokens = SerializingDependencyNodeVisitor.EXTENDED_TOKENS;
        } else {
            graphTokens = SerializingDependencyNodeVisitor.STANDARD_TOKENS;
        }

        return graphTokens;
    }

    /**
     * Gets the dependency node filter to use when serializing the dependency graph.
     *
     * @return the dependency node filter, or <code>null</code> if none required
     */
    private DependencyNodeFilter createDependencyNodeFilter() {
        List<DependencyNodeFilter> filters = new ArrayList<>();

        // filter includes
        if (includes != null && !includes.isEmpty()) {

            getLog().debug("+ Filtering dependency tree by artifact include patterns: " + includes);

            ArtifactFilter artifactFilter = new StrictPatternIncludesArtifactFilter(includes);
            filters.add(new ArtifactDependencyNodeFilter(artifactFilter));
        }

        // filter excludes
        if (excludes != null && !excludes.isEmpty()) {

            getLog().debug("+ Filtering dependency tree by artifact exclude patterns: " + excludes);

            ArtifactFilter artifactFilter = new StrictPatternExcludesArtifactFilter(excludes);
            filters.add(new ArtifactDependencyNodeFilter(artifactFilter));
        }

        return filters.isEmpty() ? null : new AndDependencyNodeFilter(filters);
    }
}
