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
package com.forwardmeasure.documentintelligence.contracts;

import java.net.URI;
import java.util.Objects;

public record ArtifactReference(
    URI uri, String versionId, String sha256, long size, String mediaType) {
  public ArtifactReference {
    Objects.requireNonNull(uri, "uri");
    if (!uri.isAbsolute()
        || uri.getRawUserInfo() != null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null)
      throw new IllegalArgumentException(
          "artifact URI must be an absolute storage identity without credentials or query");
    Digests.requireSha256(sha256, "artifact hash");
    if (size < 0) throw new IllegalArgumentException("artifact size must not be negative");
    if (mediaType == null || mediaType.isBlank())
      throw new IllegalArgumentException("artifact media type is required");
    if (versionId != null && versionId.isBlank())
      throw new IllegalArgumentException("object version must not be blank");
  }
}
