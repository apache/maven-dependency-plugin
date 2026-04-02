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

// Verify existing dependency is preserved
def junit = deps.find { it.groupId == 'junit' && it.artifactId == 'junit' }
assert junit != null : "Existing junit dependency should be preserved"
assert junit.version == '4.13.2'
assert junit.scope == 'test'

// Verify new dependency was added
def lang3 = deps.find { it.groupId == 'org.apache.commons' && it.artifactId == 'commons-lang3' }
assert lang3 != null : "commons-lang3 dependency should have been added"
assert lang3.version == '3.12.0'

// Verify build log
def buildLog = new File( basedir, "build.log" ).text
assert buildLog.contains( 'Added/updated org.apache.commons:commons-lang3:3.12.0' )

return true
