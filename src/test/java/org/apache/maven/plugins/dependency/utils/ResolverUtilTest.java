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
package org.apache.maven.plugins.dependency.utils;

import javax.inject.Provider;

import java.util.stream.Stream;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.of;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolverUtilTest {

    @Mock
    private MavenExecutionRequest executionRequest;

    @Mock
    private RepositorySystemSession repositorySystemSession;

    @Mock
    private MavenSession mavenSession;

    @Mock
    private Provider<MavenSession> sessionProvider;

    @InjectMocks
    private ResolverUtil resolverUtil;

    public static Stream<Arguments> prepareRepositoryTest() {

        return Stream.of(
                of("", "temp", "default", ""),
                of("https://repo.maven.apache.org", "temp", "default", "https://repo.maven.apache.org"),
                of("central::https://repo.maven.apache.org", "central", "default", "https://repo.maven.apache.org"),
                of("central::::https://repo.maven.apache.org", "central", "default", "https://repo.maven.apache.org"),
                of(
                        "central::layout2::https://repo.maven.apache.org",
                        "central",
                        "layout2",
                        "https://repo.maven.apache.org"));
    }

    @ParameterizedTest
    @MethodSource
    void prepareRepositoryTest(String repository, String id, String type, String url) {
        when(sessionProvider.get()).thenReturn(mavenSession);
        when(mavenSession.getRepositorySession()).thenReturn(repositorySystemSession);
        when(mavenSession.getRequest()).thenReturn(executionRequest);
        when(executionRequest.isUpdateSnapshots()).thenReturn(true);

        RemoteRepository remoteRepository = resolverUtil.prepareRemoteRepository(repository);

        assertThat(remoteRepository).isNotNull();
        assertThat(remoteRepository.getId()).isEqualTo(id);
        assertThat(remoteRepository.getContentType()).isEqualTo(type);
        assertThat(remoteRepository.getUrl()).isEqualTo(url);

        RepositoryPolicy snapshotPolicy = remoteRepository.getPolicy(true);
        assertThat(snapshotPolicy).isNotNull();
        assertThat(snapshotPolicy.getUpdatePolicy()).isEqualTo(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        assertThat(snapshotPolicy.getChecksumPolicy()).isEqualTo(RepositoryPolicy.CHECKSUM_POLICY_WARN);

        RepositoryPolicy releasePolicy = remoteRepository.getPolicy(true);
        assertThat(releasePolicy).isNotNull();
        assertThat(releasePolicy.getUpdatePolicy()).isEqualTo(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        assertThat(releasePolicy.getChecksumPolicy()).isEqualTo(RepositoryPolicy.CHECKSUM_POLICY_WARN);
    }

    @Test
    void prepareRepositoryWithNull() {
        assertThatCode(() -> resolverUtil.prepareRemoteRepository(null))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("repository must be not null");
    }
}
