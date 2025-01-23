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

// Make sure non-exluded explicit dependencies are resolved
assert buildLog.contains( 'Resolved dependency: slf4j-simple-2.0.13.jar' )
assert buildLog.contains( 'Resolved dependency: slf4j-api-2.0.13.jar' )

// Did group excludes work?
assert !buildLog.contains( 'Resolving artifact skip.this.groupid' )

// Did artifact excludes work?
assert !buildLog.contains( 'Resolving artifact org.junit.jupiter:skip-this-artifact' )

// Did scope exludes work?
assert !buildLog.contains( 'Resolving artifact ch.qos.logback:logback-classic' )

// Did type excludes work?
assert !buildLog.contains( 'Resolving artifact ch.qos.logback:logback-examples' )

// Did classifier excludes work?
assert !buildLog.contains( 'Resolving artifact ch.qos.logback:logback-core:jar:skipThisClassifierToo' )
assert !buildLog.contains( 'Resolving artifact ch.qos.logback:logback-core' )

assert !buildLog.contains( 'Resolving artifact ch.qos.logback:logback-access:jar:skipThisClassifier' )
assert !buildLog.contains( 'Resolving artifact ch.qos.logback:logback-access' )



return true
