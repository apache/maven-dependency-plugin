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

File buildLog = new File( basedir, "build.log" );
String log = buildLog.getText( "UTF-8" );

int unused = log.indexOf( "Unused declared dependencies found in org.apache.maven.its.dependency:test:" );
assert unused >= 0 : "expected an unused declared dependencies warning in build.log";
String report = log.substring( unused );
assert report.contains( "org.apache.maven:maven-repository-metadata:jar:3.9.16:compile" ) :
    "the compile scoped dependency must still be reported";
assert !report.contains( "org.apache.maven:maven-artifact:jar:3.9.16:runtime" ) :
    "the runtime scoped dependency matched by the scope segment must be ignored";

return true;
