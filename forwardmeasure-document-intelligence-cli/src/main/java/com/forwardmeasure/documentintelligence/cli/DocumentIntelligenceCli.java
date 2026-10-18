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
package com.forwardmeasure.documentintelligence.cli;

import com.forwardmeasure.documentintelligence.contracts.ConversionContext;
import com.forwardmeasure.documentintelligence.contracts.ConversionReceipt;
import com.forwardmeasure.documentintelligence.contracts.ConversionSubmission;
import com.forwardmeasure.documentintelligence.contracts.Digests;
import com.forwardmeasure.documentintelligence.contracts.RuntimeIdentity;
import com.forwardmeasure.documentintelligence.core.DoclingConfiguration;
import com.forwardmeasure.documentintelligence.core.DoclingOptions;
import com.forwardmeasure.documentintelligence.core.DocumentIntelligenceException;
import com.forwardmeasure.documentintelligence.core.DocumentIntelligenceService;
import com.forwardmeasure.documentintelligence.core.FileArtifactRepository;
import com.forwardmeasure.documentintelligence.core.GeneratedDoclingGateway;
import com.forwardmeasure.documentintelligence.core.ProvenanceJson;
import com.forwardmeasure.documentintelligence.docling.generated.model.ConversionStatus;
import com.forwardmeasure.documentintelligence.docling.generated.model.OutputFormat;
import com.forwardmeasure.documentintelligence.validation.DoclangArchiveValidator;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runnable operator client; secrets are read from the environment rather than command arguments.
 */
public final class DocumentIntelligenceCli {
  private DocumentIntelligenceCli() {}

  public static void main(String[] args) {
    int status = run(args, System.getenv(), System.out, System.err);
    if (status != 0) System.exit(status);
  }

  public static int run(
      String[] args, Map<String, String> environment, PrintStream out, PrintStream err) {
    if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
      out.println(
          "Commands: health | convert <file> <artifact-root> [json,md,doclang,dclx] | submit <file>"
              + " <artifact-root> [formats] | poll <submission.json> | fetch <submission.json>"
              + " <artifact-root> | validate <file.xml|file.dclx>");
      return 0;
    }
    try {
      if (args[0].equals("validate")) {
        requireArgs(args, 2, 2);
        byte[] bytes = readBounded(Path.of(args[1]), 64 * 1024 * 1024);
        var validator = new DoclangArchiveValidator();
        var result =
            args[1].toLowerCase(java.util.Locale.ROOT).endsWith(".dclx")
                ? validator.validateDclx(bytes)
                : validator.validateDoclang(bytes);
        out.println(new String(ProvenanceJson.encode(result), StandardCharsets.UTF_8));
        return result.valid() ? 0 : 1;
      }
      var settings =
          DoclingConfiguration.read(
              k ->
                  environment.get(
                      k.toUpperCase(java.util.Locale.ROOT).replace('.', '_').replace('-', '_')));
      try (var gateway = new GeneratedDoclingGateway(settings)) {
        switch (args[0]) {
          case "health" -> {
            requireArgs(args, 1, 1);
            out.println(
                new String(
                    ProvenanceJson.encode(
                        Map.of("ready", gateway.ready(), "versions", gateway.versions())),
                    StandardCharsets.UTF_8));
          }
          case "submit", "convert" -> {
            requireArgs(args, 3, 4);
            var root = Path.of(args[2]);
            var repository = new FileArtifactRepository(root);
            var service =
                new DocumentIntelligenceService(
                    gateway, repository, new DoclangArchiveValidator(), settings.maxInputBytes());
            byte[] bytes = readBounded(Path.of(args[1]), settings.maxInputBytes());
            var source =
                repository.write(
                    Digests.sha256(bytes), "source", bytes, "application/octet-stream", Map.of());
            String image = environment.get("DOCUMENT_INTELLIGENCE_RUNTIME_IMAGE");
            String bundle = environment.get("DOCUMENT_INTELLIGENCE_MODEL_BUNDLE_SHA256");
            var versions = new java.util.TreeMap<String, String>();
            gateway.versions().forEach((k, v) -> versions.put(k, String.valueOf(v)));
            var context =
                new ConversionContext(
                    environment.getOrDefault("DOCUMENT_INTELLIGENCE_SCOPE", "cli"),
                    new RuntimeIdentity(image, bundle, versions));
            var formats =
                args.length == 4
                    ? Arrays.stream(args[3].split(","))
                        .map(DocumentIntelligenceCli::format)
                        .collect(Collectors.toUnmodifiableSet())
                    : Set.of(OutputFormat.JSON, OutputFormat.MD);
            var submission =
                service.submit(
                    source,
                    Path.of(args[1]).getFileName().toString(),
                    DoclingOptions.standard().toFormats(formats.stream().sorted().toList()),
                    context);
            Path handle = root.resolve(submission.operationId() + ".submission.json");
            Files.write(handle, ProvenanceJson.encode(submission));
            out.println(handle.toAbsolutePath());
            if (args[0].equals("convert")) {
              var progress =
                  gateway.await(
                      submission.task(),
                      Duration.parse(
                          environment.getOrDefault("DOCUMENT_INTELLIGENCE_DEADLINE", "PT15M")),
                      () -> false);
              if (progress.getTaskStatus() != ConversionStatus.SUCCESS)
                throw new DocumentIntelligenceException(
                    "TASK_NOT_SUCCESSFUL", 422, "Task did not complete successfully");
              saveReceipt(root, service.retrieveAndStore(submission), out);
            }
          }
          case "poll" -> {
            requireArgs(args, 2, 2);
            var submission =
                ProvenanceJson.decode(
                    readBounded(Path.of(args[1]), 1024 * 1024), ConversionSubmission.class);
            out.println(
                new String(
                    ProvenanceJson.encode(gateway.poll(submission.task(), Duration.ZERO)),
                    StandardCharsets.UTF_8));
          }
          case "fetch" -> {
            requireArgs(args, 3, 3);
            var root = Path.of(args[2]);
            var submission =
                ProvenanceJson.decode(
                    readBounded(Path.of(args[1]), 1024 * 1024), ConversionSubmission.class);
            var service =
                new DocumentIntelligenceService(
                    gateway,
                    new FileArtifactRepository(root),
                    new DoclangArchiveValidator(),
                    settings.maxInputBytes());
            saveReceipt(root, service.retrieveAndStore(submission), out);
          }
          default -> throw new IllegalArgumentException("Unknown command; run --help");
        }
      }
      return 0;
    } catch (DocumentIntelligenceException error) {
      err.println(error.code() + ": " + error.getMessage());
      return 1;
    } catch (Exception error) {
      err.println("Invalid command, configuration or artifact; run --help and consult README.md");
      return 2;
    }
  }

  private static OutputFormat format(String value) {
    return OutputFormat.fromValue(value.trim());
  }

  private static void requireArgs(String[] args, int min, int max) {
    if (args.length < min || args.length > max)
      throw new IllegalArgumentException("Invalid arguments");
  }

  private static byte[] readBounded(Path path, long max) throws java.io.IOException {
    if (Files.size(path) > max) throw new java.io.IOException("Input exceeds bound");
    try (var input = Files.newInputStream(path)) {
      byte[] bytes = input.readNBytes(Math.toIntExact(max) + 1);
      if (bytes.length > max) throw new java.io.IOException("Input exceeds bound");
      return bytes;
    }
  }

  private static void saveReceipt(Path root, ConversionReceipt receipt, PrintStream out)
      throws java.io.IOException {
    Path path = root.resolve(receipt.operationId() + ".receipt.json");
    Files.write(path, ProvenanceJson.encode(receipt));
    out.println(path.toAbsolutePath());
  }
}
