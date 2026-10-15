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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.documentintelligence.contracts.ArtifactReference;
import com.forwardmeasure.documentintelligence.contracts.BinaryDocument;
import com.forwardmeasure.documentintelligence.contracts.ConversionContext;
import com.forwardmeasure.documentintelligence.contracts.ConversionOptions;
import com.forwardmeasure.documentintelligence.contracts.ConversionPayload;
import com.forwardmeasure.documentintelligence.contracts.ConversionSubmission;
import com.forwardmeasure.documentintelligence.contracts.Digests;
import com.forwardmeasure.documentintelligence.contracts.OutputFormat;
import com.forwardmeasure.documentintelligence.contracts.RuntimeIdentity;
import com.forwardmeasure.documentintelligence.contracts.TaskHandle;
import com.forwardmeasure.documentintelligence.contracts.TaskProgress;
import com.forwardmeasure.documentintelligence.contracts.TaskState;
import com.forwardmeasure.documentintelligence.docling.generated.model.CapabilitiesResponse;
import com.forwardmeasure.documentintelligence.docling.generated.model.HierarchicalChunkerOptionsDocumentsRequest;
import com.forwardmeasure.documentintelligence.docling.generated.model.HybridChunkerOptionsDocumentsRequest;
import com.forwardmeasure.documentintelligence.validation.DoclangArchiveValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactWorkflowTest {
  @TempDir Path root;
  static final String HASH = "a".repeat(64);

  static class Gateway implements DoclingGateway {
    TaskState state = TaskState.SUCCESS;
    int submits, fetches;
    String response =
        "{\"status\":\"success\",\"document\":{\"json_content\":{\"future\":9223372036854775807},\"md_content\":\"#"
            + " café\"}}";

    public TaskHandle submit(BinaryDocument source, ConversionOptions options) {
      submits++;
      return new TaskHandle("task", options.archiveRequired());
    }

    public TaskProgress poll(TaskHandle task, Duration wait) {
      return new TaskProgress(task, state, null);
    }

    public ConversionPayload fetch(TaskHandle task) {
      fetches++;
      return new ConversionPayload(response.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    public ConversionPayload convert(BinaryDocument source, ConversionOptions options) {
      return fetch(new TaskHandle("task", false));
    }

    public TaskProgress await(TaskHandle task, Duration timeout, BooleanSupplier cancelled) {
      return poll(task, Duration.ZERO);
    }

    public boolean ready() {
      return true;
    }

    public Map<String, Object> versions() {
      return Map.of();
    }

    public CapabilitiesResponse capabilities() {
      return new CapabilitiesResponse();
    }

    public ConversionPayload chunk(HierarchicalChunkerOptionsDocumentsRequest request) {
      return fetch(new TaskHandle("task", false));
    }

    public ConversionPayload chunk(HybridChunkerOptionsDocumentsRequest request) {
      return fetch(new TaskHandle("task", false));
    }

    public void close() {}
  }

  ConversionContext context(String scope) {
    return new ConversionContext(
        scope, new RuntimeIdentity("registry/image@sha256:" + HASH, HASH, Map.of()));
  }

  @Test
  void persistsPinnedSourceHandleArtifactsAndProvenance() throws Exception {
    var repository = new FileArtifactRepository(root);
    var gateway = new Gateway();
    var service =
        new DocumentIntelligenceService(gateway, repository, new DoclangArchiveValidator(), 1000);
    var source =
        repository.write(HASH, "source", new byte[] {1, 2, 3}, "application/pdf", Map.of());
    var submission =
        service.submit(source, "source.pdf", ConversionOptions.standard(), context("tenant-1"));
    assertEquals(
        submission,
        ProvenanceJson.decode(ProvenanceJson.encode(submission), ConversionSubmission.class));
    var receipt = service.retrieveAndStore(submission);
    assertEquals(
        Set.of("response.json", "document.json", "document.md"), receipt.artifacts().keySet());
    assertEquals(
        "# café",
        new String(
            repository.read(receipt.artifacts().get("document.md"), 1000), StandardCharsets.UTF_8));
    assertTrue(
        new String(repository.read(receipt.provenance(), 100000), StandardCharsets.UTF_8)
            .contains("deployment-operator"));
    assertEquals(1, gateway.submits);
    assertEquals(1, gateway.fetches);
    assertNotEquals(
        submission.operationId(),
        service
            .submit(source, "source.html", ConversionOptions.standard(), context("tenant-1"))
            .operationId());
    var same =
        repository.write(
            submission.operationId(),
            "document.md",
            "# café".getBytes(StandardCharsets.UTF_8),
            "text/markdown",
            Map.of());
    assertEquals(receipt.artifacts().get("document.md"), same);
    assertNotEquals(
        submission.operationId(),
        service
            .submit(source, "source.pdf", ConversionOptions.standard(), context("tenant-2"))
            .operationId());
  }

  @Test
  void refusesChangedSourceAndIncompleteOutput() throws Exception {
    var repository = new FileArtifactRepository(root);
    var gateway = new Gateway();
    var service =
        new DocumentIntelligenceService(gateway, repository, new DoclangArchiveValidator(), 1000);
    var source = repository.write(HASH, "source", new byte[] {1}, "application/pdf", Map.of());
    Files.write(Path.of(source.uri()), new byte[] {2});
    assertThrows(
        java.io.IOException.class,
        () -> service.submit(source, "source.pdf", ConversionOptions.standard(), context("scope")));
    assertEquals(0, gateway.submits);
    Files.write(Path.of(source.uri()), new byte[] {1});
    var submission =
        service.submit(source, "source.pdf", ConversionOptions.standard(), context("scope"));
    gateway.state = TaskState.PARTIAL_SUCCESS;
    assertThrows(DocumentIntelligenceException.class, () -> service.retrieveAndStore(submission));
    assertEquals(0, gateway.fetches);
    gateway.state = TaskState.SUCCESS;
    gateway.response = "{\"status\":\"success\",\"document\":{}}";
    assertThrows(java.io.IOException.class, () -> service.retrieveAndStore(submission));
  }

  @Test
  void rejectsInvalidDoclangAndAlteredSubmission() throws Exception {
    var repository = new FileArtifactRepository(root);
    var gateway = new Gateway();
    var service =
        new DocumentIntelligenceService(gateway, repository, new DoclangArchiveValidator(), 1000);
    var source = repository.write(HASH, "source", new byte[] {1}, "application/pdf", Map.of());
    var submission =
        service.submit(
            source,
            "source.pdf",
            ConversionOptions.standard().withFormats(Set.of(OutputFormat.DOCLANG)),
            context("scope"));
    gateway.response = "{\"status\":\"success\",\"document\":{\"doclang_content\":\"<bad/>\"}}";
    assertThrows(DocumentIntelligenceException.class, () -> service.retrieveAndStore(submission));
    var changed =
        new ConversionSubmission(
            "b".repeat(64),
            submission.task(),
            source,
            submission.filename(),
            submission.options(),
            submission.context(),
            submission.optionsSha256());
    assertThrows(java.io.IOException.class, () -> service.retrieveAndStore(changed));
  }

  @Test
  void localRepositoryRejectsEscapingPathsAndWrongHashes() throws Exception {
    var repository = new FileArtifactRepository(root);
    Path outside = Files.createTempFile("fdi-test-outside-", ".bin");
    try {
      Files.write(outside, new byte[] {1});
      var ref =
          new ArtifactReference(
              outside.toUri(), null, Digests.sha256(new byte[] {1}), 1, "application/octet-stream");
      assertThrows(java.io.IOException.class, () -> repository.read(ref, 100));
    } finally {
      Files.delete(outside);
    }
  }

  @Test
  void configurationUsesSameVocabularyAndRedactsSecrets() {
    var settings =
        DoclingConfiguration.read(
            k ->
                Map.of(
                        DoclingConfiguration.PREFIX + "api-key",
                        "hidden",
                        DoclingConfiguration.PREFIX + "prepared-vlm-presets",
                        "granite_docling,granite_vision")
                    .get(k));
    assertEquals(Set.of("granite_docling", "granite_vision"), settings.preparedVlmPresets());
    assertFalse(settings.toString().contains("hidden"));
    assertThrows(
        IllegalArgumentException.class,
        () -> DoclingSettings.defaults(java.net.URI.create("http://user:secret@localhost"), null));
  }
}
