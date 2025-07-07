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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;
import java.io.IOException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Provide a copyFile method in one place.
 *
 * @since 3.7.0
 */
@Named
@Singleton
public class CopyUtil {

    private final BuildContext buildContext;

    private final Logger logger = LoggerFactory.getLogger(CopyUtil.class);

    @Inject
    public CopyUtil(BuildContext buildContext) {
        this.buildContext = buildContext;
    }

    /**
     * Copies the artifact (file)
     *
     * @param sourceArtifact the artifact (file) to copy
     * @param destination file name of destination file
     * @throws IOException if copy has failed
     * @throws MojoExecutionException if artifact file is a directory (which has not been packaged yet)
     *
     * @since 3.7.0
     */
    public void copyArtifactFile(Artifact sourceArtifact, File destination) throws IOException, MojoExecutionException {
        File source = sourceArtifact.getFile();
        if (source.isDirectory()) {
            // usual case is a future jar packaging, but there are special cases: classifier and other packaging
            throw new MojoExecutionException("Artifact '" + sourceArtifact
                    + "' has not been packaged yet (is a directory). When used on reactor artifact, "
                    + "copy should be executed after packaging: see MDEP-187.");
        }
        logger.debug("Copying artifact '{}' ({}) to {}", sourceArtifact.getId(), sourceArtifact.getFile(), destination);
        FileUtils.copyFile(source, destination);
        buildContext.refresh(destination);
    }

    /**
     * Copies a file to a destination and refreshes the build context for the new file.
     *
     * @param source the source file to copy
     * @param destination the destination file
     * @throws IOException if copy has failed
     *
     * @since 3.2.0
     */
    public void copyFile(File source, File destination) throws IOException {
        logger.debug("Copying file '{}' to {}", source, destination);
        FileUtils.copyFile(source, destination);
        buildContext.refresh(destination);
    }
}
