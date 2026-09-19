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

def parents = ['project-parent-1.0.pom', 'dependency-parent-1.0.pom', 'grandparent-1.0.pom']
def dependencyPoms = ['library-1.0.pom', 'classified-1.0.pom']
def sources = ['library-1.0-sources.jar', 'grandparent-1.0-sources.pom', 'classified-1.0-sources.pom']

['sources', 'sources-jar', 'excluded-parent'].each { name ->
    def expected = parents + dependencyPoms + sources
    if (name == 'sources-jar') {
        expected = expected.collect { it.endsWith('-sources.pom') ? it.replace('-sources.pom', '-sources.jar') : it }
    }
    if (name == 'excluded-parent') {
        expected = expected - 'dependency-parent-1.0.pom'
    }
    def output = new File(basedir, "target/${name}")
    def actual = output.list().toList().sort()
    assert actual == expected.sort() : "Unexpected copied artifacts in ${name}: ${actual}; expected ${expected}"
}

// Explicit classified POMs must not be replaced with their primary POMs.
['grandparent', 'classified'].each { artifactId ->
    assert new File(basedir, "target/sources/${artifactId}-1.0-sources.pom").text.contains('sources attachment')
}

// Repository layout does not add a primary POM for an artifact whose type is already pom.
(parents + ['library-1.0.pom'] + sources).each { name ->
    def artifactId = name.substring(0, name.indexOf('-1.0'))
    assert new File(basedir, "target/repository-layout/org/apache/maven/its/mdep803/${artifactId}/1.0/${name}").isFile()
}
assert !new File(basedir, 'target/repository-layout/org/apache/maven/its/mdep803/project-parent/1.0/project-parent-1.0-sources.pom').exists()
return true
