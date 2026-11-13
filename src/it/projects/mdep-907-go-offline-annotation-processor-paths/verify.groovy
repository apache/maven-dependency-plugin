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

def assertResolved(String artifactId, String version) {
    ['jar', 'pom'].each { ext ->
        File f = new File(localRepositoryPath, "org/apache/maven/its/dependency/${artifactId}/${version}/${artifactId}-${version}.${ext}")
        assert f.isFile() : "${f} was not resolved"
    }
}

// configured in the "explicit" execution, with transitive dependencies
assertResolved('mdep907-processor', '1.0')
assertResolved('mdep907-processor-dep', '1.0')
// not managed for this configuration, so the version the processor declares
assertResolved('mdep907-lib', '1.0')
// configured in an execution without a version: 1.1 can only come from dependencyManagement
assertResolved('mdep907-managed-processor', '1.1')
assert !new File(localRepositoryPath, 'org/apache/maven/its/dependency/mdep907-managed-processor/1.0').exists()
// annotationProcessorPathsUseDepMgmt manages the transitive lib of that execution to 2.0; both versions must land
assertResolved('mdep907-lib', '2.0')
// excluded by the path's exclusions
assert !new File(localRepositoryPath, 'org/apache/maven/its/dependency/mdep907-excluded-dep').exists()

String buildLog = new File(basedir, 'build.log').getText('UTF-8')
assert buildLog.contains('Resolved annotation processor path: mdep907-processor-1.0.jar')
assert buildLog.contains('Resolved annotation processor path: mdep907-managed-processor-1.1.jar')

return true
