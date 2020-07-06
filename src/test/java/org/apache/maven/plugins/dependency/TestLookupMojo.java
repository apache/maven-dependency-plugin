package org.apache.maven.plugins.dependency;

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

import java.io.File;


public class TestLookupMojo
    extends AbstractDependencyMojoTestCase
{
    GetMojo mojo;

    protected void setUp()
        throws Exception
    {
        // required for mojo lookups to work
        super.setUp( "markers", false );

        File testPom = new File( getBasedir(), "target/test-classes/unit/get-test/plugin-config.xml" );
        mojo = (GetMojo) lookupMojo( "get", testPom );

        assertNotNull( mojo );

    }

    /**
     * Test transitive parameter
     * 
     * @throws Exception in case of errors
     */
    public void testSomething()
        throws Exception
    {
        assertEquals(1, 1);
    }

}
