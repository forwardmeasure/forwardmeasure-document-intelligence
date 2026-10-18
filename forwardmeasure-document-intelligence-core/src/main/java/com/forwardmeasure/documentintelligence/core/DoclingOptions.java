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
import com.forwardmeasure.documentintelligence.docling.generated.model.ConvertDocumentsOptions;
import com.forwardmeasure.documentintelligence.docling.generated.model.ImageRefMode;
import com.forwardmeasure.documentintelligence.docling.generated.model.OutputFormat;
import com.forwardmeasure.documentintelligence.docling.generated.model.ProcessingPipeline;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Defaults and application policy for upstream-generated conversion options. */
public final class DoclingOptions {
  private DoclingOptions() {}

  public static ConvertDocumentsOptions standard() {
    return new ConvertDocumentsOptions()
        .pipeline(ProcessingPipeline.STANDARD)
        .toFormats(List.of(OutputFormat.JSON, OutputFormat.MD))
        .doOcr(true)
        .forceOcr(false)
        .ocrLang(List.of())
        .doTableStructure(true)
        .imageExportMode(ImageRefMode.PLACEHOLDER)
        .includePageImages(false)
        .imagesScale(BigDecimal.ONE)
        .documentTimeout(BigDecimal.valueOf(3600))
        .pageRange(List.of(1L, (long) Integer.MAX_VALUE))
        .doCodeEnrichment(false)
        .doFormulaEnrichment(false)
        .doPictureClassification(false)
        .doPictureDescription(false)
        .doChartExtraction(false);
  }

  /** An omitted format list uses the pinned upstream schema's Markdown default. */
  public static List<OutputFormat> formats(ConvertDocumentsOptions options) {
    Objects.requireNonNull(options, "options");
    return options.getToFormats() == null
        ? List.of(OutputFormat.MD)
        : List.copyOf(options.getToFormats());
  }

  public static boolean archiveRequired(ConvertDocumentsOptions options) {
    return formats(options).contains(OutputFormat.DCLX)
        || options.getImageExportMode() == ImageRefMode.REFERENCED;
  }

  static void validate(ConvertDocumentsOptions options) {
    if (formats(options).isEmpty())
      throw new IllegalArgumentException("at least one output format is required");
    var pages = options.getPageRange();
    if (pages != null
        && (pages.size() != 2
            || pages.get(0) == null
            || pages.get(1) == null
            || pages.get(0) < 1
            || pages.get(1) < pages.get(0)))
      throw new IllegalArgumentException("page range is invalid");
    var scale = options.getImagesScale();
    if (scale != null && (scale.signum() <= 0 || scale.compareTo(BigDecimal.valueOf(2)) > 0))
      throw new IllegalArgumentException("image scale must be in (0,2]");
    var timeout = options.getDocumentTimeout();
    if (timeout != null
        && (timeout.signum() <= 0 || timeout.compareTo(BigDecimal.valueOf(86400)) > 0))
      throw new IllegalArgumentException("document timeout must be in (0,1 day]");
    var languages = options.getOcrLang();
    if (languages != null
        && (languages.size() > 16
            || languages.stream().anyMatch(s -> s == null || !s.matches("[A-Za-z0-9_-]{1,32}"))))
      throw new IllegalArgumentException("OCR languages are invalid");
    String vlm = options.getVlmPipelinePreset();
    if (options.getPipeline() == ProcessingPipeline.VLM
        && (vlm == null || !vlm.matches("[A-Za-z0-9_.-]{1,128}") || vlm.equals("default")))
      throw new IllegalArgumentException("VLM requires an explicit named prepared preset");
    if (options.getPipeline() == ProcessingPipeline.STANDARD && vlm != null)
      throw new IllegalArgumentException("standard pipeline does not select a VLM preset");
    for (String preset :
        java.util.Arrays.asList(
            options.getLayoutPreset(),
            options.getTableStructurePreset(),
            options.getCodeFormulaPreset(),
            options.getPictureClassificationPreset(),
            options.getPictureDescriptionPreset(),
            options.getOcrPreset(),
            options.getChartExtractionPreset()))
      if (preset != null && !preset.matches("[A-Za-z0-9_.-]{1,128}"))
        throw new IllegalArgumentException("stage preset is invalid");
  }

  static Set<Enrichment> enrichments(ConvertDocumentsOptions options) {
    var result = EnumSet.noneOf(Enrichment.class);
    if (Boolean.TRUE.equals(options.getDoCodeEnrichment())) result.add(Enrichment.CODE);
    if (Boolean.TRUE.equals(options.getDoFormulaEnrichment())) result.add(Enrichment.FORMULA);
    if (Boolean.TRUE.equals(options.getDoPictureClassification()))
      result.add(Enrichment.PICTURE_CLASSIFICATION);
    if (Boolean.TRUE.equals(options.getDoPictureDescription()))
      result.add(Enrichment.PICTURE_DESCRIPTION);
    if (Boolean.TRUE.equals(options.getDoChartExtraction()))
      result.add(Enrichment.CHART_EXTRACTION);
    return result;
  }
}
