<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->
# Apache Maven Dependency Plugin

[![Apache License, Version 2.0, January 2004](https://img.shields.io/github/license/apache/maven.svg?label=License)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.maven.plugins/maven-dependency-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.apache.maven.plugins/maven-dependency-plugin)
[![Reproducible Builds](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/jvm-repo-rebuild/reproducible-central/master/content/org/apache/maven/plugins/maven-dependency-plugin/badge.json)](https://github.com/jvm-repo-rebuild/reproducible-central/blob/master/content/org/apache/maven/plugins/maven-dependency-plugin/README.md)
[![Verify](https://github.com/apache/maven-dependency-plugin/actions/workflows/maven-verify.yml/badge.svg)](https://github.com/apache/maven-dependency-plugin/actions/workflows/maven-verify.yml)

The Maven plugin for working with a project's dependencies: it displays the dependency tree, analyzes declared and
used dependencies, copies and unpacks artifacts, builds class paths, resolves artifacts without a project, and adds or
removes dependencies in a `pom.xml`. It requires Maven 3.9.0 or later and Java 8 or later.

```shell
mvn dependency:tree
mvn dependency:analyze
mvn dependency:copy-dependencies -DincludeScope=runtime
mvn dependency:get -Dartifact=org.apache.commons:commons-collections4:4.4
```

- Documentation: <https://maven.apache.org/plugins/maven-dependency-plugin/>
- Issues: <https://github.com/apache/maven-dependency-plugin/issues>
- Security reports: see [SECURITY.md](SECURITY.md); never a public issue
- Agents and contributors: see [AGENTS.md](AGENTS.md)

## Build

Build and run the unit tests:

```shell
mvn verify
```

Run the integration tests in `src/it` as well, all of them or one by name:

```shell
mvn -Prun-its verify
mvn -Prun-its verify -Dinvoker.test=tree-show-sizes
```

Build the site into `target/site`:

```shell
mvn -DskipTests install site
```

## Contributing

Open a pull request against `master` with a test that covers the change, and run `mvn spotless:apply` before you
commit. Discuss larger changes on the [developer mailing list](https://maven.apache.org/mailing-lists.html) first.
See the [guide to helping with Maven](https://maven.apache.org/guides/development/guide-helping.html) for more.
