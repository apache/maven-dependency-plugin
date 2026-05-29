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

// A single, leaf POM whose existing dependencies are version-less because their
// versions come from a BOM import in <dependencyManagement>. A versioned add must
// land in <dependencies> (not be redirected into this POM's <dependencyManagement>).
File pom = new File(basedir, "pom.xml")
assert pom.exists()
def xml = new groovy.xml.XmlSlurper().parseText(pom.text)

def dep = xml.dependencies.dependency.find { it.artifactId.text() == 'a1' }
assert dep != null : "a1 should be added to <dependencies>"
assert dep.groupId.text() == 'org.apache.maven.its.dependency'
assert dep.version.text() == '1.0.0'

def managed = xml.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == 'a1' }
assert managed == null || managed.size() == 0 : "a1 must NOT be added to <dependencyManagement>"
