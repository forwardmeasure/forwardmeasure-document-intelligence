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
import com.forwardmeasure.documentintelligence.contracts.ValidationIssue;
import com.forwardmeasure.documentintelligence.contracts.ValidationReport;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XsltExecutable;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Full validation against the pinned DocLang 0.7.3 XSD and Schematron rules. */
public final class DoclangValidator {
  public static final String VERSION = "0.7.3";
  public static final String NAMESPACE = "https://www.doclang.ai/ns/v0";
  private final ValidationLimits limits;
  private final boolean allowEmptyNamespace;
  private final Schema schema;
  private final Processor processor;
  private final XsltExecutable schematron;

  public DoclangValidator() {
    this(ValidationLimits.defaults(), true);
  }

  public DoclangValidator(ValidationLimits limits, boolean allowEmptyNamespace) {
    this.limits = limits;
    this.allowEmptyNamespace = allowEmptyNamespace;
    try {
      var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      schema =
          factory.newSchema(new StreamSource(new ByteArrayInputStream(resource("doclang.xsd"))));
      processor = new Processor(false);
      processor.setConfigurationProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, false);
      var compiler = processor.newXsltCompiler();
      compiler.setResourceResolver(
          request -> {
            throw new net.sf.saxon.trans.XPathException("External resources are disabled");
          });
      var transpiler =
          compiler.compile(
              new StreamSource(new ByteArrayInputStream(resource("schematron-transpiler.xsl"))));
      var transform = transpiler.load();
      transform.setInitialContextNode(
          processor
              .newDocumentBuilder()
              .build(
                  new DOMSource(
                      SecureXml.parse(resource("doclang.sch"), ValidationLimits.defaults()))));
      var output = new XdmDestination();
      transform.setDestination(output);
      transform.transform();
      schematron = compiler.compile(output.getXdmNode().asSource());
    } catch (Exception e) {
      throw new IllegalStateException("Pinned DocLang validator could not initialize", e);
    }
  }

  private static byte[] resource(String name) throws java.io.IOException {
    try (var input = DoclangValidator.class.getResourceAsStream("/doclang/" + name)) {
      if (input == null) throw new java.io.IOException("Missing pinned validation resource");
      return input.readAllBytes();
    }
  }

  public ValidationReport validate(byte[] xml) {
    String original = Digests.sha256(xml);
    String normalizedHash = original;
    boolean normalized = false;
    var issues = new ArrayList<ValidationIssue>();
    try {
      Document document = SecureXml.parse(xml, limits);
      if (allowEmptyNamespace) normalized = SecureXml.normalizeEmptyNamespace(document, NAMESPACE);
      if (normalized) normalizedHash = Digests.sha256(SecureXml.serialize(document));
      var validator = schema.newValidator();
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      validator.setErrorHandler(
          new DefaultHandler() {
            @Override
            public void warning(SAXParseException error) {
              record(error);
            }

            @Override
            public void error(SAXParseException error) {
              record(error);
            }

            @Override
            public void fatalError(SAXParseException error) throws SAXException {
              record(error);
              throw error;
            }

            private void record(SAXParseException error) {
              if (issues.size() < 100)
                issues.add(
                    new ValidationIssue(
                        "xsd",
                        "line:" + error.getLineNumber() + ":column:" + error.getColumnNumber(),
                        "Document does not satisfy the pinned DocLang XSD"));
            }
          });
      validator.validate(new DOMSource(document));
      var transform = schematron.load();
      transform.setResourceResolver(
          request -> {
            throw new net.sf.saxon.trans.XPathException("External resources are disabled");
          });
      transform.setInitialContextNode(
          processor.newDocumentBuilder().build(new DOMSource(document)));
      var destination = new XdmDestination();
      transform.setDestination(destination);
      transform.transform();
      var xpath = processor.newXPathCompiler();
      xpath.declareNamespace("svrl", "http://purl.oclc.org/dsdl/svrl");
      var selector = xpath.compile("//svrl:failed-assert").load();
      selector.setContextItem(destination.getXdmNode());
      for (var node : selector) {
        if (issues.size() >= 100) break;
        var failed = (net.sf.saxon.s9api.XdmNode) node;
        String location = failed.getAttributeValue(new net.sf.saxon.s9api.QName("location"));
        String message = failed.getStringValue().replaceAll("\\s+", " ").trim();
        issues.add(
            new ValidationIssue(
                "schematron", location, message.substring(0, Math.min(512, message.length()))));
      }
    } catch (Exception e) {
      if (issues.isEmpty())
        issues.add(
            new ValidationIssue("xml", "document", "Malformed, excessive or prohibited XML"));
    }
    return new ValidationReport(VERSION, normalized, original, normalizedHash, issues);
  }

  public ValidationLimits limits() {
    return limits;
  }
}
