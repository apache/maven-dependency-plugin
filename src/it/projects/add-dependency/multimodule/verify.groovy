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

// Verify parent POM got the property and managed dependency
File parentPom = new File(basedir, "parent/pom.xml")
assert parentPom.exists()
def parentContent = parentPom.text

// Parent should have the guava.version property
assert parentContent.contains('<guava.version>33.0.0-jre</guava.version>')

// Parent should have the managed dependency with property reference
def parentXml = new groovy.xml.XmlSlurper().parseText(parentContent)
def managedDeps = parentXml.dependencyManagement.dependencies.dependency
def guavaManaged = managedDeps.find { it.artifactId.text() == 'guava' }
assert guavaManaged != null : "guava should be in parent's dependencyManagement"
assert guavaManaged.groupId.text() == 'com.google.guava'
assert guavaManaged.version.text() == '${guava.version}'

// Verify child POM got a version-less dependency
File childPom = new File(basedir, "pom.xml")
assert childPom.exists()
def childContent = childPom.text
def childXml = new groovy.xml.XmlSlurper().parseText(childContent)
def childDeps = childXml.dependencies.dependency
def guavaChild = childDeps.find { it.artifactId.text() == 'guava' }
assert guavaChild != null : "guava should be in child's dependencies"
assert guavaChild.groupId.text() == 'com.google.guava'
// Child should NOT have a version element
assert guavaChild.version.text() == '' : "child dependency should be version-less"
