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

import org.apache.commons.lang3.StringUtils;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;

import java.io.Writer;
import java.util.List;

/**
 * A dependency node visitor that serializes visited nodes to a writer using the
 * JSON format.
 *
 * @author <a href="mailto:kezhenxu94@apache.org">Zhenxu Ke</a>
 * @since 3.3.1
 */
public class JSONDependencyNodeVisitor
    extends AbstractSerializingVisitor
    implements DependencyNodeVisitor
{

    /**
     * Constructor.
     *
     * @param writer the writer to write to.
     */
    public JSONDependencyNodeVisitor( Writer writer )
    {
        super( writer );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean endVisit( DependencyNode node )
    {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit( DependencyNode node )
    {
        if ( node.getParent() == null || node.getParent() == node )
        {
            writeNode( 0, node, true );
        }

        return true;
    }

    private void writeNode( int indent, DependencyNode node, boolean root )
    {
        Artifact artifact = node.getArtifact();

        writer.println( indentations( indent ) + "{" );
        indent++;
        String groupId = indentations( indent ) + "\"groupId\": \"" + artifact.getGroupId() + "\"";
        String artifactId = indentations( indent ) + "\"artifactId\": \"" + artifact.getArtifactId() + "\"";
        String version = indentations( indent ) + "\"version\": \"" + artifact.getVersion() + "\"";
        String type = indentations( indent ) + "\"type\": \"" + artifact.getType() + "\"";
        String scope = indentations( indent ) + "\"scope\": \"" + artifact.getScope() + "\"";
        String[] elements = root ? new String[] { groupId, artifactId, version, type }
                                 : new String[] { groupId, artifactId, version, type, scope };

        writer.print( StringUtils.join( "," + System.lineSeparator(), elements ) );

        List<DependencyNode> children = node.getChildren();
        if ( children.size() > 0 )
        {
            writer.println( "," );
            writer.println( indentations( indent ) + "\"dependencies\": [" );
            indent++;
            for ( int i = 0; i < children.size(); i++ )
            {
                writeNode( indent, children.get( i ), false );
                if ( i < children.size() - 1 )
                {
                    writer.println( "," );
                }
                else
                {
                    writer.println();
                }
            }
            indent--;
            writer.println( indentations( indent ) + "]" );
        }
        else
        {
            writer.println();
        }
        indent--;
        writer.print(  indentations( indent ) + "}" );
    }

    private static String indentations( int indent )
    {
        return StringUtils.repeat( "\t", indent );
    }
}
