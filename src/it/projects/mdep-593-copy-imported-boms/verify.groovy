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

def base = 'org/apache/maven/its/mdep593'
def always = ["lib/1.0/lib-1.0.jar", "lib/1.0/lib-1.0.pom", "lib-parent/1.0/lib-parent-1.0.pom", "grandparent/1.0/grandparent-1.0.pom"]
def boms = ["bom-a/1.0/bom-a-1.0.pom",   // imported by lib-parent, a parent of the dependency, through ${bom.version}
            "bom-b/1.0/bom-b-1.0.pom",   // imported by bom-a
            "bom-parent/1.0/bom-parent-1.0.pom", // parent of bom-b
            "bom-c/1.0/bom-c-1.0.pom"]   // imported by the project itself

always.each { assert new File(basedir, "target/parents-only/${base}/${it}").isFile() : "Missing ${it} in parents-only" }
boms.each { assert !new File(basedir, "target/parents-only/${base}/${it}").exists() : "Unexpected ${it} in parents-only" }
(always + boms).each { assert new File(basedir, "target/with-boms/${base}/${it}").isFile() : "Missing ${it} in with-boms" }
return true
