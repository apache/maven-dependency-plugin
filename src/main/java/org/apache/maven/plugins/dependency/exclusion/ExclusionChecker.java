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
package org.apache.maven.plugins.dependency.exclusion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;

class ExclusionChecker {

    private final Map<Coordinates, List<Coordinates>> violations = new HashMap<>();

    Map<Coordinates, List<Coordinates>> getViolations() {
        return violations;
    }

    void check(Coordinates artifact, Set<Coordinates> excludes, Set<Coordinates> actualDependencies) {
        List<Coordinates> invalidExclusions = excludes.stream()
                .filter(exclude -> actualDependencies.stream().noneMatch(exclude.getExclusionPattern()))
                .collect(toList());

        if (!invalidExclusions.isEmpty()) {
            violations.put(artifact, invalidExclusions);
        }
    }
}
