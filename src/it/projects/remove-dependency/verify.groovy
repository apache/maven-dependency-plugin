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

def pom = new groovy.xml.XmlSlurper().parse( new File( basedir, "pom.xml" ) )
def deps = pom.dependencies.dependency

// Verify junit was removed
def junit = deps.find { it.groupId == 'junit' && it.artifactId == 'junit' }
assert junit.isEmpty() : "junit dependency should have been removed"

// Verify slf4j-api is still there
def slf4j = deps.find { it.groupId == 'org.slf4j' && it.artifactId == 'slf4j-api' }
assert slf4j != null : "slf4j-api dependency should be preserved"
assert slf4j.version == '2.0.9'

// Verify build log
def buildLog = new File( basedir, "build.log" ).text
assert buildLog.contains( 'Removed junit:junit' )

return true
