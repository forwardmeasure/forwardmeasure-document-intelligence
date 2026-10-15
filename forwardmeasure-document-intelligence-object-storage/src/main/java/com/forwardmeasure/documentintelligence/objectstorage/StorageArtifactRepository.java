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

import com.forwardmeasure.documentintelligence.contracts.ArtifactReference;
import com.forwardmeasure.documentintelligence.contracts.ArtifactRepository;
import com.forwardmeasure.documentintelligence.contracts.Digests;
import com.forwardmeasure.objectstorage.StorageClient;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Adapter to shared ForwardMeasure storage; ownership of the StorageClient remains with its caller.
 */
public final class StorageArtifactRepository implements ArtifactRepository {
  private final StorageClient storage;
  private final String backend, bucket, prefix;
  private final String scheme;

  public StorageArtifactRepository(
      StorageClient storage, String backend, String bucket, String prefix) {
    this.storage = java.util.Objects.requireNonNull(storage);
    this.backend = backend;
    this.bucket = bucket;
    if (bucket == null
        || !bucket.matches("[A-Za-z0-9._-]{1,255}")
        || prefix == null
        || !prefix.matches("[A-Za-z0-9/_-]{0,512}")
        || prefix.contains("//"))
      throw new IllegalArgumentException("Invalid artifact storage location");
    this.prefix = prefix.isEmpty() ? "" : prefix.replaceAll("/+$", "") + "/";
    scheme = URI.create(StorageClient.buildPlatformUri(backend, bucket, "probe")).getScheme();
  }

  @Override
  public byte[] read(ArtifactReference reference, long maxBytes) throws IOException {
    var uri = reference.uri();
    if (!scheme.equals(uri.getScheme())
        || !bucket.equals(uri.getHost())
        || uri.getPath() == null
        || !uri.getPath().startsWith("/" + prefix))
      throw new IOException("Artifact is outside this repository");
    if (maxBytes < 1 || maxBytes > Integer.MAX_VALUE - 8 || reference.size() > maxBytes)
      throw new IOException("Source exceeds input bound");
    try (var object =
        storage.getObject(
            StorageClient.GetObjectRequest.ofVersion(
                bucket, uri.getPath().substring(1), reference.versionId()))) {
      if (reference.versionId() != null
          && !reference.versionId().equals(object.metadata().versionId().orElse(null)))
        throw new IOException("Storage returned a different object version");
      if (object.metadata().contentLength() != reference.size())
        throw new IOException("Storage length differs from the declared source");
      byte[] bytes = object.content().readNBytes(Math.toIntExact(maxBytes) + 1);
      if (bytes.length > maxBytes
          || bytes.length != reference.size()
          || !Digests.sha256(bytes).equals(reference.sha256()))
        throw new IOException("Storage bytes differ from the declared source");
      return bytes;
    }
  }

  @Override
  public ArtifactReference write(
      String operationId, String name, byte[] content, String media, Map<String, String> metadata)
      throws IOException {
    Digests.requireSha256(operationId, "operation ID");
    if (name == null || name.isBlank() || name.length() > 1024)
      throw new IllegalArgumentException("Invalid artifact name");
    String digest = Digests.sha256(content);
    String key =
        prefix
            + operationId
            + "/"
            + Digests.sha256(name.getBytes(StandardCharsets.UTF_8))
            + "-"
            + digest;
    var attributes = new HashMap<>(metadata);
    attributes.put("sha256", digest);
    var result =
        storage.putObject(
            StorageClient.PutObjectRequest.fromBytes(bucket, key, content, media, attributes));
    if (!bucket.equals(result.bucketName()) || !key.equals(result.key()))
      throw new IOException("Storage returned a different artifact location");
    return new ArtifactReference(
        URI.create(StorageClient.buildPlatformUri(backend, bucket, key)),
        result.versionId(),
        digest,
        content.length,
        media);
  }
}
