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

// Maven 4 use transitive dependency manager
def expected1 = mavenVersion.startsWith('4.') ? "expected1-v4.txt" : "expected1.txt"
def expected2 = mavenVersion.startsWith('4.') ? "expected2-v4.txt" : "expected2.txt"

assertThat(new File(basedir, "target/tree1.txt"))
        .hasSameTextualContentAs(new File(basedir, expected1))

assertThat(new File(basedir, "target/tree2.txt"))
        .hasSameTextualContentAs(new File(basedir, expected2))

return true