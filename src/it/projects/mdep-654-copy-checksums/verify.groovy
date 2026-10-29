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

// MDEP-654: checksum files from the local repository are copied only when copyChecksums is set.
def relative = 'org/apache/maven/its/dependency/mdep654-dep/1.0'
def localRepo = new File(localRepositoryPath, relative)
assert new File(localRepo, 'mdep654-dep-1.0.jar.sha1').isFile() : "No jar checksum in the local repository ${localRepo}"

def withDir = new File(basedir, "target/with-checksums/${relative}")
def withoutDir = new File(basedir, "target/without-checksums/${relative}")
['mdep654-dep-1.0.jar', 'mdep654-dep-1.0.pom'].each { name ->
    assert new File(withDir, name).isFile()
    assert new File(withoutDir, name).isFile()
    assert new File(withDir, name + '.sha1').isFile() : "Missing ${name}.sha1 in ${withDir}"
    assert new File(withDir, name + '.sha1').text == new File(localRepo, name + '.sha1').text
    assert !new File(withoutDir, name + '.sha1').exists()
}
return true
