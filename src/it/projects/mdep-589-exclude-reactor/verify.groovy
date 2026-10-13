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

File all = new File( basedir, 'child-b/target/default' )
assert new File( all, 'child-a-1.0-SNAPSHOT.jar' ).isFile()
assert new File( all, 'ResourceArtifact-1.0.jar' ).isFile()

File withoutReactor = new File( basedir, 'child-b/target/exclude-reactor' )
assert !new File( withoutReactor, 'child-a-1.0-SNAPSHOT.jar' ).exists()
assert new File( withoutReactor, 'ResourceArtifact-1.0.jar' ).isFile()

return true
