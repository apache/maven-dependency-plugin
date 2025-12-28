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
package org.apache.maven.plugins.dependency.utils;

import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class TestDependencyStatusSets {

    @Test
    void testDependencyStatusSettersGetters() {
        DependencyStatusSets dss = new DependencyStatusSets();
        Set<Artifact> set = new HashSet<>();
        dss.setResolvedDependencies(set);
        assertEquals(set, dss.getResolvedDependencies());

        set = new HashSet<>();
        dss.setUnResolvedDependencies(set);
        assertEquals(set, dss.getUnResolvedDependencies());

        set = new HashSet<>();
        dss.setSkippedDependencies(set);
        assertEquals(set, dss.getSkippedDependencies());

        assertNotSame(dss.getResolvedDependencies(), dss.getSkippedDependencies());
        assertNotSame(dss.getResolvedDependencies(), dss.getUnResolvedDependencies());
        assertNotSame(dss.getSkippedDependencies(), dss.getUnResolvedDependencies());
    }

    @Test
    void testDependencyStatusConstructor() {
        Set<Artifact> r = new HashSet<>();
        Set<Artifact> u = new HashSet<>();
        Set<Artifact> s = new HashSet<>();
        DependencyStatusSets dss = new DependencyStatusSets(r, u, s);
        assertEquals(r, dss.getResolvedDependencies());
        assertEquals(u, dss.getUnResolvedDependencies());
        assertEquals(s, dss.getSkippedDependencies());
    }
}
