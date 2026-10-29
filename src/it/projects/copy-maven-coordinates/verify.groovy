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

def names = { String dir -> new File(basedir, dir).list().toList() }

assertThat(names("target/type-version")).containsExactly("ResourceArtifact-1.0.jar")
assertThat(names("target/type-classifier-version-scope")).containsExactly("ResourceArtifact-1.0-tests.jar")
assertThat(names("target/legacy")).containsExactly("ResourceArtifact-1.0-client.jar")

return true
