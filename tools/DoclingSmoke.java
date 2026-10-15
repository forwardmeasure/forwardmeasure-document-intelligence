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
import com.forwardmeasure.documentintelligence.core.DoclingSettings;
import com.forwardmeasure.documentintelligence.core.GeneratedDoclingGateway;
import com.forwardmeasure.documentintelligence.contracts.BinaryDocument;
import com.forwardmeasure.documentintelligence.contracts.ConversionOptions;
import com.forwardmeasure.documentintelligence.contracts.OutputFormat;
import com.forwardmeasure.documentintelligence.contracts.TaskState;
import com.forwardmeasure.documentintelligence.validation.DoclangArchiveValidator;
import com.forwardmeasure.documentintelligence.validation.BoundedZip;
import com.forwardmeasure.documentintelligence.validation.ValidationLimits;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Set;
public class DoclingSmoke {
 public static void main(String[] args)throws Exception {
  var settings=new DoclingSettings(URI.create(args[0]),System.getenv("FDI_SMOKE_API_KEY"),Duration.ofSeconds(5),Duration.ofSeconds(10),1024*1024,16*1024*1024,Set.of(),Set.of());
  var document=new BinaryDocument("forwardmeasure-smoke.html","<html><body><h1>ForwardMeasure café</h1><p>Document intelligence qualification.</p><table><tr><th>Company</th><th>Country</th></tr><tr><td>Acme</td><td>USA</td></tr></table></body></html>".getBytes(StandardCharsets.UTF_8));
  var out=Path.of(args[1]);Files.createDirectories(out);
  try(var gateway=new GeneratedDoclingGateway(settings)){
   if(!gateway.ready())throw new AssertionError("Server not ready");
   var payload=gateway.convert(document,ConversionOptions.standard().withFormats(Set.of(OutputFormat.JSON,OutputFormat.MARKDOWN,OutputFormat.DOCLANG)));
   Files.write(out.resolve("inline-response.json"),payload.content());
   var tree=new ObjectMapper().readTree(payload.content());if(!tree.path("status").asText().equals("success"))throw new AssertionError("Conversion status: "+tree.path("status"));
   if(!tree.at("/document/md_content").asText().contains("café"))throw new AssertionError("UTF-8 text missing");
   var xml=tree.at("/document/doclang_content").asText().getBytes(StandardCharsets.UTF_8);Files.write(out.resolve("document.doclang.xml"),xml);
   var validator=new DoclangArchiveValidator();var report=validator.validateDoclang(xml);if(!report.valid())throw new AssertionError("DocLang validation: "+report.issues());
   var task=gateway.submit(document,ConversionOptions.standard().withFormats(Set.of(OutputFormat.JSON,OutputFormat.MARKDOWN,OutputFormat.DOCLANG,OutputFormat.DCLX)));
   var progress=gateway.await(task,Duration.ofMinutes(2),()->false);if(progress.state()!=TaskState.SUCCESS)throw new AssertionError("Task status: "+progress.state());
   var archive=gateway.fetch(task);Files.write(out.resolve("conversion.zip"),archive.content());var archiveReport=validator.validateArchive(archive.content());if(!archiveReport.valid())throw new AssertionError("Archive validation: "+archiveReport.issues());
   var members=BoundedZip.read(archive.content(),ValidationLimits.defaults());for(var member:members.entrySet())if(member.getKey().endsWith(".dclx")){Files.write(out.resolve("document.dclx"),member.getValue());}
   System.out.println("PASS readiness, inline JSON/Markdown/DocLang, UTF-8, async submit/poll/result, ZIP/DCLX validation: "+members.keySet());
   System.out.println(gateway.versions());
  }
 }
}
