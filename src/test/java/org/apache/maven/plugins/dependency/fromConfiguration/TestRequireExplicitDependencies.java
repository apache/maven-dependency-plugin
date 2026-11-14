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
package org.apache.maven.plugins.dependency.fromConfiguration;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MojoTest(realRepositorySession = true)
class TestRequireExplicitDependencies {

    private static Dependency dependency(String artifactId, String version, String type, String classifier) {
        Dependency dependency = new Dependency();
        dependency.setGroupId("g");
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        dependency.setType(type);
        dependency.setClassifier(classifier);
        return dependency;
    }

    private static ArtifactItem item(String artifactId, String version, String type, String classifier) {
        ArtifactItem item = new ArtifactItem();
        item.setGroupId("g");
        item.setArtifactId(artifactId);
        item.setVersion(version);
        item.setType(type);
        item.setClassifier(classifier);
        return item;
    }

    private static void setSource(CopyMojo mojo, MojoExecution execution) throws Exception {
        Field field = AbstractFromConfigurationMojo.class.getDeclaredField("mojoExecution");
        field.setAccessible(true);
        field.set(mojo, execution);
    }

    private static MojoExecution lifecycleExecution() {
        return new MojoExecution(new org.apache.maven.model.Plugin(), "copy", "test");
    }

    @Test
    @InjectMojo(goal = "copy")
    @MojoParameter(name = "requireExplicitDependencies", value = "true")
    void reportsEveryUndeclaredItemWithTheFix(CopyMojo mojo) throws Exception {
        setSource(mojo, lifecycleExecution());
        mojo.getProject().setDependencies(Collections.singletonList(dependency("a", "1.0", "jar", null)));
        List<ArtifactItem> items = new ArrayList<>();
        items.add(item("a", "1.0", "jar", null));
        items.add(item("b", "1.0", "zip", "resources"));
        items.add(item("a", "2.0", "jar", null));
        mojo.setArtifactItems(items);

        MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);

        String nl = System.lineSeparator();
        assertEquals(
                "requireExplicitDependencies is set, but artifactItems must be declared as dependencies of the "
                        + "project, so that Maven orders the reactor by them:" + nl
                        + "  g:b:resources:1.0:zip is not declared as a dependency of the project" + nl
                        + "  g:a:2.0:jar is declared as a dependency with version 1.0, not 2.0" + nl
                        + "Declare each of them in the dependencies of the project, for example with scope provided "
                        + "or test, and use the same version.",
                e.getMessage());
    }

    @Test
    @InjectMojo(goal = "copy")
    @MojoParameter(name = "requireExplicitDependencies", value = "true")
    @MojoParameter(name = "artifact", value = "g:undeclared:1.0")
    void checksAnArtifactConfiguredInThePom(CopyMojo mojo) throws Exception {
        setSource(mojo, lifecycleExecution());
        mojo.getProject().setDependencies(Collections.singletonList(dependency("a", "1.0", "jar", null)));

        MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);

        assertTrue(e.getMessage().contains("g:undeclared:1.0:jar is not declared"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "copy")
    @MojoParameter(name = "requireExplicitDependencies", value = "true")
    @MojoParameter(name = "artifact", value = "g:undeclared:1.0")
    void skipsTheCheckForAGoalInvokedFromTheCommandLine(CopyMojo mojo) throws Exception {
        setSource(mojo, new MojoExecution(new MojoDescriptor(), "default-cli", MojoExecution.Source.CLI));
        mojo.getProject().setDependencies(Collections.<Dependency>emptyList());

        // the check passes; the execution then fails later, while resolving the artifact
        Exception e = assertThrows(Exception.class, mojo::execute);

        assertTrue(!(e instanceof MojoFailureException), String.valueOf(e));
    }

    @Test
    @InjectMojo(goal = "copy")
    @MojoParameter(name = "requireExplicitDependencies", value = "true")
    void versionlessItemMustResolveToTheDeclaredVariant(CopyMojo mojo) throws Exception {
        setSource(mojo, lifecycleExecution());
        // g:a:jar:tests without a version falls back to the loose match g:a:2.0, which is not the test-jar
        mojo.getProject()
                .setDependencies(java.util.Arrays.asList(
                        dependency("a", "2.0", "jar", null), dependency("a", "1.0", "test-jar", null)));
        mojo.setArtifactItems(Collections.singletonList(item("a", null, "jar", "tests")));

        MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);

        assertTrue(e.getMessage().contains("is declared as a dependency with version 1.0, not 2.0"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "copy")
    @MojoParameter(name = "requireExplicitDependencies", value = "true")
    void listsAVersionlessItemThatIsNotDeclaredWithoutLookingUpItsVersion(CopyMojo mojo) throws Exception {
        setSource(mojo, lifecycleExecution());
        mojo.getProject().setDependencies(Collections.singletonList(dependency("a", "1.0", "jar", null)));
        mojo.setArtifactItems(Collections.singletonList(item("undeclared", null, "jar", null)));

        MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);

        assertTrue(e.getMessage().contains("g:undeclared"), e.getMessage());
        assertTrue(e.getMessage().contains("is not declared as a dependency of the project"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "copy")
    @MojoParameter(name = "requireExplicitDependencies", value = "true")
    void namesTheVariantsThatAreDeclaredInstead(CopyMojo mojo) throws Exception {
        setSource(mojo, lifecycleExecution());
        mojo.getProject()
                .setDependencies(java.util.Arrays.asList(
                        dependency("a", "1.0", "jar", null), dependency("a", "1.0", "zip", "resources")));
        mojo.setArtifactItems(Collections.singletonList(item("a", "1.0", "war", null)));

        MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);

        assertTrue(
                e.getMessage()
                        .contains("is not declared as a dependency of the project (declared as jar, zip:resources)"),
                e.getMessage());
    }
}
