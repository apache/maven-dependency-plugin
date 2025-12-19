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

File file = new File( basedir, "classpath.txt" )
assert file.exists() : "output file $file does not exist"
String output = file.getText( "UTF-8" )
assert output.startsWith( 'The following files have been resolved:')
// no escape codes
assert !output.contains( '\u001B' ) 

// expected output depends on whether we have a version of Java that supports modules or not
def javaVersion = System.getProperty("java.specification.version")
def majorVersion = (javaVersion.startsWith("1.") ? javaVersion.split("\\.")[1] : javaVersion).toInteger()

if (majorVersion >= 9) {
    assert output.contains('compile -- module') : "Output should contain module compilation info on Java 9+"
} else {
    assert output.contains( 'compile' ) 
}




