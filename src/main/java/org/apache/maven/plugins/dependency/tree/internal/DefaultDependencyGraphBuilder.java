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

import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugins.dependency.tree.DependencyNode;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.plugins.dependency.tree.DependencyGraphBuilder;
import org.apache.maven.plugins.dependency.tree.DependencyGraphBuilderException;
import org.apache.maven.plugins.dependency.tree.DependencyNode;
import org.apache.maven.plugins.dependency.tree.internal.Maven31DependencyGraphBuilder;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;

import java.util.Collection;

/**
 * Default dependency graph builder that detects current Maven version to delegate to either Maven 3.0 or 3.1+ specific
 * code.
 *
 * @see Maven31DependencyGraphBuilder
 * @author Hervé Boutemy
 * @since 2.0
 */
@Component( role = DependencyGraphBuilder.class )
public class DefaultDependencyGraphBuilder
        extends AbstractLogEnabled
        implements DependencyGraphBuilder, Contextualizable
{
    protected PlexusContainer container;

    /**
     * Builds a dependency graph.
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
     * Builds a dependency graph.
     *
     * @param filter artifact filter (can be <code>null</code>)
     * @param reactorProjects Collection of those projects contained in the reactor (can be <code>null</code>)
     * @return DependencyNode containing the dependency graph.
     * @throws DependencyGraphBuilderException if some of the dependencies could not be resolved.
     */
    @Override
    public DependencyNode buildDependencyGraph( ProjectBuildingRequest buildingRequest, ArtifactFilter filter,
                                                Collection<MavenProject> reactorProjects )
        throws DependencyGraphBuilderException
    {
        try
        {
            String hint = isMaven31() ? "maven31" : "maven3";

            DependencyGraphBuilder effectiveGraphBuilder =
                (DependencyGraphBuilder) container.lookup( DependencyGraphBuilder.class.getCanonicalName(), hint );
            
            if ( getLogger().isDebugEnabled() )
            {
                MavenProject project = buildingRequest.getProject();
                
                getLogger().debug( "building " + hint + " dependency graph for " + project.getId() + " with "
                                + effectiveGraphBuilder.getClass().getSimpleName() );
            }

            return effectiveGraphBuilder.buildDependencyGraph( buildingRequest, filter, reactorProjects );
        }
        catch ( ComponentLookupException e )
        {
            throw new DependencyGraphBuilderException( e.getMessage(), e );
        }
    }

    /**
     * @return true if the current Maven version is Maven 3.1.
     */
    protected static boolean isMaven31()
    {
        return canFindCoreClass( "org.eclipse.aether.artifact.Artifact" ); // Maven 3.1 specific
    }

    private static boolean canFindCoreClass( String className )
    {
        try
        {
            Thread.currentThread().getContextClassLoader().loadClass( className );

            return true;
        }
        catch ( ClassNotFoundException e )
        {
            return false;
        }
    }

    /**
     * Injects the Plexus content.
     *
     * @param context   Plexus context to inject.
     * @throws ContextException if the PlexusContainer could not be located.
     */
    @Override
    public void contextualize( Context context )
        throws ContextException
    {
        container = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );
    }
}
