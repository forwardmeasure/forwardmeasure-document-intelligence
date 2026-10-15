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
package com.forwardmeasure.documentintelligence.quarkus;

import com.forwardmeasure.documentintelligence.core.DoclingConfiguration;
import com.forwardmeasure.documentintelligence.core.DoclingGateway;
import com.forwardmeasure.documentintelligence.core.DoclingSettings;
import com.forwardmeasure.documentintelligence.core.GeneratedDoclingGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.ConfigProvider;

@ApplicationScoped
public class DocumentIntelligenceProducer {
  @Produces
  @Singleton
  public DoclingSettings settings() {
    return DoclingConfiguration.read(
        k -> ConfigProvider.getConfig().getOptionalValue(k, String.class).orElse(null));
  }

  @Produces
  @Singleton
  public DoclingGateway gateway(DoclingSettings settings) {
    return new GeneratedDoclingGateway(settings);
  }

  public void close(@Disposes DoclingGateway gateway) {
    gateway.close();
  }
}
