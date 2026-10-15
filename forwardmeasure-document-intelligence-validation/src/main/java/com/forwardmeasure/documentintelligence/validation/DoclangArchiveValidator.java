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

import com.forwardmeasure.documentintelligence.contracts.Digests;
import com.forwardmeasure.documentintelligence.contracts.DocumentArtifactValidator;
import com.forwardmeasure.documentintelligence.contracts.ValidationIssue;
import com.forwardmeasure.documentintelligence.contracts.ValidationReport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Validates DocLang XML and the OPC package/asset relationships used by DCLX. */
public final class DoclangArchiveValidator implements DocumentArtifactValidator {
  private final DoclangValidator xml;

  public DoclangArchiveValidator() {
    this(new DoclangValidator());
  }

  public DoclangArchiveValidator(DoclangValidator xml) {
    this.xml = xml;
  }

  @Override
  public ValidationReport validateDoclang(byte[] bytes) {
    return xml.validate(bytes);
  }

  @Override
  public ValidationReport validateDclx(byte[] bytes) {
    return validateDclx(bytes, new BoundedZip.Budget());
  }

  private ValidationReport validateDclx(byte[] bytes, BoundedZip.Budget budget) {
    var issues = new ArrayList<ValidationIssue>();
    try {
      var members = BoundedZip.read(bytes, xml.limits(), budget);
      for (String required : List.of("document.xml", "[Content_Types].xml", "_rels/.rels"))
        if (!members.containsKey(required))
          throw new IllegalArgumentException("Missing required OPC package member");
      var report = xml.validate(members.get("document.xml"));
      issues.addAll(report.issues());
      Document document = SecureXml.parse(members.get("document.xml"), xml.limits());
      requireAssetReferences(document, members);
      Document types = SecureXml.parse(members.get("[Content_Types].xml"), xml.limits());
      if (!"http://schemas.openxmlformats.org/package/2006/content-types"
              .equals(types.getDocumentElement().getNamespaceURI())
          || !"Types".equals(types.getDocumentElement().getLocalName()))
        throw new IllegalArgumentException("Invalid OPC content types");
      var overrides =
          types.getElementsByTagNameNS(
              "http://schemas.openxmlformats.org/package/2006/content-types", "Override");
      boolean documentType = false;
      for (int i = 0; i < overrides.getLength(); i++) {
        var item = (Element) overrides.item(i);
        if (item.getAttribute("PartName").equals("/document.xml")
            && item.getAttribute("ContentType").equals("application/vnd.doclang.document+xml"))
          documentType = true;
      }
      if (!documentType)
        throw new IllegalArgumentException("Missing DocLang document content type");
      Document relationships = SecureXml.parse(members.get("_rels/.rels"), xml.limits());
      if (!"http://schemas.openxmlformats.org/package/2006/relationships"
              .equals(relationships.getDocumentElement().getNamespaceURI())
          || !"Relationships".equals(relationships.getDocumentElement().getLocalName()))
        throw new IllegalArgumentException("Invalid OPC relationships");
      var rels =
          relationships.getElementsByTagNameNS(
              "http://schemas.openxmlformats.org/package/2006/relationships", "Relationship");
      boolean documentRelationship = false;
      for (int i = 0; i < rels.getLength(); i++) {
        var relationship = (Element) rels.item(i);
        String target = relationship.getAttribute("Target");
        if ("External".equalsIgnoreCase(relationship.getAttribute("TargetMode")))
          throw new IllegalArgumentException("External OPC relationship");
        BoundedZip.requirePath(target);
        if (!members.containsKey(target))
          throw new IllegalArgumentException("Missing OPC relationship target");
        if (target.equals("document.xml")
            && relationship
                .getAttribute("Type")
                .equals("http://doclang.ai/ns/package/2026/relationships/document"))
          documentRelationship = true;
      }
      if (!documentRelationship)
        throw new IllegalArgumentException("Missing DocLang document relationship");
    } catch (Exception e) {
      issues.add(
          new ValidationIssue("dclx", "archive", "Invalid, excessive or incomplete DCLX package"));
    }
    return report(bytes, issues);
  }

  private void requireAssetReferences(Document document, Map<String, byte[]> members)
      throws Exception {
    var elements = document.getElementsByTagName("*");
    for (int i = 0; i < elements.getLength(); i++) {
      var element = (Element) elements.item(i);
      for (String attribute : List.of("src", "source", "image", "image_path", "uri")) {
        if (attribute.equals("uri")
            && !"src"
                .equals(
                    element.getLocalName() == null ? element.getTagName() : element.getLocalName()))
          continue;
        if (!element.hasAttribute(attribute)) continue;
        String path = element.getAttribute(attribute);
        if (path.startsWith("data:")) continue;
        BoundedZip.requirePath(path);
        if (!members.containsKey(path))
          throw new IllegalArgumentException("Referenced archive asset is absent");
      }
    }
  }

  @Override
  public ValidationReport validateArchive(byte[] bytes) {
    var issues = new ArrayList<ValidationIssue>();
    try {
      var budget = new BoundedZip.Budget();
      var members = BoundedZip.read(bytes, xml.limits(), budget);
      for (var member : members.entrySet()) {
        String name = member.getKey().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".dclx")) issues.addAll(validateDclx(member.getValue(), budget).issues());
        else if (name.endsWith(".xml") || name.endsWith(".dclg")) {
          var document = SecureXml.parse(member.getValue(), xml.limits());
          issues.addAll(xml.validate(member.getValue()).issues());
        }
      }
    } catch (Exception e) {
      issues.add(
          new ValidationIssue("archive", "archive", "Invalid or excessive conversion archive"));
    }
    return report(bytes, issues);
  }

  private static ValidationReport report(byte[] bytes, List<ValidationIssue> issues) {
    String hash = Digests.sha256(bytes);
    return new ValidationReport(DoclangValidator.VERSION, false, hash, hash, issues);
  }
}
