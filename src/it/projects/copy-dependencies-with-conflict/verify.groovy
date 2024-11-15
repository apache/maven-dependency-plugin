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

File file = new File( basedir, "build.log" )
assert file.exists()

String buildLog = file.getText( "UTF-8" )
assert buildLog.contains( '[WARNING] Overwriting ' )
assert buildLog.contains( 'maven-dependency-plugin/target/it/copy-dependencies-with-conflict/target/it/copy-dep-test-2/jdom-1.1.3.jar' )
assert buildLog.contains( '[DEBUG] Copying artifact \'org.jdom:jdom:jar:1.1.3\'' )
assert buildLog.contains( '[DEBUG] Copying artifact \'org.jdom:jdom:pom:1.1.3\'' )
assert buildLog.contains( '[DEBUG] Copying artifact \'org.jdom:jdom:jar:1.1.3\'' )
assert buildLog.contains( '[DEBUG] Copying artifact \'org.lucee:jdom:jar:1.1.3\'' )
assert buildLog.contains( '[WARNING] Multiple files with the name jdom-1.1.3.jar in the dependency tree.' )

return true
