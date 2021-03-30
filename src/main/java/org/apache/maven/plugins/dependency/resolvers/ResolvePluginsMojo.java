package org.apache.maven.plugins.dependency.resolvers;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.commons.collections4.map.UnmodifiableMap;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.graph.Dependency;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Goal that resolves all project plugins and reports and their dependencies.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 2.0
 */
//CHECKSTYLE_OFF: LineLength
@Mojo( name = "resolve-plugins", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresOnline = true, threadSafe = true )
//CHECKSTYLE_ON: LineLength
public class ResolvePluginsMojo
    extends AbstractResolveMojo
{
    /**
     * Maven artifact handler manager
     */
    @Requirement
    private ArtifactHandlerManager artifactHandlerManager;

    @Parameter( property = "outputEncoding", defaultValue = "${project.reporting.outputEncoding}" )
    private String outputEncoding;

    private ArtifactTypeRegistry typeRegistry;

    /**
     * Main entry into mojo. Gets the list of dependencies and iterates through displaying the resolved version.
     *
     * @throws MojoExecutionException with a message if an error occurs.
     */
    @Override
    protected void doExecute()
        throws MojoExecutionException
    {
        try
        {
            typeRegistry = RepositoryUtils.newArtifactTypeRegistry( artifactHandlerManager );
            // ideally this should either be DependencyCoordinates or DependencyNode
            final Map<String, Plugin> plugins = resolvePlugins();
            final Map<String, Artifact> resolvedArtifacts = fetchArtifacts( plugins.values() );

            StringBuilder sb = new StringBuilder();
            sb.append( System.lineSeparator() );
            sb.append( "The following plugins have been resolved:" );
            sb.append( System.lineSeparator() );
            if ( plugins.isEmpty() )
            {
                sb.append( "   none" );
                sb.append( System.lineSeparator() );
            }
            else
            {
                for ( Map.Entry<String, Artifact> plugin : resolvedArtifacts.entrySet() )
                {
                    String artifactFilename = null;
                    if ( outputAbsoluteArtifactFilename )
                    {
                        try
                        {
                            // we want to print the absolute file name here
                            artifactFilename = plugin.getValue().getFile().getAbsoluteFile().getPath();
                        }
                        catch ( NullPointerException e )
                        {
                            // ignore the null pointer, we'll output a null string
                            artifactFilename = null;
                        }
                    }

                    String id = plugin.getKey();
                    sb.append( "   " )
                            .append( id )
                            .append( outputAbsoluteArtifactFilename ? ":" + artifactFilename : "" )
                            .append( System.lineSeparator() );

                    if ( !excludeTransitive )
                    {
                        DefaultDependableCoordinate pluginCoordinate = new DefaultDependableCoordinate();
                        pluginCoordinate.setGroupId( plugin.getValue().getGroupId() );
                        pluginCoordinate.setArtifactId( plugin.getValue().getArtifactId() );
                        pluginCoordinate.setVersion( plugin.getValue().getVersion() );

                        Set<Artifact> artifacts = resolveArtifactDependencies( pluginCoordinate );
                        for ( org.apache.maven.model.Dependency d : plugins.get( plugin.getKey() ).getDependencies() )
                        {
                            Dependency dependency = RepositoryUtils.toDependency( d, typeRegistry );
                            Artifact artifact = RepositoryUtils.toArtifact( dependency.getArtifact() );

                            ProjectBuildingRequest buildingRequest = newResolvePluginProjectBuildingRequest();
                            getArtifactResolver().resolveArtifact( buildingRequest, artifact );

                            artifacts.add( artifact );
                        }

                        for ( final Artifact artifact : artifacts )
                        {
                            artifactFilename = null;
                            if ( outputAbsoluteArtifactFilename )
                            {
                                try
                                {
                                    // we want to print the absolute file name here
                                    artifactFilename = artifact.getFile().getAbsoluteFile().getPath();
                                }
                                catch ( NullPointerException e )
                                {
                                    // ignore the null pointer, we'll output a null string
                                    artifactFilename = null;
                                }
                            }

                            id = artifact.toString();
                            sb.append( "      " )
                                    .append( id )
                                    .append( outputAbsoluteArtifactFilename ? ":" + artifactFilename : "" )
                                    .append( System.lineSeparator() );
                        }
                    }
                }
                sb.append( System.lineSeparator() );

                String output = sb.toString();
                if ( outputFile == null )
                {
                    DependencyUtil.log( output, getLog() );
                }
                else
                {
                    String encoding = Objects.toString( outputEncoding, "UTF-8" );
                    DependencyUtil.write( output, outputFile, appendOutput, encoding );
                }
            }
        }
        catch ( IOException | ArtifactFilterException | ArtifactResolverException | DependencyResolverException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

    /**
     * This method resolves the plugin artifacts from the project.
     *
     * @return map of resolved plugins
     */
    protected Map<String, Plugin> resolvePlugins()
        throws ArtifactFilterException, ArtifactResolverException
    {
        Set<Plugin> plugins = new LinkedHashSet<>( getProject().getBuildPlugins() );

        Reporting reporting = getProject().getReporting();
        if ( reporting != null )
        {
            List<ReportPlugin> reportPlugins = reporting.getPlugins();
            for ( ReportPlugin reportPlugin : reportPlugins )
            {
                // Conversion borrowed from
                // org.apache.maven.project.MavenProject#getReportArtifacts
                Plugin plugin = new Plugin();
                plugin.setGroupId( reportPlugin.getGroupId() );
                plugin.setArtifactId( reportPlugin.getArtifactId() );
                plugin.setVersion( reportPlugin.getVersion() );
                plugins.add( plugin );
            }
        }

        HashMap<String, Plugin> result = new HashMap<>( plugins.size() );
        for ( Plugin plugin : plugins )
        {
            result.put( plugin.getId(), plugin );
        }

        return UnmodifiableMap.unmodifiableMap( result );
    }

    private Map<String, Artifact> fetchArtifacts( Collection<Plugin> plugins )
        throws ArtifactResolverException, ArtifactFilterException
    {
        Set<Artifact> artifacts = new LinkedHashSet<>( plugins.size() );
        for ( Plugin plugin : plugins )
        {
            artifacts.add(
                new DefaultArtifact(
                    plugin.getGroupId(),
                    plugin.getArtifactId(),
                    plugin.getVersion(),
                    Artifact.SCOPE_RUNTIME,
                    "maven-plugin",
                    null,
                    artifactHandlerManager.getArtifactHandler( "maven-plugin" )
                )
            );
        }

        final FilterArtifacts filter = getArtifactsFilter();
        artifacts = filter.filter( artifacts );

        Map<String, Artifact> resolvedArtifacts = new HashMap<>( artifacts.size() );
        // final ArtifactFilter filter = getPluginFilter();
        for ( final Artifact artifact : artifacts )
        {
            // if ( !filter.include( artifact ) )
            // {
            // final String logStr =
            // String.format( " Plugin SKIPPED: %s", DependencyUtil.getFormattedFileName( artifact, false ) );
            //
            // if ( !silent )
            // {
            // this.getLog().info( logStr );
            // }
            //
            // artifacts.remove( artifact );
            // continue;
            // }

            ProjectBuildingRequest buildingRequest = newResolvePluginProjectBuildingRequest();

            // resolve the new artifact
            Artifact resolved = getArtifactResolver().resolveArtifact( buildingRequest, artifact ).getArtifact();
            resolvedArtifacts.put( ArtifactUtils.key( resolved ), resolved );
        }
        return resolvedArtifacts;
    }

    @Override
    protected ArtifactsFilter getMarkedArtifactFilter()
    {
        return null;
    }
}
