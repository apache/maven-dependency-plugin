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

// Align should detect that all 3 existing deps use .version property convention
// and create a guava.version property for the new dependency
assert pomText.contains( '<guava.version>33.0.0-jre</guava.version>' ) :
    "Should have created guava.version property (matching existing .version suffix convention)"
assert pomText.contains( '${guava.version}' ) :
    "Dependency version should reference \${guava.version}"

// Align should NOT use managed dependencies (all existing deps have explicit versions)
def managedDeps = pom.dependencyManagement.dependencies.dependency
assert managedDeps.isEmpty() :
    "Should NOT have created dependencyManagement (existing deps all have versions)"

// Verify the dependency was added to regular dependencies
def dep = pom.dependencies.dependency.find { it.artifactId == 'guava' }
assert dep != null : "guava should be in dependencies"

// Verify existing deps are preserved
assert pom.dependencies.dependency.size() == 4 : "Should have 4 dependencies total"
assert pomText.contains( '${junit.version}' ) : "Existing junit property ref preserved"
assert pomText.contains( '${slf4j.version}' ) : "Existing slf4j property ref preserved"
assert pomText.contains( '${commons-io.version}' ) : "Existing commons-io property ref preserved"

return true
