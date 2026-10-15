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
package com.forwardmeasure.documentintelligence.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

class ArchiveValidationTest {
  static byte[] zip(Map<String, byte[]> entries) throws Exception {
    var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(bytes)) {
      for (var e : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(e.getKey()));
        zip.write(e.getValue());
        zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }

  static byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  static Map<String, byte[]> packageParts() {
    return new java.util.HashMap<>(
        Map.of(
            "document.xml",
            utf8("<doclang><text>Hello</text></doclang>"),
            "[Content_Types].xml",
            utf8(
                "<Types"
                    + " xmlns='http://schemas.openxmlformats.org/package/2006/content-types'><Override"
                    + " PartName='/document.xml'"
                    + " ContentType='application/vnd.doclang.document+xml'/></Types>"),
            "_rels/.rels",
            utf8(
                "<Relationships"
                    + " xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship"
                    + " Id='rId1' Target='document.xml'"
                    + " Type='http://doclang.ai/ns/package/2026/relationships/document'/></Relationships>")));
  }

  @Test
  void validatesOpcAndNestedDclx() throws Exception {
    var validator = new DoclangArchiveValidator();
    var packageBytes = zip(packageParts());
    assertTrue(validator.validateDclx(packageBytes).valid());
    assertTrue(validator.validateArchive(zip(Map.of("result.dclx", packageBytes))).valid());
  }

  @Test
  void rejectsMissingAssetsContentTypeAndRelationships() throws Exception {
    var validator = new DoclangArchiveValidator();
    var parts = packageParts();
    parts.put(
        "document.xml",
        utf8("<doclang><picture><src uri='media/missing.png'/></picture></doclang>"));
    assertFalse(validator.validateDclx(zip(parts)).valid());
    parts = packageParts();
    parts.remove("_rels/.rels");
    assertFalse(validator.validateDclx(zip(parts)).valid());
    parts = packageParts();
    parts.put(
        "[Content_Types].xml",
        utf8("<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'/>"));
    assertFalse(validator.validateDclx(zip(parts)).valid());
  }

  @Test
  void rejectsTraversalSymlinksAndTruncatedArchive() throws Exception {
    assertThrows(
        java.io.IOException.class,
        () -> BoundedZip.read(zip(Map.of("../outside", utf8("x"))), ValidationLimits.defaults()));
    var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipArchiveOutputStream(bytes)) {
      var entry = new ZipArchiveEntry("link");
      entry.setUnixMode(0120777);
      zip.putArchiveEntry(entry);
      zip.write(utf8("/etc/passwd"));
      zip.closeArchiveEntry();
    }
    assertThrows(
        java.io.IOException.class,
        () -> BoundedZip.read(bytes.toByteArray(), ValidationLimits.defaults()));
    var valid = zip(Map.of("ok", utf8("x")));
    assertThrows(
        java.io.IOException.class,
        () ->
            BoundedZip.read(
                java.util.Arrays.copyOf(valid, valid.length - 22), ValidationLimits.defaults()));
  }

  @Test
  void enforcesSharedNestedExpansionBudget() throws Exception {
    var defaults = ValidationLimits.defaults();
    var inner = zip(packageParts());
    var outer = zip(Map.of("nested.dclx", inner));
    var limits =
        new ValidationLimits(
            defaults.maxXmlBytes(),
            defaults.maxDepth(),
            defaults.maxElements(),
            defaults.maxArchiveBytes(),
            100,
            inner.length + 20,
            defaults.maxMemberBytes(),
            1000);
    var validator = new DoclangArchiveValidator(new DoclangValidator(limits, true));
    assertFalse(validator.validateArchive(outer).valid());
  }

  @Test
  void checksCompressionAndMemberCount() throws Exception {
    var d = ValidationLimits.defaults();
    var limits =
        new ValidationLimits(
            d.maxXmlBytes(),
            d.maxDepth(),
            d.maxElements(),
            d.maxArchiveBytes(),
            1,
            100000,
            100000,
            2);
    assertThrows(
        java.io.IOException.class,
        () -> BoundedZip.read(zip(Map.of("a", new byte[10000])), limits));
    assertThrows(
        java.io.IOException.class,
        () -> BoundedZip.read(zip(Map.of("a", utf8("a"), "b", utf8("b"))), limits));
  }
}
