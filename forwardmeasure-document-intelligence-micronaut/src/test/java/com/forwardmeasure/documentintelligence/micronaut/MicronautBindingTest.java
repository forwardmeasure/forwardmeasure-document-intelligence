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
package com.forwardmeasure.documentintelligence.micronaut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.documentintelligence.core.DoclingGateway;
import com.forwardmeasure.documentintelligence.core.DoclingSettings;
import com.sun.net.httpserver.HttpServer;
import io.micronaut.context.ApplicationContext;
import java.net.InetSocketAddress;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MicronautBindingTest {
  @Test
  void bindsConfigurationAndCallsGeneratedClient() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/ready",
        e -> {
          int code = "binding-key".equals(e.getRequestHeaders().getFirst("X-Api-Key")) ? 200 : 401;
          byte[] bytes = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
          e.sendResponseHeaders(code, bytes.length);
          e.getResponseBody().write(bytes);
          e.close();
        });
    server.start();
    try (var context =
        ApplicationContext.run(
            Map.of(
                "document-intelligence.docling.endpoint",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "document-intelligence.docling.api-key",
                "binding-key"))) {
      assertTrue(context.getBean(DoclingGateway.class).ready());
      assertEquals("binding-key", context.getBean(DoclingSettings.class).apiKey());
    } finally {
      server.stop(0);
    }
  }
}
