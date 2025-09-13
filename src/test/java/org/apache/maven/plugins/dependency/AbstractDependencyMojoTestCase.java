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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.plugins.dependency.utils.CopyUtil;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.sonatype.plexus.build.incremental.DefaultBuildContext;

public abstract class AbstractDependencyMojoTestCase extends AbstractMojoTestCase {

    protected File testDir;

    protected DependencyArtifactStubFactory stubFactory;

    /**
     * Initializes the test environment by creating a temporary directory and setting up the stub factory.
     * Subclasses must call super.setUp() in their own setUp method to ensure proper initialization.
     * To customize the test directory name, file creation, or path structure, override getTestDirectoryName(),
     * shouldCreateFiles(), and shouldUseFlattenedPath() respectively.
     *
     * @throws Exception if setup fails
     */
    protected void setUp() throws Exception {
        // Required for mojo lookups to work
        super.setUp();

        testDir = Files.createTempDirectory(getTestDirectoryName()).toFile();
        testDir.deleteOnExit();

        stubFactory = new DependencyArtifactStubFactory(testDir, shouldCreateFiles(), shouldUseFlattenedPath());
    }

    /**
     * Returns the name of the temporary test directory. Subclasses can override to customize.
     *
     * @return the test directory name
     */
    protected String getTestDirectoryName() {
        return "test-dir";
    }

    /**
     * Determines whether files should be created by the stub factory. Subclasses can override to customize.
     *
     * @return true if files should be created, false otherwise
     */
    protected boolean shouldCreateFiles() {
        return true;
    }

    /**
     * Determines whether the stub factory should use flattened paths. Subclasses can override to customize.
     *
     * @return true if flattened paths should be used, false otherwise
     */
    protected boolean shouldUseFlattenedPath() {
        return true;
    }

    /**
     * Cleans up the test environment by deleting the temporary directory.
     * Subclasses must call super.tearDown() in their own tearDown method to ensure proper cleanup.
     *
     * @throws Exception if cleanup fails
     */
    @Override
    protected void tearDown() throws Exception {
        if (testDir != null) {
            FileUtils.deleteDirectory(testDir);
            assertFalse("Test directory should not exist after cleanup", testDir.exists());
        }
        super.tearDown();
    }

    protected void copyArtifactFile(Artifact sourceArtifact, File destFile) throws MojoExecutionException, IOException {
        new CopyUtil(new DefaultBuildContext()).copyArtifactFile(sourceArtifact, destFile, true);
    }

    protected void installLocalRepository(LegacySupport legacySupport) throws ComponentLookupException {
        DefaultRepositorySystemSession repoSession =
                (DefaultRepositorySystemSession) legacySupport.getRepositorySession();
        RepositorySystem system = lookup(RepositorySystem.class);
        String directory = stubFactory.getWorkingDir().toString();
        LocalRepository localRepository = new LocalRepository(directory);
        LocalRepositoryManager manager = system.newLocalRepositoryManager(repoSession, localRepository);
        repoSession.setLocalRepositoryManager(manager);
    }

    protected void installLocalRepository(RepositorySystemSession repoSession) throws ComponentLookupException {
        RepositorySystem system = lookup(RepositorySystem.class);
        String directory = stubFactory.getWorkingDir().toString();
        LocalRepository localRepository = new LocalRepository(directory);
        LocalRepositoryManager manager = system.newLocalRepositoryManager(repoSession, localRepository);
        ((DefaultRepositorySystemSession) repoSession).setLocalRepositoryManager(manager);
    }
}
