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

def json = new JsonSlurper().parse(new File(basedir, "target/list.json"))
assertThat(json.keySet()).containsExactly("resolved", "skipped", "unresolved")
assertThat(json.resolved).hasSize(3)
assertThat(json.skipped).isEmpty()
assertThat(json.unresolved).isEmpty()
def byScope = json.resolved.collectEntries { [it.scope, it] }
assertThat(byScope.compile).containsEntry("artifactId", "ResourceArtifact").containsEntry("type", "jar").containsEntry("classifier", "")
assertThat(byScope.runtime).containsEntry("classifier", "client")
assertThat(byScope.test).containsEntry("type", "test-jar").containsEntry("classifier", "tests")

def pom = new File(basedir, "target/list.pom.xml").text.replace("\r\n", "\n")
assertThat(pom).isEqualTo("""<dependencies>
  <dependency>
    <groupId>org.apache.maven.its.dependency</groupId>
    <artifactId>ResourceArtifact</artifactId>
    <version>1.0</version>
  </dependency>
  <dependency>
    <groupId>org.apache.maven.its.dependency</groupId>
    <artifactId>ResourceArtifact</artifactId>
    <version>1.0</version>
    <classifier>client</classifier>
    <scope>runtime</scope>
  </dependency>
  <dependency>
    <groupId>org.apache.maven.its.dependency</groupId>
    <artifactId>ResourceArtifact</artifactId>
    <version>1.0</version>
    <type>test-jar</type>
    <classifier>tests</classifier>
    <scope>test</scope>
  </dependency>
</dependencies>
""")

// the default output is unchanged
def text = new File(basedir, "target/list.txt").text
assertThat(text).startsWith("The following files have been resolved:")
assertThat(text).contains("org.apache.maven.its.dependency:ResourceArtifact:jar:1.0:compile")

return true
