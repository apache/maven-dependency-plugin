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

def pom = new XmlSlurper().parse( new File( basedir, "pom.xml" ) )

// Verify dependency was added to dependencyManagement
def managedDeps = pom.dependencyManagement.dependencies.dependency
def lang3 = managedDeps.find { it.groupId == 'org.apache.commons' && it.artifactId == 'commons-lang3' }
assert lang3 != null : "commons-lang3 should be in dependencyManagement"
assert lang3.version == '3.12.0'

// Verify it was NOT added to regular dependencies
def regularDeps = pom.dependencies.dependency
def regularLang3 = regularDeps.find { it.groupId == 'org.apache.commons' && it.artifactId == 'commons-lang3' }
assert regularLang3.isEmpty() : "commons-lang3 should NOT be in regular dependencies"

return true
