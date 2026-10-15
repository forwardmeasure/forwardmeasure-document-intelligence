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
import java.util.Set;

/** Connection limits and the model features explicitly prepared by the deployment operator. */
public record DoclingSettings(
    URI endpoint,
    String apiKey,
    Duration connectTimeout,
    Duration readTimeout,
    long maxInputBytes,
    long maxResponseBytes,
    Set<String> preparedVlmPresets,
    Set<Enrichment> preparedEnrichments) {
  public DoclingSettings {
    if (endpoint == null
        || !Set.of("http", "https").contains(endpoint.getScheme())
        || endpoint.getHost() == null
        || endpoint.getUserInfo() != null
        || endpoint.getRawQuery() != null
        || endpoint.getRawFragment() != null)
      throw new IllegalArgumentException(
          "endpoint must be an HTTP(S) URI without credentials, query or fragment");
    for (Duration duration : java.util.List.of(connectTimeout, readTimeout))
      if (duration.isNegative()
          || duration.isZero()
          || duration.toMillis() < 1
          || duration.toMillis() > Integer.MAX_VALUE)
        throw new IllegalArgumentException(
            "timeouts must be finite positive millisecond durations");
    if (apiKey != null && (apiKey.isBlank() || apiKey.contains("\r") || apiKey.contains("\n")))
      throw new IllegalArgumentException("Invalid API key");
    if (maxInputBytes < 1
        || maxInputBytes > Integer.MAX_VALUE - 8
        || maxResponseBytes < 1
        || maxResponseBytes > Integer.MAX_VALUE - 8)
      throw new IllegalArgumentException("byte limits must fit bounded Java arrays");
    preparedVlmPresets = Set.copyOf(preparedVlmPresets);
    preparedEnrichments = Set.copyOf(preparedEnrichments);
  }

  public static DoclingSettings defaults(URI endpoint, String apiKey) {
    return new DoclingSettings(
        endpoint,
        apiKey,
        Duration.ofSeconds(10),
        Duration.ofMinutes(2),
        16 * 1024 * 1024,
        64 * 1024 * 1024,
        Set.of(),
        Set.of());
  }

  @Override
  public String toString() {
    return "DoclingSettings[endpoint="
        + endpoint
        + ",apiKey=<redacted>,maxInputBytes="
        + maxInputBytes
        + ",maxResponseBytes="
        + maxResponseBytes
        + "]";
  }
}
