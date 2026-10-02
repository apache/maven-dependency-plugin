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

import static org.assertj.core.api.Assertions.assertThat

String dependency = "org.apache.commons:commons-lang3:jar:3.18.0:compile"
String ascii = new File(basedir, "auto.txt").getText("UTF-8")
assertThat(ascii).contains("\\- " + dependency).doesNotContain("├", "└", "─", "│")
assertThat(new File(basedir, "standard.txt").getText("UTF-8")).isEqualTo(ascii)
assertThat(new File(basedir, "invalid.txt").getText("UTF-8")).isEqualTo(ascii)
assertThat(new File(basedir, "extended.txt").getText("UTF-8")).contains("└─ " + dependency)
assertThat(new File(basedir, "whitespace.txt").getText("UTF-8")).contains("   " + dependency)
        .doesNotContain("\\-", "└")
assertThat(new File(basedir, "tree.log").getText("UTF-8")).contains("\\- " + dependency)
        .doesNotContain("├", "└", "─", "│")

return true
