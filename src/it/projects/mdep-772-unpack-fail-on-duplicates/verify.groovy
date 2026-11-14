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

// the first entry was written and the second was not; nothing is marked as unpacked, so a repeated run fails again
for ( dir in ["dependencies", "unpack"] )
{
    assert new File( basedir, "target/" + dir + "/same.txt" ).isFile() : dir + " should hold the first entry"
    assert !new File( basedir, "target/" + dir + "/resource1.txt" ).exists() : dir + " should hold the mapped name only"
    File markers = new File( basedir, "target/markers-" + dir )
    assert !markers.exists() || markers.list().length == 0 : dir + " should not be marked as unpacked"
}

return true
