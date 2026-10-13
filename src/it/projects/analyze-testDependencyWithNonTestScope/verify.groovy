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

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

File htmlFile = new File( basedir, "target/reports/dependency-analysis.html" )
assert htmlFile.isFile() : "Dependency analysis file missing " + htmlFile
assert htmlFile.length() > 0 : "Dependency analysis file empty " + htmlFile

Document doc = Jsoup.parse( htmlFile, "UTF-8" )
Elements entry = doc.select( "#bodyColumn > section > section:nth-child(5) > table > tbody > tr.b" )
assert entry.size() == 1 : "Table entry under CompileScopedTestDependencies header has " + entry.size() +
    " rows but exactly 1 was expected " + htmlFile

String groupId = entry.first().select( "td:nth-child(1)" ).text()
assert groupId == "junit" : "Table entry under CompileScopedTestDependencies header groupId is not junit. Value is " +
    groupId + " in " + htmlFile
