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

import groovy.json.JsonSlurper

import static org.assertj.core.api.Assertions.assertThat

// the mock repository serves a stub jar for each artifact with a POM; tree-sizes-gone has neither, so its size is unknown
def jarSize = { String artifactId ->
    def jar = new File(localRepositoryPath, "org/apache/maven/its/dependency/${artifactId}/1.0/${artifactId}-1.0.jar")
    assertThat(jar).exists()
    return jar.length()
}
long leaf = jarSize("tree-sizes-leaf")
long root = jarSize("tree-sizes-root")
long system = new File(basedir, "lib/system.jar").length()
assertThat(system).isEqualTo(100L)
long total = leaf + root + system
// the sizes are formatted as bytes below 1 KB
assertThat(total).isLessThan(1024L)

assertThat(new File(basedir, "target/tree.txt").readLines()).containsExactly(
        "org.apache.maven.plugins.dependency.its:tree-show-sizes:jar:1.0-SNAPSHOT (total ${total} B)".toString(),
        "+- org.apache.maven.its.dependency:tree-sizes-root:jar:1.0:compile (${root} B, total ${root + leaf} B)".toString(),
        "|  +- org.apache.maven.its.dependency:tree-sizes-leaf:jar:1.0:compile (${leaf} B)".toString(),
        "|  \\- org.apache.maven.its.dependency:tree-sizes-gone:jar:1.0:compile (size unknown)",
        "\\- org.apache.maven.its.dependency:tree-sizes-system:jar:1.0:system (${system} B)".toString())

def json = new JsonSlurper().parse(new File(basedir, "target/tree.json"))
assertThat(json).doesNotContainKey("size")
assertThat(json.totalSize).isEqualTo(total)
def node = json.children[0]
assertThat(node.size).isEqualTo(root)
assertThat(node.totalSize).isEqualTo(root + leaf)
assertThat(json.children[1].size).isEqualTo(system)
assertThat(node.children[0].size).isEqualTo(leaf)
assertThat(node.children[0].totalSize).isEqualTo(leaf)
assertThat(node.children[1]).doesNotContainKey("size")
assertThat(node.children[1].totalSize).isEqualTo(0)

// the DOT output ignores the parameter
def dot = new File(basedir, "target/tree.dot").text
assertThat(dot).contains("tree-sizes-root").doesNotContain("KB").doesNotContain("total")

return true
