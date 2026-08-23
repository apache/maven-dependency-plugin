---
title: Frequently Asked Questions
---

<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

<a id="top"></a>

# Frequently Asked Questions

1. [What is the difference between dependency-maven-plugin and maven-dependency-plugin?](#plugin_name)
2. [When executing `mvn dependency:unpack` or `dependency:copy` from the command line, I get &quot;One or more required plugin parameters are invalid/missing for &apos;dependency:unpack&apos;&quot;](#cli)
3. [Why am I getting errors that a documented goal or parameter is missing?](#missing)
4. [Why is Maven resolving &quot;dependency:xxx&quot; to the older org.codehaus.mojo:dependency-maven-plugin?](#question)
5. [Why am I having trouble unpacking only a specific file?](#includes)
6. [Why does my dependency analysis report `Unused declared dependencies`?](#unused)

<a id="plugin_name"></a>

### What is the difference between dependency-maven-plugin and maven-dependency-plugin?

Actually, they are the same, it's just that it was moved and renamed. The dependency-maven-plugin is hosted at
Mojo while maven-dependency-plugin is hosted at Apache. The recommended plugin to use is the
maven-dependency-plugin.

<a id="cli"></a>

### When executing `mvn dependency:unpack` or `dependency:copy` from the command line, I get &quot;One or more required plugin parameters are invalid/missing for &apos;dependency:unpack&apos;&quot;

In order for this to work, you must configure the ArtifactItems as shown
[here](examples/copying-artifacts.html#Copying_from_the_command_line). Note that when executing a plugin from
the command line, you must put the configuration tag outside of the executions.

If you haven't done this correctly, the error will look like this:

```
[0] inside the definition for plugin: 'maven-dependency-plugin', specify the following:
```

<a id="missing"></a>

### Why am I getting errors that a documented goal or parameter is missing?

The latest documents are published and may preceed the actual release. Check to make sure the goal/parameter is
in the most recent version. **-OR-** Maven may be resolving the older codehaus version of the dependency plugin.
See next question.

<a id="question"></a>

### Why is Maven resolving &quot;dependency:xxx&quot; to the older org.codehaus.mojo:dependency-maven-plugin?

Due to a bug in Maven in versions prior to 2.0.7
([MNG-2926](https://issues.apache.org/jira/browse/MNG-2926)), the search order was reversed and caused Mojo
plugins to supercede ones with the same prefix at Apache. The metadata at Mojo was cleaned up when the
maven-dependency-plugin was released at Apache. If you are still experiencing this error, chances are you have
old metadata in your local repository or in a proxy / internal repository. Removing
`/org/codehaus/mojo/maven-metadata.*` from your repo/proxy will cause it to be refreshed. Alternatively, you can
specify the groupId explicitely in your pom (if you are using a bound goal), or on the command line, use
groupId:artifactId:version:mojo, ie `mvn org.apache.maven.plugins:maven-dependency-plugin:2.5:unpack`

<a id="includes"></a>

### Why am I having trouble unpacking only a specific file?

The excludes will override the includes declaration. That means if you specify excludes=\*\*/\* ,includes=\*\*/foo,
you will exclude everything. If you only want foo, then just specify the includes. The plexus component used to
unpack uses the following code to determine which files to unpack:
`return isIncluded( name ) AND !isExcluded( name );`

<a id="unused"></a>

### Why does my dependency analysis report `Unused declared dependencies`?

By default, dependency analysis is done at bytecode level: anything that doesn't get into bytecode isn't
detected. This is the case, for example, of constants, annotations with source retention policy, or javadoc
links.

If the only use of a dependency consists of such undetected constructs, the dependency is analyzed as unused.
Since 2.6, you can force use report with `usedDependencies` parameter.
