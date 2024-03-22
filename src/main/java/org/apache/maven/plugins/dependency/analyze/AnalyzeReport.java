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

import java.util.Locale;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzerException;
import org.codehaus.plexus.i18n.I18N;

/**
 * Analyzes the dependencies of this project and produces a report that summarizes which are: used and declared; used
 * and undeclared; unused and declared.
 *
 * @since 2.0-alpha-5
 */
@Mojo(name = "analyze-report", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
@Execute(phase = LifecyclePhase.TEST_COMPILE)
public class AnalyzeReport extends AbstractMavenReport {
    // fields -----------------------------------------------------------------

    /**
     * The Maven project dependency analyzer to use.
     */
    @Component
    private ProjectDependencyAnalyzer analyzer;

    /**
     * Ignore Runtime/Provided/Test/System scopes for unused dependency analysis
     *
     * @since 2.2
     */
    @Parameter(property = "ignoreNonCompile", defaultValue = "false")
    private boolean ignoreNonCompile;

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
     * Internationalization component
     */
    @Component
    private I18N i18n;

    // Mojo methods -----------------------------------------------------------

    /*
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public void executeReport(Locale locale) throws MavenReportException {
        // Step 1: Analyze the project
        ProjectDependencyAnalysis analysis;
        try {
            analysis = analyzer.analyze(project);

            if (usedDependencies != null) {
                analysis = analysis.forceDeclaredDependenciesUsage(usedDependencies);
            }
        } catch (ProjectDependencyAnalyzerException exception) {
            throw new MavenReportException("Cannot analyze dependencies", exception);
        }

        // remove everything that's not in the compile scope
        if (ignoreNonCompile) {
            analysis = analysis.ignoreNonCompile();
        }

        // Step 3: Generate the report
        AnalyzeReportRenderer r = new AnalyzeReportRenderer(getSink(), i18n, locale, analysis);
        r.render();
    }

    // MavenReport methods ----------------------------------------------------

    @Override
    public boolean canGenerateReport() {
        if (skip) {
            return false;
        }

        // Step 0: Checking pom availability
        if ("pom".equals(project.getPackaging())) {
            return false;
        }

        return true;
    }

    /** {@inheritDoc} */
    @Override
    public String getOutputName() {
        return "dependency-analysis";
    }

    /** {@inheritDoc} */
    public String getName(Locale locale) {
        return getI18nString(locale, "name");
    }

    /** {@inheritDoc} */
    public String getDescription(Locale locale) {
        return getI18nString(locale, "description");
    }

    // protected methods ------------------------------------------------------

    /**
     * @param locale The locale
     * @param key The key to search for
     * @return The text appropriate for the locale.
     */
    protected String getI18nString(Locale locale, String key) {
        return i18n.getString("analyze-report", locale, "report.analyze." + key);
    }
}
