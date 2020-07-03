package org.apache.maven.plugins.dependency.testUtils;

/* 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.    
 */

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

public class DependencyTestUtils
{

    /**
     * Deletes a directory and its contents.
     *
     * @param dir {@link File} The base directory of the included and excluded files.
     * @throws IOException in case of an error. When a directory failed to get deleted.
     * @deprecated use Apache Commons FileUtils.deleteDirectory
     */
    @Deprecated
    public static void removeDirectory( File dir )
        throws IOException
    {
        if ( dir != null )
        {
            Log log = new SilentLog();
            FileSetManager fileSetManager = new FileSetManager( log, false );

            FileSet fs = new FileSet();
            fs.setDirectory( dir.getPath() );
            fs.addInclude( "**/**" );
            fileSetManager.delete( fs );

        }
    }

}
