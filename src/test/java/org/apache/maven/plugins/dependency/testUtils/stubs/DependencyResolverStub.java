package org.apache.maven.plugins.dependency.testUtils.stubs;

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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.filter.resolve.TransformableFilter;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;

/**
 * A very simple stub to use for whiteboxing the a Maven DependencyResolver
 * class.
 */
public class DependencyResolverStub implements DependencyResolver {

    private Map<String, Artifact> coordCache = new HashMap<>();

    public void addDependableCoordinateLookup(
            final DependableCoordinate coord,
            final Artifact artifact )
    {
        coordCache.put( coord.toString(), artifact );
    }

    @Override
    public Iterable<ArtifactResult> resolveDependencies(
            final ProjectBuildingRequest buildingRequest,
            final DependableCoordinate coordinate,
            final TransformableFilter filter ) throws DependencyResolverException
    {


        if ( coordCache.get( coordinate.toString() ) != null ) {
            ArtifactResult result = new ArtifactResult()
            {

                @Override
                public Artifact getArtifact()
                {
                    // TODO Auto-generated method stub
                    return coordCache.get( coordinate.toString() );
                }
            };

            return Arrays.asList( result );
        } else {
            throw new DependencyResolverException( "Cannot resolve coordinates: " + coordinate, new IOException() );
        }
    }

    @Override
    public Iterable<ArtifactResult> resolveDependencies(
            ProjectBuildingRequest buildingRequest,
            Model model,
            TransformableFilter filter ) throws DependencyResolverException {

        throw new UnsupportedOperationException( "Method not implemented" );
    }

    @Override
    public Iterable<ArtifactResult> resolveDependencies(
            ProjectBuildingRequest buildingRequest,
            Collection<Dependency> dependencies,
            Collection<Dependency> managedDependencies,
            TransformableFilter filter ) throws DependencyResolverException {

        throw new UnsupportedOperationException( "Method not implemented" );
    }

}
