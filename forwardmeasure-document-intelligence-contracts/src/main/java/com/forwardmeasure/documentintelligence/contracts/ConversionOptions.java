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
package com.forwardmeasure.documentintelligence.contracts;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ConversionOptions(
    Pipeline pipeline,
    Set<OutputFormat> formats,
    boolean doOcr,
    boolean forceOcr,
    List<String> ocrLanguages,
    String vlmPreset,
    ImageMode imageMode,
    int firstPage,
    int lastPage,
    boolean includePageImages,
    double imageScale,
    Duration documentTimeout,
    Set<Enrichment> enrichments,
    Map<String, String> stagePresets) {
  private static final Set<String> STAGES =
      Set.of(
          "layout_preset",
          "table_structure_preset",
          "code_formula_preset",
          "picture_classification_preset",
          "picture_description_preset",
          "ocr_preset");

  public ConversionOptions {
    Objects.requireNonNull(pipeline, "pipeline");
    Objects.requireNonNull(imageMode, "imageMode");
    formats = Set.copyOf(formats);
    ocrLanguages = List.copyOf(ocrLanguages);
    enrichments = Set.copyOf(enrichments);
    stagePresets = Map.copyOf(stagePresets);
    if (formats.isEmpty())
      throw new IllegalArgumentException("at least one output format is required");
    if (firstPage < 1 || lastPage < firstPage)
      throw new IllegalArgumentException("page range is invalid");
    if (!Double.isFinite(imageScale) || imageScale <= 0 || imageScale > 2)
      throw new IllegalArgumentException("image scale must be in (0,2]");
    if (documentTimeout == null
        || documentTimeout.isZero()
        || documentTimeout.isNegative()
        || documentTimeout.compareTo(Duration.ofDays(1)) > 0)
      throw new IllegalArgumentException("document timeout must be in (0,1 day]");
    if (ocrLanguages.size() > 16
        || ocrLanguages.stream().anyMatch(s -> s == null || !s.matches("[A-Za-z0-9_-]{1,32}")))
      throw new IllegalArgumentException("OCR languages are invalid");
    if (pipeline == Pipeline.VLM
        && (vlmPreset == null
            || !vlmPreset.matches("[A-Za-z0-9_.-]{1,128}")
            || vlmPreset.equals("default")))
      throw new IllegalArgumentException("VLM requires an explicit named prepared preset");
    if (pipeline == Pipeline.STANDARD && vlmPreset != null)
      throw new IllegalArgumentException("standard pipeline does not select a VLM preset");
    if (stagePresets.entrySet().stream()
        .anyMatch(
            e -> !STAGES.contains(e.getKey()) || !e.getValue().matches("[A-Za-z0-9_.-]{1,128}")))
      throw new IllegalArgumentException("stage presets are invalid");
  }

  public static ConversionOptions standard() {
    return new ConversionOptions(
        Pipeline.STANDARD,
        Set.of(OutputFormat.JSON, OutputFormat.MARKDOWN),
        true,
        false,
        List.of(),
        null,
        ImageMode.PLACEHOLDER,
        1,
        Integer.MAX_VALUE,
        false,
        1.0,
        Duration.ofHours(1),
        Set.of(),
        Map.of());
  }

  public ConversionOptions withFormats(Set<OutputFormat> value) {
    return new ConversionOptions(
        pipeline,
        value,
        doOcr,
        forceOcr,
        ocrLanguages,
        vlmPreset,
        imageMode,
        firstPage,
        lastPage,
        includePageImages,
        imageScale,
        documentTimeout,
        enrichments,
        stagePresets);
  }

  public ConversionOptions withVlm(String preset) {
    return new ConversionOptions(
        Pipeline.VLM,
        formats,
        doOcr,
        forceOcr,
        ocrLanguages,
        preset,
        imageMode,
        firstPage,
        lastPage,
        includePageImages,
        imageScale,
        documentTimeout,
        enrichments,
        stagePresets);
  }

  public ConversionOptions withImageMode(ImageMode value) {
    return new ConversionOptions(
        pipeline,
        formats,
        doOcr,
        forceOcr,
        ocrLanguages,
        vlmPreset,
        value,
        firstPage,
        lastPage,
        includePageImages,
        imageScale,
        documentTimeout,
        enrichments,
        stagePresets);
  }

  public boolean archiveRequired() {
    return formats.contains(OutputFormat.DCLX) || imageMode == ImageMode.REFERENCED;
  }
}
