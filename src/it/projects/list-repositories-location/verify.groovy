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

File file = new File( basedir, "build.log" )
assert file.exists()

String buildLog = file.getText( "UTF-8" )

// Maven 3 reports the POM location as a plain path, Maven 4 as a file: URI, so compare only the path tail
void assertDeclared( String buildLog, String repo, String modelId, String pomTail )
{
    List<String> lines = buildLog.readLines()
    int index = lines.findIndexOf { it.contains( "* ${repo}" ) }
    assert index >= 0 : "${repo} not listed"
    String declared = lines[index + 1].replace( '\\', '/' )
    assert declared ==~ /.*   declared in \Q${modelId}\E \S*\Q${pomTail}\E:\d+/ : "unexpected location for ${repo}: ${declared}"
}

assertDeclared( buildLog, "fake-remote-repository (http://localhost:2345, default, releases+snapshots)", "org.apache.maven.its.dependency:test:1.0-SNAPSHOT", "/list-repositories-location/pom.xml" )
assertDeclared( buildLog, "fake-plugin-repository (http://localhost:3456, default, releases+snapshots)", "org.apache.maven.its.dependency:test:1.0-SNAPSHOT", "/list-repositories-location/pom.xml" )
assertDeclared( buildLog, "parent-repository (http://localhost:2346, default, releases+snapshots)", "org.apache.maven.its.dependency:test-parent:1.0-SNAPSHOT", "/list-repositories-location/parent/pom.xml" )
assertDeclared( buildLog, "parent-plugin-repository (http://localhost:3457, default, releases+snapshots)", "org.apache.maven.its.dependency:test-parent:1.0-SNAPSHOT", "/list-repositories-location/parent/pom.xml" )

if ( !mavenVersion.startsWith( '4.' ) )
{
    // Maven 4 drops central from the super POM model
    List<String> lines = buildLog.readLines()
    int central = lines.findIndexOf { it.contains( '* central (https://repo.maven.apache.org/maven2' ) }
    assert central >= 0 : 'central not listed'
    assert lines[central + 1].endsWith( '   declared in the Maven super POM' ) : lines[central + 1]
}
