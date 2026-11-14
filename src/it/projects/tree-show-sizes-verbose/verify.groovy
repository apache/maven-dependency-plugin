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

def jarSize = { String artifactId ->
    def jar = new File(localRepositoryPath, "org/apache/maven/its/dependency/${artifactId}/1.0/${artifactId}-1.0.jar")
    assertThat(jar).exists()
    return jar.length()
}
long leaf = jarSize("tree-sizes-leaf")
long root = jarSize("tree-sizes-root")
assertThat(leaf + root).isLessThan(1024L)

// the leaf is a direct dependency, so the one below tree-sizes-root is omitted: it shows no size, but the root
// still brings it in, and the project counts it once
def verbose = [
        "org.apache.maven.plugins.dependency.its:tree-show-sizes-verbose:jar:1.0-SNAPSHOT (total ${leaf + root} B)".toString(),
        "+- org.apache.maven.its.dependency:tree-sizes-root:jar:1.0:compile (${root} B, total ${root + leaf} B)".toString(),
        "|  +- (org.apache.maven.its.dependency:tree-sizes-leaf:jar:1.0:compile - omitted for duplicate)",
        "|  \\- org.apache.maven.its.dependency:tree-sizes-gone:jar:1.0:compile (size unknown)",
        "\\- org.apache.maven.its.dependency:tree-sizes-leaf:jar:1.0:compile (${leaf} B)".toString()]
assertThat(new File(basedir, "target/tree.txt").readLines()).containsExactlyElementsOf(verbose)

// the plain tree shows what Maven resolves, and the same totals: the root brings in the leaf kept elsewhere
assertThat(new File(basedir, "target/tree-plain.txt").readLines()).containsExactly(
        "org.apache.maven.plugins.dependency.its:tree-show-sizes-verbose:jar:1.0-SNAPSHOT (total ${leaf + root} B)".toString(),
        "+- org.apache.maven.its.dependency:tree-sizes-root:jar:1.0:compile (${root} B, total ${root + leaf} B)".toString(),
        "|  \\- org.apache.maven.its.dependency:tree-sizes-gone:jar:1.0:compile (size unknown)",
        "\\- org.apache.maven.its.dependency:tree-sizes-leaf:jar:1.0:compile (${leaf} B)".toString())

return true
