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

import com.fasterxml.jackson.core.type.TypeReference;
import com.forwardmeasure.documentintelligence.docling.generated.ApiClient;
import com.forwardmeasure.documentintelligence.docling.generated.ApiException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;

/** Bounds success and error entities, and ensures failed downloads do not leave partial files. */
final class BoundedApiClient extends ApiClient {
  private final int maxBytes;

  BoundedApiClient(CloseableHttpClient transport, long maxBytes) {
    super(transport);
    this.maxBytes = Math.toIntExact(maxBytes);
  }

  @Override
  protected <T> T processResponse(CloseableHttpResponse response, TypeReference<T> type)
      throws ApiException, IOException, ParseException {
    var entity = response.getEntity();
    if (entity != null) {
      if (entity.getContentLength() > maxBytes)
        throw new IOException("Response exceeds configured bound");
      byte[] bytes;
      try (var stream = entity.getContent()) {
        bytes = stream.readNBytes(maxBytes + 1);
      }
      if (bytes.length > maxBytes) throw new IOException("Response exceeds configured bound");
      var contentType =
          entity.getContentType() == null
              ? ContentType.APPLICATION_JSON
              : ContentType.parse(entity.getContentType());
      response.setEntity(new ByteArrayEntity(bytes, contentType));
    }
    return super.processResponse(response, type);
  }

  @Override
  protected File downloadFileFromResponse(CloseableHttpResponse response) throws IOException {
    var path = Files.createTempFile("fdi-docling-", ".response");
    try (var stream = response.getEntity().getContent()) {
      Files.copy(stream, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      return path.toFile();
    } catch (IOException | RuntimeException error) {
      Files.deleteIfExists(path);
      throw error;
    }
  }
}
