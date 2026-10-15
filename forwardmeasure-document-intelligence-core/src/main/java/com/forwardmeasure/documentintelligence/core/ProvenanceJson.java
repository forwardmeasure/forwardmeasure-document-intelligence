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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.forwardmeasure.documentintelligence.contracts.ConversionOptions;
import com.forwardmeasure.documentintelligence.contracts.Digests;
import java.io.IOException;
import java.util.TreeMap;

/** Canonical serialization for fingerprints, durable handles, receipts and provenance. */
public final class ProvenanceJson {
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  private ProvenanceJson() {}

  public static byte[] encode(Object value) throws IOException {
    return MAPPER.writeValueAsBytes(value);
  }

  public static <T> T decode(byte[] bytes, Class<T> type) throws IOException {
    return MAPPER.readValue(bytes, type);
  }

  public static String optionsHash(ConversionOptions options) throws IOException {
    var map = new TreeMap<String, Object>();
    map.put("pipeline", options.pipeline());
    map.put("formats", options.formats().stream().map(Enum::name).sorted().toList());
    map.put("enrichments", options.enrichments().stream().map(Enum::name).sorted().toList());
    map.put("doOcr", options.doOcr());
    map.put("forceOcr", options.forceOcr());
    map.put("ocrLanguages", options.ocrLanguages());
    map.put("vlmPreset", options.vlmPreset());
    map.put("imageMode", options.imageMode());
    map.put("firstPage", options.firstPage());
    map.put("lastPage", options.lastPage());
    map.put("includePageImages", options.includePageImages());
    map.put("imageScale", options.imageScale());
    map.put("documentTimeout", options.documentTimeout());
    map.put("stagePresets", new TreeMap<>(options.stagePresets()));
    return Digests.sha256(encode(map));
  }
}
