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

import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.maven.plugins.dependency.tree.verbose.RepositoryUtility.mavenRepositoryFromUrl;

/**
 * Builds the VerboseDependencyGraph
 */
public class VerboseDependencyGraphBuilder
{
    private RepositorySystem repositorySystem;

    /**
     * Maven repositories to use when resolving dependencies.
     */
    private final List<RemoteRepository> repositories;

    private static final String PRE_MANAGED_SCOPE = "preManagedScope", PRE_MANAGED_VERSION = "preManagedVersion",
            MANAGED_SCOPE = "managedScope";


    public static final RemoteRepository CENTRAL = new RemoteRepository.Builder( "central", "default",
            "https://repo1.maven.org/maven2/" ).build();

    public VerboseDependencyGraphBuilder()
    {
        this( Collections.singletonList( CENTRAL.getUrl() ) );
    }

    static
    {
        for ( Map.Entry<String, String> entry : OsProperties.detectOsProperties().entrySet() )
        {
            System.setProperty( entry.getKey(), entry.getValue() );
        }
    }

    /**
     * @param mavenRepositoryUrls remote Maven repositories to search for dependencies
     * @throws IllegalArgumentException if a URL is malformed or does not have an allowed scheme
     */
    public VerboseDependencyGraphBuilder( Iterable<String> mavenRepositoryUrls )
    {
        List<RemoteRepository> repositoryList = new ArrayList<RemoteRepository>();
        for ( String mavenRepositoryUrl : mavenRepositoryUrls )
        {
            RemoteRepository repository = mavenRepositoryFromUrl( mavenRepositoryUrl );
            repositoryList.add( repository );
        }
        this.repositories = repositoryList;
    }

    public VerboseDependencyGraphBuilder(  List<Repository> repositories )
    {
        List<RemoteRepository> repositoryList = new ArrayList<RemoteRepository>();
        for ( Repository repo : repositories )
        {
            repositoryList.add( mavenRepositoryFromUrl( repo.getUrl() ) );
        }
        this.repositories = repositoryList;
    }

    public DependencyNode buildVerboseGraphNoManagement( MavenProject project, RepositorySystem system )
            throws MojoExecutionException
    {
        repositorySystem = system;
        List<org.apache.maven.model.Dependency> dependencies = project.getDependencies();

        DependencyNode rootNode = new DefaultDependencyNode( getProjectDependency( project ) );


        for ( org.apache.maven.model.Dependency dependency : dependencies )
        {
            rootNode.getChildren().add( buildFullDependencyGraph( Collections.singletonList( dependency ), project ) );
        }

        // Don't want transitive test dependencies included in analysis
        DependencyNode prunedRoot = pruneTransitiveTestDependencies( rootNode, project );
        applyDependencyManagement( project, prunedRoot );
        return prunedRoot;
    }

    private void applyDependencyManagement( MavenProject project, DependencyNode root )
    {
        Map<String, org.apache.maven.model.Dependency> dependencyManagementMap = createDependencyManagementMap(
                project.getDependencyManagement() );

        for ( DependencyNode child : root.getChildren() )
        {
            for ( DependencyNode nonTransitiveDependencyNode : child.getChildren() )
            {
                applyDependencyManagementDfs( dependencyManagementMap, nonTransitiveDependencyNode );
            }
        }
    }

    private void applyDependencyManagementDfs( Map<String, org.apache.maven.model.Dependency> dependencyManagementMap,
                                               DependencyNode node )
    {
        if ( dependencyManagementMap.containsKey( getDependencyManagementCoordinate( node.getArtifact() ) ) )
        {
            org.apache.maven.model.Dependency manager = dependencyManagementMap.get(
                    getDependencyManagementCoordinate( node.getArtifact() ) );
            Map<String, String> artifactProperties = new HashMap<>();
            for ( Map.Entry<String, String> entry : node.getArtifact().getProperties().entrySet() )
            {
                artifactProperties.put( entry.getKey(), entry.getValue() );
            }

            if ( !manager.getVersion().equals( node.getArtifact().getVersion() ) )
            {
                artifactProperties.put( PRE_MANAGED_VERSION, node.getArtifact().getVersion() );
                node.setArtifact( node.getArtifact().setVersion( manager.getVersion() ) );
            }

            if ( !manager.getScope().equals( node.getDependency().getScope() ) )
            {
                artifactProperties.put( PRE_MANAGED_SCOPE, node.getDependency().getScope() );
                // be aware this does not actually change the node's scope, it may need to be fixed in the future
                artifactProperties.put( MANAGED_SCOPE, manager.getScope() );
            }
            node.setArtifact( node.getArtifact().setProperties( artifactProperties ) );
            node.getDependency().setArtifact( node.getDependency().getArtifact().setProperties( artifactProperties ) );
        }
        for ( DependencyNode child : node.getChildren() )
        {
            applyDependencyManagementDfs( dependencyManagementMap, child );
        }
    }

    private static Map<String, org.apache.maven.model.Dependency> createDependencyManagementMap(
            DependencyManagement dependencyManagement )
    {
        Map<String, org.apache.maven.model.Dependency> dependencyManagementMap = new HashMap<>();
        if ( dependencyManagement == null )
        {
            return dependencyManagementMap;
        }
        for ( org.apache.maven.model.Dependency dependency : dependencyManagement.getDependencies() )
        {
            dependencyManagementMap.put( getDependencyManagementCoordinate( dependency ), dependency );
        }
        return dependencyManagementMap;
    }

    private static String getDependencyManagementCoordinate( org.apache.maven.model.Dependency dependency )
    {
        StringBuilder string = new StringBuilder();
        string.append( dependency.getGroupId() ).append( ":" ).append( dependency.getArtifactId() )
                .append( ":" ).append( dependency.getType() );
        if ( dependency.getClassifier() != null && !dependency.getClassifier().equals( "" ) )
        {
            string.append( ":" ).append( dependency.getClassifier() );
        }
        return string.toString();
    }

    private static String getDependencyManagementCoordinate( Artifact artifact )
    {
        StringBuilder string = new StringBuilder();
        string.append( artifact.getGroupId() ).append( ":" ).append( artifact.getArtifactId() ).append( ":" )
                .append( artifact.getExtension() );
        if ( artifact.getClassifier() != null && !artifact.getClassifier().equals( "" ) )
        {
            string.append( ":" ).append( artifact.getClassifier() );
        }
        return string.toString();
    }

    private Dependency getProjectDependency( MavenProject project )
    {
        Model model = project.getModel();

        return new Dependency( new DefaultArtifact( model.getGroupId(), model.getArtifactId(), model.getPackaging(),
                model.getVersion() ), "" );
    }

    private DependencyNode pruneTransitiveTestDependencies( DependencyNode rootNode, MavenProject project )
    {
        Set<DependencyNode> visitedNodes = new HashSet<>();
        DependencyNode newRoot = new DefaultDependencyNode( getProjectDependency( project ) );
        newRoot.setChildren( new ArrayList<DependencyNode>() );

        for ( int i = 0; i < rootNode.getChildren().size(); i++ )
        {
            DependencyNode childNode = rootNode.getChildren().get( i );
            newRoot.getChildren().add( childNode );

            dfs( childNode, visitedNodes );
        }

        return newRoot;
    }

    private void dfs( DependencyNode node , Set<DependencyNode> visitedNodes )
    {
        if ( !visitedNodes.contains( node ) )
        {
            visitedNodes.add( node );
            // iterator needed to avoid concurrentModificationException
            Iterator<DependencyNode> iterator = node.getChildren().iterator();
            while ( iterator.hasNext() )
            {
                DependencyNode child = iterator.next();
                if ( child.getDependency().getScope().equals( "test" ) )
                {
                    iterator.remove();
                    // node.getChildren().remove( child );
                }
                else
                {
                    dfs( child, visitedNodes );
                }
            }
        }
    }

    private DependencyNode resolveCompileTimeDependencies( List<DependencyNode> dependencyNodes,
                                                           DefaultRepositorySystemSession session )
            throws org.eclipse.aether.resolution.DependencyResolutionException
    {
        List<Dependency> dependencyList = new ArrayList<Dependency>();

        for ( DependencyNode dependencyNode : dependencyNodes )
        {
            dependencyList.add( dependencyNode.getDependency() );
        }

        CollectRequest collectRequest = new CollectRequest();

        collectRequest.setRoot( dependencyList.get( 0 ) );
        if ( dependencyList.size() != 1 )
        {
            // With setRoot, the result includes dependencies with `optional:true` or `provided`
            collectRequest.setRoot( dependencyList.get( 0 ) );
            collectRequest.setDependencies( dependencyList );
        }
        else
        {
            collectRequest.setDependencies( dependencyList );
        }
        for ( RemoteRepository repository : repositories )
        {
            collectRequest.addRepository( repository );
        }
        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setCollectRequest( collectRequest );


        // resolveDependencies equals to calling both collectDependencies (build dependency tree) and
        // resolveArtifacts (download JAR files).
        DependencyResult dependencyResult = repositorySystem.resolveDependencies( session, dependencyRequest );
        return dependencyResult.getRoot();
    }

    private DependencyNode buildFullDependencyGraph( List<org.apache.maven.model.Dependency> dependencies,
                                                     MavenProject project )
    {
        List<DependencyNode> dependencyNodes = new ArrayList<DependencyNode>();

        for ( org.apache.maven.model.Dependency dependency : dependencies )
        {
            Artifact aetherArtifact = new DefaultArtifact( dependency.getGroupId(), dependency.getArtifactId(),
                    dependency.getClassifier(), dependency.getType(), dependency.getVersion() );

            Dependency aetherDependency = new Dependency( aetherArtifact , dependency.getScope() );
            DependencyNode node = new DefaultDependencyNode( aetherDependency );
            node.setOptional( dependency.isOptional() );
            dependencyNodes.add( node );
        }

        DefaultRepositorySystemSession session = RepositoryUtility.newSessionForFullDependency( repositorySystem );
        return buildDependencyGraph( dependencyNodes, session );
    }

    private DependencyNode buildDependencyGraph( List<DependencyNode> dependencyNodes,
                                                 DefaultRepositorySystemSession session )
    {
        try
        {
            return resolveCompileTimeDependencies( dependencyNodes, session );
        }
        catch ( org.eclipse.aether.resolution.DependencyResolutionException ex )
        {
            DependencyResult result = ex.getResult();
            return result.getRoot();
        }
    }
}