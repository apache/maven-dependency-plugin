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
package org.apache.maven.artifact.repository.metadata;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;

/**
 * Metadata for the artifact version directory of the repository.
 * <p>
 * Class copied verbatim from maven-compat so that the maven-compat dependency can be dropped. It is used by the
 * unit tests only, never by the plugin itself, and it is a plain data holder over
 * {@link AbstractRepositoryMetadata} (which lives in maven-core), so copying it is enough — there is no component
 * implementation left behind in maven-compat. Same technique as maven-plugin-plugin used for
 * {@code GroupRepositoryMetadata} in MPLUGIN-384.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class SnapshotArtifactRepositoryMetadata extends AbstractRepositoryMetadata {
    private final Artifact artifact;

    public SnapshotArtifactRepositoryMetadata(Artifact artifact) {
        super(createMetadata(artifact, null));
        this.artifact = artifact;
    }

    public SnapshotArtifactRepositoryMetadata(Artifact artifact, Snapshot snapshot) {
        super(createMetadata(artifact, createVersioning(snapshot)));
        this.artifact = artifact;
    }

    @Override
    public boolean storedInGroupDirectory() {
        return false;
    }

    @Override
    public boolean storedInArtifactVersionDirectory() {
        return true;
    }

    @Override
    public String getGroupId() {
        return artifact.getGroupId();
    }

    @Override
    public String getArtifactId() {
        return artifact.getArtifactId();
    }

    @Override
    public String getBaseVersion() {
        return artifact.getBaseVersion();
    }

    @Override
    public Object getKey() {
        return "snapshot " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getBaseVersion();
    }

    @Override
    public boolean isSnapshot() {
        return artifact.isSnapshot();
    }

    @Override
    public int getNature() {
        return isSnapshot() ? SNAPSHOT : RELEASE;
    }

    @Override
    public ArtifactRepository getRepository() {
        return artifact.getRepository();
    }

    @Override
    public void setRepository(ArtifactRepository remoteRepository) {
        artifact.setRepository(remoteRepository);
    }
}
