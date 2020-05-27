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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugins.dependency.tree.DependencyNode;
import org.apache.maven.plugins.dependency.tree.traversal.DependencyNodeVisitor;

import java.util.List;

/**
 * Default implementation of a DependencyNode.
 */
public class DefaultDependencyNode implements DependencyNode
{
    private final Artifact artifact;
    
    private final DependencyNode parent;

    private final String premanagedVersion;

    private final String premanagedScope;

    private final String versionConstraint;

    private List<DependencyNode> children;

    private Boolean optional;

    private List<Exclusion> exclusions;

    /**
     * Constructs the DefaultDependencyNode.
     *
     * @param parent            Parent node, may be {@code null}.
     * @param artifact          Artifact associated with this dependency.
     * @param premanagedVersion the premanaged version, may be {@code null}.
     * @param premanagedScope   the premanaged scope, may be {@code null}.
     * @param versionConstraint the version constraint, may be {@code null.}
     */
    public DefaultDependencyNode( DependencyNode parent, Artifact artifact, String premanagedVersion,
                                  String premanagedScope, String versionConstraint )
    {
        this.parent = parent;
        this.artifact = artifact;
        this.premanagedVersion = premanagedVersion;
        this.premanagedScope = premanagedScope;
        this.versionConstraint = versionConstraint;
    }

    public DefaultDependencyNode( DependencyNode parent, Artifact artifact, String premanagedVersion,
                                  String premanagedScope, String versionConstraint, Boolean optional,
                                  List<Exclusion> exclusions )
    {
        this.parent = parent;
        this.artifact = artifact;
        this.premanagedVersion = premanagedVersion;
        this.premanagedScope = premanagedScope;
        this.versionConstraint = versionConstraint;
        this.optional = optional;
        this.exclusions = exclusions;
    }

    /**
     * Applies the specified dependency node visitor to this dependency node and its children.
     * 
     * @param visitor the dependency node visitor to use
     * @return the visitor result of ending the visit to this node
     * @since 1.1
     */
    @Override
    public boolean accept( DependencyNodeVisitor visitor )
    {
        if ( visitor.visit( this ) )
        {
            for ( DependencyNode child : getChildren() )
            {
                if ( !child.accept( visitor ) )
                {
                    break;
                }
            }
        }

        return visitor.endVisit( this );
    }

    /**
     * @return Artifact for this DependencyNode.
     */
    @Override
    public Artifact getArtifact()
    {
        return artifact;
    }

    /**
     *
     * @param children  List of DependencyNode to set as child nodes.
     */
    public void setChildren( List<DependencyNode> children )
    {
        this.children = children;
    }

    /**
     * @return List of child nodes for this DependencyNode.
     */
    @Override
    public List<DependencyNode> getChildren()
    {
        return children;
    }

    /**
     * @return Parent of this DependencyNode.
     */
    @Override
    public DependencyNode getParent()
    {
        return parent;
    }

    @Override
    public String getPremanagedVersion()
    {
        return premanagedVersion;
    }

    @Override
    public String getPremanagedScope()
    {
        return premanagedScope;
    }

    @Override
    public String getVersionConstraint()
    {
        return versionConstraint;
    }

    @Override
    public Boolean getOptional()
    {
        return optional;
    }

    @Override
    public List<Exclusion> getExclusions()
    {
        return exclusions;
    }

    /**
     * @return Stringified representation of this DependencyNode.
     */
    @Override
    public String toNodeString()
    {
        StringBuilder buffer = new StringBuilder();

        buffer.append( artifact );

        ItemAppender appender = new ItemAppender( buffer, " (", "; ", ")" );

        if ( getPremanagedVersion() != null )
        {
            appender.append( "version managed from ", getPremanagedVersion() );
        }

        if ( getPremanagedScope() != null )
        {
            appender.append( "scope managed from ", getPremanagedScope() );
        }

        if ( getVersionConstraint() != null )
        {
            appender.append( "version selected from constraint ", getVersionConstraint() );
        }


        appender.flush();
        if ( optional != null && optional )
        {
            buffer.append( " (optional) " );
        }

        return buffer.toString();
    }

    /**
     * Utility class to concatenate a number of parameters with separator tokens.
     */
    private static class ItemAppender
    {
        private StringBuilder buffer;

        private String startToken;

        private String separatorToken;

        private String endToken;

        private boolean appended;

        ItemAppender( StringBuilder buffer, String startToken, String separatorToken, String endToken )
        {
            this.buffer = buffer;
            this.startToken = startToken;
            this.separatorToken = separatorToken;
            this.endToken = endToken;

            appended = false;
        }

        public ItemAppender append( String item1, String item2 )
        {
            appendToken();

            buffer.append( item1 ).append( item2 );

            return this;
        }

        public void flush()
        {
            if ( appended )
            {
                buffer.append( endToken );

                appended = false;
            }
        }

        private void appendToken()
        {
            buffer.append( appended ? separatorToken : startToken );

            appended = true;
        }
    }
}
