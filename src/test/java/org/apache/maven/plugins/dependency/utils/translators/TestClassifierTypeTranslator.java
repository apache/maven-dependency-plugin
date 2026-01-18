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

import javax.inject.Inject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author brianf
 */
@MojoTest
class TestClassifierTypeTranslator {

    Set<Artifact> artifacts = new HashSet<>();

    @Inject
    private Log log;

    @Inject
    private ArtifactHandlerManager artifactHandlerManager;

    @BeforeEach
    void setUp() throws Exception {
        DependencyArtifactStubFactory factory = new DependencyArtifactStubFactory(null, false);
        artifacts = factory.getMixedArtifacts();
    }

    @Test
    void testNullClassifier() {
        doTestNullEmptyClassifier(null);
    }

    @Test
    void testEmptyClassifier() {
        doTestNullEmptyClassifier("");
    }

    private void doTestNullEmptyClassifier(String classifier) {
        String type = "zip";

        ArtifactTranslator at = new ClassifierTypeTranslator(artifactHandlerManager, classifier, type);
        Set<org.eclipse.aether.artifact.Artifact> results = at.translate(artifacts, log);

        for (Artifact artifact : artifacts) {
            Iterator<org.eclipse.aether.artifact.Artifact> resultIter = results.iterator();
            boolean found = false;
            while (resultIter.hasNext()) {
                org.eclipse.aether.artifact.Artifact translatedArtifact = resultIter.next();
                if (artifact.getArtifactId().equals(translatedArtifact.getArtifactId())
                        && artifact.getGroupId().equals(translatedArtifact.getGroupId())
                /* && artifact.getScope().equals(translatedArtifact.getScope()) */ ) {
                    // classifier is always empty for Resolver sub artifact
                    assertEquals("", translatedArtifact.getClassifier());
                    assertEquals(type, translatedArtifact.getExtension());

                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }
    }

    @Test
    void testNullType() {
        doTestNullEmptyType(null);
    }

    @Test
    void testEmptyType() {
        doTestNullEmptyType("");
    }

    private void doTestNullEmptyType(String type) {
        String classifier = "jdk5";

        ArtifactTranslator at = new ClassifierTypeTranslator(artifactHandlerManager, classifier, type);
        Set<org.eclipse.aether.artifact.Artifact> results = at.translate(artifacts, log);

        for (Artifact artifact : artifacts) {
            Iterator<org.eclipse.aether.artifact.Artifact> resultIter = results.iterator();
            boolean found = false;
            while (!found && resultIter.hasNext()) {
                org.eclipse.aether.artifact.Artifact translatedArtifact = resultIter.next();
                if (artifact.getArtifactId() == translatedArtifact.getArtifactId()
                        && artifact.getGroupId() == translatedArtifact.getGroupId()
                /* && artifact.getScope() == translatedArtifact.getScope() */ ) {
                    // classifier is null, should be the same as the artifact
                    assertEquals(classifier, translatedArtifact.getClassifier());
                    assertEquals(artifact.getType(), translatedArtifact.getExtension());

                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }
    }

    @Test
    void testClassifierAndType() {
        String classifier = "jdk14";
        String type = "sources";
        ArtifactTranslator at = new ClassifierTypeTranslator(artifactHandlerManager, classifier, type);
        Set<org.eclipse.aether.artifact.Artifact> results = at.translate(artifacts, log);

        for (Artifact artifact : artifacts) {
            Iterator<org.eclipse.aether.artifact.Artifact> resultIter = results.iterator();
            boolean found = false;
            while (!found && resultIter.hasNext()) {
                org.eclipse.aether.artifact.Artifact translatedArtifact = resultIter.next();
                if (artifact.getArtifactId() == translatedArtifact.getArtifactId()
                        && artifact.getGroupId() == translatedArtifact.getGroupId()) {
                    assertEquals(translatedArtifact.getClassifier(), classifier);
                    assertEquals(translatedArtifact.getExtension(), type);

                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }
    }

    @Test
    void testGetterSetter() {
        String classifier = "class";
        String type = "type";
        ClassifierTypeTranslator at = new ClassifierTypeTranslator(artifactHandlerManager, classifier, type);

        assertEquals(classifier, at.getClassifier());
        assertEquals(type, at.getType());

        at.setClassifier(type);
        at.setType(classifier);

        assertEquals(type, at.getClassifier());
        assertEquals(classifier, at.getType());
    }
}
