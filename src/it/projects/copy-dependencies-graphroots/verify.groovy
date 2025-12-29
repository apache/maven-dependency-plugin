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

def targetFiles1 = ['a-with-dep-1.0.0.jar', 'b-with-dep-1.0.0.jar', 'c-without-dep-1.0.0.jar']
def directory1 = new File(basedir, 'target/it/copy-dep-test-1')

// Get only file names from the directory (excludes subdirectories)
def actualFiles1 = directory1.listFiles().findAll { it.isFile() }.collect { it.name }

// Check if the sets are identical and have exactly 3 files
assert (actualFiles1.size() == 3 && actualFiles1.containsAll(targetFiles1))


def targetFiles2 = ['get-artifact-1.0.jar', 'get-artifact-transitive-1.0.jar']
def directory2 = new File(basedir, 'target/it/copy-dep-test-2')

// Get only file names from the directory (excludes subdirectories)
def actualFiles2 = directory2.listFiles().findAll { it.isFile() }.collect { it.name }

// Check if the sets are identical and have exactly 3 files
assert (actualFiles2.size() == 2 && actualFiles2.containsAll(targetFiles2))
