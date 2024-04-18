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

import java.lang.reflect.Proxy;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Objects;
import java.util.function.Predicate;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.InputLocation;

/**
 * Simple "record" to hold the coordinates of the dependency which is excluded.
 * <p>
 * When dealing with exclusions the version is not important. Only groupId and artifactId is used.
 * </p>
 */
class Coordinates implements Comparable<Coordinates> {

    private final String groupId;

    private final String artifactId;

    private final Dependency dependency;

    private final InputLocation location;

    private Coordinates(String groupId, String artifactId, Dependency dependency, InputLocation location) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.dependency = dependency;
        this.location = location;
    }

    public static Coordinates coordinates(String groupId, String artifactId) {
        return new Coordinates(groupId, artifactId, null, null);
    }

    public static Coordinates coordinates(Dependency dependency) {
        return new Coordinates(dependency.getGroupId(), dependency.getArtifactId(), dependency, null);
    }

    public static Coordinates coordinates(Exclusion exclusion) {
        return new Coordinates(exclusion.getGroupId(), exclusion.getArtifactId(), null, exclusion.getLocation(""));
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public Dependency getDependency() {
        return dependency;
    }

    Predicate<Coordinates> getExclusionPattern() {
        PathMatcher groupId = FileSystems.getDefault().getPathMatcher("glob:" + getGroupId());
        PathMatcher artifactId = FileSystems.getDefault().getPathMatcher("glob:" + getArtifactId());
        Predicate<Coordinates> predGroupId = a -> groupId.matches(createPathProxy(a.getGroupId()));
        Predicate<Coordinates> predArtifactId = a -> artifactId.matches(createPathProxy(a.getArtifactId()));

        return predGroupId.and(predArtifactId);
    }

    /**
     * In order to reuse the glob matcher from the filesystem, we need
     * to create Path instances.  Those are only used with the toString method.
     * This hack works because the only system-dependent thing is the path
     * separator which should not be part of the groupId or artifactId.
     */
    private static Path createPathProxy(String value) {
        return (Path) Proxy.newProxyInstance(
                Coordinates.class.getClassLoader(), new Class[] {Path.class}, (proxy1, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return value;
                    }
                    throw new UnsupportedOperationException();
                });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Coordinates that = (Coordinates) o;
        return Objects.equals(groupId, that.groupId)
                && Objects.equals(artifactId, that.artifactId)
                && Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, location);
    }

    @Override
    public int compareTo(Coordinates that) {

        if (location != null && that.location != null) {
            return location.getLineNumber() - that.location.getLineNumber();
        }

        return toString().compareTo(that.toString());
    }

    @Override
    public String toString() {
        String version = "";
        if (dependency != null) {
            version = ":" + dependency.getVersion();
        }
        String line = "";
        if (location != null) {
            line = " @ line: " + location.getLineNumber();
        }
        return groupId + ":" + artifactId + version + line;
    }
}
