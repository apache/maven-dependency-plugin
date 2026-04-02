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

// --- Verify parent POM was modified ---
def parentPomText = new File( basedir, "parent/pom.xml" ).text
def parentPom = new groovy.xml.XmlSlurper().parse( new File( basedir, "parent/pom.xml" ) )

// 1. Property should use dash-version convention (matching existing pattern)
assert parentPomText.contains( '<guava-version>33.0.0-jre</guava-version>' ) :
    "Parent should have guava-version property (dash convention)"

// 2. Managed dependency should reference property with dash convention
def managedDeps = parentPom.dependencyManagement.dependencies.dependency
def managedGuava = managedDeps.find { it.artifactId == 'guava' }
assert managedGuava != null : "guava should be in parent's dependencyManagement"
assert parentPomText.contains( '${guava-version}' ) :
    "Parent managed dependency version should reference \${guava-version}"

// 3. Existing managed dependencies should be preserved
assert managedDeps.find { it.artifactId == 'junit' } != null : "junit should still be managed"
assert managedDeps.find { it.artifactId == 'slf4j-api' } != null : "slf4j-api should still be managed"
assert managedDeps.find { it.artifactId == 'commons-io' } != null : "commons-io should still be managed"
assert parentPomText.contains( '${junit-version}' ) : "Existing junit-version property ref preserved"
assert parentPomText.contains( '${slf4j-api-version}' ) : "Existing slf4j-api-version property ref preserved"

// --- Verify child POM was modified ---
def childPomText = new File( basedir, "pom.xml" ).text
def childPom = new groovy.xml.XmlSlurper().parse( new File( basedir, "pom.xml" ) )

// 4. Version-less dependency should have been added to child
def childDeps = childPom.dependencies.dependency
def childGuava = childDeps.find { it.artifactId == 'guava' }
assert childGuava != null : "guava should be in child dependencies"

// 5. The child dependency should be version-less
def childDepsSection = childPomText.substring( childPomText.lastIndexOf( '<dependencies>' ) )
def guavaBlock = childDepsSection.substring( childDepsSection.indexOf( 'guava' ) )
guavaBlock = guavaBlock.substring( 0, guavaBlock.indexOf( '</dependency>' ) )
assert !guavaBlock.contains( '<version>' ) :
    "Child dependency should be version-less (managed by parent)"

// 6. Existing child dependencies should be preserved
assert childDeps.size() == 4 : "Child should now have 4 dependencies"

return true
