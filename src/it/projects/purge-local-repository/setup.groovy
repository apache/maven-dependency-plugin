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

void createJar( String artifactId )
{
    File jar = new File( localRepositoryPath,
            "org/apache/maven/its/dependency/" + artifactId + "/1.0/" + artifactId + "-1.0.jar" );
    jar.getParentFile().mkdirs();
    jar.createNewFile();
}

createJar( "purged" );
createJar( "not-purged-test" );
createJar( "not-purged-provided" );
createJar( "not-purged-optional" );

File purgedPom = new File( localRepositoryPath,
        "org/apache/maven/its/dependency/purged/1.0/purged-1.0.pom" );
PrintWriter writer = new PrintWriter( purgedPom, "UTF-8" );
try
{
    writer.println( "<project>" );
    writer.println( "  <modelVersion>4.0.0</modelVersion>" );
    writer.println( "  <groupId>org.apache.maven.its.dependency</groupId>" );
    writer.println( "  <artifactId>purged</artifactId>" );
    writer.println( "  <version>1.0</version>" );
    writer.println( "  <dependencies>" );
    writer.println( "    <dependency>" );
    writer.println( "      <groupId>org.apache.maven.its.dependency</groupId>" );
    writer.println( "      <artifactId>not-purged-optional</artifactId>" );
    writer.println( "      <version>1.0</version>" );
    writer.println( "      <optional>true</optional>" );
    writer.println( "    </dependency>" );
    writer.println( "  </dependencies>" );
    writer.println( "</project>" );
}
finally
{
    writer.close();
}

return true;
