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
package com.forwardmeasure.documentintelligence.objectstorage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.documentintelligence.contracts.ArtifactReference;
import com.forwardmeasure.documentintelligence.contracts.Digests;
import com.forwardmeasure.objectstorage.ObjectMetadata;
import com.forwardmeasure.objectstorage.StorageClient;
import com.forwardmeasure.objectstorage.StorageObject;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StorageArtifactRepositoryTest {
  @Test
  void pinsVersionChecksHashAndPreservesMetadata() throws Exception {
    byte[] bytes = {1, 2, 3};
    var get = new AtomicReference<StorageClient.GetObjectRequest>();
    var put = new AtomicReference<StorageClient.PutObjectRequest>();
    var closed = new java.util.concurrent.atomic.AtomicBoolean();
    var metadata =
        (ObjectMetadata)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] {ObjectMetadata.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "contentLength" -> 3L;
                      case "versionId" -> Optional.of("generation-7");
                      default -> Optional.empty();
                    });
    var storage =
        (StorageClient)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] {StorageClient.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "getObject" -> {
                        get.set((StorageClient.GetObjectRequest) args[0]);
                        yield new StorageObject() {
                          public String bucketName() {
                            return "bucket";
                          }

                          public String key() {
                            return "artifacts/source";
                          }

                          public ObjectMetadata metadata() {
                            return metadata;
                          }

                          public java.io.InputStream content() {
                            return new ByteArrayInputStream(bytes);
                          }

                          public void close() {
                            closed.set(true);
                          }
                        };
                      }
                      case "putObject" -> {
                        var request = (StorageClient.PutObjectRequest) args[0];
                        put.set(request);
                        yield new StorageClient.PutObjectResult(
                            request.bucketName(), request.key(), "etag", "generation-8");
                      }
                      default ->
                          throw new AssertionError(
                              "Unexpected storage operation: " + method.getName());
                    });
    var repository = new StorageArtifactRepository(storage, "gcs", "bucket", "artifacts");
    var reference =
        new ArtifactReference(
            URI.create("gs://bucket/artifacts/source"),
            "generation-7",
            Digests.sha256(bytes),
            3,
            "application/pdf");
    assertArrayEquals(bytes, repository.read(reference, 100));
    assertEquals("generation-7", get.get().versionId());
    assertTrue(closed.get());
    var output =
        repository.write(
            "a".repeat(64),
            "document.md",
            bytes,
            "text/markdown",
            Map.of("source-sha256", reference.sha256()));
    assertEquals("generation-8", output.versionId());
    assertEquals(reference.sha256(), put.get().userMetadata().get("sha256"));
    assertTrue(output.uri().getPath().startsWith("/artifacts/"));
    var wrong =
        new ArtifactReference(
            reference.uri(), reference.versionId(), "b".repeat(64), 3, reference.mediaType());
    assertThrows(java.io.IOException.class, () -> repository.read(wrong, 100));
    var wrongVersion =
        new ArtifactReference(
            reference.uri(), "other", reference.sha256(), 3, reference.mediaType());
    assertThrows(java.io.IOException.class, () -> repository.read(wrongVersion, 100));
    assertThrows(java.io.IOException.class, () -> repository.read(reference, 2));
  }
}
