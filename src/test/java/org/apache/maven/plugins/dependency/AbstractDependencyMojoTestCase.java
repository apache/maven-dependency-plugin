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
import java.util.UUID;

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
import org.junit.Before;
import org.sonatype.plexus.build.incremental.DefaultBuildContext;

public abstract class AbstractDependencyMojoTestCase extends AbstractMojoTestCase {

    protected File testDir;
    protected DependencyArtifactStubFactory stubFactory;

    @Before
    public void setUp() throws Exception {
        // Initialize mojo lookups as required by AbstractMojoTestCase
        super.setUp();

        // Create a unique temporary test directory to avoid parallel test conflicts
        String uniqueDirName = "test-dependency" + UUID.randomUUID();
        testDir = Files.createTempDirectory(uniqueDirName).toFile();
        testDir.deleteOnExit();

        // Initialize stub factory with default settings
        stubFactory = new DependencyArtifactStubFactory(testDir, true, true);
    }

    /**
     * Allows subclasses to customize the setup with specific test directory name and stub factory settings.
     *
     * @param testDirectoryName the name for the temporary test directory.
     * @param createFiles whether to create files in the stub factory.
     * @param flattenedPath whether to use flattened paths in the stub factory.
     * @throws Exception If setup fails.
     */
    protected final void customizeSetUp(String testDirectoryName, boolean createFiles, boolean flattenedPath)
            throws Exception {
        // Create new test directory with specified name
        testDir = Files.createTempDirectory(testDirectoryName).toFile();
        testDir.deleteOnExit();

        // Reinitialize stub factory with custom settings
        stubFactory = new DependencyArtifactStubFactory(testDir, createFiles, flattenedPath);
    }

    @Override
    protected void tearDown() throws Exception {
        if (testDir != null) {
            FileUtils.deleteDirectory(testDir);
            testDir = null;
        }
        super.tearDown();
    }

    protected void copyArtifactFile(Artifact sourceArtifact, File destFile) throws MojoExecutionException, IOException {
        new CopyUtil(new DefaultBuildContext()).copyArtifactFile(sourceArtifact, destFile);
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
