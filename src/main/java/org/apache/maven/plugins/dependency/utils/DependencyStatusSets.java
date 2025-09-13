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

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;

/**
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public class DependencyStatusSets {
    Set<Artifact> resolvedDependencies = null;

    Set<Artifact> unResolvedDependencies = null;

    Set<Artifact> skippedDependencies = null;

    /**
     * Default ctor.
     */
    public DependencyStatusSets() {}

    /**
     * @param resolved set of {@link Artifact}
     * @param unResolved set of {@link Artifact}
     * @param skipped set of {@link Artifact}
     */
    public DependencyStatusSets(Set<Artifact> resolved, Set<Artifact> unResolved, Set<Artifact> skipped) {
        if (resolved != null) {
            this.resolvedDependencies = new LinkedHashSet<>(resolved);
        }
        if (unResolved != null) {
            this.unResolvedDependencies = new LinkedHashSet<>(unResolved);
        }
        if (skipped != null) {
            this.skippedDependencies = new LinkedHashSet<>(skipped);
        }
    }

    /**
     * @return returns the resolvedDependencies
     */
    public Set<Artifact> getResolvedDependencies() {
        return this.resolvedDependencies;
    }

    /**
     * @param resolvedDependencies the resolvedDependencies to set
     */
    public void setResolvedDependencies(Set<Artifact> resolvedDependencies) {
        if (resolvedDependencies != null) {
            this.resolvedDependencies = new LinkedHashSet<>(resolvedDependencies);
        } else {
            this.resolvedDependencies = null;
        }
    }

    /**
     * @return returns the skippedDependencies
     */
    public Set<Artifact> getSkippedDependencies() {
        return this.skippedDependencies;
    }

    /**
     * @param skippedDependencies the skippedDependencies to set
     */
    public void setSkippedDependencies(Set<Artifact> skippedDependencies) {
        if (skippedDependencies != null) {
            this.skippedDependencies = new LinkedHashSet<>(skippedDependencies);
        } else {
            this.skippedDependencies = null;
        }
    }

    /**
     * @return returns the unResolvedDependencies
     */
    public Set<Artifact> getUnResolvedDependencies() {
        return this.unResolvedDependencies;
    }

    /**
     * @param unResolvedDependencies the unResolvedDependencies to set
     */
    public void setUnResolvedDependencies(Set<Artifact> unResolvedDependencies) {
        if (unResolvedDependencies != null) {
            this.unResolvedDependencies = new LinkedHashSet<>(unResolvedDependencies);
        } else {
            this.unResolvedDependencies = null;
        }
    }
}
