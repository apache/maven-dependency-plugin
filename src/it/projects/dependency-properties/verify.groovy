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

File projectProperties = new File( basedir, "target/project.properties" );

Properties props = new Properties();
props.load( new FileInputStream( projectProperties ) );

String fixtureJarPath = props.getProperty( "org.apache.maven.its.dependency:unpack-fixture:jar" );
assert fixtureJarPath != null : "org.apache.maven.its.dependency:unpack-fixture:jar is null";
assert (new File( fixtureJarPath )).isFile() : "unpack-fixture jar is not a file: " + fixtureJarPath;

String mavenArtifactPath = props.getProperty( "org.apache.maven:maven-artifact:jar" );
assert mavenArtifactPath != null : "org.apache.maven:maven-artifact:jar is null";
assert (new File( mavenArtifactPath )).isFile() : "maven-artifact jar is not a file: " + mavenArtifactPath;

// bound through the mdep.properties.includeVersions user property
assert "1.0".equals( props.getProperty( "org.apache.maven.its.dependency:unpack-fixture:jar.version" ) ) : "unpack-fixture version: " + props.getProperty( "org.apache.maven.its.dependency:unpack-fixture:jar.version" )
assert "2.0.9".equals( props.getProperty( "org.apache.maven:maven-artifact:jar.version" ) ) : "maven-artifact version: " + props.getProperty( "org.apache.maven:maven-artifact:jar.version" )

// transitive dependency of maven-artifact 2.0.9
String plexusUtilsVersion = props.getProperty( "org.codehaus.plexus:plexus-utils:jar.version" )
assert "1.5.1".equals( plexusUtilsVersion ) : "plexus-utils version: " + plexusUtilsVersion

// the version property is visible to the configuration of a plugin running after the goal
File interpolated = new File( basedir, "target/interpolated-2.0.9.properties" )
assert interpolated.isFile() : "version property not interpolated into a later plugin configuration"

return true;
