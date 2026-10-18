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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ContractTest {
  @Test
  void binaryValuesDefensivelyCopyEvidence() {
    byte[] bytes = {1, 2};
    var document = new BinaryDocument("a.pdf", bytes);
    bytes[0] = 9;
    document.content()[0] = 8;
    assertEquals(1, document.content()[0]);
    assertEquals(Digests.sha256(new byte[] {1, 2}), document.sha256());
    assertFalse(document.toString().contains("[1, 2]"));
    var payload = new ConversionPayload(bytes, "application/json");
    payload.content()[0] = 4;
    assertEquals(9, payload.content()[0]);
  }

  @Test
  void boundsOpaqueIdentifiersAndReferences() {
    assertThrows(IllegalArgumentException.class, () -> new TaskHandle("../task", false));
    assertThrows(
        IllegalArgumentException.class, () -> new BinaryDocument("../file.pdf", new byte[] {1}));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ArtifactReference(
                URI.create("https://user:secret@store/key"),
                null,
                "a".repeat(64),
                1,
                "application/pdf"));
  }
}
