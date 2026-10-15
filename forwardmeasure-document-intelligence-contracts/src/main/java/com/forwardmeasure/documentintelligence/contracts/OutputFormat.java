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

public enum OutputFormat {
  JSON("json", "application/json", "json"),
  MARKDOWN("md", "text/markdown", "md"),
  TEXT("text", "text/plain", "txt"),
  HTML("html", "text/html", "html"),
  DOCLANG("doclang", "application/xml", "dclg.xml"),
  DCLX("dclx", "application/vnd.doclang.dclx+zip", "dclx");
  private final String wire, mediaType, extension;

  OutputFormat(String wire, String mediaType, String extension) {
    this.wire = wire;
    this.mediaType = mediaType;
    this.extension = extension;
  }

  public String wireValue() {
    return wire;
  }

  public String mediaType() {
    return mediaType;
  }

  public String extension() {
    return extension;
  }
}
