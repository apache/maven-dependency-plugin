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
package org.apache.maven.plugins.dependency.resolvers;

import javax.inject.Inject;

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.utils.ResolverUtil;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.transfer.repository.RepositoryManager;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * <p>
 * Goal that collects the project dependencies from the repository. This goal
 * uses "requiresDependencyCollection" to list the groupId:artifactId:version information
 * by downloading the pom files without downloading the actual artifacts such as jar files.
 * </p>
 * <p>
 * This is very useful when full dependency resolution might fail due to projects which haven't been built yet.
 * </p>
 * <p>
 * It is identical to <a href="https://maven.apache.org/plugins/maven-dependency-plugin/resolve-mojo.html">resolve</a>
 * except for using the requiresDependencyCollection annotation attribute instead of requiresDependencyResolution.
 * </p>
 *
 * @author <a href="mailto:epabst@gmail.com">Eric Pabst</a>
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 3.0
 */
@Mojo(
        name = "collect",
        requiresDependencyCollection = ResolutionScope.TEST,
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        threadSafe = true)
public class CollectDependenciesMojo extends ResolveDependenciesMojo {

    @Inject
    // CHECKSTYLE_OFF: ParameterNumber
    protected CollectDependenciesMojo(
            MavenSession session,
            BuildContext buildContext,
            MavenProject project,
            ResolverUtil resolverUtil,
            DependencyResolver dependencyResolver,
            RepositoryManager repositoryManager,
            ProjectBuilder projectBuilder,
            ArtifactHandlerManager artifactHandlerManager) {
        super(
                session,
                buildContext,
                project,
                resolverUtil,
                dependencyResolver,
                repositoryManager,
                projectBuilder,
                artifactHandlerManager);
    }
    // CHECKSTYLE_ON: ParameterNumber

}
