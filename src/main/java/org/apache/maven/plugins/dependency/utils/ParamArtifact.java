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

/**
 * Represent artifact data collected from Mojo parameters
 */
public class ParamArtifact {
    private String groupId;

    private String artifactId;

    private String version;

    private String classifier;

    private String packaging;

    private String artifact;

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getPackaging() {
        return packaging;
    }

    public String getArtifact() {
        return artifact;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public void setArtifact(String artifact) {
        this.artifact = artifact;
    }

    /**
     * Determinate if all needed data is set
     */
    public boolean isDataSet() {
        return artifact != null || (groupId != null && artifactId != null && version != null);
    }
}
