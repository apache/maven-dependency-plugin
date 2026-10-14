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

def verbose = new JsonSlurper().parse(new File(basedir, "target/verbose.json"))

// scope-root -> scope-leaf:1.0 is the node Maven keeps
def kept = verbose.children[0].children[0]
assertThat(kept.version).isEqualTo("1.0")
assertThat(kept.included).isEqualTo("true")
assertThat(kept).doesNotContainKey("omittedReason")

// scope-test-root -> scope-leaf:2.0 loses to 1.0
def omitted = verbose.children[1].children[0]
assertThat(omitted.version).isEqualTo("2.0")
assertThat(omitted.included).isEqualTo("false")
assertThat(omitted.omittedReason).isEqualTo("conflict")
assertThat(omitted.winnerVersion).isEqualTo("1.0")

// provided-root -> scope-leaf:1.0 repeats the kept node
def duplicate = verbose.children[2].children[0]
assertThat(duplicate.version).isEqualTo("1.0")
assertThat(duplicate.included).isEqualTo("false")
assertThat(duplicate.omittedReason).isEqualTo("duplicate")
assertThat(duplicate).doesNotContainKey("winnerVersion")

// the direct dependencies are included
assertThat(verbose.children*.included).containsOnly("true")

// without verbose the JSON has none of the additional fields
def plain = new File(basedir, "target/plain.json").text
assertThat(plain).doesNotContain("included").doesNotContain("omittedReason").doesNotContain("winnerVersion")

return true
