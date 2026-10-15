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

import com.forwardmeasure.documentintelligence.contracts.Enrichment;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Common settings vocabulary used by all framework bindings. */
public final class DoclingConfiguration {
  public static final String PREFIX = "document-intelligence.docling.";
  public static final Set<String> KEYS =
      Set.of(
          "endpoint",
          "api-key",
          "connect-timeout",
          "read-timeout",
          "max-input-bytes",
          "max-response-bytes",
          "prepared-vlm-presets",
          "prepared-enrichments");

  private DoclingConfiguration() {}

  public static DoclingSettings read(Function<String, String> lookup) {
    var defaults = DoclingSettings.defaults(URI.create("http://localhost:5001"), null);
    return new DoclingSettings(
        URI.create(value(lookup, "endpoint", defaults.endpoint().toString())),
        lookup.apply(PREFIX + "api-key"),
        Duration.parse(value(lookup, "connect-timeout", defaults.connectTimeout().toString())),
        Duration.parse(value(lookup, "read-timeout", defaults.readTimeout().toString())),
        Long.parseLong(value(lookup, "max-input-bytes", "" + defaults.maxInputBytes())),
        Long.parseLong(value(lookup, "max-response-bytes", "" + defaults.maxResponseBytes())),
        csv(value(lookup, "prepared-vlm-presets", "")),
        csv(value(lookup, "prepared-enrichments", "")).stream()
            .map(v -> Enrichment.valueOf(v.toUpperCase(java.util.Locale.ROOT)))
            .collect(Collectors.toUnmodifiableSet()));
  }

  private static String value(Function<String, String> lookup, String key, String fallback) {
    String value = lookup.apply(PREFIX + key);
    return value == null ? fallback : value;
  }

  private static Set<String> csv(String value) {
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }
}
