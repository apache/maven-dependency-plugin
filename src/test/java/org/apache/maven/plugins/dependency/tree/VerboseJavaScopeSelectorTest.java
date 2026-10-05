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

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ConflictContext;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ConflictItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VerboseJavaScopeSelectorTest {

    private static DependencyNode node(String scope) {
        return new DefaultDependencyNode(new Dependency(new DefaultArtifact("g:a:1"), scope));
    }

    private static ConflictContext select(String winnerScope, String loserScope) throws Exception {
        DependencyNode winnerNode = node(winnerScope);
        ConflictItem winner = new ConflictItem(null, winnerNode, 1, ConflictItem.OPTIONAL_FALSE, winnerScope);
        ConflictItem loser = new ConflictItem(null, node(loserScope), 2, ConflictItem.OPTIONAL_FALSE, loserScope);
        ConflictContext context =
                new ConflictContext(node(""), "g:a", Collections.emptyMap(), Arrays.asList(winner, loser));
        context.setWinner(winner);

        new VerboseJavaScopeSelector().selectScope(context);
        return context;
    }

    @Test
    void reportsWiderScopeThatWasNotApplied() throws Exception {
        ConflictContext context = select("system", "compile");

        assertEquals("system", context.getScope());
        assertEquals("compile", context.getWinner().getNode().getData().get(VerboseJavaScopeSelector.REDUCED_SCOPE));
    }

    @Test
    void ignoresScopesOutsideTheJavaScopes() throws Exception {
        ConflictContext context = select("compile", "system");

        assertEquals("compile", context.getScope());
        assertNull(context.getWinner().getNode().getData().get(VerboseJavaScopeSelector.REDUCED_SCOPE));
    }
}
