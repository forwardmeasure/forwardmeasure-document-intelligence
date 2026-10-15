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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DoclangValidationTest {
  private static final DoclangValidator VALIDATOR = new DoclangValidator();

  static Stream<Path> valid() throws Exception {
    return Files.list(Path.of(DoclangValidationTest.class.getResource("/doclang/valid").toURI()))
        .filter(p -> p.toString().endsWith(".dclg"));
  }

  static Stream<Path> invalid() throws Exception {
    return Files.list(Path.of(DoclangValidationTest.class.getResource("/doclang/invalid").toURI()))
        .filter(p -> p.toString().endsWith(".dclg"));
  }

  @ParameterizedTest
  @MethodSource("valid")
  void acceptsUpstreamValidFixtures(Path fixture) throws Exception {
    var report = VALIDATOR.validate(Files.readAllBytes(fixture));
    assertTrue(report.valid(), fixture + ": " + report.issues());
  }

  @ParameterizedTest
  @MethodSource("invalid")
  void rejectsUpstreamInvalidFixtures(Path fixture) throws Exception {
    assertFalse(VALIDATOR.validate(Files.readAllBytes(fixture)).valid(), fixture.toString());
  }

  @Test
  void runsSchematronForStructurallyValidInput() throws Exception {
    Path path = Path.of(getClass().getResource("/doclang/invalid").toURI());
    boolean semanticOnly = false;
    try (var files = Files.list(path)) {
      for (var file : files.toList()) {
        var report = VALIDATOR.validate(Files.readAllBytes(file));
        if (report.issues().stream().anyMatch(i -> i.stage().equals("schematron"))
            && report.issues().stream().noneMatch(i -> i.stage().equals("xsd")))
          semanticOnly = true;
      }
    }
    assertTrue(semanticOnly, "At least one XSD-valid fixture must fail the semantic rules");
  }

  @Test
  void rejectsEntitiesAndPreservesOriginalHash() {
    String xml =
        "<!DOCTYPE doclang [<!ENTITY secret SYSTEM"
            + " 'file:///etc/passwd'>]><doclang><text>&secret;</text></doclang>";
    var bytes = xml.getBytes(StandardCharsets.UTF_8);
    assertFalse(VALIDATOR.validate(bytes).valid());
    assertEquals(
        com.forwardmeasure.documentintelligence.contracts.Digests.sha256(bytes),
        VALIDATOR.validate(bytes).originalSha256());
  }

  @Test
  void namespaceNormalizationIsExplicitAndNonDestructive() {
    byte[] bytes = "<doclang><text>Hello café</text></doclang>".getBytes(StandardCharsets.UTF_8);
    var report = VALIDATOR.validate(bytes);
    assertTrue(report.valid(), report.issues().toString());
    assertTrue(report.namespaceNormalized());
    assertNotEquals(report.originalSha256(), report.normalizedSha256());
    assertFalse(new DoclangValidator(ValidationLimits.defaults(), false).validate(bytes).valid());
  }

  @Test
  void boundsXmlDepthSizeAndElements() {
    var defaults = ValidationLimits.defaults();
    var limits =
        new ValidationLimits(
            80,
            3,
            3,
            defaults.maxArchiveBytes(),
            defaults.maxArchiveMembers(),
            defaults.maxExpandedBytes(),
            defaults.maxMemberBytes(),
            defaults.maxCompressionRatio());
    var bounded = new DoclangValidator(limits, true);
    assertFalse(
        bounded
            .validate(
                "<doclang><div><div><div><text>x</text></div></div></div></doclang>"
                    .getBytes(StandardCharsets.UTF_8))
            .valid());
    assertFalse(
        bounded
            .validate(
                ("<doclang><text>" + "x".repeat(100) + "</text></doclang>")
                    .getBytes(StandardCharsets.UTF_8))
            .valid());
  }
}
