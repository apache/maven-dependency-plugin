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
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.analyze.internal.ArtifactFilter;
import org.apache.maven.plugins.dependency.analyze.internal.PomEditor;
import org.apache.maven.plugins.dependency.analyze.internal.PropertiesFactory;
import org.apache.maven.plugins.dependency.analyze.internal.Verifier;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzerException;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;

import java.io.File;
import java.util.Set;

@Execute(phase = LifecyclePhase.TEST_COMPILE)
abstract class AbstractFixDependenciesMojo extends AbstractMojo implements Contextualizable {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;


    @Parameter(defaultValue = "${basedir}", readonly = true)
    File baseDir;
    /**
     * The indent used in your pom.xml.
     */
    @Parameter(property = "indent", defaultValue = "    ")
    String indent;
    /**
     * Large projects typically use a {@code dependencyManagement} section in their pom.xml to managed dependencies.
     * When this is the case, you can set this to {@code true} and no {@code version} tags will be added to you pom.xml.
     */
    @Parameter(property = "dependencyManaged", defaultValue = "false")
    boolean dependencyManaged;
    @Parameter(property = "analyzer", defaultValue = "default")
    private String analyzer;
    /**
     * The list of dependencies in the form of groupId:artifactId which should be removed.
     * Lower precedence than exclusion.
     */
    @Parameter(property = "include")
    private String include;

    /**
     * The list of dependencies in the form of groupId:artifactId which should NOT be removed.
     * Higher precedence than inclusion.
     */
    @Parameter(property = "exclude")
    private String exclude;

    private Context context;


    Set<Artifact> filter(Set<Artifact> artifacts) {
        return new ArtifactFilter(getLog(), include, exclude).filter(artifacts);
    }

    PomEditor getEditor(Verifier verifier) {
        return new PomEditor(PropertiesFactory.getProperties(project), baseDir, indent, verifier, dependencyManaged);
    }

    private ProjectDependencyAnalyzer getAnalyzer() throws ContextException, ComponentLookupException {
        return (ProjectDependencyAnalyzer) ((PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY))
                .lookup(ProjectDependencyAnalyzer.ROLE, analyzer);
    }


    ProjectDependencyAnalysis getAnalysis() throws ProjectDependencyAnalyzerException, ContextException, ComponentLookupException {
        return getAnalyzer().analyze(project);
    }

    @Override
    public void contextualize(Context context) {
        this.context = context;
    }
}
