~~ Licensed to the Apache Software Foundation (ASF) under one
~~ or more contributor license agreements.  See the NOTICE file
~~ distributed with this work for additional information
~~ regarding copyright ownership.  The ASF licenses this file
~~ to you under the Apache License, Version 2.0 (the
~~ "License"); you may not use this file except in compliance
~~ with the License.  You may obtain a copy of the License at
~~
~~ http://www.apache.org/licenses/LICENSE-2.0
~~
~~ Unless required by applicable law or agreed to in writing,
~~ software distributed under the License is distributed on an
~~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
~~ KIND, either express or implied.  See the License for the
~~ specific language governing permissions and limitations
~~ under the License.

    ------
    Dependency Tree Output Formats
    ------
    Tayebwa Noah
    ------
    2025-06-04
    ------

Dependency Tree Output Formats

  The <<<dependency:tree>>> goal of the Maven Dependency Plugin generates a representation
  of a project's dependency tree. The <<<outputType>>> parameter allows you to specify
  the output format, which can be written to a file using the <<<outputFile>>> parameter
  or printed to the console. Supported formats are <<<json>>>, <<<dot>>>, <<<graphml>>>,
  and <<<tgf>>>. This section describes each format, its structure, and example usage.

* Usage

  To <<generate>> the dependency tree in a specific format, use the following command:

+---+
mvn dependency:tree -DoutputType=<format> -DoutputFile=<filename>
+---+

  * <<<format>>>: One of <<<json>>>, <<<dot>>>, <<<graphml>>>, or <<<tgf>>>. If omitted,
    the default is a text-based tree printed to the console.

  * <<<filename>>>: The file to write the output to (e.g., <<<dependency-tree.json>>>).
    If omitted, the output is printed to the console.

  Example:

+---+
mvn dependency:tree -DoutputType=json -DoutputFile=dependency-tree.json
+---+

  <<Note>>: Ensure you are using Maven Dependency Plugin version 3.7.0 or later
  (latest is 3.8.1 as of June 2025) to access these output formats.

* Output Formats

{JSON (outputType=json)}

  <<Description>>: The JSON format represents the dependency tree as a hierarchical JSON
  object. The root node describes the project's artifact, and its dependencies are listed
  in a <<<children>>> array, with nested dependencies recursively included.

  <<Structure>>:

    * <<Root Node>>:

      * <<<groupId>>>: The group ID of the project or dependency (e.g., <<<org.apache.maven.extensions>>>).

      * <<<artifactId>>>: The artifact ID (e.g., <<<maven-build-cache-extension>>>).

      * <<<version>>>: The version (e.g., <<<1.2.1-SNAPSHOT>>>).

      * <<<type>>>: The artifact type (e.g., <<<jar>>>).

      * <<<scope>>>: The dependency scope (e.g., <<<compile>>>, <<<test>>>, or empty for the project itself).

      * <<<classifier>>>: The classifier, if any (e.g., empty string if none).

      * <<<optional>>>: Whether the dependency is optional (<<<true>>> or <<<false>>>).

      * <<<children>>>: An array of dependency objects with the same structure, representing transitive dependencies.

    * <<Nested Dependencies>>: Each dependency in the <<<children>>> array follows the same
      structure, allowing for recursive representation of the dependency tree.


  <<Example>>:

+---+
{
  "groupId": "org.apache.maven.extensions",
  "artifactId": "maven-build-cache-extension",
  "version": "1.2.1-SNAPSHOT",
  "type": "jar",
  "scope": "",
  "classifier": "",
  "optional": "false",
  "children": [
    {
      "groupId": "net.openhft",
      "artifactId": "zero-allocation-hashing",
      "version": "0.27ea0",
      "type": "jar",
      "scope": "compile",
      "classifier": "",
      "optional": "false"
    },
    {
      "groupId": "org.apache.maven.wagon",
      "artifactId": "wagon-webdav-jackrabbit",
      "version": "3.5.3",
      "type": "jar",
      "scope": "compile",
      "classifier": "",
      "optional": "false",
      "children": [
        {
          "groupId": "org.apache.jackrabbit",
          "artifactId": "jackrabbit-webdav",
          "version": "2.14.4",
          "type": "jar",
          "scope": "compile",
          "classifier": "",
          "optional": "false",
          "children": [
            {
              "groupId": "commons-codec",
              "artifactId": "commons-codec",
              "version": "1.10",
              "type": "jar",
              "scope": "compile",
              "classifier": "",
              "optional": "false"
            }
          ]
        }
      ]
    }
  ]
}
+---+

  <<Usage Example>>:

+---+
mvn dependency:tree -DoutputType=json -DoutputFile=dependency-tree.json
+---+

  <<Use Case>>: Parse the JSON output programmatically for dependency analysis or integration
  with other tools.

{DOT (outputType=dot)}

  <<Description>>: The DOT format is a plain-text graph description language used by Graphviz.
  It represents the dependency tree as a directed graph (<<<digraph>>>), with nodes for
  artifacts and directed edges for dependencies, labeled with their scope (e.g., <<<compile>>>).

  <<Structure>>:

    * <<Graph Declaration>>: Starts with <<<digraph "<groupId>:<artifactId>:<type>:<version>:">>>.

    * <<Nodes>>: Represented implicitly by their identifiers in edge definitions
      (e.g., <<<"org.apache.maven.extensions:maven-build-cache-extension:jar:1.2.1-SNAPSHOT:">>>).

    * <<Edges>>: Defined as <<<"source" -> "target">>>, where <<<source>>> is the parent artifact
      and <<<target>>> is the dependency, optionally labeled with the scope.

  <<Example>>:

+---+
digraph "org.apache.maven.extensions:maven-build-cache-extension:jar:1.2.1-SNAPSHOT:" {
  "org.apache.maven.extensions:maven-build-cache-extension:jar:1.2.1-SNAPSHOT:" -> "net.openhft:zero-allocation-hashing:jar:0.27ea0:compile";
  "org.apache.maven.extensions:maven-build-cache-extension:jar:1.2.1-SNAPSHOT:" -> "org.apache.maven.wagon:wagon-webdav-jackrabbit:jar:3.5.3:compile";
  "org.apache.maven.wagon:wagon-webdav-jackrabbit:jar:3.5.3:compile" -> "org.apache.jackrabbit:jackrabbit-webdav:jar:2.14.4:compile";
  "org.apache.jackrabbit:jackrabbit-webdav:jar:2.14.4:compile" -> "commons-codec:commons-codec:jar:1.10:compile";
}
+---+

  <<Usage Example>>:

+---+
mvn dependency:tree -DoutputType=dot -DoutputFile=dependency-tree.dot
+---+

  <<Visualization>>: Convert the DOT file to an image using Graphviz:

+---+
dot -Tpng dependency-tree.dot -o dependency-tree.png
+---+

  <<Use Case>>: Visualize the dependency graph using tools like Graphviz for presentations
  or analysis.

{GraphML (outputType=graphml)}

  <<Description>>: GraphML is an XML-based format for representing graphs, compatible with
  tools like yEd or Gephi. It represents the dependency tree as a directed graph with nodes
  for artifacts and edges for dependencies, including scope information.

  <<Structure>>:

    * <<GraphML Root>>: Contains namespace declarations and keys for node and edge graphics
      (using <<<yworks.com>>> extensions).

    * <<Graph>>: Defined with <<<edgedefault="directed">>>.

    * <<Nodes>>: Each node has an <<<id>>> (a unique number) and a <<<y:ShapeNode>>> with a
      <<<y:NodeLabel>>> containing the artifact coordinates (e.g., <<<groupId:artifactId:type:version:scope>>>).

    * <<Edges>>: Defined with <<<source>>> and <<<target>>> node IDs, with a <<<y:PolyLineEdge>>>
      containing a <<<y:EdgeLabel>>> for the scope (e.g., <<<compile>>>).

  <<Example>>:

+---+
<?xml version="1.0" encoding="UTF-8"?>
<graphml xmlns="http://graphml.graphdrawing.org/xmlns" xmlns:y="http://www.yworks.com/xml/graphml">
  <key for="node" id="d0" yfiles.type="nodegraphics"/>
  <key for="edge" id="d1" yfiles.type="edgegraphics"/>
  <graph id="dependencies" edgedefault="directed">
    <node id="955994360"><data key="d0"><y:ShapeNode><y:NodeLabel>org.apache.maven.extensions:maven-build-cache-extension:jar:1.2.1-SNAPSHOT:</y:NodeLabel></y:ShapeNode></data></node>
    <node id="2058879732"><data key="d0"><y:ShapeNode><y:NodeLabel>net.openhft:zero-allocation-hashing:jar:0.27ea0:compile</y:NodeLabel></y:ShapeNode></data></node>
    <edge source="955994360" target="2058879732"><data key="d1"><y:PolyLineEdge><y:EdgeLabel>compile</y:EdgeLabel></y:PolyLineEdge></data></edge>
    <node id="1889106580"><data key="d0"><y:ShapeNode><y:NodeLabel>org.apache.maven.wagon:wagon-webdav-jackrabbit:jar:3.5.3:compile</y:NodeLabel></y:ShapeNode></data></node>
    <node id="1817415346"><data key="d0"><y:ShapeNode><y:NodeLabel>org.apache.jackrabbit:jackrabbit-webdav:jar:2.14.4:compile</y:NodeLabel></y:ShapeNode></data></node>
    <edge source="1889106580" target="1817415346"><data key="d1"><y:PolyLineEdge><y:EdgeLabel>compile</y:EdgeLabel></y:PolyLineEdge></data></edge>
  </graph>
</graphml>
+---+

  <<Usage Example>>:

+---+
mvn dependency:tree -DoutputType=graphml -DoutputFile=dependency-tree.graphml
+---+

  <<Visualization>>: Open the GraphML file in yEd or Gephi to visualize the dependency graph.

  <<Use Case>>: Analyze complex dependency structures using graph visualization tools.

{TGF (outputType=tgf)}

  <<Description>>: The Trivial Graph Format (TGF) is a simple text-based format for representing
  graphs. It lists nodes followed by edges, with each node and edge described on a single line.

  <<Structure>>:

    * <<Nodes Section>>: Each line contains a unique node ID (a number) followed by the artifact
      coordinates (e.g., <<<groupId:artifactId:type:version:scope>>>).

    * <<Separator>>: A line containing <<<#>>> separates nodes from edges.

    * <<Edges Section>>: Each line contains the source node ID, target node ID, and the scope
      (e.g., <<<compile>>>).

  <<Example>>:

+---+
1474640235 org.apache.maven.extensions:maven-build-cache-extension:jar:1.2.1-SNAPSHOT:
788877168 net.openhft:zero-allocation-hashing:jar:0.27ea0:compile
1662807313 org.apache.maven.wagon:wagon-webdav-jackrabbit:jar:3.5.3:compile
1655562261 org.apache.jackrabbit:jackrabbit-webdav:jar:2.14.4:compile
1894638973 commons-codec:commons-codec:jar:1.10:compile
#
1474640235 788877168 compile
1474640235 1662807313 compile
1662807313 1655562261 compile
1655562261 1894638973 compile
+---+

  <<Usage Example>>:

+---+
mvn dependency:tree -DoutputType=tgf -DoutputFile=dependency-tree.tgf
+---+

  <<Visualization>>: Convert TGF to other formats (e.g., DOT) using tools like <<<graphviz>>> or
  custom scripts for visualization.

  <<Use Case>>: Lightweight format for simple graph processing or conversion to other graph formats.

* Notes

  * <<Visualization Tools>>:

    * <<JSON>>: Parse with any JSON-compatible tool (e.g., Python's <<<json>>> module, JavaScript's <<<JSON.parse>>>).

    * <<DOT>>: Use Graphviz (<<<dot>>> command) to generate PNG, SVG, or other visual formats.

    * <<GraphML>>: Use yEd, Gephi, or other GraphML-compatible tools for visualization.

    * <<TGF>>: Use tools supporting TGF or convert to DOT/GraphML for visualization.

