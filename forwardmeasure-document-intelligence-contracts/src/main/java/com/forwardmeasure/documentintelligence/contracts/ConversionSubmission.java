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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardmeasure.documentintelligence.docling.generated.model.ConvertDocumentsOptions;
import java.util.Objects;

public record ConversionSubmission(
    String operationId,
    TaskHandle task,
    ArtifactReference source,
    String filename,
    ConvertDocumentsOptions options,
    ConversionContext context,
    String optionsSha256) {
  public ConversionSubmission {
    Digests.requireSha256(operationId, "operation ID");
    Digests.requireSha256(optionsSha256, "options hash");
    Objects.requireNonNull(task, "task");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(filename, "filename");
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(context, "context");
    options = copyOptions(options);
  }

  /** Generated options are mutable; durable submissions own a defensive snapshot. */
  @Override
  public ConvertDocumentsOptions options() {
    return copyOptions(options);
  }

  private static ConvertDocumentsOptions copyOptions(ConvertDocumentsOptions options) {
    return new ObjectMapper().convertValue(options, ConvertDocumentsOptions.class);
  }
}
