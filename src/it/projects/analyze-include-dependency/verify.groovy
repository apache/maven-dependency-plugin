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

File file = new File( basedir, "build.log" );
assert file.exists();

String buildLog = file.getText( "UTF-8" );

// assert that the dependencies have been put into the expected buckets
assert onlyWhiteSpaceAndInfoBetween(buildLog, "Not included unused declared dependencies:", "org.apache.maven:maven-project")
assert onlyWhiteSpaceAndInfoBetween(buildLog, "Ignored non-test scoped test only dependencies:", "org.apache.maven:maven-settings")

return true;

boolean onlyWhiteSpaceAndInfoBetween(String input, String left, String right) {
  int indexOfLeft = input.indexOf(left)
  if (indexOfLeft == -1) {
    return false;
  }
  String afterLeft = input.substring(indexOfLeft + left.length());
  int indexOfRight = afterLeft.indexOf(right);
  if (indexOfRight == -1) {
    return false;
  }
  int indexBeforeRight = indexOfRight -1;
  if (indexBeforeRight < 0) {
    return false;
  }
  String result = afterLeft.substring(0, indexBeforeRight).replaceAll(/(?m)\s*\[INFO\]\s*/,'');
  return result.length() == 0;
}