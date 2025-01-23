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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.handler.manager.DefaultArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.plugin.testing.stubs.StubArtifactRepository;
import org.apache.maven.plugins.dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;

/**
 * @author brianf
 */
public class TestClassifierTypeTranslator extends AbstractDependencyMojoTestCase {
    Set<Artifact> artifacts = new HashSet<>();

    ArtifactRepository artifactRepository;

    Log log = new SilentLog();

    private ArtifactHandlerManager artifactHandlerManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp("classifiertype-translator", false);

        artifactHandlerManager = new DefaultArtifactHandlerManager();
        this.setVariableValueToObject(artifactHandlerManager, "artifactHandlers", new HashMap<>());

        artifactRepository = new StubArtifactRepository(null);

        DependencyArtifactStubFactory factory = new DependencyArtifactStubFactory(null, false);
        artifacts = factory.getMixedArtifacts();

        LegacySupport legacySupport = lookup(LegacySupport.class);
        MavenSession session = newMavenSession(new MavenProjectStub());
        legacySupport.setSession(session);

        installLocalRepository(legacySupport);
    }

    public void testNullClassifier() {
        doTestNullEmptyClassifier(null);
    }

    public void testEmptyClassifier() {
        doTestNullEmptyClassifier("");
    }

    public void doTestNullEmptyClassifier(String classifier) {
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

    public void testNullType() {
        doTestNullEmptyType(null);
    }

    public void testEmptyType() {
        doTestNullEmptyType("");
    }

    public void doTestNullEmptyType(String type) {
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

    public void testClassifierAndType() {
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

    public void testGetterSetter() {
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
