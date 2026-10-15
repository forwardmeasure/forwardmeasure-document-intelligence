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

import java.util.Objects;

public record ConversionPayload(byte[] content, String mediaType) {
  public ConversionPayload {
    Objects.requireNonNull(content, "content");
    if (content.length == 0) throw new IllegalArgumentException("empty conversion response");
    if (mediaType == null || mediaType.isBlank())
      throw new IllegalArgumentException("response media type is required");
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }

  @Override
  public String toString() {
    return "ConversionPayload[mediaType=" + mediaType + ",size=" + content.length + "]";
  }
}
