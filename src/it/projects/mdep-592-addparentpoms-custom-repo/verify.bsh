/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.*;

File outputDir = new File( basedir, "target/dependencies" );

String[] expectedFiles = {
    "org/apache/maven/its/mdep592/test-child/1.0/test-child-1.0.jar",
    "org/apache/maven/its/mdep592/test-child/1.0/test-child-1.0.pom",
    // CRITICAL TEST for MDEP-592: Parent POM from custom repository
    // Without the fix, this file would be missing because the custom repository
    // would not be propagated to the ProjectBuildingRequest
    "org/apache/maven/its/mdep592/test-parent/1.0/test-parent-1.0.pom"
};

for ( String expectedFile : expectedFiles )
{
    File file = new File( outputDir, expectedFile );
    if ( !file.isFile() )
    {
        throw new Exception( "Missing file " + file );
    }
}

return true;
