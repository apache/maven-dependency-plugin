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

import javax.inject.Inject;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.codehaus.plexus.i18n.I18N;

/**
 * Analyzes the dependencies of this project and produces the same report as the <code>dependency:analyze-report</code>
 * goal. Unlike that goal, it does not fork the <code>test-compile</code> phase, so it assumes that the project was
 * already compiled, for example by an earlier phase of the same build.
 *
 * @see AnalyzeReport
 * @since 3.12.0
 */
@Mojo(name = "analyze-report-only", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
@Execute(phase = LifecyclePhase.NONE)
public class AnalyzeReportOnly extends AnalyzeReport {
    // subclassed to provide annotations, without the forked test-compile of analyze-report

    @Inject
    public AnalyzeReportOnly(ProjectDependencyAnalyzer analyzer, I18N i18n) {
        super(analyzer, i18n);
    }
}
