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
package org.apache.maven.plugins.dependency.testUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.testing.ArtifactStubFactory;
import org.apache.maven.plugins.dependency.fromConfiguration.ArtifactItem;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.archiver.war.WarArchiver;

public class DependencyArtifactStubFactory extends ArtifactStubFactory {
    private boolean flattenedPath = true;
    private ArchiverManager archiverManager;

    public DependencyArtifactStubFactory(File theWorkingDir, boolean theCreateFiles, boolean flattenedPath) {
        this(theWorkingDir, theCreateFiles);
        this.flattenedPath = flattenedPath;
    }

    /**
     * @param theWorkingDir {@link File}
     * @param theCreateFiles true/false.
     */
    public DependencyArtifactStubFactory(File theWorkingDir, boolean theCreateFiles) {
        super(theWorkingDir, theCreateFiles);
    }

    public ArtifactItem getArtifactItem(Artifact artifact) {
        ArtifactItem item = new ArtifactItem(artifact);
        return item;
    }

    public List<ArtifactItem> getArtifactItems(Collection<Artifact> artifacts) {
        List<ArtifactItem> list = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            list.add(getArtifactItem(artifact));
        }
        return list;
    }

    @Override
    public Artifact createArtifact(
            String groupId,
            String artifactId,
            VersionRange versionRange,
            String scope,
            String type,
            String classifier,
            boolean optional)
            throws IOException {
        File workingDir = getWorkingDir();

        if (!flattenedPath) {
            StringBuilder path = new StringBuilder(128);

            path.append(groupId.replace('.', '/')).append('/');

            path.append(artifactId).append('/');

            path.append(ArtifactUtils.toSnapshotVersion(
                    versionRange.getRecommendedVersion().toString()));

            // don't use flatten directories, won't happen at runtime
            setWorkingDir(new File(workingDir, path.toString()));
        }

        Artifact artifact = super.createArtifact(groupId, artifactId, versionRange, scope, type, classifier, optional);

        setWorkingDir(workingDir);

        return artifact;
    }

    @Override
    public void setUnpackableFile(ArchiverManager archiverManager) {
        // it is needed in createUnpackableFile method
        this.archiverManager = archiverManager;
        super.setUnpackableFile(archiverManager);
    }

    /**
     * We need override original method which try to set wrong class of logger on Archiver.
     * <p>
     * Newer version of Archiver use SLF4J instead of Plexus logger.
     */
    @Override
    public void createUnpackableFile(Artifact artifact, File destFile)
            throws NoSuchArchiverException, ArchiverException, IOException {
        Archiver archiver = archiverManager.getArchiver(destFile);

        archiver.setDestFile(destFile);
        archiver.addFile(getSrcFile(), getUnpackableFileName(artifact));

        if (archiver instanceof WarArchiver) {
            WarArchiver war = (WarArchiver) archiver;
            // the use of this is counter-intuitive:
            // http://jira.codehaus.org/browse/PLX-286
            war.setIgnoreWebxml(false);
        }
        archiver.createArchive();
    }
}
