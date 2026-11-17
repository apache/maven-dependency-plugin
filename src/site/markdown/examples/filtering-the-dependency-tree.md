---
title: Filtering the dependency tree
author: 
  - Mark Hobson
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

# Filtering the dependency tree

A project's dependency tree can be filtered to locate specific dependencies. For example, to find out why Commons Lang
is on the class path of a project, run the following in the project's directory:

```shell
mvn dependency:tree -Dincludes=org.apache.commons:commons-lang3
```

Which outputs:

```text
[INFO] org.example:app:jar:1.0
[INFO] \- org.apache.commons:commons-text:jar:1.12.0:compile
[INFO]    \- org.apache.commons:commons-lang3:jar:3.14.0:compile
```

Thus we can see that Commons Lang is not declared by the project, but is brought in by its direct dependency on
Commons Text.

## Filter pattern syntax

The syntax for filter patterns is as follows:

```text
[groupId]:[artifactId]:[type]:[version]
```

Where each pattern segment is optional and supports full and partial `*` wildcards. An empty pattern segment is treated as an implicit wildcard.

For example, `org.apache.*` would match all artifacts whose group id started with `org.apache.`, and `:::*-SNAPSHOT` would match all snapshot artifacts.

## Excluding dependencies from the tree

The dependency tree can also be filtered to remove specific dependencies. For example, to exclude Plexus dependencies from the tree, we can execute the following:

```shell
mvn dependency:tree -Dexcludes=org.codehaus.plexus
```

A dependency matching an exclude pattern and its entire dependency subtree are removed from the serialized tree.
This affects only the displayed tree; it does not change the project's dependency resolution.

## Specifying multiple patterns

Multiple patterns can be specified when filtering the dependency tree by separating the patterns with commas. For example, to exclude Maven and Plexus dependencies from the tree, we can execute the following:

```shell
mvn dependency:tree -Dexcludes=org.apache.maven*,org.codehaus.plexus
```

## Including and excluding dependencies from the tree

Both include and exclude patterns can be specified together to filter the dependency tree. For example, to locate all non-snapshot Plexus dependencies in the tree, we can execute the following:

```shell
mvn dependency:tree -Dincludes=org.codehaus.plexus -Dexcludes=:::*-SNAPSHOT
```

Excludes are applied first and take precedence over includes. Includes then select matching dependencies from the
remaining tree and retain the paths leading to those dependencies. An include therefore cannot restore a dependency
beneath an excluded subtree.
