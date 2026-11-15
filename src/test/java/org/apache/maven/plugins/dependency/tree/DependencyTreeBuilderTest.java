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
package org.apache.maven.plugins.dependency.tree;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.dependency.tree.DependencyNode.VerboseInfo;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DependencyTreeBuilderTest {

    @ParameterizedTest
    @CsvSource({
        "1.0, 1.0, duplicate, 1.0",
        "1.0, 2.0, conflict, 2.0",
        "1.0-20261114.120000-1, 1.0-20261114.120000-1, duplicate, 1.0-SNAPSHOT",
        "1.0-SNAPSHOT, 1.0-20261114.120000-1, conflict, 1.0-SNAPSHOT",
        "1.0-20261114.120000-1, 1.0-20261115.080000-2, conflict, 1.0-SNAPSHOT"
    })
    void omittedNodeIsADuplicateOnlyOfTheSameFullVersion(
            String loserVersion, String winnerVersion, String omittedReason, String shownWinnerVersion) {
        DefaultDependencyNode winner = node(winnerVersion);
        DefaultDependencyNode loser = node(loserVersion);
        loser.setData(ConflictResolver.NODE_DATA_WINNER, winner);
        Artifact artifact = RepositoryUtils.toArtifact(loser.getArtifact());

        VerboseInfo info = DependencyTreeBuilder.verboseInfo(loser, artifact);

        assertEquals(omittedReason, info.getOmittedReason());
        assertEquals(shownWinnerVersion, info.getWinnerVersion());
    }

    private static DefaultDependencyNode node(String version) {
        return new DefaultDependencyNode(new Dependency(new DefaultArtifact("g:a:" + version), "compile"));
    }
}
