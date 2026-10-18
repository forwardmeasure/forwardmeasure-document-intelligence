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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.forwardmeasure.documentintelligence.contracts.Digests;
import com.forwardmeasure.documentintelligence.docling.generated.model.ConvertDocumentsOptions;
import java.io.IOException;
import java.util.Map;
import java.util.stream.StreamSupport;

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

  public static String optionsHash(ConvertDocumentsOptions options) throws IOException {
    ObjectNode canonical = (ObjectNode) canonical(MAPPER.valueToTree(options));
    var formats = canonical.get("to_formats");
    if (formats != null && formats.isArray()) {
      var sorted = MAPPER.createArrayNode();
      StreamSupport.stream(formats.spliterator(), false)
          .map(JsonNode::asText)
          .distinct()
          .sorted()
          .forEach(sorted::add);
      canonical.set("to_formats", sorted);
    }
    return Digests.sha256(encode(canonical));
  }

  private static JsonNode canonical(JsonNode value) {
    if (value.isObject()) {
      var result = MAPPER.createObjectNode();
      value.properties().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(entry -> result.set(entry.getKey(), canonical(entry.getValue())));
      return result;
    }
    if (value.isArray()) {
      var result = MAPPER.createArrayNode();
      value.forEach(item -> result.add(canonical(item)));
      return result;
    }
    return value;
  }
}
