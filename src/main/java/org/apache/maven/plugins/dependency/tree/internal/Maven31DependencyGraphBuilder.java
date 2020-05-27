package org.apache.maven.plugins.dependency.tree.internal;

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

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.version.VersionConstraint;
import org.apache.maven.plugins.dependency.tree.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wrapper around Eclipse Aether dependency resolver, used in Maven 3.1.
 *
 * @see ProjectDependenciesResolver
 * @author Hervé Boutemy
 * @since 2.1
 */
@Component( role = DependencyGraphBuilder.class, hint = "maven31" )
public class Maven31DependencyGraphBuilder
        extends AbstractLogEnabled
        implements DependencyGraphBuilder
{
    @Requirement
    private ProjectDependenciesResolver resolver;

    /**
     * Builds the dependency graph for Maven 3.1+.
     *
     * @param buildingRequest the buildingRequest
     * @param filter artifact filter (can be <code>null</code>)
     * @return DependencyNode containing the dependency graph.
     * @throws DependencyGraphBuilderException if some of the dependencies could not be resolved.
     */
    @Override
    public DependencyNode buildDependencyGraph( ProjectBuildingRequest buildingRequest, ArtifactFilter filter )
        throws DependencyGraphBuilderException
    {
        return buildDependencyGraph( buildingRequest, filter, null );
    }

    /**
     * Builds the dependency graph for Maven 3.1+, eventually hacking for collecting projects from
     * reactor not yet built.
     *
     * @param buildingRequest the buildingRequest
     * @param filter artifact filter (can be <code>null</code>)
     * @param reactorProjects Collection of those projects contained in the reactor (can be <code>null</code>).
     * @return DependencyNode containing the dependency graph.
     * @throws DependencyGraphBuilderException if some of the dependencies could not be resolved.
     */
    @Override
    public DependencyNode buildDependencyGraph( ProjectBuildingRequest buildingRequest, ArtifactFilter filter,
                                                Collection<MavenProject> reactorProjects )
        throws DependencyGraphBuilderException
    {
        MavenProject project = buildingRequest.getProject();

        RepositorySystemSession session =
               // (RepositorySystemSession) buildingRequest.getRepositorySession();
          (RepositorySystemSession) Invoker.invoke( buildingRequest, "getRepositorySession" );

        /*
         * if ( Boolean.TRUE != ( (Boolean) session.getConfigProperties().get(
         * DependencyManagerUtils.NODE_DATA_PREMANAGED_VERSION ) ) ) { DefaultRepositorySystemSession newSession = new
         * DefaultRepositorySystemSession( session ); newSession.setConfigProperty(
         * DependencyManagerUtils.NODE_DATA_PREMANAGED_VERSION, true ); session = newSession; }
         */

        final DependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
        request.setMavenProject( project );
        // request.setRepositorySession( (org.sonatype.aether.RepositorySystemSession) session );
        Invoker.invoke( request, "setRepositorySession", RepositorySystemSession.class, session );

        final DependencyResolutionResult result = resolveDependencies( request, reactorProjects );
        org.eclipse.aether.graph.DependencyNode graph =
        //        (org.eclipse.aether.graph.DependencyNode) result.getDependencyGraph();
            (org.eclipse.aether.graph.DependencyNode) Invoker.invoke( DependencyResolutionResult.class, result,
                                                                      "getDependencyGraph" );

        return buildDependencyNode( null, graph, project.getArtifact(), filter );
    }

    private DependencyResolutionResult resolveDependencies( DependencyResolutionRequest request,
                                                            Collection<MavenProject> reactorProjects )
        throws DependencyGraphBuilderException
    {
        try
        {
            return resolver.resolve( request );
        }
        catch ( DependencyResolutionException e )
        {
            if ( reactorProjects == null )
            {
                throw new DependencyGraphBuilderException( "Could not resolve following dependencies: "
                    + e.getResult().getUnresolvedDependencies(), e );
            }

            // try collecting from reactor
            return collectDependenciesFromReactor( e, reactorProjects );
        }
    }

    private DependencyResolutionResult collectDependenciesFromReactor( DependencyResolutionException e,
                                                                       Collection<MavenProject> reactorProjects )
        throws DependencyGraphBuilderException
    {
        DependencyResolutionResult result = e.getResult();

        List<Dependency> reactorDeps = getReactorDependencies( reactorProjects, result.getUnresolvedDependencies() );
        result.getUnresolvedDependencies().removeAll( reactorDeps );
        Invoker.invoke( result.getResolvedDependencies(), "addAll", Collection.class, reactorDeps );

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

    private Artifact getDependencyArtifact( Dependency dep )
    {
        org.eclipse.aether.artifact.Artifact artifact = dep.getArtifact();

        Artifact mavenArtifact = RepositoryUtils.toArtifact( (org.sonatype.aether.artifact.Artifact) artifact );
        //        (Artifact) Invoker.invoke( RepositoryUtils.class, "toArtifact",
        //                                  org.eclipse.aether.artifact.Artifact.class, artifact );

        mavenArtifact.setScope( dep.getScope() );
        mavenArtifact.setOptional( dep.isOptional() );

        return mavenArtifact;
    }

    private DependencyNode buildDependencyNode( DependencyNode parent, org.eclipse.aether.graph.DependencyNode node,
                                                Artifact artifact, ArtifactFilter filter )
    {
        String premanagedVersion = null; // DependencyManagerUtils.getPremanagedVersion( node );
        String premanagedScope = null; // DependencyManagerUtils.getPremanagedScope( node );

        List<org.apache.maven.model.Exclusion> exclusions = null;
        Boolean optional = null;
        if ( node.getDependency() != null )
        {
            exclusions = new ArrayList<>( node.getDependency().getExclusions().size() );
            for ( Exclusion exclusion : node.getDependency().getExclusions() )
            {
                org.apache.maven.model.Exclusion modelExclusion = new org.apache.maven.model.Exclusion();
                modelExclusion.setGroupId( exclusion.getGroupId() );
                modelExclusion.setArtifactId( exclusion.getArtifactId() );
                exclusions.add( modelExclusion );
            }
        }

        DefaultDependencyNode current =
            new DefaultDependencyNode( parent, artifact, premanagedVersion, premanagedScope,
                                       getVersionSelectedFromRange( node.getVersionConstraint() ),
                                       optional, exclusions );

        List<DependencyNode> nodes = new ArrayList<DependencyNode>( node.getChildren().size() );
        for ( org.eclipse.aether.graph.DependencyNode child : node.getChildren() )
        {
            Artifact childArtifact = getDependencyArtifact( child.getDependency() );

            if ( ( filter == null ) || filter.include( childArtifact ) )
            {
                nodes.add( buildDependencyNode( current, child, childArtifact, filter ) );
            }
        }

        current.setChildren( Collections.unmodifiableList( nodes ) );

        return current;
    }

    private String getVersionSelectedFromRange( VersionConstraint constraint )
    {
        if ( ( constraint == null ) || ( constraint.getVersion() != null ) )
        {
            return null;
        }

        return constraint.getRange().toString();
    }
}
