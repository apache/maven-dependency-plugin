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
package org.apache.maven.plugins.dependency.analyze;

import javax.inject.Inject;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @version $Id$
 */
@MojoTest
class TestAnalyzeDuplicateMojo {

    @Inject
    private Log log;

    @Test
    @Basedir("/unit/duplicate-dependencies")
    @InjectMojo(goal = "analyze-duplicate", pom = "plugin-config.xml")
    void testDuplicate(AnalyzeDuplicateMojo mojo) throws Exception {
        when(log.isInfoEnabled()).thenReturn(true);

        mojo.execute();

        ArgumentCaptor<String> logCapture = ArgumentCaptor.forClass(String.class);
        verify(log).info(logCapture.capture());

        assertTrue(logCapture
                .getValue()
                .contains("List of duplicate dependencies defined in <dependencies/> in your pom.xml"));
        assertTrue(logCapture.getValue().contains("junit:junit:jar"));
    }

    @Test
    @Basedir("/unit/duplicate-dependencies")
    @InjectMojo(goal = "analyze-duplicate", pom = "plugin-config2.xml")
    void testDuplicate2(AnalyzeDuplicateMojo mojo) throws Exception {
        when(log.isInfoEnabled()).thenReturn(true);

        mojo.execute();

        ArgumentCaptor<String> logCapture = ArgumentCaptor.forClass(String.class);
        verify(log).info(logCapture.capture());

        assertTrue(logCapture
                .getValue()
                .contains("List of duplicate dependencies defined in <dependencyManagement/> in your pom.xml"));
        assertTrue(logCapture.getValue().contains("junit:junit:jar"));
    }
}
