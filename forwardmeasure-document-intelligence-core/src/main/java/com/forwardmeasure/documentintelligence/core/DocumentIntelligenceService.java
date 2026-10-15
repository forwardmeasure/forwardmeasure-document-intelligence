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
import com.forwardmeasure.documentintelligence.contracts.ArtifactReference;
import com.forwardmeasure.documentintelligence.contracts.ArtifactRepository;
import com.forwardmeasure.documentintelligence.contracts.BinaryDocument;
import com.forwardmeasure.documentintelligence.contracts.ConversionContext;
import com.forwardmeasure.documentintelligence.contracts.ConversionOptions;
import com.forwardmeasure.documentintelligence.contracts.ConversionReceipt;
import com.forwardmeasure.documentintelligence.contracts.ConversionSubmission;
import com.forwardmeasure.documentintelligence.contracts.Digests;
import com.forwardmeasure.documentintelligence.contracts.DocumentArtifactValidator;
import com.forwardmeasure.documentintelligence.contracts.OutputFormat;
import com.forwardmeasure.documentintelligence.contracts.TaskProgress;
import com.forwardmeasure.documentintelligence.contracts.TaskState;
import com.forwardmeasure.documentintelligence.contracts.ValidationReport;
import com.forwardmeasure.documentintelligence.validation.BoundedZip;
import com.forwardmeasure.documentintelligence.validation.ValidationLimits;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Artifact-oriented operations for durable workflows; only references and task handles cross
 * workflow boundaries.
 */
public final class DocumentIntelligenceService {
  private final DoclingGateway gateway;
  private final ArtifactRepository artifacts;
  private final DocumentArtifactValidator validator;
  private final long maxInputBytes;

  public DocumentIntelligenceService(
      DoclingGateway gateway,
      ArtifactRepository artifacts,
      DocumentArtifactValidator validator,
      long maxInputBytes) {
    this.gateway = java.util.Objects.requireNonNull(gateway);
    this.artifacts = java.util.Objects.requireNonNull(artifacts);
    this.validator = java.util.Objects.requireNonNull(validator);
    if (maxInputBytes < 1 || maxInputBytes > Integer.MAX_VALUE - 8)
      throw new IllegalArgumentException("Invalid input bound");
    this.maxInputBytes = maxInputBytes;
  }

  public ConversionSubmission submit(
      ArtifactReference source,
      String filename,
      ConversionOptions options,
      ConversionContext context)
      throws IOException {
    String optionsHash = ProvenanceJson.optionsHash(options);
    String operationId = operationId(source, filename, optionsHash, context);
    byte[] bytes = artifacts.read(source, maxInputBytes);
    if (bytes.length != source.size() || !Digests.sha256(bytes).equals(source.sha256()))
      throw new IOException("Source bytes do not match the declared immutable reference");
    var task = gateway.submit(new BinaryDocument(filename, bytes), options);
    return new ConversionSubmission(
        operationId, task, source, filename, options, context, optionsHash);
  }

  private static String operationId(
      ArtifactReference source, String filename, String optionsHash, ConversionContext context)
      throws IOException {
    return Digests.sha256(
        ProvenanceJson.encode(
            Map.of(
                "source",
                source,
                "filename",
                filename,
                "scope",
                context.scopeKey(),
                "options",
                optionsHash,
                "runtime",
                context.runtime())));
  }

  public TaskProgress poll(ConversionSubmission submission, Duration wait) {
    return gateway.poll(submission.task(), wait);
  }

  public ConversionReceipt retrieveAndStore(ConversionSubmission submission) throws IOException {
    if (!ProvenanceJson.optionsHash(submission.options()).equals(submission.optionsSha256())
        || !operationId(
                submission.source(),
                submission.filename(),
                submission.optionsSha256(),
                submission.context())
            .equals(submission.operationId())
        || submission.task().archive() != submission.options().archiveRequired())
      throw new IOException("Submission fingerprint is inconsistent");
    var progress = poll(submission, Duration.ZERO);
    if (progress.state() != TaskState.SUCCESS)
      throw new DocumentIntelligenceException(
          "TASK_NOT_SUCCESSFUL",
          409,
          "Task must finish successfully before its result is accepted");
    var payload = gateway.fetch(submission.task());
    var contents = new LinkedHashMap<String, Content>();
    var validation = new TreeMap<String, ValidationReport>();
    if (submission.task().archive()) {
      var report = validator.validateArchive(payload.content());
      requireValid(report);
      validation.put("response.zip", report);
      contents.put("response.zip", new Content(payload.content(), "application/zip"));
      var members = BoundedZip.read(payload.content(), ValidationLimits.defaults());
      for (var member : members.entrySet())
        if (member.getValue().length > 0) {
          String media = mediaType(member.getKey());
          contents.put(member.getKey(), new Content(member.getValue(), media));
        }
      for (var format : submission.options().formats())
        if (members.keySet().stream().noneMatch(n -> matches(n, format)))
          throw new IOException("Requested format is absent from the archive");
    } else {
      JsonNode result = new ObjectMapper().readTree(payload.content());
      if (!"success".equals(result.path("status").asText()))
        throw new DocumentIntelligenceException(
            "CONVERSION_NOT_SUCCESSFUL", 422, "Conversion output is incomplete or failed");
      JsonNode document = result.path("document");
      if (!document.isObject()) throw new IOException("Conversion response has no document");
      contents.put("response.json", new Content(payload.content(), "application/json"));
      for (var format : submission.options().formats()) {
        String field =
            switch (format) {
              case JSON -> "json_content";
              case MARKDOWN -> "md_content";
              case TEXT -> "text_content";
              case HTML -> "html_content";
              case DOCLANG -> "doclang_content";
              case DCLX -> throw new IOException("DCLX requires an archive target");
            };
        JsonNode exported = document.get(field);
        if (exported == null
            || exported.isNull()
            || format == OutputFormat.JSON && !exported.isObject()
            || format != OutputFormat.JSON && !exported.isTextual())
          throw new IOException("Requested format is absent or has an invalid type");
        byte[] bytes =
            format == OutputFormat.JSON
                ? ProvenanceJson.encode(exported)
                : exported.asText().getBytes(StandardCharsets.UTF_8);
        String name = "document." + format.extension();
        contents.put(name, new Content(bytes, format.mediaType()));
        if (format == OutputFormat.DOCLANG) {
          var report = validator.validateDoclang(bytes);
          requireValid(report);
          validation.put(name, report);
        }
      }
    }
    var stored = new TreeMap<String, ArtifactReference>();
    var metadata =
        Map.of(
            "operation-id",
            submission.operationId(),
            "source-sha256",
            submission.source().sha256(),
            "options-sha256",
            submission.optionsSha256());
    for (var entry : contents.entrySet()) {
      var value = entry.getValue();
      stored.put(
          entry.getKey(),
          artifacts.write(
              submission.operationId(),
              entry.getKey(),
              value.bytes(),
              value.mediaType(),
              metadata));
    }
    var provenance = new TreeMap<String, Object>();
    provenance.put("schemaVersion", 1);
    provenance.put("submission", submission);
    provenance.put("runtimeIdentityAuthority", "deployment-operator");
    provenance.put("artifacts", stored);
    provenance.put("validation", validation);
    var manifest =
        artifacts.write(
            submission.operationId(),
            "provenance.json",
            ProvenanceJson.encode(provenance),
            "application/json",
            metadata);
    return new ConversionReceipt(
        submission.operationId(), submission.source(), stored, manifest, validation);
  }

  private static void requireValid(ValidationReport report) {
    if (!report.valid())
      throw new DocumentIntelligenceException(
          "ARTIFACT_VALIDATION_FAILED",
          422,
          "Conversion artifact failed DocLang or archive validation");
  }

  private static boolean matches(String name, OutputFormat format) {
    String lower = name.toLowerCase(java.util.Locale.ROOT);
    return switch (format) {
      case DOCLANG -> lower.endsWith(".xml") || lower.endsWith(".dclg");
      case TEXT -> lower.endsWith(".txt") || lower.endsWith(".text");
      default -> lower.endsWith("." + format.extension());
    };
  }

  private static String mediaType(String name) {
    String n = name.toLowerCase(java.util.Locale.ROOT);
    for (var format : OutputFormat.values()) if (matches(n, format)) return format.mediaType();
    return "application/octet-stream";
  }

  private record Content(byte[] bytes, String mediaType) {}
}
