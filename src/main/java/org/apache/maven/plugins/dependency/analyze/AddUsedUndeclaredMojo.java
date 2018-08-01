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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.analyze.internal.PomEditor;
import org.apache.maven.plugins.dependency.analyze.internal.Verifier;

import java.util.Set;
import java.util.TreeSet;

/**
 * Attempts to add used dependencies.
 * <p>
 * You must have compiled your main and test sources before you run this using {@code mvn clean test-compile} or
 * similar.
 *
 * @author <a href="mailto:alex@alexecollins.com">Alex Collins</a>
 * @since 3.1.2
 */
@Mojo(name = "add-used-undeclared", requiresDependencyResolution = ResolutionScope.TEST)
public class AddUsedUndeclaredMojo extends AbstractFixDependenciesMojo {


    @Override
    public void execute() throws MojoExecutionException {

        String packaging = project.getPackaging();
        if (packaging.equals("pom")) {
            getLog().info("Skipping because packaging '" + packaging + "' is pom.");
            return;
        }

        try {
            Set<Artifact> usedUndeclaredArtifacts = filter(getAnalysis().getUsedUndeclaredArtifacts());

            if (usedUndeclaredArtifacts.isEmpty()) {
                getLog().info("Skipping because nothing to do");
                return;
            }

            getLog().info("Adding " + usedUndeclaredArtifacts.size() + " used undeclared artifact(s).");

            PomEditor editor = getEditor(Verifier.NOOP);
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


}
