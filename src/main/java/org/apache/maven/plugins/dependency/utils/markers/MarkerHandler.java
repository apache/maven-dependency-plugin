package org.apache.maven.plugins.dependency.utils.markers;

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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public interface MarkerHandler
{
    /**
     * @return true/false.
     * @throws MojoExecutionException in case of an error.
     */
    boolean isMarkerSet()
        throws MojoExecutionException;

    /**
     * @throws MojoExecutionException in case of an error.
     */
    void setMarker()
        throws MojoExecutionException;

    /**
     * @return true/false.
     * @throws MojoExecutionException in case of an error.
     */
    boolean clearMarker()
        throws MojoExecutionException;

    /**
     * @param artifact {@link Artifact}
     * @return true/false.
     * @throws MojoExecutionException in case of an error.
     */
    boolean isMarkerOlder( Artifact artifact )
        throws MojoExecutionException;

    /**
     * @param artifact {@link Artifact}
     */
    void setArtifact( Artifact artifact );

}
