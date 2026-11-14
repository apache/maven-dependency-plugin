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

import javax.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.dependency.testUtils.DependencyArtifactStubFactory;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.components.io.filemappers.FileMapper;
import org.codehaus.plexus.components.io.filemappers.FlattenFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MDEP-772: failing when several entries are unpacked to the same path.
 */
@MojoTest(realRepositorySession = true)
@Basedir("/unit/unpack-dependencies-test")
@MojoParameter(name = "artifact", value = "test:one:1.0")
class TestUnpackFailOnDuplicates {

    @TempDir
    private File tempDir;

    @Inject
    private MavenSession session;

    @Inject
    private MavenProject project;

    private DependencyArtifactStubFactory stubFactory;

    @BeforeEach
    void setUp() throws Exception {
        stubFactory = new DependencyArtifactStubFactory(tempDir, true, false);
        session.getRequest().setLocalRepositoryPath(new File(tempDir, "localTestRepo"));
        project.getBuild().setDirectory(new File(tempDir, "target").getAbsolutePath());
    }

    /**
     * Creates a zip whose entries are given as name, content pairs. A name may repeat: java.util.zip refuses duplicate
     * names, so a repeat is written under a placeholder of the same length that is then replaced in the bytes.
     */
    private File zip(String zipName, String... nameAndContent) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Set<String> seen = new HashSet<>();
        Map<String, String> placeholders = new LinkedHashMap<>();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (int i = 0; i < nameAndContent.length; i += 2) {
                String name = nameAndContent[i];
                String written = name;
                if (!seen.add(name)) {
                    char[] chars = name.toCharArray();
                    chars[chars.length - 1] = (char) ('A' + placeholders.size());
                    written = new String(chars);
                    placeholders.put(written, name);
                }
                out.putNextEntry(new ZipEntry(written));
                if (!name.endsWith("/")) {
                    out.write(nameAndContent[i + 1].getBytes(StandardCharsets.UTF_8));
                }
                out.closeEntry();
            }
        }
        String content = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            // the local header and the central directory
            int count = content.split(Pattern.quote(placeholder.getKey()), -1).length - 1;
            assertEquals(2, count, placeholder.getKey());
            content = content.replace(placeholder.getKey(), placeholder.getValue());
        }
        File zip = new File(tempDir, zipName);
        Files.write(zip.toPath(), content.getBytes(StandardCharsets.ISO_8859_1));
        return zip;
    }

    private void artifact(String artifactId, File zip) throws Exception {
        stubFactory.setSrcFile(zip);
        stubFactory.createArtifact("test", artifactId, "1.0", Artifact.SCOPE_COMPILE, "jar", null);
    }

    private static ArtifactItem item(String artifactId, FileMapper... mappers) {
        ArtifactItem item = new ArtifactItem();
        item.setGroupId("test");
        item.setArtifactId(artifactId);
        item.setVersion("1.0");
        item.setType("jar");
        if (mappers.length > 0) {
            item.setFileMappers(mappers);
        }
        return item;
    }

    private static String read(File dir, String name) throws Exception {
        return new String(Files.readAllBytes(new File(dir, name).toPath()), StandardCharsets.UTF_8);
    }

    private static int markerCount(UnpackMojo mojo) {
        String[] markers = mojo.getMarkersDirectory().list();
        return markers == null ? 0 : markers.length;
    }

    private void twoArtifactsWithSameEntry(UnpackMojo mojo) throws Exception {
        artifact("one", zip("one.zip", "dir/", "", "same.txt", "from one", "only-one.txt", "one"));
        artifact("two", zip("two.zip", "dir/", "", "same.txt", "from two", "only-two.txt", "two"));
        mojo.setArtifactItems(Arrays.asList(item("one"), item("two")));
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testDuplicateAcrossArtifactsFails(UnpackMojo mojo) throws Exception {
        twoArtifactsWithSameEntry(mojo);
        mojo.setFailOnDuplicates(true);
        MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        String message = e.getMessage();
        assertTrue(message.contains("same.txt"), message);
        assertTrue(message.contains("test:one:jar:1.0"), message);
        assertTrue(message.contains("test:two:jar:1.0"), message);
        // the directory entry present in both archives and the files that are not duplicated are no findings
        assertFalse(message.contains("only-"), message);
        assertFalse(message.contains("dir"), message);
        // the first copy is kept; the second is not written
        assertEquals("from one", read(mojo.getOutputDirectory(), "same.txt"));
        assertEquals("two", read(mojo.getOutputDirectory(), "only-two.txt"));
        // nothing is marked as unpacked, so that a repeated execution detects the duplicate again
        assertEquals(0, markerCount(mojo));
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testDuplicateAcrossArtifactsIsAllowedByDefault(UnpackMojo mojo) throws Exception {
        twoArtifactsWithSameEntry(mojo);
        assertDoesNotThrow(mojo::execute);
        assertEquals("from two", read(mojo.getOutputDirectory(), "same.txt"));
        assertEquals(2, markerCount(mojo));
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testDuplicateIsReportedWhenExistingFilesAreKept(UnpackMojo mojo) throws Exception {
        twoArtifactsWithSameEntry(mojo);
        mojo.setFailOnDuplicates(true);
        mojo.setOverWriteFiles(false);
        assertThrows(MojoFailureException.class, mojo::execute);
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testExistingFileIsNotADuplicate(UnpackMojo mojo) throws Exception {
        artifact("one", zip("one.zip", "same.txt", "from one"));
        mojo.setArtifactItems(Collections.singletonList(item("one")));
        mojo.getOutputDirectory().mkdirs();
        Files.write(new File(mojo.getOutputDirectory(), "same.txt").toPath(), "old".getBytes(StandardCharsets.UTF_8));
        mojo.setFailOnDuplicates(true);
        assertDoesNotThrow(mojo::execute);
        assertEquals("from one", read(mojo.getOutputDirectory(), "same.txt"));
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testDuplicateEntryInOneArchiveFails(UnpackMojo mojo) throws Exception {
        artifact("one", zip("one.zip", "a.txt", "first", "a.txt", "second"));
        mojo.setArtifactItems(Collections.singletonList(item("one")));
        mojo.setFailOnDuplicates(true);
        MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("a.txt"), e.getMessage());
        assertEquals("first", read(mojo.getOutputDirectory(), "a.txt"));
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testDuplicateCreatedByFileMapperFails(UnpackMojo mojo) throws Exception {
        artifact("one", zip("one.zip", "x/a.txt", "x", "y/a.txt", "y"));
        mojo.setArtifactItems(Collections.singletonList(item("one", new FlattenFileMapper())));
        mojo.setFailOnDuplicates(true);
        MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("a.txt"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testSameNamesWithoutFileMapperAreNotDuplicates(UnpackMojo mojo) throws Exception {
        artifact("one", zip("one.zip", "x/a.txt", "x", "y/a.txt", "y"));
        mojo.setArtifactItems(Collections.singletonList(item("one")));
        mojo.setFailOnDuplicates(true);
        assertDoesNotThrow(mojo::execute);
        assertEquals("y", read(mojo.getOutputDirectory(), "y/a.txt"));
        assertEquals(1, markerCount(mojo));
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testSameEntryInDifferentOutputDirectoriesIsNotADuplicate(UnpackMojo mojo) throws Exception {
        artifact("one", zip("one.zip", "same.txt", "from one"));
        artifact("two", zip("two.zip", "same.txt", "from two"));
        ArtifactItem one = item("one");
        one.setOutputDirectory(new File(tempDir, "out-one"));
        ArtifactItem two = item("two");
        two.setOutputDirectory(new File(tempDir, "out-two"));
        mojo.setArtifactItems(Arrays.asList(one, two));
        mojo.setFailOnDuplicates(true);
        assertDoesNotThrow(mojo::execute);
        assertEquals("from two", read(new File(tempDir, "out-two"), "same.txt"));
    }

    @Test
    @InjectMojo(goal = "unpack")
    void testNamesDifferingByCaseFailOnlyOnCaseInsensitiveFileSystem(UnpackMojo mojo) throws Exception {
        Path probe = tempDir.toPath().resolve("Probe.txt");
        Files.write(probe, new byte[0]);
        boolean caseInsensitive = Files.exists(tempDir.toPath().resolve("probe.txt"))
                && Files.isSameFile(probe, tempDir.toPath().resolve("probe.txt"));
        artifact("one", zip("one.zip", "A.txt", "upper", "a.txt", "lower"));
        mojo.setArtifactItems(Collections.singletonList(item("one")));
        mojo.setFailOnDuplicates(true);
        if (caseInsensitive) {
            MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
            assertTrue(e.getMessage().contains("A.txt"), e.getMessage());
            assertTrue(e.getMessage().contains("(a.txt)"), e.getMessage());
        } else {
            // on a case-sensitive file system these are two files
            assertDoesNotThrow(mojo::execute);
            assertEquals("upper", read(mojo.getOutputDirectory(), "A.txt"));
            assertEquals("lower", read(mojo.getOutputDirectory(), "a.txt"));
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @InjectMojo(goal = "unpack")
    void testOutputDirectoryReachedThroughSymbolicLinkIsTheSame(UnpackMojo mojo) throws Exception {
        Path real = Files.createDirectories(tempDir.toPath().resolve("real-out"));
        Path link = Files.createSymbolicLink(tempDir.toPath().resolve("link-out"), real);
        artifact("one", zip("one.zip", "same.txt", "from one"));
        artifact("two", zip("two.zip", "same.txt", "from two"));
        ArtifactItem one = item("one");
        one.setOutputDirectory(real.toFile());
        ArtifactItem two = item("two");
        two.setOutputDirectory(link.toFile());
        mojo.setArtifactItems(Arrays.asList(one, two));
        mojo.setFailOnDuplicates(true);
        MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("same.txt"), e.getMessage());
    }
}
