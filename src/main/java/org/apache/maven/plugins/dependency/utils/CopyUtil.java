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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.FileUtils;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Provide a copyFile method in one place.
 */
@Named
@Singleton
public class CopyUtil {

    private final BuildContext buildContext;

    @Inject
    public CopyUtil(BuildContext buildContext) {
        this.buildContext = buildContext;
    }

    /**
     * Does the actual copy of the file and logging.
     *
     * @param source represents the file to copy.
     * @param destination file name of destination file.
     * @throws MojoExecutionException with a message if an error occurs.
     */
    public void copyFile(Log log, File source, File destination) throws MojoExecutionException {
        try {
            log.info("Copying " + source + " to " + destination);

            if (source.isDirectory()) {
                // usual case is a future jar packaging, but there are special cases: classifier and other packaging
                throw new MojoExecutionException("Artifact has not been packaged yet. When used on reactor artifact, "
                        + "copy should be executed after packaging: see MDEP-187.");
            }

            FileUtils.copyFile(source, destination);
            buildContext.refresh(destination);
        } catch (IOException e) {
            throw new MojoExecutionException("Error copying artifact from " + source + " to " + destination, e);
        }
    }
}
