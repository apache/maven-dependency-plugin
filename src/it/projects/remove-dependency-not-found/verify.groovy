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

// Build is expected to fail
def buildLog = new File( basedir, "build.log" ).text
assert buildLog.contains( 'not found in' ) : "Should report dependency not found"

// POM should be unchanged
def pom = new groovy.xml.XmlSlurper().parse( new File( basedir, "pom.xml" ) )
def deps = pom.dependencies.dependency
assert deps.size() == 1 : "POM should still have exactly 1 dependency"
assert deps[0].artifactId == 'slf4j-api'

return true
