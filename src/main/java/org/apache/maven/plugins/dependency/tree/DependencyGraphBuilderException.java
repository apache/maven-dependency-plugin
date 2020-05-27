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

/**
 * Indicates that a Maven project's dependency graph cannot be resolved.
 *
 * @author Hervé Boutemy
 * @since 2.0
 */
public class DependencyGraphBuilderException
    extends Exception
{
    private static final long serialVersionUID = -7428777046707410949L;

    // constructors -----------------------------------------------------------

    /**
     * @param message   Message indicating why dependency graph could not be resolved.
     */
    public DependencyGraphBuilderException( String message )
    {
        super( message );
    }

    /**
     * @param message   Message indicating why dependency graph could not be resolved.
     * @param cause     Throwable indicating at which point the graph failed to be resolved.
     */
    public DependencyGraphBuilderException( String message, Throwable cause )
    {
        super( message, cause );
    }
}
