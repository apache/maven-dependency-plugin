<!---
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

# Specification: CLI Dependency Management Goals

**Plugin:** `org.apache.maven.plugins:maven-dependency-plugin`
**Version target:** 3.11.0 (next minor release after 3.10.1-SNAPSHOT)
**Status:** Draft
**Authors:** Bruno Borges

---

## 1. Motivation

Every major dependency management ecosystem provides a single-command way to add a dependency from the terminal:

| Ecosystem  | Command                           |
|------------|-----------------------------------|
| Python/pip | `pip install google-adk`          |
| Node/npm   | `npm install @google/adk`         |
| Rust/Cargo | `cargo add google-adk`            |
| .NET/NuGet | `dotnet add package Google.Adk`   |
| Go modules | `go get google.golang.org/adk`    |
| **Maven**  | _(manual XML editing required)_   |

Maven's lack of a CLI-based dependency management workflow is a friction point for developers — particularly those coming from other ecosystems — and for AI coding agents, where a single CLI invocation is significantly cheaper (in token usage) than instructing an XML file edit. This specification proposes three new Mojo goals to close that gap.

## 2. Goals Overview

| Goal                  | Purpose                                              | Requires Project |
|-----------------------|------------------------------------------------------|------------------|
| `dependency:add`      | Add or update a dependency in a project's `pom.xml`  | Yes              |
| `dependency:remove`   | Remove a dependency from a project's `pom.xml`       | Yes              |
| `dependency:search`   | Query Maven Central for matching artifacts            | No               |

## 3. Goal: `dependency:add`

### 3.1 Description

Adds a `<dependency>` element to the project's `pom.xml`. If the dependency already exists, the behavior depends on the `-DupdateExisting` flag (see §3.5). The goal modifies the POM file on disk and preserves existing formatting, indentation, and XML comments.

### 3.2 Mojo Annotation

```java
@Mojo(name = "add", requiresProject = true, threadSafe = true)
```

### 3.3 Parameters

| Parameter            | Property                | Type      | Required | Default   | Description |
|----------------------|-------------------------|-----------|----------|-----------|-------------|
| `groupId`            | `groupId`               | `String`  | No¹      | —         | The dependency's groupId. |
| `artifactId`         | `artifactId`            | `String`  | No¹      | —         | The dependency's artifactId. |
| `version`            | `version`               | `String`  | No²      | —         | The dependency's version. |
| `gav`                | `gav`                   | `String`  | No¹      | —         | Shorthand coordinates in the format `groupId:artifactId[:version[:scope[:type[:classifier]]]]`. |
| `scope`              | `scope`                 | `String`  | No       | —         | Dependency scope (`compile`, `provided`, `runtime`, `test`, `system`, `import`). Omitted from the POM when not specified (Maven defaults to `compile`). |
| `type`               | `type`                  | `String`  | No       | —         | Dependency type/packaging (e.g., `jar`, `pom`, `war`). Omitted from the POM when not specified (Maven defaults to `jar`). |
| `classifier`         | `classifier`            | `String`  | No       | —         | Dependency classifier (e.g., `sources`, `javadoc`, `tests`). |
| `optional`           | `optional`              | `Boolean` | No       | `false`   | Whether the dependency is optional. Omitted from the POM when `false`. |
| `managed`            | `managed`               | `Boolean` | No       | `false`   | When `true`, insert into `<dependencyManagement>` instead of `<dependencies>`. |
| `module`             | `module`                | `String`  | No       | —         | Target a specific child module by artifactId when running from the root of a multi-module project. |
| `updateExisting`     | `updateExisting`        | `Boolean` | No       | `false`   | When `true` and the dependency already exists, update its version (and other specified fields). When `false`, fail with an error if the dependency already exists. |
| `bom`                | `bom`                   | `Boolean` | No       | `false`   | When `true`, add as a BOM import (`type=pom`, `scope=import`) into `<dependencyManagement>`. See §3.11. |
| `skip`               | `mdep.skip`             | `Boolean` | No       | `false`   | Skip plugin execution. |

> ¹ Either `gav` **or** both `groupId` + `artifactId` must be provided. If both forms are present, `gav` takes precedence.
>
> ² Version is required when adding to `<dependencyManagement>` (`-Dmanaged`). When adding to `<dependencies>`, version is optional if the dependency is already declared in an ancestor's `<dependencyManagement>` — in that case the `<version>` element is omitted from the inserted XML, following Maven convention.

> **Scope validation:** The `scope` parameter is validated against Maven's known scope values: `compile`, `provided`, `runtime`, `test`, `system`, `import`. Invalid scope values are rejected with a `MojoFailureException`.

### 3.4 GAV Shorthand Format

The `gav` parameter accepts a colon-separated coordinate string:

```
groupId:artifactId[:version[:scope[:type[:classifier]]]]
```

**Examples:**

```bash
# Minimal (groupId + artifactId only; version resolved from dependencyManagement)
-Dgav="com.google.adk:google-adk"

# With version
-Dgav="com.google.adk:google-adk:1.0.0"

# With version and scope
-Dgav="com.google.adk:google-adk:1.0.0:test"

# With version, scope, and type
-Dgav="com.google.adk:google-adk:1.0.0:compile:pom"

# Full form
-Dgav="com.google.adk:google-adk:1.0.0:compile:jar:sources"
```

This format is consistent with the existing `dependency:get` goal's `-Dartifact` parameter (which uses `groupId:artifactId:version[:packaging[:classifier]]`), extended to also accept scope.

### 3.5 Behavior: Existing Dependencies

When the target `pom.xml` already contains a dependency with the same `groupId`, `artifactId`, `type`, and `classifier`:

| `updateExisting` | Behavior |
|------------------|----------|
| `false` (default) | The goal **fails** with a `MojoFailureException` and message: `Dependency groupId:artifactId already exists. Use -DupdateExisting to update it.` |
| `true` | The existing `<dependency>` element is **updated in place** — only the fields explicitly provided by the user are changed (version, scope, type, classifier, optional). Fields not provided are left unchanged. |

> **Type/classifier matching semantics:**
> - Matching uses Maven's default type semantics: `null` and `"jar"` are treated as equivalent.
> - Classifier `null` matches only dependencies without a classifier.
> - When a dependency exists in the POM via property references (e.g., `${project.groupId}`), the goal cross-references Maven's resolved original model to detect it and blocks with a clear error.

### 3.6 Behavior: Multi-Module Projects

| Execution context | `-Dmanaged` | Behavior |
|-------------------|-------------|----------|
| Child module directory | `false` | Dependency added to the child module's `<dependencies>`. |
| Child module directory | `true` | Dependency added to the child module's `<dependencyManagement>`. |
| Root/parent directory | `false` | Dependency added to the parent's `<dependencies>`. **Warning emitted:** _"Adding dependency to parent POM — this will be inherited by all child modules. Use -Dmanaged to add to `<dependencyManagement>` instead."_ |
| Root/parent directory | `true` | Dependency added to the parent's `<dependencyManagement>`. |
| Root directory with `-Dmodule=X` | `false` | Dependency added to module X's `<dependencies>`. |
| Root directory with `-Dmodule=X` | `true` | Dependency added to module X's `<dependencyManagement>`. |

When `-Dmodule` is specified but the module is not found among the reactor modules, the goal **fails** with a `MojoFailureException`.

### 3.7 Version Inference

When adding to `<dependencies>` (i.e., `-Dmanaged` is `false`) and no `-Dversion` is provided:

1. The goal walks the project's effective model hierarchy (current POM → parent → grandparent, etc.) looking for a `<dependencyManagement>` entry matching the `groupId:artifactId`.
2. If a managed version is found, the `<version>` element is **omitted** from the inserted dependency block, and an informational message is logged: _"Version managed by parent: groupId:artifactId:managedVersion"_.
3. If **no** managed version is found, the goal **fails** with a `MojoFailureException`: _"No version specified and no managed version found for groupId:artifactId. Provide a version with -Dversion=..."_

### 3.8 POM Modification Strategy

The goal **must** preserve the existing structure of the `pom.xml`, including:

- XML comments
- Indentation style (spaces vs. tabs, indent depth)
- Element ordering within `<dependency>` blocks
- Blank lines between sections

**Implementation approach:** Use a DOM-level XML parser (not `MavenXpp3Reader`/`MavenXpp3Writer`, which loses comments and formatting). The recommended approach is:

1. Parse the `pom.xml` using a DOM parser that preserves comments and whitespace (e.g., `javax.xml.parsers.DocumentBuilderFactory` with appropriate settings, or `org.codehaus.plexus.util.xml.Xpp3DomBuilder`).
2. Locate or create the target `<dependencies>` or `<dependencyManagement><dependencies>` element.
3. Build and insert the new `<dependency>` element, matching the indentation of existing sibling elements.
4. Serialize back to disk preserving the original encoding declared in the XML prolog.

Alternatively, consider leveraging the `maven-model` APIs along with a formatting-preserving XML manipulation library if one is available in the plugin's dependency tree.

#### 3.8.1 Security Hardening

The DOM parser is configured with the following security measures:
- DOCTYPE declarations are **disallowed** (`disallow-doctype-decl`) to prevent entity-expansion denial-of-service attacks.
- External DTD loading and external entity resolution are disabled.
- `FEATURE_SECURE_PROCESSING` is enabled.
- The root element is validated to be `<project>` — non-POM XML files are rejected with a clear error.

#### 3.8.2 Atomic Writes

POM modifications are written using atomic file operations: content is first written to a temporary file in the same directory, then renamed over the original using `Files.move(REPLACE_EXISTING)`. This prevents partial writes from corrupting the POM if the process is interrupted.

#### 3.8.3 Encoding and BOM Preservation

- The original XML encoding declaration (e.g., `encoding="UTF-8"`) is preserved on output.
- UTF-8 BOM (Byte Order Mark) is detected on input and re-emitted on output if present.
- Line endings (LF vs CRLF) are detected and preserved.
- Indentation style is auto-detected from the existing file content using GCD-based analysis.

### 3.9 Output

On success, the goal logs:

```
[INFO] Added dependency com.google.adk:google-adk:1.0.0 [scope=test] to pom.xml
```

Or, when version is managed:

```
[INFO] Added dependency com.google.adk:google-adk (scope: test) to pom.xml
[INFO] Version managed by parent: com.google.adk:google-adk:1.0.0
```

### 3.10 Usage Examples

```bash
# Add with explicit coordinates
mvn dependency:add -DgroupId=com.google.adk -DartifactId=google-adk -Dversion=1.0.0

# Add with GAV shorthand
mvn dependency:add -Dgav="com.google.adk:google-adk:1.0.0"

# Add as test dependency
mvn dependency:add -Dgav="com.google.adk:google-adk:1.0.0" -Dscope=test

# Add with scope in GAV shorthand
mvn dependency:add -Dgav="com.google.adk:google-adk:1.0.0:test"

# Add to dependencyManagement
mvn dependency:add -Dgav="com.google.adk:google-adk:1.0.0" -Dmanaged

# Add to a specific child module from root
mvn dependency:add -Dgav="com.google.adk:google-adk:1.0.0" -Dmodule=my-service

# Update an existing dependency's version
mvn dependency:add -Dgav="com.google.adk:google-adk:2.0.0" -DupdateExisting
```

---

## 4. Goal: `dependency:remove`

### 4.1 Description

Removes a `<dependency>` element from the project's `pom.xml`. The goal modifies the POM file on disk and preserves existing formatting, indentation, and XML comments.

### 4.2 Mojo Annotation

```java
@Mojo(name = "remove", requiresProject = true, threadSafe = true)
```

### 4.3 Parameters

| Parameter         | Property            | Type      | Required | Default   | Description |
|-------------------|---------------------|-----------|----------|-----------|-------------|
| `groupId`         | `groupId`           | `String`  | No¹      | —         | The dependency's groupId. |
| `artifactId`      | `artifactId`        | `String`  | No¹      | —         | The dependency's artifactId. |
| `gav`             | `gav`               | `String`  | No¹      | —         | Shorthand coordinates: `groupId:artifactId`. Version and scope segments are accepted but only groupId and artifactId are used for locating the dependency. Type and classifier segments are used for precise matching when multiple variants exist (e.g., jar vs test-jar). |
| `managed`         | `managed`           | `Boolean` | No       | `false`   | When `true`, remove from `<dependencyManagement>` instead of `<dependencies>`. |
| `module`          | `module`            | `String`  | No       | —         | Target a specific child module by artifactId. |
| `skip`            | `mdep.skip`         | `Boolean` | No       | `false`   | Skip plugin execution. |

> ¹ Either `gav` **or** both `groupId` + `artifactId` must be provided. If both forms are present, `gav` takes precedence.

### 4.4 Behavior

1. The goal locates the `<dependency>` element matching the given `groupId`, `artifactId`, `type`, and `classifier` in the target section (`<dependencies>` or `<dependencyManagement><dependencies>`).
2. If found, the entire `<dependency>` element is removed, along with any immediately preceding XML comment that appears to be associated with it (i.e., a comment on the line(s) directly above the `<dependency>` tag, with no blank line separating them).
3. If **not found**, the goal **fails** with a `MojoFailureException`: _"Dependency groupId:artifactId not found in `<dependencies>`."_
4. If not found in the raw XML but detected in Maven's resolved original model (indicating the dependency uses property references like `${project.groupId}`), the goal **fails** with a message: _"Dependency groupId:artifactId exists in `<dependencies>` but uses property references in the POM. Please remove it manually."_

### 4.5 Behavior: Managed Dependency Removal Safety Check

When removing a dependency from `<dependencyManagement>` in a parent POM (i.e., the current project has `<modules>`), the goal performs a safety check:

1. Scan all child module `pom.xml` files for references to the same `groupId:artifactId` in their `<dependencies>` section **without** an explicit `<version>`.
2. If any such references are found, emit a **warning** listing the affected modules: _"Warning: The following child modules depend on groupId:artifactId without an explicit version and will break: [module-a, module-b]. Proceeding with removal."_
3. The removal **proceeds** despite the warning (it is not a blocking error). Users who want a blocking check can integrate this into a CI validation step.

### 4.6 Output

```
[INFO] Removed dependency com.google.adk:google-adk from pom.xml
```

### 4.7 Usage Examples

```bash
# Remove with explicit coordinates
mvn dependency:remove -DgroupId=com.google.adk -DartifactId=google-adk

# Remove with GAV shorthand
mvn dependency:remove -Dgav="com.google.adk:google-adk"

# Remove from dependencyManagement
mvn dependency:remove -Dgav="com.google.adk:google-adk" -Dmanaged

# Remove from a specific child module
mvn dependency:remove -Dgav="com.google.adk:google-adk" -Dmodule=my-service
```

---

## 5. Goal: `dependency:search`

### 5.1 Description

Queries Maven Central's search API for artifacts matching a given search term and displays the results in the console. This goal does **not** require a project context — it can be run from any directory.

### 5.2 Mojo Annotation

```java
@Mojo(name = "search", requiresProject = false, threadSafe = true)
```

### 5.3 Parameters

| Parameter       | Property           | Type      | Required | Default                                          | Description |
|-----------------|--------------------|-----------|----------|--------------------------------------------------|-------------|
| `query`         | `query`            | `String`  | Yes      | —                                                | Free-text search term, or a structured query (e.g., `g:com.google.adk`, `a:google-adk`). |
| `rows`          | `rows`             | `Integer` | No       | `10`                                             | Maximum number of results to return. |
| `repositoryUrl` | `repositoryUrl`    | `String`  | No       | `https://search.maven.org/solrsearch/select`     | Maven Central Search v2 REST API endpoint. Can be overridden for private registries that expose a compatible API. |
| `skip`          | `mdep.skip`        | `Boolean` | No       | `false`                                          | Skip plugin execution. |

### 5.4 Search API Integration

The goal uses the [Maven Central Search v2 REST API](https://central.sonatype.com/search):

```
GET https://search.maven.org/solrsearch/select?q={query}&rows={rows}&wt=json
```

> **Note:** The endpoint path remains Solr-compatible for now, but the implementation targets the newer v2 API behavior. The `repositoryUrl` parameter allows overriding the endpoint for forward compatibility or private registries.

The `query` parameter is passed directly to the API's `q` parameter, supporting both free-text and structured queries:

- Free-text: `-Dquery=google-adk`
- By groupId: `-Dquery="g:com.google.adk"`
- By artifactId: `-Dquery="a:google-adk"`
- Combined: `-Dquery="g:com.google AND a:adk"`

### 5.5 Output Format

Results are displayed in a tabular format:

```
[INFO] Search results for: google-adk
[INFO]
[INFO]   groupId                  artifactId          latest version
[INFO]   ─────────────────────────────────────────────────────────────
[INFO]   com.google.adk           google-adk          1.0.0
[INFO]   com.google.adk           google-adk-spring   0.5.0
[INFO]
[INFO] 2 results found. Use dependency:add to add one to your project:
[INFO]   mvn dependency:add -Dgav="com.google.adk:google-adk:1.0.0"
```

When no results are found:

```
[INFO] Search results for: nonexistent-library
[INFO]
[INFO] No artifacts found matching 'nonexistent-library'.
```

### 5.6 Network and Error Handling

| Scenario | Behavior |
|----------|----------|
| No network connectivity | `MojoFailureException`: _"Unable to reach Maven Central search API. Check your network connection."_ |
| HTTP 429 (rate limit) | `MojoFailureException`: _"Maven Central search API rate limit exceeded (HTTP 429). Please wait a moment and try again."_ with server response body if available. |
| Other HTTP errors (4xx/5xx) | `MojoFailureException` with HTTP status code and server response body (truncated to 500 characters). |
| Response Content-Type is not JSON | `MojoFailureException`: _"Maven Central search API returned unexpected content type."_ |
| Response body is not valid JSON | `MojoFailureException`: _"Maven Central search API returned a non-JSON response."_ |
| API returns malformed JSON | `MojoExecutionException`: _"Failed to parse search API response."_ |
| Query is empty or blank | `MojoFailureException`: _"Search query must not be empty."_ |

### 5.7 Usage Examples

```bash
# Simple text search
mvn dependency:search -Dquery=google-adk

# Search by groupId
mvn dependency:search -Dquery="g:com.google.adk"

# Search with more results
mvn dependency:search -Dquery=jackson -Drows=20

# Search against a private registry
mvn dependency:search -Dquery=my-lib -DrepositoryUrl=https://nexus.example.com/solrsearch/select
```

---

## 6. Implementation Plan

### 6.1 New Source Files

```
src/main/java/org/apache/maven/plugins/dependency/
├── AddDependencyMojo.java          # dependency:add goal
├── RemoveDependencyMojo.java       # dependency:remove goal
├── SearchDependencyMojo.java       # dependency:search goal
└── pom/
    ├── PomEditor.java              # Formatting-preserving POM read/write
    └── DependencyCoordinates.java  # GAV parsing and coordinate representation
```

### 6.2 New Test Files

```
src/test/java/org/apache/maven/plugins/dependency/
├── AddDependencyMojoTest.java
├── RemoveDependencyMojoTest.java
├── SearchDependencyMojoTest.java
├── SearchDependencyMojoHttpTest.java
└── pom/
    ├── PomEditorTest.java
    └── DependencyCoordinatesTest.java

src/it/projects/
├── add-dependency/
│   ├── basic/                      # Add dependency with explicit coordinates
│   ├── gav-shorthand/              # Add with -Dgav
│   ├── managed/                    # Add to dependencyManagement
│   ├── existing-update/            # Update existing dependency
│   ├── existing-fail/              # Fail on existing without -DupdateExisting
│   ├── version-inference/          # Omit version when managed by parent
│   └── multi-module/               # Multi-module project scenarios
├── remove-dependency/
│   ├── basic/                      # Remove a dependency
│   ├── managed/                    # Remove from dependencyManagement
│   ├── not-found/                  # Fail when dependency not found
│   └── safety-check/              # Warning for child module breakage
└── search-dependency/
    ├── basic/                      # Basic search
    └── no-results/                 # Empty result set
```

### 6.3 Documentation

New APT pages under `src/site/apt/`:

- `add-mojo.apt.vm` — Usage documentation for `dependency:add`
- `remove-mojo.apt.vm` — Usage documentation for `dependency:remove`
- `search-mojo.apt.vm` — Usage documentation for `dependency:search`

Update `src/site/site.xml` to add navigation entries for the new goals.

### 6.4 Implementation Phases

**Phase 1 — Core infrastructure**
- `DependencyCoordinates`: GAV parsing, validation, coordinate representation
- `PomEditor`: DOM-based XML read/write with formatting preservation
- `DependencyInserter`: Insert, update, and remove `<dependency>` elements
- Unit tests for all utility classes

**Phase 2 — `dependency:add` goal**
- `AddDependencyMojo` implementation
- Version inference from `<dependencyManagement>` hierarchy
- Multi-module support (`-Dmodule`)
- Unit tests and integration tests

**Phase 3 — `dependency:remove` goal**
- `RemoveDependencyMojo` implementation
- Child module safety check for managed dependency removal
- Unit tests and integration tests

**Phase 4 — `dependency:search` goal**
- `SearchDependencyMojo` implementation
- HTTP client integration with Maven Central Search API
- Tabular output formatting
- Unit tests (with mocked HTTP responses) and integration tests

**Phase 5 — Documentation and polish**
- APT documentation pages
- Site navigation updates
- End-to-end integration test suite review

---

## 7. Compatibility and Constraints

### 7.1 Maven Version

These goals require Maven 3.9.x+ (consistent with the plugin's current `mavenVersion` property of `3.9.14`).

### 7.2 Java Version

Java 8+ (consistent with the plugin's current baseline).

### 7.3 Backward Compatibility

These are **new goals only** — no existing goal behavior is modified. The change is fully additive. Existing CI pipelines, build configurations, and POM files are unaffected.

### 7.4 Thread Safety

All three goals are annotated `threadSafe = true`. POM file writes use atomic file operations (temp file + rename) to prevent partial writes. Note: concurrent modifications by separate processes are not guarded against with file locking; the last writer wins.

---

## 8. Design Decisions

The following questions were evaluated and resolved:

| # | Question | Decision | Rationale |
|---|----------|----------|-----------|
| 1 | **BOM support** — `-Dbom` shorthand for `scope=import` + `type=pom` in `<dependencyManagement>` | **Yes, include in initial implementation** | BOMs are a common pattern; a `-Dbom` flag significantly reduces friction for this use case. See §3.11 for details. |
| 2 | **Exclusions** — inline `-Dexclusions` parameter | **No** | Exclusions should be added manually via POM editing. Keeps the CLI surface simple. |
| 3 | **Dry-run mode** — `-DdryRun` flag to preview changes | **No** | Not needed for the initial implementation. Users can rely on version control to review changes. |
| 4 | **Sorting** — insertion order for new dependencies | **Always append** | New dependencies are appended to the end of the `<dependencies>` or `<dependencyManagement>` list. No auto-sorting. |
| 5 | **Search API** — legacy Solr vs. newer REST API | **Newer REST API only** | Target `search.maven.org` v2 REST API. The legacy Solr API is being phased out. |
| 6 | **Property references** — create `<properties>` entries for versions | **No, always literal** | Always insert the literal version string. Property extraction is a stylistic choice best left to the developer.  |

### 8.1 Implementation Safety Measures

The following safety measures were implemented based on iterative edge case analysis:

| # | Measure | Description |
|---|---------|-------------|
| 1 | **Type/classifier matching** | Dependency matching uses `groupId:artifactId:type:classifier` (not just `groupId:artifactId`) to prevent incorrect updates when multiple variants exist. |
| 2 | **Scope validation** | Scope values are validated against Maven's known set (`compile`, `provided`, `runtime`, `test`, `system`, `import`). |
| 3 | **XXE protection** | DOCTYPE declarations are disallowed; external entities disabled; secure processing enabled. |
| 4 | **Root element validation** | Only files with `<project>` as root element are accepted. |
| 5 | **Atomic writes** | POM modifications use temp file + rename to prevent partial writes. |
| 6 | **Property-interpolation detection** | When a dependency exists in Maven's resolved model but not in the raw XML (indicating property references), the goal blocks with a clear error instead of creating duplicates. |
| 7 | **Original model cross-reference** | Uses `project.getOriginalModel()` (not `project.getDependencies()`) to avoid false positives from inherited parent dependencies. |
| 8 | **Search response validation** | Content-Type, JSON structure, and HTTP status are all validated. Rate limiting (429) produces a specific user-friendly message. |

### 3.11 BOM Support (`-Dbom` flag)

When the `-Dbom` flag is set to `true`, `dependency:add` automatically:

1. Sets `<type>pom</type>` and `<scope>import</scope>` on the dependency.
2. Implies `-Dmanaged` — the dependency is inserted into `<dependencyManagement>`, since BOM imports are only valid there.
3. Requires `-Dversion` (BOMs must have an explicit version).

Any explicit `-Dscope` or `-Dtype` values provided alongside `-Dbom` are **ignored** with a warning: _"The -Dbom flag overrides scope and type. Using scope=import and type=pom."_

**Parameter addition to §3.3:**

| Parameter | Property | Type | Required | Default | Description |
|-----------|----------|------|----------|---------|-------------|
| `bom`     | `bom`    | `Boolean` | No | `false` | When `true`, add as a BOM import (`type=pom`, `scope=import`) into `<dependencyManagement>`. |

**Example:**

```bash
# Add a BOM import
mvn dependency:add -Dgav="org.springframework.boot:spring-boot-dependencies:3.2.0" -Dbom

# Equivalent to (but shorter than):
mvn dependency:add -Dgav="org.springframework.boot:spring-boot-dependencies:3.2.0" -Dmanaged -Dtype=pom -Dscope=import
```
