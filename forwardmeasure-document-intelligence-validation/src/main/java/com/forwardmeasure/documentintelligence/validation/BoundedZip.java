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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

/** Reads the central directory and bounded members without extracting filesystem paths. */
public final class BoundedZip {
  private BoundedZip() {}

  public static final class Budget {
    private long bytes;
    private int members;

    void member(ValidationLimits limits) throws IOException {
      if (++members > limits.maxArchiveMembers())
        throw new IOException("Excessive archive member count");
    }

    void consume(int count, ValidationLimits limits) throws IOException {
      bytes += count;
      if (bytes > limits.maxExpandedBytes())
        throw new IOException("Excessive aggregate archive expansion");
    }
  }

  public static Map<String, byte[]> read(byte[] archive, ValidationLimits limits)
      throws IOException {
    return read(archive, limits, new Budget());
  }

  public static Map<String, byte[]> read(byte[] archive, ValidationLimits limits, Budget budget)
      throws IOException {
    if (archive.length > limits.maxArchiveBytes() || archive.length < 22)
      throw new IOException("Invalid or excessive archive");
    var members = new LinkedHashMap<String, byte[]>();
    try (var channel = new SeekableInMemoryByteChannel(archive);
        var zip = ZipFile.builder().setSeekableByteChannel(channel).get()) {
      var entries = zip.getEntries();
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        budget.member(limits);
        String name = entry.getName();
        requirePath(name);
        if (members.containsKey(name)
            || entry.isUnixSymlink()
            || entry.getGeneralPurposeBit().usesEncryption()
            || !zip.canReadEntryData(entry)) throw new IOException("Unsafe archive member");
        int mode = entry.getUnixMode() & 0170000;
        if (mode != 0 && mode != 0100000 && mode != 0040000)
          throw new IOException("Non-regular archive member");
        if (entry.getSize() < 0
            || entry.getSize() > limits.maxMemberBytes()
            || entry.getCompressedSize() < 0
            || entry.getSize() / (double) Math.max(1, entry.getCompressedSize())
                > limits.maxCompressionRatio())
          throw new IOException("Excessive archive expansion");
        if (entry.isDirectory()) {
          members.put(name, new byte[0]);
          continue;
        }
        var out = new ByteArrayOutputStream();
        var crc = new CRC32();
        long size = 0;
        try (var input = zip.getInputStream(entry)) {
          byte[] buffer = new byte[8192];
          for (int count; (count = input.read(buffer)) != -1; ) {
            size += count;
            budget.consume(count, limits);
            if (size > limits.maxMemberBytes()) throw new IOException("Excessive member size");
            out.write(buffer, 0, count);
            crc.update(buffer, 0, count);
          }
        }
        if (size != entry.getSize() || crc.getValue() != entry.getCrc())
          throw new IOException("Corrupt archive member");
        members.put(name, out.toByteArray());
      }
    }
    if (members.isEmpty()) throw new IOException("Archive has no members");
    return Map.copyOf(members);
  }

  public static void requirePath(String name) throws IOException {
    if (name == null
        || name.isBlank()
        || name.length() > 1024
        || name.startsWith("/")
        || name.contains("\\")
        || name.indexOf('\0') >= 0
        || name.contains(":")
        || name.contains("%")
        || java.util.Arrays.stream(name.split("/", -1))
            .anyMatch(p -> p.equals("..") || p.equals(".")))
      throw new IOException("Unsafe member path");
  }
}
