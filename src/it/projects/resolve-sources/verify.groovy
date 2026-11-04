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
def lines = new File(basedir, "target/resolved.txt").readLines()*.trim()
def notResolved = lines.findIndexOf { it.startsWith("The following files have NOT been resolved") }
def resolved = notResolved < 0 ? lines : lines.subList(0, notResolved)
def missing = notResolved < 0 ? [] : lines.subList(notResolved, lines.size())

assert lines[0] == "The following files have been resolved:" : "Unexpected header in " + lines
assert resolved.any { it.startsWith("org.apache.maven:maven-project:jar:sources:2.0.8") } : "Expected the sources of the dependency in " + lines
assert resolved.any { it.startsWith("org.apache.maven:maven-model:jar:sources:2.0.8") } : "Expected the sources of a transitive dependency in " + lines
// some old dependencies have no sources (which ones differs between Maven 3 and 4); they are listed instead of failing the build
def missingSources = missing.drop(1).findAll { it }
assert !missingSources.isEmpty() : "Expected the missing sources to be listed in " + lines
assert missingSources.every { it.contains(":jar:sources:") } : "Expected only sources jars among the missing in " + lines
