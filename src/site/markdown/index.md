---
title: Introduction
author: 
  - Allan Ramirez
  - Brian Fox
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

# Apache Maven Dependency Plugin

The Dependency Plugin works with a project's dependencies and with artifacts in repositories. It shows where each
dependency comes from, reports dependencies that the code uses without declaring them or declares without using them,
copies or unpacks artifacts to a directory, builds class paths, and edits the dependencies of a `pom.xml` from the
command line.

The plugin requires Maven 3.9.0 or later and Java 8 or later.

## Common tasks

Find out why a dependency is on the class path. The tree shows every path that leads to the artifact:

```shell
mvn dependency:tree -Dincludes=org.apache.commons:commons-lang3
```

```text
[INFO] org.example:app:jar:1.0
[INFO] \- org.apache.commons:commons-text:jar:1.12.0:compile
[INFO]    \- org.apache.commons:commons-lang3:jar:3.14.0:compile
```

Check the declared dependencies against the classes that the compiled code references:

```shell
mvn dependency:analyze
```

```text
[WARNING] Used undeclared dependencies found: (in org.example:app)
[WARNING]    org.apache.commons:commons-lang3:jar:3.14.0:compile
[WARNING] Unused declared dependencies found: (in org.example:app)
[WARNING]    commons-io:commons-io:jar:2.16.1:compile
```

The analysis works on bytecode, so it cannot see some uses of a dependency; see
[Exclude dependencies from dependency analysis](./examples/exclude-dependencies-from-dependency-analysis.html).

Copy the runtime dependencies to `target/dependency`, for example to ship them next to the application:

```shell
mvn dependency:copy-dependencies -DincludeScope=runtime
```

Download an artifact and its dependencies into the local repository, without a project:

```shell
mvn dependency:get -Dartifact=org.apache.commons:commons-collections4:4.4
```

## Goals Overview

The plugin has the following goals.

### Inspecting dependencies

- [dependency:tree](./tree-mojo.html) displays the dependency tree of the project, as text or in the JSON, DOT,
  GraphML, TGF or Mermaid format. See [Filtering the dependency tree](./examples/filtering-the-dependency-tree.html)
  and [Dependency tree output formats](./examples/tree-mojo.html).
- [dependency:list](./list-mojo.html) lists the resolved dependencies of the project. It is an alias for `resolve`.
- [dependency:resolve](./resolve-mojo.html) resolves all dependencies of the project and displays their versions, and
  the Java module name of each one where it has one.
- [dependency:resolve-sources](./resolve-sources-mojo.html) resolves all dependencies of the project and their source
  attachments, and displays their versions.
- [dependency:resolve-plugins](./resolve-plugins-mojo.html) resolves the plugins of the project and their dependencies.
- [dependency:collect](./collect-mojo.html) lists the dependencies of the project from their POM files only, without
  downloading the artifacts themselves, such as jar files.
- [dependency:list-repositories](./list-repositories-mojo.html) lists the repositories that the build and its
  transitive dependencies use.
- [dependency:list-classes](./list-classes-mojo.html) displays the fully qualified names of all classes in an
  artifact.
- [dependency:display-ancestors](./display-ancestors-mojo.html) displays all ancestor POMs of the project, for example
  for a continuous integration system that needs to know them.

### Analyzing dependencies

- [dependency:analyze](./analyze-mojo.html) analyzes the dependencies of the project and determines which are used and
  declared, used and undeclared, and unused and declared. It also reports compile scope dependencies that only the
  tests use. The goal runs the build up to `test-compile` first; to run the analysis as part of the build, bind
  `analyze-only` instead.
- [dependency:analyze-only](./analyze-only-mojo.html) is the same as `analyze`, but is meant to be bound in a POM. It
  does not fork the build and run `test-compile`. It is bound to the `verify` phase by default.
- [dependency:analyze-report](./analyze-report-mojo.html) analyzes the dependencies of the project and produces a
  report of the used and declared, used and undeclared, and unused and declared dependencies.
- [dependency:analyze-report-only](./analyze-report-only-mojo.html) is the same as `analyze-report`, but does not fork
  the build and run `test-compile`.
- [dependency:analyze-dep-mgt](./analyze-dep-mgt-mojo.html) lists mismatches between the resolved dependencies of the
  project and those listed in its `dependencyManagement` section.
- [dependency:analyze-duplicate](./analyze-duplicate-mojo.html) finds dependencies that are declared more than once in
  the `dependencies` and `dependencyManagement` sections of the `pom.xml`.
- [dependency:analyze-exclusions](./analyze-exclusions-mojo.html) checks whether each exclusion on a dependency
  actually removes an artifact that the dependency brings in.

### Copying and unpacking artifacts

- [dependency:copy](./copy-mojo.html) copies the artifacts listed in the plugin configuration to a directory,
  renaming them or stripping the version if desired. It resolves the artifacts from remote repositories if they are
  neither in the local repository nor in the reactor.
- [dependency:copy-dependencies](./copy-dependencies-mojo.html) copies the direct and, optionally, the transitive
  dependencies of the project to a directory, stripping the version if desired.
- [dependency:unpack](./unpack-mojo.html) is like `copy`, but unpacks the artifacts.
- [dependency:unpack-dependencies](./unpack-dependencies-mojo.html) is like `copy-dependencies`, but unpacks the
  artifacts.

### Building class paths and properties

- [dependency:build-classpath](./build-classpath-mojo.html) outputs the paths of the dependencies in the local
  repository in class path format, for use with `java -cp`. The class path file can also be attached and installed
  or deployed along with the main artifact.
- [dependency:get-classpath](./get-classpath-mojo.html) resolves a single artifact given by its coordinates, with its
  transitive runtime dependencies, and outputs the class path to a file or the log. Like `get`, it needs no project.
- [dependency:render-dependencies](./render-dependencies-mojo.html) is like `build-classpath`, but renders the
  dependencies through a Velocity template. See [Render a Velocity template](./examples/render-dependencies.html).
- [dependency:properties](./properties-mojo.html) sets a property for each dependency of the project that holds the
  path of the artifact file.

### Working with repositories

- [dependency:get](./get-mojo.html) resolves a single artifact, and optionally its dependencies, from the remote
  repositories. It needs no project.
- [dependency:go-offline](./go-offline-mojo.html) resolves everything the project depends on (dependencies, plugins,
  reports) in preparation for working offline.
- [dependency:purge-local-repository](./purge-local-repository-mojo.html) deletes the dependencies of the project from
  the local repository, and optionally resolves them again.

### Editing the POM

- [dependency:add](./add-mojo.html) adds a dependency to the `pom.xml` of the project. It supports GAV shorthand,
  version inference from `dependencyManagement`, BOM imports and profiles. See
  [Managing dependencies](./examples/managing-dependencies.html).
- [dependency:remove](./remove-mojo.html) removes a dependency from the `pom.xml` of the project. It supports
  `dependencyManagement`, BOM imports and profiles, and warns when child modules still use a managed dependency that
  it removes. See
  [Managing dependencies](./examples/managing-dependencies.html).

### Other goals

- [dependency:help](./help-mojo.html) displays help information on the plugin. Run
  `mvn dependency:help -Ddetail=true -Dgoal=<goal-name>` to display the parameters of a goal.

The `dependency:sources` goal was removed in 3.12.0; use [dependency:resolve-sources](./resolve-sources-mojo.html)
instead.

## Usage

General instructions on how to use the Dependency Plugin are on the [usage page](./usage.html). The
[FAQ](./faq.html) answers common questions; ask others on the [user mailing list](./mailing-lists.html), whose
archive may already hold the answer.

To report a defect or request a feature, open an issue in the [issue tracker](./issue-management.html). For a defect,
include the steps to reproduce it, ideally a small project that shows the problem. Contributors can check out the
project from the [source repository](./scm.html) and find more information in the
[guide to helping with Maven](https://maven.apache.org/guides/development/guide-helping.html).

## Examples

The following examples show how to use the dependency plugin in more advanced cases:

- [Copying specific artifacts](./examples/copying-artifacts.html)
- [Copying project dependencies](./examples/copying-project-dependencies.html)
- [Unpacking specific artifacts](./examples/unpacking-artifacts.html)
- [Unpacking the project dependencies](./examples/unpacking-project-dependencies.html)
- [Rewriting target path and file name](./examples/unpacking-filemapper.html)
- [Using project dependencies' sources](./examples/using-dependencies-sources.html)
- [Failing the build on dependency analysis warnings](./examples/failing-the-build-on-dependency-analysis-warnings.html)
- [Exclude dependencies from dependency analysis](./examples/exclude-dependencies-from-dependency-analysis.html)
- [Filtering the dependency tree](./examples/filtering-the-dependency-tree.html)
- [Dependency tree output formats](./examples/tree-mojo.html)
- [Managing dependencies](./examples/managing-dependencies.html)
- [Purging the local repository](./examples/purging-local-repository.html)
- [Render a Velocity template](./examples/render-dependencies.html)

## Resources

The [Introduction to the Dependency Mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
explains transitive dependencies, dependency scopes and dependency management.
