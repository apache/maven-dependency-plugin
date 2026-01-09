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
package org.apache.maven.plugins.dependency.fromDependencies;

import java.util.Arrays;
import java.util.Collection;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrDependencyMatcherTest {

    @ParameterizedTest
    @NullAndEmptySource
    void illegalArgument(Collection<DependencyMatcher> source) {
        assertThatThrownBy(() -> new OrDependencyMatcher(source))
                .hasMessage("There must be at least 1 dependencyMatcher");
    }

    @ParameterizedTest
    @CsvSource({
        "true, true",
        "false, false",
    })
    void singleMatcher(boolean input, boolean result) {
        Collection<DependencyMatcher> matchers = Arrays.asList(d -> input);
        assertThat(new OrDependencyMatcher(matchers).matches(null)).isEqualTo(result);
    }

    @ParameterizedTest
    @CsvSource({
        "true,  true,  true",
        "true,  false, true",
        "false, true,  true",
        "false, false, false",
    })
    void doubleMatcher(boolean input1, boolean input2, boolean result) {
        Collection<DependencyMatcher> matchers = Arrays.asList(d -> input1, d -> input2);
        assertThat(new OrDependencyMatcher(matchers).matches(null)).isEqualTo(result);
    }
}
