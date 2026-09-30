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

[ 'test-jar': 'tests', 'ejb-client': 'client', 'explicit-classifier': 'client' ].each { directory, classifier ->
    File output = new File( basedir, 'target/' + directory )
    assert new File( output, 'ResourceArtifact-1.0-' + classifier + '.jar' ).isFile()
    assert new File( output, classifier + '.txt' ).isFile()
    assert !new File( output, 'ResourceArtifact-1.0.jar' ).exists()
    assert !new File( output, 'resource1.txt' ).exists()
    if ( classifier == 'client' ) {
        assert !new File( output, 'tests.txt' ).exists()
    }
}

return true
