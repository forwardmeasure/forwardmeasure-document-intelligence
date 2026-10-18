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
package com.forwardmeasure.documentintelligence.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.documentintelligence.core.DoclingGateway;
import com.forwardmeasure.documentintelligence.core.DoclingSettings;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.net.InetSocketAddress;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(QuarkusBindingTest.ServerResource.class)
class QuarkusBindingTest {
  @Inject DoclingGateway gateway;
  @Inject DoclingSettings settings;

  @Test
  void bindsConfigurationAndCallsGeneratedClient() {
    assertEquals("binding-key", settings.apiKey());
    assertTrue(gateway.ready());
  }

  public static class ServerResource implements QuarkusTestResourceLifecycleManager {
    private HttpServer server;

    @Override
    public Map<String, String> start() {
      try {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
            "/ready",
            e -> {
              int code =
                  "binding-key".equals(e.getRequestHeaders().getFirst("X-Api-Key")) ? 200 : 401;
              byte[] bytes = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
              e.sendResponseHeaders(code, bytes.length);
              e.getResponseBody().write(bytes);
              e.close();
            });
        server.start();
        return Map.of(
            "document-intelligence.docling.endpoint",
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "document-intelligence.docling.api-key",
            "binding-key");
      } catch (Exception error) {
        throw new IllegalStateException(error);
      }
    }

    @Override
    public void stop() {
      if (server != null) server.stop(0);
    }
  }
}
