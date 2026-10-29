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

// MDEP-510: the second execution runs after the jar already exists in the output directory
// and must still add the POM next to it.
def artifactDir = new File(basedir, 'target/deps/org/apache/maven/its/dependency/mdep510-dep/1.0')
assert new File(artifactDir, 'mdep510-dep-1.0.jar').isFile() : "Missing jar in ${artifactDir}"
assert new File(artifactDir, 'mdep510-dep-1.0.pom').isFile() : "Missing pom in ${artifactDir}"
return true
