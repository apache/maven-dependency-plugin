package org.apache.maven.plugins.dependency.tree;

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
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.eclipse.aether.util.graph.transformer.JavaDependencyContextRefiner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the VerboseDependencyGraph
 */
class VerboseDependencyGraphBuilder
{
    private static final String PRE_MANAGED_SCOPE = "preManagedScope", PRE_MANAGED_VERSION = "preManagedVersion",
            MANAGED_SCOPE = "managedScope";

    public DependencyNode buildVerboseGraph( MavenProject project, ProjectDependenciesResolver resolver,
                                             RepositorySystemSession repositorySystemSession,
                                             Collection<MavenProject> reactorProjects,
                                             ProjectBuildingRequest buildingRequest )
            throws DependencyGraphBuilderException
    {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager( repositorySystemSession.getLocalRepositoryManager() );

        DependencySelector dependencySelector = new AndDependencySelector(
                // ScopeDependencySelector takes exclusions. 'Provided' scope is not here to avoid
                // false positive in LinkageChecker.
                new ScopeDependencySelector(), new ExclusionDependencySelector() );

        session.setDependencySelector( dependencySelector );
        session.setDependencyGraphTransformer(
                new ChainedDependencyGraphTransformer( new CycleBreakerGraphTransformer(), // Avoids StackOverflowError
                        new JavaDependencyContextRefiner() ) );
        session.setDependencyManager( null );

        DependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
        request.setMavenProject( buildingRequest.getProject() );
        request.setRepositorySession( session ) ;
        DependencyNode rootNode;
        boolean reactor = false;

        try
        {
            rootNode = resolver.resolve( request ).getDependencyGraph();
        }
        catch ( DependencyResolutionException e )
        {
            // cannot properly resolve reactor dependencies with verbose RepositorySystemSession
            // this should be fixed in the future
            DependencyResolutionRequest reactorRequest = new DefaultDependencyResolutionRequest();
            reactorRequest.setMavenProject( buildingRequest.getProject() );
            reactorRequest.setRepositorySession( buildingRequest.getRepositorySession() ) ;
            try
            {
                rootNode = resolver.resolve( reactorRequest ).getDependencyGraph();
            }
            catch ( DependencyResolutionException exception )
            {
                if ( reactorProjects == null )
                {
                    throw new DependencyGraphBuilderException( "Could not resolve following dependencies: "
                            + exception.getResult().getUnresolvedDependencies(), exception );
                }
                reactor = true;
                // try collecting from reactor
                rootNode = collectDependenciesFromReactor( exception, reactorProjects ).getDependencyGraph();
                rootNode.setData( "ContainsModule", "True" );
                // rootNode.setArtifact( rootArtifact.setProperties( artifactProperties ) );
            }
        }

        // Don't want transitive test dependencies included in analysis
        DependencyNode prunedRoot = pruneTransitiveTestDependencies( rootNode, project );
        applyDependencyManagement( project, prunedRoot );
        if ( reactor )
        {
            prunedRoot.setData( "ContainsModule", "True" );
        }
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
        StringBuilder builder = new StringBuilder();
        builder.append( dependency.getGroupId() ).append( ":" ).append( dependency.getArtifactId() ).append( ":" )
                .append( dependency.getType() );
        if ( dependency.getClassifier() != null && !dependency.getClassifier().equals( "" ) )
        {
            builder.append( ":" ).append( dependency.getClassifier() );
        }
        return builder.toString();
    }

    private static String getDependencyManagementCoordinate( Artifact artifact )
    {
        StringBuilder builder = new StringBuilder();
        builder.append( artifact.getGroupId() ).append( ":" ).append( artifact.getArtifactId() ).append( ":" ).append(
                artifact.getExtension() );
        if ( artifact.getClassifier() != null && !artifact.getClassifier().equals( "" ) )
        {
            builder.append( ":" ).append( artifact.getClassifier() );
        }
        return builder.toString();
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

            pruneTransitiveTestDependenciesDfs( childNode, visitedNodes );
        }

        return newRoot;
    }

    private void pruneTransitiveTestDependenciesDfs( DependencyNode node, Set<DependencyNode> visitedNodes )
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
                }
                else
                {
                    pruneTransitiveTestDependenciesDfs( child, visitedNodes );
                }
            }
        }
    }

    private DependencyResolutionResult collectDependenciesFromReactor( DependencyResolutionException e,
                                                                       Collection<MavenProject> reactorProjects )
            throws DependencyGraphBuilderException
    {
        DependencyResolutionResult result = e.getResult();

        List<Dependency> reactorDeps = getReactorDependencies( reactorProjects, result.getUnresolvedDependencies() );
        result.getUnresolvedDependencies().removeAll( reactorDeps );
        result.getResolvedDependencies().addAll( reactorDeps );

        if ( !result.getUnresolvedDependencies().isEmpty() )
        {
            throw new DependencyGraphBuilderException( "Could not resolve nor collect following dependencies: "
                    + result.getUnresolvedDependencies(), e );
        }

        return result;
    }

    private List<Dependency> getReactorDependencies( Collection<MavenProject> reactorProjects, List<?> dependencies )
    {
        Set<ArtifactKey> reactorProjectsIds = new HashSet<ArtifactKey>();
        for ( MavenProject project : reactorProjects )
        {
            reactorProjectsIds.add( new ArtifactKey( project ) );
        }

        List<Dependency> reactorDeps = new ArrayList<Dependency>();
        for ( Object untypedDependency : dependencies )
        {
            Dependency dependency = (Dependency) untypedDependency;
            org.eclipse.aether.artifact.Artifact depArtifact = dependency.getArtifact();

            ArtifactKey key =
                    new ArtifactKey( depArtifact.getGroupId(), depArtifact.getArtifactId(), depArtifact.getVersion() );

            if ( reactorProjectsIds.contains( key ) )
            {
                reactorDeps.add( dependency );
            }
        }

        return reactorDeps;
    }
}
