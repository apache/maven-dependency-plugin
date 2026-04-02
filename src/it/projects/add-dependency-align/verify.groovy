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

def pomText = new File( basedir, "pom.xml" ).text
def pom = new groovy.xml.XmlSlurper().parse( new File( basedir, "pom.xml" ) )

// Verify auto-detection created a version property following the .version convention
assert pomText.contains( '<guava.version>33.0.0-jre</guava.version>' ) :
    "Should have created guava.version property (matching existing .version suffix pattern)"

// Verify managed dependency was added with property reference
def managedDeps = pom.dependencyManagement.dependencies.dependency
def managedGuava = managedDeps.find { it.artifactId == 'guava' }
assert managedGuava != null : "guava should be in dependencyManagement"
assert pomText.contains( '${guava.version}' ) :
    "Managed dependency version should reference \${guava.version}"

// Verify version-less dependency was added to regular dependencies
def deps = pom.dependencies.dependency
def guava = deps.find { it.artifactId == 'guava' }
assert guava != null : "guava should be in dependencies"

// Verify the regular dependency is version-less (version comes from managed deps)
// In the raw XML, the dependency entry should NOT have a <version> element
def depsSection = pomText.substring( pomText.lastIndexOf( '<dependencies>' ) )
def guavaBlock = depsSection.substring( depsSection.indexOf( 'guava' ) )
guavaBlock = guavaBlock.substring( 0, guavaBlock.indexOf( '</dependency>' ) )
assert !guavaBlock.contains( '<version>' ) :
    "Regular dependency should be version-less (managed by dependencyManagement)"

// Verify existing dependencies are preserved
assert deps.find { it.artifactId == 'junit' } != null
assert deps.find { it.artifactId == 'slf4j-api' } != null
assert deps.find { it.artifactId == 'commons-io' } != null

return true
