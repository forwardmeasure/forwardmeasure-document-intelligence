/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *     https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.forwardmeasure.documentintelligence.validation;

public record ValidationLimits(
    long maxXmlBytes,
    int maxDepth,
    int maxElements,
    long maxArchiveBytes,
    int maxArchiveMembers,
    long maxExpandedBytes,
    long maxMemberBytes,
    double maxCompressionRatio) {
  public ValidationLimits {
    if (maxXmlBytes < 1
        || maxDepth < 1
        || maxElements < 1
        || maxArchiveBytes < 1
        || maxArchiveMembers < 1
        || maxExpandedBytes < 1
        || maxMemberBytes < 1
        || !Double.isFinite(maxCompressionRatio)
        || maxCompressionRatio < 1)
      throw new IllegalArgumentException("validation limits must be finite and positive");
  }

  public static ValidationLimits defaults() {
    return new ValidationLimits(
        16L * 1024 * 1024,
        128,
        100000,
        64L * 1024 * 1024,
        1000,
        128L * 1024 * 1024,
        32L * 1024 * 1024,
        100);
  }
}
