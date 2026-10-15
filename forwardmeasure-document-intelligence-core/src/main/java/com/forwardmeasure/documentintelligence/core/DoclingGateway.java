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
package com.forwardmeasure.documentintelligence.core;

import com.forwardmeasure.documentintelligence.contracts.BinaryDocument;
import com.forwardmeasure.documentintelligence.contracts.ConversionOptions;
import com.forwardmeasure.documentintelligence.contracts.ConversionPayload;
import com.forwardmeasure.documentintelligence.contracts.TaskHandle;
import com.forwardmeasure.documentintelligence.contracts.TaskProgress;
import com.forwardmeasure.documentintelligence.docling.generated.model.CapabilitiesResponse;
import com.forwardmeasure.documentintelligence.docling.generated.model.HierarchicalChunkerOptionsDocumentsRequest;
import com.forwardmeasure.documentintelligence.docling.generated.model.HybridChunkerOptionsDocumentsRequest;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Synchronous transport primitives; task handles can be persisted by the owning workflow. */
public interface DoclingGateway extends AutoCloseable {
  Map<String, Object> versions();

  boolean ready();

  CapabilitiesResponse capabilities();

  ConversionPayload convert(BinaryDocument source, ConversionOptions options);

  TaskHandle submit(BinaryDocument source, ConversionOptions options);

  TaskProgress poll(TaskHandle task, Duration wait);

  ConversionPayload fetch(TaskHandle task);

  ConversionPayload chunk(HierarchicalChunkerOptionsDocumentsRequest request);

  ConversionPayload chunk(HybridChunkerOptionsDocumentsRequest request);

  TaskProgress await(TaskHandle task, Duration timeout, BooleanSupplier cancelled);

  @Override
  void close();
}
