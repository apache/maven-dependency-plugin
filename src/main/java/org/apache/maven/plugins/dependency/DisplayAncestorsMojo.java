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

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

/**
 * Displays all ancestor POMs of the project. This may be useful in a continuous integration system where you want to
 * know all parent poms of the project.
 *
 * @author Mirko Friedenhagen
 * @since 2.9
 */
@Mojo(name = "display-ancestors", threadSafe = true, requiresProject = true, defaultPhase = LifecyclePhase.VALIDATE)
public class DisplayAncestorsMojo extends AbstractMojo {

    private MavenProject project;

    @Inject
    public DisplayAncestorsMojo(MavenProject project) {
        this.project = project;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final List<String> ancestors = collectAncestors();

        if (ancestors.isEmpty()) {
            getLog().info("No Ancestor POMs!");
        } else {
            getLog().info("Ancestor POMs: " + String.join(" <- ", ancestors));
        }
    }

    private ArrayList<String> collectAncestors() {
        final ArrayList<String> ancestors = new ArrayList<>();

        MavenProject currentAncestor = project.getParent();
        while (currentAncestor != null) {
            ancestors.add(currentAncestor.getGroupId() + ":" + currentAncestor.getArtifactId() + ":"
                    + currentAncestor.getVersion());

            currentAncestor = currentAncestor.getParent();
        }

        return ancestors;
    }
}
