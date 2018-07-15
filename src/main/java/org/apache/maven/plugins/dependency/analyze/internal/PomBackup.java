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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Utility class for creating, discarding, and restoring backups of poms.
 */
class PomBackup {
    private final Path pom;
    private final Path pomBackup;

    PomBackup(File baseDir) throws IOException {
        this.pom = new File(baseDir, "pom.xml").toPath();
        this.pomBackup = new File(baseDir, "pom.xml.backup").toPath();
        Files.copy(pom, pomBackup, REPLACE_EXISTING);
    }

    void restore() throws IOException {
        Files.move(pomBackup, pom, REPLACE_EXISTING);
    }

    void discard() throws IOException {
        Files.delete(pomBackup);
    }
}
