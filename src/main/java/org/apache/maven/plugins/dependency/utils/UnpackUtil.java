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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.components.io.filemappers.FileMapper;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Provide unpack method in one place for {@link org.apache.maven.plugins.dependency.fromConfiguration.UnpackMojo}
 * and {@link org.apache.maven.plugins.dependency.fromDependencies.UnpackDependenciesMojo}
 */
@Named
@Singleton
public class UnpackUtil {

    /**
     * To look up Archiver/UnArchiver implementations
     */
    private final ArchiverManager archiverManager;

    /**
     * For IDE build support
     */
    private final BuildContext buildContext;

    /**
     * Default constructor.
     *
     * @param archiverManager an archiver {@link ArchiverManager} to use
     * @param buildContext    a build context
     */
    @Inject
    public UnpackUtil(ArchiverManager archiverManager, BuildContext buildContext) {
        this.archiverManager = archiverManager;
        this.buildContext = buildContext;
    }

    /**
     * @param file              file to unpack
     * @param type              file / artifact type
     * @param location          The location.
     * @param includes          includes list.
     * @param excludes          excludes list.
     * @param encoding          the encoding.
     * @param ignorePermissions ignore permissions
     * @param fileMappers       {@link FileMapper}s to be used for rewriting each target path, or {@code null} if no
     *                          rewriting
     *                          shall happen.
     * @param logger            a Mojo logger
     * @throws MojoExecutionException in case of an error.
     */
    public void unpack(
            File file,
            String type,
            File location,
            String includes,
            String excludes,
            String encoding,
            boolean ignorePermissions,
            FileMapper[] fileMappers,
            Log logger)
            throws MojoExecutionException {
        try {
            logUnpack(logger, file, location, includes, excludes);

            location.mkdirs();
            if (!location.exists()) {
                throw new MojoExecutionException(
                        "Location to write unpacked files to could not be created: " + location);
            }

            if (file.isDirectory()) {
                // usual case is a future jar packaging, but there are special cases: classifier and other packaging
                throw new MojoExecutionException("Artifact has not been packaged yet. When used on reactor artifact, "
                        + "unpack should be executed after packaging: see MDEP-98.");
            }

            UnArchiver unArchiver;

            try {
                unArchiver = archiverManager.getUnArchiver(type);
                logger.debug("Found unArchiver: " + unArchiver.getClass().getName() + " by type: " + type);
            } catch (NoSuchArchiverException e) {
                unArchiver = archiverManager.getUnArchiver(file);
                logger.debug("Found unArchiver: " + unArchiver.getClass().getName() + " by file extension: " + file);
            }

            if (encoding != null && unArchiver instanceof ZipUnArchiver) {
                ((ZipUnArchiver) unArchiver).setEncoding(encoding);
                logger.info("Unpacks '" + type + "' with encoding '" + encoding + "'.");
            }

            unArchiver.setIgnorePermissions(ignorePermissions);

            unArchiver.setSourceFile(file);

            unArchiver.setDestDirectory(location);

            if ((excludes != null && !excludes.isEmpty()) || (includes != null && !includes.isEmpty())) {
                // Create the selectors that will filter
                // based on include/exclude parameters
                // MDEP-47
                IncludeExcludeFileSelector[] selectors =
                        new IncludeExcludeFileSelector[] {new IncludeExcludeFileSelector()};

                if (excludes != null && !excludes.isEmpty()) {
                    selectors[0].setExcludes(excludes.split(","));
                }

                if (includes != null && !includes.isEmpty()) {
                    selectors[0].setIncludes(includes.split(","));
                }

                unArchiver.setFileSelectors(selectors);
            }

            unArchiver.setFileMappers(fileMappers);

            unArchiver.extract();
        } catch (NoSuchArchiverException e) {
            throw new MojoExecutionException("Unknown archiver type", e);
        } catch (ArchiverException e) {
            throw new MojoExecutionException("Error unpacking file: " + file + " to: " + location, e);
        }
        buildContext.refresh(location);
    }

    private void logUnpack(Log logger, File file, File location, String includes, String excludes) {
        if (logger.isInfoEnabled()) {
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("Unpacking ");
        msg.append(file);
        msg.append(" to ");
        msg.append(location);

        if (includes != null && excludes != null) {
            msg.append(" with includes \"");
            msg.append(includes);
            msg.append("\" and excludes \"");
            msg.append(excludes);
            msg.append("\"");
        } else if (includes != null) {
            msg.append(" with includes \"");
            msg.append(includes);
            msg.append("\"");
        } else if (excludes != null) {
            msg.append(" with excludes \"");
            msg.append(excludes);
            msg.append("\"");
        }

        logger.info(msg.toString());
    }
}
