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

File classFile = new File( basedir, "target/classes/Main.class" )
assert classFile.exists()
assert classFile.isFile() : "Build was not forked, class missing " + classFile

File file = new File( basedir, "build.log" )
assert file.exists()

String buildLog = file.getText( "UTF-8" )
assert buildLog.contains( '[WARNING] Used undeclared dependencies found:')
assert buildLog.contains( '[WARNING]    org.apache.maven:maven-model:jar:3.6.3:compile')
assert buildLog.contains( '[WARNING] Unused declared dependencies found:')
assert buildLog.contains( '[WARNING]    org.apache.maven:maven-core:jar:3.6.3:compile')
