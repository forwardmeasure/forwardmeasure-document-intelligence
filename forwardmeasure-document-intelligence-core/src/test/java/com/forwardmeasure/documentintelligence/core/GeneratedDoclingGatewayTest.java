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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forwardmeasure.documentintelligence.contracts.BinaryDocument;
import com.forwardmeasure.documentintelligence.contracts.ConversionOptions;
import com.forwardmeasure.documentintelligence.contracts.OutputFormat;
import com.forwardmeasure.documentintelligence.contracts.TaskHandle;
import com.forwardmeasure.documentintelligence.contracts.TaskState;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeneratedDoclingGatewayTest {
  private HttpServer server;
  private java.util.concurrent.ExecutorService executor;
  private GeneratedDoclingGateway gateway;
  private final CopyOnWriteArrayList<Request> requests = new CopyOnWriteArrayList<>();
  private Function<Request, Response> route;

  record Request(String path, String method, String key, byte[] body) {}

  record Response(int status, String media, byte[] body) {}

  private static Response json(int status, String body) {
    return new Response(status, "application/json", body.getBytes(StandardCharsets.UTF_8));
  }

  private static String task(String id, String status) {
    return "{\"task_id\":\""
        + id
        + "\",\"task_type\":\"convert\",\"task_status\":\""
        + status
        + "\"}";
  }

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    route = request -> json(200, "{\"status\":\"ok\"}");
    server.createContext(
        "/",
        exchange -> {
          try {
            var request =
                new Request(
                    exchange.getRequestURI().toString(),
                    exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("X-Api-Key"),
                    exchange.getRequestBody().readAllBytes());
            requests.add(request);
            var response = route.apply(request);
            if (response == null) {
              exchange.close();
              return;
            }
            exchange.getResponseHeaders().set("Content-Type", response.media());
            exchange.sendResponseHeaders(response.status(), response.body().length);
            exchange.getResponseBody().write(response.body());
          } finally {
            exchange.close();
          }
        });
    server.start();
    gateway = new GeneratedDoclingGateway(settings(1024 * 1024, 1024 * 1024));
  }

  private DoclingSettings settings(long input, long output) {
    return new DoclingSettings(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
        "secret-key",
        Duration.ofSeconds(1),
        Duration.ofMillis(100),
        input,
        output,
        Set.of(),
        Set.of());
  }

  @AfterEach
  void stop() {
    gateway.close();
    server.stop(0);
    executor.shutdownNow();
  }

  @Test
  void usesGeneratedSchemaAndPreservesNativeJson() throws Exception {
    byte[] original =
        "{\"document\":{\"json_content\":{\"unknown_future_field\":9223372036854775807}},\"status\":\"success\"}"
            .getBytes(StandardCharsets.UTF_8);
    route = request -> new Response(200, "application/json", original);
    var document =
        new BinaryDocument(
            "café.html", "<html><body>café</body></html>".getBytes(StandardCharsets.UTF_8));
    var response = gateway.convert(document, ConversionOptions.standard());
    assertArrayEquals(original, response.content());
    assertEquals("application/json", response.mediaType());
    var request = requests.getFirst();
    assertEquals("POST", request.method());
    assertEquals("/v1/convert/source", request.path());
    assertEquals("secret-key", request.key());
    var tree = new ObjectMapper().readTree(request.body());
    assertEquals("file", tree.at("/sources/0/kind").asText());
    assertEquals("inbody", tree.at("/target/kind").asText());
    assertArrayEquals(
        document.content(),
        java.util.Base64.getDecoder().decode(tree.at("/sources/0/base64_string").asText()));
    assertEquals("café.html", tree.at("/sources/0/filename").asText());
    assertFalse(tree.path("options").has("layout_custom_config"));
    assertFalse(tree.path("target").has("credentials"));
  }

  @Test
  void persistsTaskHandlesPollsAndChecksTerminalState() {
    var polls = new java.util.concurrent.atomic.AtomicInteger();
    route =
        request ->
            json(
                200,
                task(
                    "opaque-task",
                    request.path().contains("poll") && polls.incrementAndGet() > 1
                        ? "success"
                        : "pending"));
    var handle =
        gateway.submit(new BinaryDocument("a.html", new byte[] {1}), ConversionOptions.standard());
    assertEquals("opaque-task", handle.taskId());
    assertEquals(
        TaskState.SUCCESS, gateway.await(handle, Duration.ofSeconds(5), () -> false).state());
    assertTrue(
        requests.stream().anyMatch(r -> r.path().startsWith("/v1/status/poll/opaque-task?wait=0")));
  }

  @Test
  void preservesZipBytesAndSelectsArchiveTarget() throws Exception {
    byte[] bytes = {'P', 'K', 1, 2, 3};
    route = request -> new Response(200, "application/zip", bytes);
    var result =
        gateway.convert(
            new BinaryDocument("a.html", new byte[] {1}),
            ConversionOptions.standard().withFormats(Set.of(OutputFormat.DCLX)));
    assertArrayEquals(bytes, result.content());
    assertEquals(
        "zip", new ObjectMapper().readTree(requests.getFirst().body()).at("/target/kind").asText());
  }

  @Test
  void sanitizesErrorAndDoesNotRetrySubmission() {
    route = request -> json(429, "{\"private\":\"secret-key private evidence\"}");
    var error =
        assertThrows(
            DocumentIntelligenceException.class,
            () ->
                gateway.submit(
                    new BinaryDocument("a.html", new byte[] {1}), ConversionOptions.standard()));
    assertEquals("BACKPRESSURE", error.code());
    assertEquals(429, error.problem().getStatus());
    assertFalse(error.toString().contains("secret-key"));
    assertEquals(1, requests.size());
  }

  @Test
  void reportsUncertainSubmissionWithoutRetry() {
    route = request -> null;
    var error =
        assertThrows(
            DocumentIntelligenceException.class,
            () ->
                gateway.submit(
                    new BinaryDocument("a.html", new byte[] {1}), ConversionOptions.standard()));
    assertEquals("SUBMISSION_OUTCOME_UNKNOWN", error.code());
    assertEquals(1, requests.size());
  }

  @Test
  void boundsResponseAndInput() {
    gateway.close();
    gateway = new GeneratedDoclingGateway(settings(1, 32));
    route = request -> json(200, "x".repeat(100));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            gateway.convert(
                new BinaryDocument("a.html", new byte[] {1, 2}), ConversionOptions.standard()));
    assertTrue(requests.isEmpty());
    assertThrows(
        DocumentIntelligenceException.class,
        () ->
            gateway.convert(
                new BinaryDocument("a.html", new byte[] {1}), ConversionOptions.standard()));
  }

  @Test
  void rejectsUnpreparedModelsAndWrongTaskIdentifier() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            gateway.submit(
                new BinaryDocument("a.html", new byte[] {1}),
                ConversionOptions.standard().withVlm("granite_docling")));
    route = request -> json(200, task("different", "success"));
    assertEquals(
        "TASK_ID_MISMATCH",
        assertThrows(
                DocumentIntelligenceException.class,
                () -> gateway.poll(new TaskHandle("expected", false), Duration.ZERO))
            .code());
  }

  @Test
  void handlesExpiryLocalCancellationAndDeadline() {
    route = request -> json(404, "{\"detail\":\"expired\"}");
    var task = new TaskHandle("task", false);
    assertEquals(
        "NOT_FOUND_OR_EXPIRED",
        assertThrows(DocumentIntelligenceException.class, () -> gateway.fetch(task)).code());
    assertEquals(
        "LOCAL_WAIT_CANCELLED",
        assertThrows(
                DocumentIntelligenceException.class,
                () -> gateway.await(task, Duration.ofSeconds(3), () -> true))
            .code());
    assertEquals(
        "POLL_DEADLINE_EXCEEDED",
        assertThrows(
                DocumentIntelligenceException.class,
                () -> gateway.await(task, Duration.ofMillis(10), () -> false))
            .code());
    assertThrows(IllegalArgumentException.class, () -> gateway.poll(task, Duration.ofSeconds(30)));
  }

  @Test
  void restoresInterruption() {
    Thread.currentThread().interrupt();
    try {
      assertEquals(
          "INTERRUPTED",
          assertThrows(DocumentIntelligenceException.class, () -> gateway.ready()).code());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void usesGeneratedHealthAndCapabilities() {
    route =
        request ->
            json(200, request.path().equals("/version") ? "{\"docling_serve\":\"1.37.0\"}" : "{}");
    assertTrue(gateway.ready());
    assertEquals("1.37.0", gateway.versions().get("docling_serve"));
    assertNotNull(gateway.capabilities());
    assertEquals(
        Set.of("/ready", "/version", "/v1/capabilities"),
        requests.stream().map(Request::path).collect(java.util.stream.Collectors.toSet()));
  }
}
