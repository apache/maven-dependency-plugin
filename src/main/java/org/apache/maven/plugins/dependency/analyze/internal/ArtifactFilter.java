package org.apache.maven.plugins.dependency.analyze.internal;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;

import java.util.HashSet;
import java.util.Set;

public class ArtifactFilter {

    private final Log log;
    private final String include, exclude;

    public ArtifactFilter(Log log, String include, String exclude) {
        this.log = log;
        this.include = include;
        this.exclude = exclude;
    }

    public Set<Artifact> filter(Set<Artifact> in) {
        Set<Artifact> out = new HashSet<>();

        for (Artifact artifact : in) {
            if (!exclude(artifact) && include(artifact)) {
                out.add(artifact);
            } else {
                log.info("x " + artifact);
            }
        }
        return out;
    }

    private boolean exclude(Artifact artifact) {
        if (exclude == null) {
            return false;
        }

        for (String coordinate : exclude.split(",")) {
            if (artifact.toString().contains(coordinate)) {
                return true;
            }
        }

        return false;
    }

    private boolean include(Artifact artifact) {
        if (include == null) {
            return true;
        }

        for (String coordinate : include.split(",")) {
            if (artifact.toString().contains(coordinate)) {
                return true;
            }
        }

        return false;
    }
}
