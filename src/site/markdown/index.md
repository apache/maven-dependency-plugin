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
The dependency plugin provides the capability to manipulate artifacts. It can copy and/or unpack artifacts from local or remote repositories to a specified location.

## Goals Overview

The Dependency plugin has several goals:

- [dependency:add](./add-mojo.html) adds a dependency to the project's `pom.xml` from the command line. Supports GAV shorthand, version inference from `<dependencyManagement>`, BOM imports, profile targeting, and more. See [Managing Dependencies](./examples/managing-dependencies.html).
- [dependency:analyze](./analyze-mojo.html) analyzes the dependencies of this project and determines which are: used and declared; used and undeclared; unused and declared.
- [dependency:analyze-dep-mgt](./analyze-dep-mgt-mojo.html) analyzes the project's dependencies and lists mismatches between resolved dependencies and those listed in your dependencyManagement section.
- [dependency:analyze-exclusions](./analyze-exclusions-mojo.html) analyzes the exclusions on dependencies and checks if the artifact actually brings in the given dependency.
- [dependency:analyze-only](./analyze-only-mojo.html) is the same as analyze, but is meant to be bound in a pom. It does not fork the build and execute test-compile.
- [dependency:analyze-report](./analyze-report-mojo.html) analyzes the dependencies of this project and produces a report that summarises which are: used and declared; used and undeclared; unused and declared.
- [dependency:analyze-duplicate](./analyze-duplicate-mojo.html) analyzes the `<dependencies/>` and `<dependencyManagement/>` tags in the pom.xml and determines the duplicate declared dependencies.
- [dependency:build-classpath](./build-classpath-mojo.html) tells Maven to output the path of the dependencies from the local repository in a classpath format to be used in java -cp. The classpath file may also be attached and installed/deployed along with the main artifact.
- [dependency:collect](./collect-mojo.html) collects the project dependencies from the repository. It lists the groupId:artifactId:version information by downloading the pom files without downloading the actual artifacts such as jar files.
- [dependency:copy](./copy-mojo.html) takes a list of artifacts defined in the plugin configuration section and copies them to a specified location, renaming them or stripping the version if desired. This goal can resolve the artifacts from remote repositories if they don't exist in either the local repository or the reactor.
- [dependency:copy-dependencies](./copy-dependencies-mojo.html) takes the list of project direct dependencies and optionally transitive dependencies and copies them to a specified location, stripping the version if desired. This goal can also be run from the command line.
- [dependency:display-ancestors](./display-ancestors-mojo.html) displays all ancestor POMs of the project. This may be useful in a continuous integration system where you want to know all parent poms of the project. This goal can also be run from the command line.
- [dependency:get](./get-mojo.html) resolves a single artifact, eventually transitively, from a specified remote repository.
- [dependency:go-offline](./go-offline-mojo.html) tells Maven to resolve everything this project is dependent on (dependencies, plugins, reports) in preparation for going offline.
- [dependency:list](./list-mojo.html) alias for resolve that lists the dependencies for this project.
- [dependency:list-classes](./list-classes-mojo.html) displays the fully package-qualified names of all classes found in a specified artifact.
- [dependency:list-repositories](./list-repositories-mojo.html) collects all project dependencies and then lists the repositories used by the build and by the transitive dependencies.
- [dependency:properties](./properties-mojo.html) sets a property for each project dependency containing the artifact on the file system.
- [dependency:purge-local-repository](./purge-local-repository-mojo.html) tells Maven to clear dependency artifact files out of the local repository, and optionally re-resolve them.
- [dependency:remove](./remove-mojo.html) removes a dependency from the project's `pom.xml` from the command line. Supports `<dependencyManagement>`, BOM imports, profile targeting, and child module safety checks. See [Managing Dependencies](./examples/managing-dependencies.html).
- [dependency:resolve](./resolve-mojo.html) tells Maven to resolve all dependencies and displays the version. **JAVA 9 NOTE:** _will display the module name when running with Java 9._
- [dependency:resolve-plugins](./resolve-plugins-mojo.html) tells Maven to resolve plugins and their dependencies.
- [dependency:resolve-sources](./resolve-sources-mojo.html) tells Maven to resolve all dependencies and their source attachments, and displays the version.
- [dependency:sources](./sources-mojo.html) has been deprecated for removal in favor of [dependency:resolve-sources](./resolve-sources-mojo.html).
- [dependency:tree](./tree-mojo.html) displays the dependency tree for this project.
- [dependency:unpack](./unpack-mojo.html) like copy but unpacks.
- [dependency:unpack-dependencies](./unpack-dependencies-mojo.html) like copy-dependencies but unpacks.
- [dependency:render-dependencies](./render-dependencies-mojo.html) like build-classpath but with a custom Velocity template.
## Usage

General instructions on how to use the Dependency Plugin can be found on the [usage page](./usage.html). Some more specific use cases are described in the following examples.

If you have questions regarding the plugin's usage, please have a look at the [FAQ](./faq.html) and feel free to contact the [user mailing list](./mailing-lists.html). The posts to the mailing list are archived and could already contain the answer to your question as part of an older thread. Hence, it is also worth browsing/searching the [mail archive](./mailing-lists.html).

If you think the plugin is missing a feature or has a defect, you can file a feature request or bug report in the [issue tracker](./issue-management.html). When creating a new issue, please provide a comprehensive description of your concern. Especially for fixing bugs it is crucial that the developers can reproduce your problem. For this reason, entire debug logs, POMs or most preferably little demo projects attached to the issue are very much appreciated. Of course, patches are welcome, too. Contributors can check out the project from our [source repository](./scm.html) and will find supplementary information in the [guide to helping with Maven](https://maven.apache.org/guides/development/guide-helping.html).

## Examples

The following examples show how to use the dependency plugin in more advanced use-cases:

- [Copying Specific Artifacts](./examples/copying-artifacts.html)
- [Copying Project Dependencies](./examples/copying-project-dependencies.html)
- [Unpacking Specific Artifacts](./examples/unpacking-artifacts.html)
- [Unpacking the Project Dependencies](./examples/unpacking-project-dependencies.html)
- [Rewriting target path and file name](./examples/unpacking-filemapper.html)
- [Using Project Dependencies' Sources](./examples/using-dependencies-sources.html)
- [Failing the Build on Dependency Analysis Warnings](./examples/failing-the-build-on-dependency-analysis-warnings.html)
- [Exclude Dependencies from Dependency Analysis](./examples/exclude-dependencies-from-dependency-analysis.html)
- [Filtering the Dependency Tree](./examples/filtering-the-dependency-tree.html)
- [Managing Dependencies](./examples/managing-dependencies.html)
- [Purging the local repository](./examples/purging-local-repository.html)
- [Tree Mojo](./examples/tree-mojo.html)
- [Render Dependencies](./examples/render-dependencies.html)
## Resources

Here is a link that provides more reference regarding dependencies (i.e. dependency management, transitive dependencies).

- [Dependency Mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
