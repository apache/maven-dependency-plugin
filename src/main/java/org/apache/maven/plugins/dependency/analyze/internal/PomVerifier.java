package org.apache.maven.plugins.dependency.analyze.internal;

import org.apache.maven.plugin.logging.Log;

import java.io.*;

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

/**
 * Utility class for verifying a pom is OK.
 */
public class PomVerifier {
    private final Log log;
    private final File baseDir;
    private final String command;

    public PomVerifier(Log log, File baseDir, String command) {
        this.log = log;
        this.baseDir = baseDir;
        this.command = command;
    }

    void verify() throws IOException, InterruptedException {

        Process process = new ProcessBuilder()
                .directory(baseDir)
                .command(command.split(" "))
                .start();

        log(process.getInputStream(), new LogConsumer() {
            @Override
            public void consume(String line) {
                log.info(line);
            }
        });
        log(process.getErrorStream(), new LogConsumer() {
            @Override
            public void consume(String line) {
                log.error(line);
            }
        });

        if (process.waitFor() != 0) {
            throw new IllegalStateException(String.valueOf(process.exitValue()));
        }
    }

    private void log(final InputStream inputStream, final LogConsumer log) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))) {

                    String line;
                    while ((line = in.readLine()) != null) {
                        log.consume(line);
                    }

                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }).start();
    }

    private interface LogConsumer {
        void consume(String line);
    }
}