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

// The goals ran without any lifecycle phase, so the sibling modules must still be unbuilt.
assert !new File( basedir, 'provided-module/target/provided-module-1.0-SNAPSHOT.jar' ).exists()
assert !new File( basedir, 'test-module/target/test-module-1.0-SNAPSHOT.jar' ).exists()

// The selected runtime dependency is copied, the provided and test siblings are not.
File copied = new File( basedir, 'consumer/target/dependency/ResourceArtifact-1.0.jar' )
assert copied.isFile() : "Missing $copied"
assert !new File( basedir, 'consumer/target/dependency/provided-module-1.0-SNAPSHOT.jar' ).exists()
assert !new File( basedir, 'consumer/target/dependency/test-module-1.0-SNAPSHOT.jar' ).exists()

// ... and unpacked.
File unpacked = new File( basedir, 'consumer/target/dependency/resource1.txt' )
assert unpacked.isFile() : "Missing $unpacked"

return true
