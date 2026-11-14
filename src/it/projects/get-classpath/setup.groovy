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
import org.codehaus.plexus.util.FileUtils;

// forces the download: every artifact of the IT is removed from the local repository first
File dir = new File( localRepositoryPath, "org/apache/maven/its/dependency" );
File[] artifacts = dir.listFiles( { File f -> f.getName().startsWith( "get-classpath-lib" ) } as FileFilter );
if ( artifacts != null )
{
    for ( File artifact : artifacts )
    {
        FileUtils.deleteDirectory( artifact );
    }
}

return true;
