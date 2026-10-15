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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {
  @TempDir Path root;

  @Test
  void providesHelpAndRejectsInvalidCommandsWithoutExposingSecrets() {
    var out = new ByteArrayOutputStream();
    var err = new ByteArrayOutputStream();
    assertEquals(
        0,
        DocumentIntelligenceCli.run(
            new String[] {"--help"}, Map.of(), new PrintStream(out), new PrintStream(err)));
    assertTrue(out.toString().contains("submit"));
    assertEquals(
        2,
        DocumentIntelligenceCli.run(
            new String[] {"bad"},
            Map.of("DOCUMENT_INTELLIGENCE_DOCLING_API_KEY", "secret"),
            new PrintStream(out),
            new PrintStream(err)));
    assertFalse(err.toString().contains("secret"));
  }

  @Test
  void validatesDoclangFromLocalFile() throws Exception {
    Path file = root.resolve("valid.xml");
    Files.writeString(file, "<doclang><text>café</text></doclang>");
    assertEquals(
        0,
        DocumentIntelligenceCli.run(
            new String[] {"validate", file.toString()},
            Map.of(),
            new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(new ByteArrayOutputStream())));
    Files.writeString(file, "<bad/>");
    assertEquals(
        1,
        DocumentIntelligenceCli.run(
            new String[] {"validate", file.toString()},
            Map.of(),
            new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(new ByteArrayOutputStream())));
  }
}
