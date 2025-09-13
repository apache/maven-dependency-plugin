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

import java.util.Collection;
import java.util.Locale;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.reporting.AbstractMavenReportRenderer;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.codehaus.plexus.i18n.I18N;

/**
 * This is the view part of the analyze-report mojo. It generates the HTML report for the project website. The HTML
 * output is same as the CLI output.
 */
public class AnalyzeReportRenderer extends AbstractMavenReportRenderer {
    private final I18N i18n;

    private final Locale locale;

    private final ProjectDependencyAnalysis analysis;

    public AnalyzeReportRenderer(Sink sink, I18N i18n, Locale locale, ProjectDependencyAnalysis analysis) {
        super(sink);
        this.i18n = i18n;
        this.locale = locale;
        this.analysis = analysis;
    }

    @Override
    public String getTitle() {
        return getI18nString("title");
    }

    /**
     * @param key the key
     * @return the translated string
     */
    private String getI18nString(String key) {
        return i18n.getString("analyze-report", locale, "report.analyze." + key);
    }

    protected void renderBody() {
        startSection(getTitle());

        boolean reported = false;

        // Generate Used Declared dependencies:
        if (!analysis.getUsedDeclaredArtifacts().isEmpty()) {
            startSection(getI18nString("UsedDeclaredDependencies"));
            renderDependenciesTable(sink, analysis.getUsedDeclaredArtifacts());
            endSection();
            reported = true;
        }

        // Generate Used Undeclared dependencies:

        if (!analysis.getUsedUndeclaredArtifacts().isEmpty()) {
            startSection(getI18nString("UsedUndeclaredDependencies"));
            renderDependenciesTable(sink, analysis.getUsedUndeclaredArtifacts());
            endSection();
            reported = true;
        }

        // Generate Unused declared dependencies:
        if (!analysis.getUnusedDeclaredArtifacts().isEmpty()) {
            startSection(getI18nString("UnusedDeclaredDependencies"));
            renderDependenciesTable(sink, analysis.getUnusedDeclaredArtifacts());
            endSection();
            reported = true;
        }

        // Generate Non-Test Scoped Test Dependencies:
        if (!analysis.getTestArtifactsWithNonTestScope().isEmpty()) {
            startSection(getI18nString("CompileScopeTestOnlyDependencies"));
            renderDependenciesTable(sink, analysis.getTestArtifactsWithNonTestScope());
            endSection();
            reported = true;
        }

        if (!reported) {
            text(getI18nString("noDependencyProblems"));
        }

        endSection();
    }

    private void renderDependenciesTable(Sink sink, Collection<Artifact> artifacts) {
        startTable();

        tableHeader(new String[] {"GroupId", "ArtifactId", "Version", "Scope", "Classifier", "Type", "Optional"});

        for (Artifact artifact : artifacts) {
            tableRow(new String[] {
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getScope(),
                artifact.getClassifier(),
                artifact.getType(),
                artifact.isOptional() ? "" : "false"
            });
        }

        endTable();
    }
}
