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


def lines = new File(basedir, "target/resolved.txt").readLines()*.trim()

// the plugin, its dependency and the parent chains of both
[
    "org.apache.maven.its.dependency:resolve-plugins-plugin:jar:1.0",
    "org.apache.maven.its.dependency:resolve-plugins-dep:jar:1.0",
    "org.apache.maven.its.dependency:resolve-plugins-parent:pom:1.0",
    "org.apache.maven.its.dependency:resolve-plugins-grandparent:pom:1.0",
    "org.apache.maven.its.dependency:resolve-plugins-dep-parent:pom:1.0"
].each { expected ->
    assert lines.contains(expected) : "Expected '" + expected + "' in resolved plugins but got " + lines
}

return true
