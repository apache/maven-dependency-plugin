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

// Read the raw POM text to verify the property reference (XmlSlurper resolves properties)
def pomText = new File( basedir, "pom.xml" ).text

// Verify property was created
assert pomText.contains( '<guava.version>33.0.0-jre</guava.version>' ) :
    "Property guava.version should be defined"

// Verify dependency version references the property
assert pomText.contains( '${guava.version}' ) :
    "Dependency version should reference \${guava.version}"

// Also verify via XML parsing that the dependency exists
def pom = new XmlSlurper().parse( new File( basedir, "pom.xml" ) )
def dep = pom.dependencies.dependency.find { it.artifactId == 'guava' }
assert dep != null : "guava dependency should have been added"

return true
