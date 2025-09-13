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
package org.apache.maven.plugins.dependency.utils.translators;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public class ClassifierTypeTranslator implements ArtifactTranslator {
    private final ArtifactHandlerManager artifactHandlerManager;

    private String classifier;

    private String type;

    /**
     * @param artifactHandlerManager {@link ArtifactHandlerManager}
     * @param theClassifier the classifier to use
     * @param theType the type
     */
    public ClassifierTypeTranslator(
            ArtifactHandlerManager artifactHandlerManager, String theClassifier, String theType) {
        this.artifactHandlerManager = artifactHandlerManager;
        this.classifier = theClassifier;
        this.type = theType;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.mojo.dependency.utils.translators.ArtifactTranslator#translate(java.util.Set,
     * org.apache.maven.plugin.logging.Log)
     */
    @Override
    public Set<org.eclipse.aether.artifact.Artifact> translate(Set<Artifact> artifacts, Log log) {
        Set<org.eclipse.aether.artifact.Artifact> results;

        log.debug("Translating Artifacts using Classifier: " + this.classifier + " and Type: " + this.type);
        results = new LinkedHashSet<>();
        for (Artifact artifact : artifacts) {
            // this translator must pass both type and classifier here so we
            // will use the
            // base artifact value if null comes in
            final String useType;
            if (this.type != null && !this.type.isEmpty()) {
                useType = this.type;
            } else {
                useType = artifact.getType();
            }

            ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler(useType);

            final String extension;
            if (artifactHandler != null) {
                extension = artifactHandler.getExtension();
            } else {
                extension = this.type;
            }

            String useClassifier;
            if (this.classifier != null && !this.classifier.isEmpty()) {
                useClassifier = this.classifier;
            } else {
                useClassifier = artifact.getClassifier();
            }

            results.add(new SubArtifact(RepositoryUtils.toArtifact(artifact), useClassifier, extension));
        }

        return results;
    }

    /**
     * @return returns the type
     */
    public String getType() {
        return this.type;
    }

    /**
     * @param theType the type to set
     */
    public void setType(String theType) {
        this.type = theType;
    }

    /**
     * @return returns the classifier
     */
    public String getClassifier() {
        return this.classifier;
    }

    /**
     * @param theClassifier the classifier to set
     */
    public void setClassifier(String theClassifier) {
        this.classifier = theClassifier;
    }
}
