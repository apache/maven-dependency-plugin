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
package org.apache.maven.plugins.dependency.utils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import java.util.Collection;

import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

/**
 * Helper class for using Resolver API.
 */
@Named
@Singleton
public class ResolverUtil {

    private final RepositorySystem repositorySystem;

    private final Provider<MavenSession> mavenSessionProvider;

    @Inject
    private ResolverUtil(RepositorySystem repositorySystem, Provider<MavenSession> mavenSessionProvider) {
        this.repositorySystem = repositorySystem;
        this.mavenSessionProvider = mavenSessionProvider;
    }

    /**
     * Collects the transitive dependencies.
     *
     * @param root a root dependency for collections
     * @return a resolved dependencies collections
     */
    public Collection<Dependency> collectDependencies(Dependency root) throws DependencyCollectionException {

        MavenSession session = mavenSessionProvider.get();

        CollectRequest request =
                new CollectRequest(root, session.getCurrentProject().getRemoteProjectRepositories());
        CollectResult result = repositorySystem.collectDependencies(session.getRepositorySession(), request);

        PreorderNodeListGenerator nodeListGenerator = new PreorderNodeListGenerator();
        result.getRoot().accept(nodeListGenerator);
        return nodeListGenerator.getDependencies(true);
    }
}
