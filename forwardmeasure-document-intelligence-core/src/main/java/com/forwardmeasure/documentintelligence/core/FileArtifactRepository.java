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

import com.forwardmeasure.documentintelligence.contracts.ArtifactReference;
import com.forwardmeasure.documentintelligence.contracts.ArtifactRepository;
import com.forwardmeasure.documentintelligence.contracts.Digests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/** Local content-addressed artifacts for CLI use; outputs never overwrite different content. */
public final class FileArtifactRepository implements ArtifactRepository {
  private final Path root;

  public FileArtifactRepository(Path root) throws IOException {
    Files.createDirectories(root);
    this.root = root.toRealPath();
  }

  @Override
  public byte[] read(ArtifactReference reference, long maxBytes) throws IOException {
    if (!"file".equals(reference.uri().getScheme()) || reference.versionId() != null)
      throw new IOException("Local artifact references must use unversioned file URIs");
    Path path = Path.of(reference.uri());
    if (!path.toRealPath().startsWith(root))
      throw new IOException("Artifact lies outside the configured root");
    if (reference.size() > maxBytes
        || Files.size(path) > maxBytes
        || maxBytes < 1
        || maxBytes > Integer.MAX_VALUE - 8)
      throw new IOException("Source exceeds the input bound");
    byte[] bytes;
    try (var input = Files.newInputStream(path)) {
      bytes = input.readNBytes(Math.toIntExact(maxBytes) + 1);
    }
    if (bytes.length > maxBytes
        || bytes.length != reference.size()
        || !Digests.sha256(bytes).equals(reference.sha256()))
      throw new IOException("Source identity does not match its bytes");
    return bytes;
  }

  @Override
  public ArtifactReference write(
      String operationId, String name, byte[] content, String media, Map<String, String> metadata)
      throws IOException {
    Digests.requireSha256(operationId, "operation ID");
    if (name == null
        || name.isBlank()
        || name.length() > 1024
        || name.contains("\\")
        || name.contains("\0")) throw new IllegalArgumentException("Invalid artifact name");
    // Storage paths are hashes; upstream member names are preserved only in the returned receipt.
    String digest = Digests.sha256(content);
    String nameDigest = Digests.sha256(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    Path directory = root.resolve(operationId);
    Files.createDirectories(directory);
    if (!directory.toRealPath().getParent().equals(root))
      throw new IOException("Unsafe artifact directory");
    Path destination = directory.resolve(nameDigest + "-" + digest);
    Path temporary = Files.createTempFile(directory, ".pending-", ".artifact");
    try {
      Files.write(temporary, content);
      try (var channel =
          java.nio.channels.FileChannel.open(temporary, java.nio.file.StandardOpenOption.WRITE)) {
        channel.force(true);
      }
      if (Files.exists(destination)) {
        if (!destination.toRealPath().getParent().equals(directory.toRealPath())
            || !Digests.sha256(Files.readAllBytes(destination)).equals(digest))
          throw new IOException("Existing artifact identity is inconsistent");
      } else Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(temporary);
    }
    return new ArtifactReference(destination.toUri(), null, digest, content.length, media);
  }
}
