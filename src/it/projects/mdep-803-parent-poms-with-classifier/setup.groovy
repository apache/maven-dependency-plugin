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

import java.util.jar.JarOutputStream
import org.codehaus.plexus.util.FileUtils

// Install a self-contained fixture into the Invoker's local repository.
FileUtils.deleteDirectory(new File(localRepositoryPath, 'org/apache/maven/its/mdep803'))
def groupId = 'org.apache.maven.its.mdep803'
def artifactDir = { String artifactId ->
    def dir = new File(localRepositoryPath, "org/apache/maven/its/mdep803/${artifactId}/1.0")
    dir.mkdirs()
    dir
}
def pom = { String artifactId, String parent, String packaging ->
    def parentXml = parent == null ? '' : """
  <parent>
    <groupId>${groupId}</groupId><artifactId>${parent}</artifactId><version>1.0</version><relativePath/>
  </parent>"""
    new File(artifactDir(artifactId), "${artifactId}-1.0.pom").text = """<project>
  <modelVersion>4.0.0</modelVersion>${parentXml}
  <groupId>${groupId}</groupId><artifactId>${artifactId}</artifactId><version>1.0</version>
  <packaging>${packaging}</packaging>
</project>
"""
}
pom('grandparent', null, 'pom')
pom('project-parent', 'grandparent', 'pom')
pom('dependency-parent', 'grandparent', 'pom')
pom('library', 'dependency-parent', 'jar')
pom('classified', null, 'pom')

// Only explicit dependencies have sources attachments. Automatically added parents
// must be copied as ordinary POMs even when classifier or type is specified.
['library', 'grandparent', 'classified'].each { artifactId ->
    new JarOutputStream(new FileOutputStream(new File(artifactDir(artifactId), "${artifactId}-1.0-sources.jar"))).close()
}
new JarOutputStream(new FileOutputStream(new File(artifactDir('library'), 'library-1.0.jar'))).close()
['grandparent', 'classified'].each { artifactId ->
    new File(artifactDir(artifactId), "${artifactId}-1.0-sources.pom").text =
            new File(artifactDir(artifactId), "${artifactId}-1.0.pom").text.replace(
                    '</project>', '<description>sources attachment</description></project>')
}
return true
