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

import com.forwardmeasure.openworkflow.common.model.Problem;

/**
 * Safe RFC 9457 problem; never copies vendor bodies, secrets or evidence into exception messages.
 */
public final class DocumentIntelligenceException extends RuntimeException {
  private final String code;
  private final int status;

  public DocumentIntelligenceException(String code, int status, String detail) {
    super(detail);
    this.code = code;
    this.status = status;
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }

  public Problem problem() {
    return new Problem()
        .type("urn:forwardmeasure:document-intelligence:" + code.toLowerCase(java.util.Locale.ROOT))
        .title(code)
        .status(status)
        .detail(getMessage());
  }
}
