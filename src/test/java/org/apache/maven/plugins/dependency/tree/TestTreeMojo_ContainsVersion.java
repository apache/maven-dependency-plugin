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

import junit.framework.TestCase;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.Restriction;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Collections;

import static org.apache.maven.plugins.dependency.tree.TreeMojo.containsVersion;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests <code>TreeMojo.containsVersion</code>.
 */
public class TestTreeMojo_ContainsVersion extends TestCase
{
    private VersionRange range = mock( VersionRange.class );

    private ArtifactVersion version = mock( ArtifactVersion.class );

    public void testWhenRecommendedVersionIsNullAndNoRestrictions()
    {
        when( range.getRecommendedVersion() ).thenReturn( null );
        when( range.getRestrictions() ).thenReturn( Collections.<Restriction>emptyList() );

        boolean doesItContain = containsVersion( range, version );

        assertFalse( doesItContain );
    }
}
