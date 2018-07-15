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

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

/**
 * Fixes analysis warnings in poms.
 */
public class WarningFixer {
    private final Log log;
    private final File baseDir;
    private final String indent;
    private final PomVerifier verifier;
    private final Set<Artifact> usedUndeclaredArtifacts;
    private final Set<Artifact> unusedDeclaredArtifacts;

    public WarningFixer(Log log, File baseDir, String indent, PomVerifier verifier, Set<Artifact> usedUndeclaredArtifacts, Set<Artifact> unusedDeclaredArtifacts) {
        this.log = log;
        this.baseDir = baseDir;
        this.indent = indent;
        this.verifier = verifier;
        this.usedUndeclaredArtifacts = usedUndeclaredArtifacts;
        this.unusedDeclaredArtifacts = unusedDeclaredArtifacts;
    }


    public void execute() throws Exception {

        log.info("Fixing warnings.");

        if (nothingToDo()) {
            log.info("Nothing to do.");
            return;
        }
        verifier.verify();

        addUsedUndeclaredDependencies();

        try {
            tryToRemoveAllUnusedDeclaredDependenciesAtOnce();
        } catch (Exception e) {
            tryToRemoveUnusedDeclaredDependenciesOneByONe();
        }
        // TODO - this is a double-check, and should not really be needed
        log.info("Double-checking.");
        verifier.verify();
    }

    private boolean nothingToDo() {
        return usedUndeclaredArtifacts.isEmpty() && unusedDeclaredArtifacts.isEmpty();
    }

    private void tryToRemoveUnusedDeclaredDependenciesOneByONe() throws Exception {
        log.info("Remove " + unusedDeclaredArtifacts.size() + " unused declared dependencies.");

        PomEditor editor = new PomEditor(new File(baseDir, "pom.xml"), indent);
        for (Artifact artifact : unusedDeclaredArtifacts) {
            log.info("- " + artifact);
            PomBackup backup = new PomBackup(baseDir);
            try {
                editor.removeArtifact(artifact);
                verifier.verify();
                editor.save();
                backup.discard();
                logSuccess();
            } catch (Exception e) {
                logFailure(e);
                backup.restore();
            }
        }
    }


    private void tryToRemoveAllUnusedDeclaredDependenciesAtOnce() throws Exception {
        log.info("Remove all " + unusedDeclaredArtifacts.size() + " unused declared dependencies.");
        PomBackup backup = new PomBackup(baseDir);
        try {
            PomEditor editor = new PomEditor(new File(baseDir, "pom.xml"), indent);
            for (Artifact artifact : unusedDeclaredArtifacts) {
                log.info("- " + artifact);
                editor.removeArtifact(artifact);
            }
            editor.save();
            backup.discard();
            logSuccess();
        } catch (Exception e) {
            logFailure(e);
            backup.restore();
        }
    }

    private void logSuccess() {
        log.info("Success.");
    }

    private void addUsedUndeclaredDependencies() throws IOException, InterruptedException {
        log.info("Add " + usedUndeclaredArtifacts.size() + " used undeclared artifact(s).");
        PomBackup pomBackup = new PomBackup(baseDir);
        try {
            PomEditor pomEditor = new PomEditor(new File(baseDir, "pom.xml"), indent);
            for (Artifact artifact : new TreeSet<>(usedUndeclaredArtifacts)) {
                log.info("+ " + artifact);
                pomEditor.addDependency(artifact);
            }
            pomEditor.save();
            verifier.verify();
            pomBackup.discard();
            logSuccess();
        } catch (Exception e) {
            logFailure(e);
            pomBackup.restore();
            throw e;
        }
    }

    private void logFailure(Exception e) {
        log.info("Failed - " + e.getMessage() + ".");
    }
}
