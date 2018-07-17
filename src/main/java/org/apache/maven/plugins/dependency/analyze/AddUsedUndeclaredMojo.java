package org.apache.maven.plugins.dependency.analyze;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.analyze.internal.PomEditor;
import org.apache.maven.plugins.dependency.analyze.internal.PropertiesFactory;
import org.apache.maven.plugins.dependency.analyze.internal.Verifier;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

/**
 * Attempts to fix the warnings identified by analysis.
 *
 * @author <a href="mailto:alex@alexecollins.com">Alex Collins</a>
 * @since 3.1.2
 */
@Mojo(name = "add-used-undeclared", requiresDependencyResolution = ResolutionScope.TEST)
public class AddUsedUndeclaredMojo extends AbstractMojo implements Contextualizable {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    private Context context;

    @Parameter(defaultValue = "${basedir}", readonly = true)
    private File baseDir;

    @Parameter(property = "analyzer", defaultValue = "default")
    private String analyzer;

    @Parameter(property = "indent", defaultValue = "    ")
    private String indent;

    @Parameter(property = "dependencyManaged", defaultValue = "false")
    private boolean dependencyManaged;

    @Override
    public void execute() throws MojoExecutionException {

        String packaging = project.getPackaging();
        if (packaging.equals("pom")) {
            getLog().info("Skipping because packaging '" + packaging + "' is pom.");
            return;
        }

        try {
            Set<Artifact> usedUndeclaredArtifacts = getAnalyzer().analyze(project).getUsedUndeclaredArtifacts();

            if (usedUndeclaredArtifacts.isEmpty()) {
                getLog().info("Skipping because nothing to do");
                return;
            }

            getLog().info("Adding " + usedUndeclaredArtifacts.size() + " used undeclared artifact(s).");

            PomEditor editor = new PomEditor(PropertiesFactory.getProperties(project), baseDir, indent, Verifier.NOOP, dependencyManaged);
            editor.start();
            for (Artifact artifact : new TreeSet<>(usedUndeclaredArtifacts)) {
                getLog().info("+ " + artifact);
                editor.addDependency(artifact);
            }
            editor.end();
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private ProjectDependencyAnalyzer getAnalyzer() throws ComponentLookupException, ContextException {
        return (ProjectDependencyAnalyzer) ((PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY)).lookup(ProjectDependencyAnalyzer.ROLE, analyzer);
    }

    @Override
    public void contextualize(Context context) {
        this.context = context;
    }

}
