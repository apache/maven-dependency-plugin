import java.lang.reflect.Array

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


static void checkMessagedInLogs(logLines, messages) {
    def index = logLines.indexOf(messages[0])
    assert index > 0: "no messages: '" + messages[0] + "' in log"

    def logMessages = logLines[index..index + messages.size() - 1]

    assert logMessages == messages

}

def file = new File(basedir, "build.log");
assert file.exists();

def logLines = buildLog = file.readLines()

checkMessagedInLogs(logLines, [
        '[WARNING] Test defines following unnecessary excludes',
        '[WARNING]     org.apache.maven.its.dependency:a-with-dep:1.0.0',
        '[WARNING]         - org.apache.maven.its.dependency:invalid-exclusion1 @ line: 52'
])

checkMessagedInLogs(logLines, [
        '[WARNING] test-module1 defines following unnecessary excludes',
        '[WARNING]     org.apache.maven.its.dependency:a-with-dep:1.0.0',
        '[WARNING]         - org.apache.maven.its.dependency:invalid-exclusion2 @ line: 40',
        '[WARNING]         - org.apache.maven.its.dependency:invalid-exclusion3 @ line: 48'

])

assert logLines.count('[INFO] No problems with dependencies exclusions') == 1
