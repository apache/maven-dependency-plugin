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

import static org.assertj.core.api.Assertions.assertThat

assertThat(new File(basedir, "target/tree.txt").readLines()).containsExactly(
        "org.apache.maven.plugins.dependency.its:tree-show-sizes-off:jar:1.0-SNAPSHOT",
        "\\- org.apache.maven.its.dependency:tree-sizes-off:jar:1.0:compile")

// only the POM was read, the jar was not downloaded, also not for an output type that ignores showSizes
def dir = new File(localRepositoryPath, "org/apache/maven/its/dependency/tree-sizes-off/1.0")
assertThat(new File(dir, "tree-sizes-off-1.0.pom")).exists()
assertThat(new File(dir, "tree-sizes-off-1.0.jar")).doesNotExist()

return true
