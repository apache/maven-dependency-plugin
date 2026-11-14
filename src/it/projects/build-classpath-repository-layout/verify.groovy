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

import java.io.*;
import java.util.*;

def read = { String name -> new File( basedir, "module-b/target/" + name ).text.trim() }

// no prefix: the reactor module is its build output, the other artifact is in the local repository
def noPrefix = read( "no-prefix.txt" ).split( File.pathSeparator ) as List
assert noPrefix.size() == 2
assert noPrefix.any { it.replace( '\\', '/' ).endsWith( "/module-a/target/module-a-1.0-SNAPSHOT.jar" ) } : noPrefix
assert noPrefix.any { it.replace( '\\', '/' ).endsWith( "/junit/junit/3.8.1/junit-3.8.1.jar" ) } : noPrefix
assert noPrefix.every { !it.startsWith( "repository" ) } : noPrefix

// prefix without layout: file names only
assert read( "prefix-only.txt" ).split( File.pathSeparator ) as Set ==
    [ "repository" + File.separator + "module-a-1.0-SNAPSHOT.jar",
      "repository" + File.separator + "junit-3.8.1.jar" ] as Set

// prefix with layout: the same for the reactor module and the external artifact
assert read( "layout.txt" ).split( ":" ) as Set ==
    [ "repository/org/apache/maven/its/dependency/module-a/1.0-SNAPSHOT/module-a-1.0-SNAPSHOT.jar",
      "repository/junit/junit/3.8.1/junit-3.8.1.jar" ] as Set

return true
