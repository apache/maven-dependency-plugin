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

import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

// Put the artifact and its .sha1 files straight into the local repository. A repository served by mrm does not
// leave checksum files in the local repository, and a checked-in .sha1 file cannot carry a license header.
def relative = 'org/apache/maven/its/dependency/mdep654-dep/1.0'
def remote = new File(localRepositoryPath, relative)
remote.deleteDir()
remote.mkdirs()

def jar = new File(remote, 'mdep654-dep-1.0.jar')
new JarOutputStream(new FileOutputStream(jar)).withCloseable { out ->
    out.putNextEntry(new JarEntry('mdep654.txt'))
    out.write('MDEP-654'.getBytes('UTF-8'))
    out.closeEntry()
}
def pom = new File(remote, 'mdep654-dep-1.0.pom')
pom.text = """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.apache.maven.its.dependency</groupId>
  <artifactId>mdep654-dep</artifactId>
  <version>1.0</version>
</project>
"""
[jar, pom].each { file ->
    new File(file.path + '.sha1').text = MessageDigest.getInstance('SHA-1').digest(file.bytes).encodeHex().toString()
}
return true
