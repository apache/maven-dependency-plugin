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

assert !new File( basedir, 'provided-module/target/provided-module-1.0-SNAPSHOT.jar' ).exists()
assert !new File( basedir, 'test-module/target/test-module-1.0-SNAPSHOT.jar' ).exists()

// With includeScope=runtime the sources of the runtime dependency are resolved and the siblings are not touched.
String runtime = new File( basedir, 'consumer/target/resolved-runtime.txt' ).text
assert runtime.contains( 'org.apache.maven.its.dependency:ResourceArtifact:jar:sources:1.0' ) : runtime
assert !runtime.contains( 'provided-module' ) : runtime
assert !runtime.contains( 'test-module' ) : runtime

// Without a scope filter the unbuilt siblings have no sources to resolve: they are reported, the build still succeeds.
String all = new File( basedir, 'consumer/target/resolved-all.txt' ).text
assert all.contains( 'org.apache.maven.its.dependency:ResourceArtifact:jar:sources:1.0' ) : all

return true
