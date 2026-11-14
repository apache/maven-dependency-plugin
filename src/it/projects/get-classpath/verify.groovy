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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

String jar( String artifactId )
{
    return new File( localRepositoryPath,
        "org/apache/maven/its/dependency/" + artifactId + "/1.0/" + artifactId + "-1.0.jar" ).getPath();
}

String read( String name )
{
    File file = new File( basedir, "target/" + name );
    assert file.isFile() : "Missing file " + file;
    return new String( Files.readAllBytes( file.toPath() ), StandardCharsets.UTF_8 );
}

// The artifact is resolved as for a project that depends on it, in the order of the running Maven's resolver: the
// artifact first, then its runtime dependencies. The test, provided and optional dependencies of the artifact and the
// system dependency of its dependency are not part of the classpath. The artifact's own dependencyManagement,
// which sets the version of the leaf to 2.0, is applied by Maven 4 only.
String mavenVersion = new File( basedir, "build.log" ).readLines().find { it.startsWith( "Apache Maven " ) };
assert mavenVersion != null : "No Maven version in build.log";
String leafVersion = mavenVersion.startsWith( "Apache Maven 4" ) ? "2.0" : "1.0";

String leaf = new File( localRepositoryPath,
    "org/apache/maven/its/dependency/get-classpath-lib-leaf/" + leafVersion + "/get-classpath-lib-leaf-" + leafVersion + ".jar" ).getPath();
String expected = [ jar( "get-classpath-lib" ), jar( "get-classpath-lib-dep" ), jar( "get-classpath-lib-runtime" ), leaf ]
    .join( File.pathSeparator );
String actual = read( "classpath.txt" );
assert actual.equals( expected ) : "Unexpected classpath: " + actual + ", expected: " + expected + " for " + mavenVersion;

String noTransitive = read( "classpath-no-transitive.txt" );
assert noTransitive.equals( jar( "get-classpath-lib" ) ) : "Unexpected classpath: " + noTransitive;

return true;
