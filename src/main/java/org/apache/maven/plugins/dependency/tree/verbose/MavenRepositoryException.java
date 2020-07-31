package org.apache.maven.plugins.dependency.tree.verbose;

/*
 *  Copyright 2020 Google LLC.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */


/**
 * Error interacting with a remote or local Maven repository such as artifact not found.
 */
public class MavenRepositoryException extends Exception
{

    MavenRepositoryException( Exception ex )
    {
        super( ex );
    }

}
