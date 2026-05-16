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

// Verify parent POM got the property with -version suffix and managed dependency
File parentPom = new File(basedir, "parent/pom.xml")
assert parentPom.exists()
def parentContent = parentPom.text
def parentXml = new groovy.xml.XmlSlurper().parseText(parentContent)

// Parent should have the a2-version property (dash convention)
assert parentXml.properties.'a2-version'.text() == '1.0.0'

// Parent should have the managed dependency with property reference
def managedDeps = parentXml.dependencyManagement.dependencies.dependency
def guavaManaged = managedDeps.find { it.artifactId.text() == 'a2' }
assert guavaManaged != null : "a2 should be in parent's dependencyManagement"
assert guavaManaged.groupId.text() == 'org.apache.maven.its.dependency'
assert guavaManaged.version.text() == '${a2-version}'

// Verify child POM got a version-less dependency
File childPom = new File(basedir, "pom.xml")
assert childPom.exists()
def childContent = childPom.text
def childXml = new groovy.xml.XmlSlurper().parseText(childContent)
def childDeps = childXml.dependencies.dependency
def guavaChild = childDeps.find { it.artifactId.text() == 'a2' }
assert guavaChild != null : "a2 should be in child's dependencies"
assert guavaChild.groupId.text() == 'org.apache.maven.its.dependency'
// Child should NOT have a version element
assert guavaChild.version.text() == '' : "child dependency should be version-less"
