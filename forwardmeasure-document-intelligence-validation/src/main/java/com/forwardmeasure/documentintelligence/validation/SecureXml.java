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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

final class SecureXml {
  private SecureXml() {}

  static Document parse(byte[] bytes, ValidationLimits limits) throws Exception {
    if (bytes.length > limits.maxXmlBytes())
      throw new IllegalArgumentException("XML size exceeds the configured bound");
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    factory.setAttribute(
        "http://www.oracle.com/xml/jaxp/properties/maxElementDepth", limits.maxDepth());
    var builder = factory.newDocumentBuilder();
    builder.setErrorHandler(
        new DefaultHandler() {
          @Override
          public void error(SAXParseException e) throws SAXParseException {
            throw e;
          }

          @Override
          public void fatalError(SAXParseException e) throws SAXParseException {
            throw e;
          }
        });
    var doc = builder.parse(new ByteArrayInputStream(bytes));
    var queue = new ArrayDeque<Node>();
    queue.add(doc.getDocumentElement());
    int count = 0;
    while (!queue.isEmpty()) {
      Node node = queue.removeFirst();
      if (node instanceof Element && ++count > limits.maxElements())
        throw new IllegalArgumentException("XML element count exceeds the configured bound");
      for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling())
        if (child instanceof Element) queue.add(child);
    }
    return doc;
  }

  static boolean normalizeEmptyNamespace(Document doc, String namespace) {
    if (doc.getDocumentElement().getNamespaceURI() != null) return false;
    var queue = new ArrayDeque<Node>();
    queue.add(doc.getDocumentElement());
    while (!queue.isEmpty()) {
      Node node = queue.removeFirst();
      if (node.getNamespaceURI() == null) doc.renameNode(node, namespace, node.getNodeName());
      for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling())
        if (child instanceof Element) queue.add(child);
    }
    doc.getDocumentElement()
        .setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns", namespace);
    return true;
  }

  static byte[] serialize(Document doc) throws Exception {
    var factory = TransformerFactory.newDefaultInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    var transformer = factory.newTransformer();
    transformer.setOutputProperty("encoding", "UTF-8");
    var output = new ByteArrayOutputStream();
    transformer.transform(new DOMSource(doc), new StreamResult(output));
    return output.toByteArray();
  }
}
