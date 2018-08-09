package org.apache.maven.plugins.dependency.analyze;

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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


@Mojo(name = "check", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true, defaultPhase = LifecyclePhase.VALIDATE)
public class CheckMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {

        Map<String, Description> descriptions = new HashMap<>();
        Map<JarFile, Integer> scores = new HashMap<>();

        for (Artifact artifact : project.getArtifacts()) {

            if (!artifact.getType().equals("jar")) {
                getLog().info("skipping " + artifact);
                continue;
            }
            try {
                File file = artifact.getFile();
                JarFile jar = new JarFile(file);
                Enumeration<JarEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();

                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                        continue;
                    }

                    String name = entry.getName();
                    Description description = new Description(jar, readEntry(jar, entry));
                    if (!descriptions.containsKey(name)) {

                        descriptions.put(name, description);
                    } else {
                        Description otherDescription = descriptions.get(name);

                        if (!otherDescription.isSuper(description)) {
                            getLog().warn(name + "(" + otherDescription.jarFile.getName() + ".." + jar.getName() + ")");
                            if (!scores.containsKey(jar)) {
                                scores.put(jar, 0);
                            }
                            scores.put(jar, scores.get(jar) + 1);
                        }
                    }

                }


            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }

        getLog().info("Dependency in order of problems:");
        for (Map.Entry<JarFile, Integer> entry : scores.entrySet()) {
            getLog().info(entry.getKey().getName() + " " + entry.getValue());
        }

    }

    private byte[] readEntry(JarFile jar, JarEntry entry) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = jar.getInputStream(entry)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
        return out.toByteArray();
    }

    private static class Description {

        private final JarFile jarFile;
        private final byte[] bytes;

        public Description(JarFile jarFile, byte[] bytes) {
            this.jarFile = jarFile;

            this.bytes = bytes;
        }


        private boolean isSuper(Description other) {
            return this.bytes.length >= other.bytes.length;
        }
    }
    // subclassed to provide annotations
}
