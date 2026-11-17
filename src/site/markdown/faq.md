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
2. [When executing `mvn dependency:unpack` or `dependency:copy` from the command line, I get &quot;Either artifact or artifactItems is required&quot;](#cli)
3. [Why am I getting errors that a documented goal or parameter is missing?](#missing)
4. [Why is Maven resolving &quot;dependency:xxx&quot; to the older org.codehaus.mojo:dependency-maven-plugin?](#question)
5. [Why am I having trouble unpacking only a specific file?](#includes)
6. [How do I keep files that already exist in the output directory when unpacking?](#overwritefiles)
7. [How do I make unpacking fail when two archives contain the same file?](#failonduplicates)
8. [Why does my dependency analysis report `Unused declared dependencies`?](#unused)

<a id="plugin_name"></a>

### What is the difference between dependency-maven-plugin and maven-dependency-plugin?

They are the same plugin. It started as `org.codehaus.mojo:dependency-maven-plugin` and moved to Apache Maven as
`org.apache.maven.plugins:maven-dependency-plugin`. The Codehaus version is no longer maintained; use the
maven-dependency-plugin.

<a id="cli"></a>

### When executing `mvn dependency:unpack` or `dependency:copy` from the command line, I get &quot;Either artifact or artifactItems is required&quot;

Both goals need to know which artifacts to process. For a single artifact, pass its coordinates on the command line:

```shell
mvn dependency:copy -Dartifact=org.apache.commons:commons-collections4:4.4 -DoutputDirectory=target/libs
```

For several artifacts, configure the `artifactItems` as shown in
[Copying from the command line](examples/copying-artifacts.html#Copying_from_the_command_line). When a goal is run
from the command line, the `configuration` element must be outside the `executions` element; a configuration inside
an execution applies only to that execution.

<a id="missing"></a>

### Why am I getting errors that a documented goal or parameter is missing?

The documentation can be published before the release that contains the goal or parameter, and each goal and parameter
page shows the version it was added in. Check that your build uses that version or a later one, for example with
`mvn dependency:help`, which prints the version of the plugin that Maven resolves.

<a id="question"></a>

### Why is Maven resolving &quot;dependency:xxx&quot; to the older org.codehaus.mojo:dependency-maven-plugin?

The `dependency` prefix belongs to the maven-dependency-plugin, and the current Maven versions resolve it to the Apache
plugin. If an old plugin is still used, its metadata is probably left over in your local repository or in a repository
manager. Delete `org/codehaus/mojo/maven-metadata-*.xml` from there, or call the plugin by its full coordinates, for
example `mvn org.apache.maven.plugins:maven-dependency-plugin:<version>:unpack`.

<a id="includes"></a>

### Why am I having trouble unpacking only a specific file?

The excludes will override the includes declaration. That means if you specify excludes=\*\*/\* ,includes=\*\*/foo,
you will exclude everything. If you only want foo, then just specify the includes. The plexus component used to
unpack uses the following code to determine which files to unpack:
`return isIncluded( name ) AND !isExcluded( name );`

When excludes are set, the default excludes of the file selector (for example `.svn`, `CVS` and `.git`) apply as well.
Set `useDefaultExcludes` to `false` to unpack those files too.

<a id="overwritefiles"></a>

### How do I keep files that already exist in the output directory when unpacking?

Set `overWriteFiles` (user property `mdep.unpack.overWriteFiles`) to `false` on `unpack` or `unpack-dependencies`.
Each entry of the archive is then written only if no file exists at its target path yet, after any `fileMappers` are
applied. An existing file is never replaced, whatever its timestamp, so a resource that was filtered into the output
directory stays as it is. For `unpack`, the parameter can also be set on a single `artifactItem`, where it overrides
the goal's value in either direction. It has no effect on single-file compression formats such as gzip.

This is a per-file rule. `overWriteReleases`, `overWriteSnapshots`, `overWriteIfNewer` and the `overWrite` of an
`artifactItem` work per artifact: together with the marker files they decide whether an artifact is unpacked at all.

<a id="failonduplicates"></a>

### How do I make unpacking fail when two archives contain the same file?

Set `failOnDuplicates` (user property `mdep.unpack.failOnDuplicates`) to `true` on `unpack` or `unpack-dependencies`.
When several artifacts are unpacked into one directory, a file that two of them contain is otherwise silently replaced
by the one unpacked last. With the option set, the build fails after all artifacts are processed and names each path
with the entries that target it. The comparison is made after any `fileMappers` are applied, so it also finds entries
that a mapper moves to the same path, and a second entry for one path within a single archive. Directories are not
compared.

Every jar contains `META-INF/MANIFEST.MF`, and many contain `module-info.class` and `META-INF/LICENSE` or `NOTICE`, so
`unpack-dependencies` on several jars reports those. Exclude them, for example with
`<excludes>META-INF/**,module-info.class</excludes>`. Names that differ only by case, such as `A.txt` and `a.txt`, are
also found on file systems that ignore case (macOS and Windows by default), where they would replace each other, and
so are paths that reach the output directory through a symbolic link.

Only the first entry for a path is written; later ones are skipped, also when `overWriteFiles` is `false`. The marker
files are not written for a failed execution, so running it again unpacks again and fails again. Files that already
exist in the output directory before the execution are not duplicates: use `overWriteFiles` to decide about those.
Artifacts that are skipped because they are already unpacked are not compared, and the option has no effect on
single-file compression formats such as gzip.

<a id="unused"></a>

### Why does my dependency analysis report `Unused declared dependencies`?

By default, dependency analysis is done at bytecode level: anything that doesn't get into bytecode isn't
detected. This is the case, for example, of constants, annotations with source retention policy, or javadoc
links.

If the only use of a dependency consists of such undetected constructs, the dependency is analyzed as unused.
To count such a dependency as used, list it in the `usedDependencies` parameter. See
[Exclude dependencies from dependency analysis](examples/exclude-dependencies-from-dependency-analysis.html) for
more ways to handle these dependencies.
