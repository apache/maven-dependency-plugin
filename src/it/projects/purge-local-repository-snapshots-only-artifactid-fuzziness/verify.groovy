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

String groupPath = "org/apache/maven/its/dependency/"

void checkAbsence( String path )
{
    File file = new File( localRepositoryPath, path )
    assert !file.exists() : "Not purged: " + file
}

void checkPresence( String path )
{
    File file = new File( localRepositoryPath, path )
    assert file.exists() : "Purged although not a snapshot: " + file
}

// every snapshot of the artifact is deleted, the releases of the artifact and the sibling artifact are kept
checkAbsence( groupPath + "snap-purged/1.0-SNAPSHOT" )
checkAbsence( groupPath + "snap-purged/2.0-SNAPSHOT" )
checkPresence( groupPath + "snap-purged/1.0/snap-purged-1.0.jar" )
checkPresence( groupPath + "sibling/1.0-SNAPSHOT/sibling-1.0-SNAPSHOT.jar" )
checkPresence( groupPath + "sibling/1.0/sibling-1.0.jar" )

return true
