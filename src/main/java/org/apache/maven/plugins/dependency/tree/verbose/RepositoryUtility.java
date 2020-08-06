package org.apache.maven.plugins.dependency.tree.verbose;

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


import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.eclipse.aether.util.graph.transformer.JavaDependencyContextRefiner;
import org.eclipse.aether.version.Version;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Aether initialization. This uses Maven Resolver 1.4.2 or later. There are many other versions of Aether
 * from Sonatype and the Eclipse Project, but this is the current one.
 */
final class RepositoryUtility
{

    public static final RemoteRepository CENTRAL = new RemoteRepository.Builder( "central", "default",
            "https://repo1.maven.org/maven2/" ).build();

    private RepositoryUtility()
    {
    }

    private static DefaultRepositorySystemSession createDefaultRepositorySystemSession( RepositorySystem system )
    {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepository = new LocalRepository( findLocalRepository() );
        session.setLocalRepositoryManager( system.newLocalRepositoryManager( session, localRepository ) );
        return session;
    }

    /**
     * Opens a new Maven repository session that looks for the local repository in the customary ~/.m2 directory. If not
     * found, it creates an initially empty repository in a temporary location.
     */
    private static DefaultRepositorySystemSession newSession( RepositorySystem system )
    {
        DefaultRepositorySystemSession session = createDefaultRepositorySystemSession( system );
        return session;
    }

    /**
     * Open a new Maven repository session for full dependency graph resolution.
     *
     * @see {@link VerboseDependencyGraphBuilder}
     */
    static DefaultRepositorySystemSession newSessionForFullDependency( RepositorySystem system )
    {
        // This combination of DependencySelector comes from the default specified in
        // `MavenRepositorySystemUtils.newSession`.
        // LinkageChecker needs to include 'provided'-scope and optional dependencies.
        DependencySelector dependencySelector = new AndDependencySelector(
                // ScopeDependencySelector takes exclusions. 'Provided' scope is not here to avoid
                // false positive in LinkageChecker.
                new ScopeDependencySelector(), // removed "test" parameter
                new ExclusionDependencySelector() );

        return newSession( system, dependencySelector );
    }

    private static DefaultRepositorySystemSession newSession( RepositorySystem system,
                                                              DependencySelector dependencySelector )
    {
        DefaultRepositorySystemSession session = createDefaultRepositorySystemSession( system );
        session.setDependencySelector( dependencySelector );

        // By default, Maven's MavenRepositorySystemUtils.newSession() returns a session with
        // ChainedDependencyGraphTransformer(ConflictResolver(...), JavaDependencyContextRefiner()).
        // Because the full dependency graph does not resolve conflicts of versions, this session does
        // not use ConflictResolver.
        session.setDependencyGraphTransformer(
                new ChainedDependencyGraphTransformer( new CycleBreakerGraphTransformer(), // Avoids StackOverflowError
                        new JavaDependencyContextRefiner() ) );

        // No dependency management in the full dependency graph
        session.setDependencyManager( null );

        return session;
    }

    private static String findLocalRepository()
    {
        // TODO is there Maven code for this?
        Path home = Paths.get( System.getProperty( "user.home" ) );
        Path localRepo = home.resolve( ".m2" ).resolve( "repository" );
        if ( Files.isDirectory( localRepo ) )
        {
            return localRepo.toAbsolutePath().toString();
        }
        else
        {
            return makeTemporaryLocalRepository();
        }
    }

    private static String makeTemporaryLocalRepository()
    {
        try
        {
            File temporaryDirectory = Files.createTempDirectory( "m2" ).toFile();
            temporaryDirectory.deleteOnExit();
            return temporaryDirectory.getAbsolutePath();
        }
        catch ( IOException ex )
        {
            return null;
        }
    }

    /**
     * Returns Maven repository specified as {@code mavenRepositoryUrl}, after validating the syntax of the URL.
     *
     * @throws IllegalArgumentException if the URL is malformed for a Maven repository
     */
    static RemoteRepository mavenRepositoryFromUrl( String mavenRepositoryUrl )
    {
        try
        {
            // Because the protocol is not an empty string (checked below), this URI is absolute.
            new URI( mavenRepositoryUrl );
        }
        catch ( URISyntaxException ex )
        {
            throw new IllegalArgumentException( "Invalid URL syntax: " + mavenRepositoryUrl );
        }

        RemoteRepository repository = new RemoteRepository.Builder( null, "default", mavenRepositoryUrl ).build();

        return repository;
    }

    static VersionRangeResult findVersionRange( RepositorySystem repositorySystem,
                                                        RepositorySystemSession session,
                                                        String groupId, String artifactId )
            throws VersionRangeResolutionException
    {

        Artifact artifactWithVersionRange = new DefaultArtifact( groupId, artifactId, null, "(0,]" );
        VersionRangeRequest request = new VersionRangeRequest( artifactWithVersionRange,
                Arrays.asList( RepositoryUtility.CENTRAL ), null );


        return repositorySystem.resolveVersionRange( session, request );

    }

    /**
     * Returns the highest version for {@code groupId:artifactId} in {@code repositorySystem}.
     */
    private static Version findHighestVersion( RepositorySystem repositorySystem, RepositorySystemSession session,
                                       String groupId, String artifactId ) throws VersionRangeResolutionException
    {
        return findVersionRange( repositorySystem, session, groupId, artifactId ).getHighestVersion();
    }

    /**
     * Returns the latest Maven coordinates for {@code groupId:artifactId} in {@code repositorySystem}.
     */
    private static String findLatestCoordinates( RepositorySystem repositorySystem, String groupId, String artifactId )
            throws VersionRangeResolutionException
    {
        RepositorySystemSession session = RepositoryUtility.newSession( repositorySystem );
        String highestVersion = findHighestVersion( repositorySystem, session, groupId, artifactId ).toString();
        return String.format( "%s:%s:%s", groupId, artifactId, highestVersion );
    }

}