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

public record BinaryDocument(String filename, byte[] content) {
  public BinaryDocument {
    Objects.requireNonNull(content, "content");
    if (content.length == 0) throw new IllegalArgumentException("document must not be empty");
    if (filename == null
        || filename.isBlank()
        || filename.length() > 255
        || filename.contains("/")
        || filename.contains("\\")
        || filename.contains("\0")
        || filename.equals(".")
        || filename.equals(".."))
      throw new IllegalArgumentException("filename must be a bounded basename");
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }

  public long size() {
    return content.length;
  }

  public String sha256() {
    return Digests.sha256(content);
  }

  @Override
  public String toString() {
    return "BinaryDocument[filename=" + filename + ",size=" + content.length + "]";
  }
}
