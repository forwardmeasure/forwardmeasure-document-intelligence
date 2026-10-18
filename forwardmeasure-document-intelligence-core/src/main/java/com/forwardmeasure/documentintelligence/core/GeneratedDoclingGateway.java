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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.forwardmeasure.documentintelligence.contracts.BinaryDocument;
import com.forwardmeasure.documentintelligence.contracts.ConversionPayload;
import com.forwardmeasure.documentintelligence.contracts.TaskHandle;
import com.forwardmeasure.documentintelligence.docling.generated.ApiException;
import com.forwardmeasure.documentintelligence.docling.generated.api.ChunkApi;
import com.forwardmeasure.documentintelligence.docling.generated.api.ConvertApi;
import com.forwardmeasure.documentintelligence.docling.generated.api.HealthApi;
import com.forwardmeasure.documentintelligence.docling.generated.api.TasksApi;
import com.forwardmeasure.documentintelligence.docling.generated.model.CapabilitiesResponse;
import com.forwardmeasure.documentintelligence.docling.generated.model.ConvertDocumentsOptions;
import com.forwardmeasure.documentintelligence.docling.generated.model.ConvertSourcesRequest;
import com.forwardmeasure.documentintelligence.docling.generated.model.HierarchicalChunkerOptionsDocumentsRequest;
import com.forwardmeasure.documentintelligence.docling.generated.model.HybridChunkerOptionsDocumentsRequest;
import com.forwardmeasure.documentintelligence.docling.generated.model.ProcessingPipeline;
import com.forwardmeasure.documentintelligence.docling.generated.model.SourcesInner1;
import com.forwardmeasure.documentintelligence.docling.generated.model.Target;
import com.forwardmeasure.documentintelligence.docling.generated.model.TaskStatusResponse;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;

/** Uses generated APIs for every endpoint. No automatic retries or redirects, including POSTs. */
public final class GeneratedDoclingGateway implements DoclingGateway {
  private final DoclingSettings settings;
  private final CloseableHttpClient transport;
  private final BoundedApiClient client;
  private final ConvertApi conversions;
  private final TasksApi tasks;
  private final HealthApi health;
  private final ChunkApi chunks;

  public GeneratedDoclingGateway(DoclingSettings settings) {
    this.settings = settings;
    transport =
        HttpClients.custom()
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setConnectionRequestTimeout(
                        Timeout.ofMilliseconds(settings.connectTimeout().toMillis()))
                    .build())
            .disableAutomaticRetries()
            .disableRedirectHandling()
            .build();
    client = new BoundedApiClient(transport, settings.maxResponseBytes());
    client.setBasePath(settings.endpoint().toString().replaceAll("/+$", ""));
    client.setConnectTimeout(Math.toIntExact(settings.connectTimeout().toMillis()));
    client.setReadTimeout(Math.toIntExact(settings.readTimeout().toMillis()));
    client.getObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    if (settings.apiKey() != null) client.addDefaultHeader("X-Api-Key", settings.apiKey());
    conversions = new ConvertApi(client);
    tasks = new TasksApi(client);
    health = new HealthApi(client);
    chunks = new ChunkApi(client);
  }

  @FunctionalInterface
  private interface Call<T> {
    T run() throws ApiException;
  }

  private <T> T invoke(Call<T> action, boolean submit) {
    if (Thread.currentThread().isInterrupted())
      throw new DocumentIntelligenceException("INTERRUPTED", 499, "Document request interrupted");
    try {
      return action.run();
    } catch (ApiException error) {
      if (Thread.currentThread().isInterrupted() || interrupted(error)) {
        Thread.currentThread().interrupt();
        throw new DocumentIntelligenceException("INTERRUPTED", 499, "Document request interrupted");
      }
      int status = error.getCode();
      if (status <= 0)
        throw new DocumentIntelligenceException(
            submit ? "SUBMISSION_OUTCOME_UNKNOWN" : "TRANSPORT_FAILURE",
            502,
            submit
                ? "Submission outcome is unknown; reconcile before resubmitting"
                : "Docling transport failed");
      throw new DocumentIntelligenceException(
          status == 404
              ? "NOT_FOUND_OR_EXPIRED"
              : status == 429 ? "BACKPRESSURE" : "DOCLING_REQUEST_FAILED",
          status,
          "Docling rejected the request (HTTP " + status + ")");
    }
  }

  private static boolean interrupted(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause())
      if (cause instanceof InterruptedException
          || cause instanceof java.io.InterruptedIOException
              && !(cause instanceof java.net.SocketTimeoutException)) return true;
    return false;
  }

  @Override
  public Map<String, Object> versions() {
    return Map.copyOf(invoke(() -> health.versions(), false));
  }

  @Override
  public boolean ready() {
    return invoke(() -> health.readiness(), false) != null;
  }

  @Override
  public CapabilitiesResponse capabilities() {
    return invoke(() -> health.capabilities(), false);
  }

  private ConvertSourcesRequest request(BinaryDocument document, ConvertDocumentsOptions options) {
    if (document.size() > settings.maxInputBytes())
      throw new IllegalArgumentException("Input exceeds configured bound");
    DoclingOptions.validate(options);
    if (options.getPipeline() == ProcessingPipeline.VLM
        && !settings.preparedVlmPresets().contains(options.getVlmPipelinePreset()))
      throw new IllegalArgumentException(
          "VLM preset is not explicitly prepared by this deployment");
    if (!settings.preparedEnrichments().containsAll(DoclingOptions.enrichments(options)))
      throw new IllegalArgumentException(
          "Enrichment models are not explicitly prepared by this deployment");
    return new ConvertSourcesRequest()
        .sources(
            List.of(
                new SourcesInner1()
                    .kind("file")
                    .filename(document.filename())
                    .base64String(Base64.getEncoder().encodeToString(document.content()))))
        .options(options)
        .target(new Target().kind(DoclingOptions.archiveRequired(options) ? "zip" : "inbody"));
  }

  @Override
  public ConversionPayload convert(BinaryDocument source, ConvertDocumentsOptions options) {
    var request = request(source, options);
    return payload(invoke(() -> conversions.convertSource(request, null), true));
  }

  @Override
  public TaskHandle submit(BinaryDocument source, ConvertDocumentsOptions options) {
    var request = request(source, options);
    var status = invoke(() -> conversions.submitSource(request, null), true);
    return handle(status, DoclingOptions.archiveRequired(options));
  }

  private static TaskHandle handle(TaskStatusResponse status, boolean archive) {
    if (status == null || status.getTaskId() == null || status.getTaskStatus() == null)
      throw new DocumentIntelligenceException(
          "INVALID_RESPONSE", 502, "Docling omitted required task fields");
    return new TaskHandle(status.getTaskId(), archive);
  }

  @Override
  public TaskStatusResponse poll(TaskHandle task, Duration wait) {
    if (wait.isNegative()
        || wait.compareTo(settings.readTimeout()) >= 0
        || wait.compareTo(Duration.ofSeconds(30)) > 0)
      throw new IllegalArgumentException("Poll wait must be in [0,30s] and below the read timeout");
    var result =
        invoke(
            () -> tasks.pollTask(task.taskId(), BigDecimal.valueOf(wait.toMillis(), 3), null),
            false);
    var actual = handle(result, task.archive());
    if (!actual.taskId().equals(task.taskId()))
      throw new DocumentIntelligenceException(
          "TASK_ID_MISMATCH", 502, "Docling returned a different task identifier");
    return result;
  }

  @Override
  public ConversionPayload fetch(TaskHandle task) {
    return payload(invoke(() -> tasks.fetchResult(task.taskId(), null), false));
  }

  @Override
  public ConversionPayload chunk(HierarchicalChunkerOptionsDocumentsRequest request) {
    return payload(invoke(() -> chunks.chunkHierarchicalSource(request, null), true));
  }

  @Override
  public ConversionPayload chunk(HybridChunkerOptionsDocumentsRequest request) {
    return payload(invoke(() -> chunks.chunkHybridSource(request, null), true));
  }

  private ConversionPayload payload(File file) {
    if (file == null)
      throw new DocumentIntelligenceException(
          "INVALID_RESPONSE", 502, "Docling returned no conversion result");
    try {
      byte[] bytes = Files.readAllBytes(file.toPath());
      String media = "application/json";
      for (var header : client.getResponseHeaders().entrySet())
        if (header.getKey().equalsIgnoreCase("Content-Type") && !header.getValue().isEmpty())
          media = header.getValue().getFirst().split(";", 2)[0];
      return new ConversionPayload(bytes, media);
    } catch (IOException error) {
      throw new DocumentIntelligenceException(
          "RESULT_READ_FAILED", 500, "Cannot read temporary conversion result");
    } finally {
      try {
        Files.deleteIfExists(file.toPath());
      } catch (IOException error) {
        throw new DocumentIntelligenceException(
            "TEMPORARY_FILE_CLEANUP_FAILED", 500, "Cannot remove temporary conversion result");
      }
    }
  }

  @Override
  public TaskStatusResponse await(TaskHandle task, Duration timeout, BooleanSupplier cancelled) {
    if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofDays(1)) > 0)
      throw new IllegalArgumentException("Await timeout must be in (0,1 day]");
    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      if (cancelled.getAsBoolean())
        throw new DocumentIntelligenceException(
            "LOCAL_WAIT_CANCELLED", 499, "Local polling stopped; the server task may continue");
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0)
        throw new DocumentIntelligenceException(
            "POLL_DEADLINE_EXCEEDED", 504, "Task did not finish before the local deadline");
      // Zero-wait requests use the configured finite transport timeout; allow for that timeout
      // explicitly.
      if (remaining
          < settings.readTimeout().plus(settings.connectTimeout().multipliedBy(2)).toNanos())
        throw new DocumentIntelligenceException(
            "POLL_DEADLINE_EXCEEDED",
            504,
            "Insufficient deadline budget for connection acquisition, connection and response"
                + " timeouts");
      var progress = poll(task, Duration.ZERO);
      if (switch (progress.getTaskStatus()) {
        case PENDING, STARTED -> false;
        case SUCCESS, PARTIAL_SUCCESS, FAILURE, SKIPPED -> true;
      }) return progress;
      try {
        Thread.sleep(Math.min(500, Math.max(1, (deadline - System.nanoTime()) / 1_000_000)));
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new DocumentIntelligenceException("INTERRUPTED", 499, "Local polling interrupted");
      }
    }
  }

  @Override
  public void close() {
    try {
      transport.close();
    } catch (IOException error) {
      throw new DocumentIntelligenceException(
          "CLIENT_CLOSE_FAILED", 500, "Cannot close Docling transport");
    }
  }
}
