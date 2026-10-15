<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:sch="http://purl.oclc.org/dsdl/schematron"
    xmlns:svrl="http://purl.oclc.org/dsdl/svrl"
    xmlns:dl="https://www.doclang.ai/ns/v0"
    xmlns:xsl2="http://www.w3.org/1999/XSL/Transform/alias"
    xmlns:svrl2="http://purl.oclc.org/dsdl/svrl/alias"
    xmlns:dl2="https://www.doclang.ai/ns/v0/alias">

  <xsl:namespace-alias stylesheet-prefix="xsl2" result-prefix="xsl"/>
  <xsl:namespace-alias stylesheet-prefix="svrl2" result-prefix="svrl"/>
  <xsl:namespace-alias stylesheet-prefix="dl2" result-prefix="dl"/>
  <xsl:output method="xml" indent="yes"/>

  <xsl:template match="/">
    <xsl2:stylesheet version="3.0"
        xmlns:svrl="http://purl.oclc.org/dsdl/svrl"
        xmlns:dl="https://www.doclang.ai/ns/v0">

      <xsl2:output method="xml" indent="yes"/>

      <!-- Main template -->
      <xsl2:template match="/">
        <svrl2:schematron-output>
          <xsl:apply-templates select="//sch:pattern" mode="apply-rules"/>
        </svrl2:schematron-output>
      </xsl2:template>

      <!-- Process patterns - generate templates at stylesheet level -->
      <xsl:apply-templates select="//sch:pattern" mode="generate-templates"/>

    </xsl2:stylesheet>
  </xsl:template>

  <!-- Generate apply-templates calls in main template -->
  <xsl:template match="sch:pattern" mode="apply-rules">
    <xsl:apply-templates select="sch:rule" mode="apply-rules"/>
  </xsl:template>

  <xsl:template match="sch:rule" mode="apply-rules">
    <xsl2:apply-templates select="//{@context}" mode="check-{generate-id()}"/>
  </xsl:template>

  <!-- Generate template definitions at stylesheet level -->
  <xsl:template match="sch:pattern" mode="generate-templates">
    <xsl:apply-templates select="sch:rule" mode="generate-templates"/>
  </xsl:template>

  <!-- Convert rule to template -->
  <xsl:template match="sch:rule" mode="generate-templates">
    <xsl2:template match="{@context}" mode="check-{generate-id()}">
      <xsl:apply-templates select="sch:let"/>
      <xsl:apply-templates select="sch:assert"/>
    </xsl2:template>
  </xsl:template>

  <!-- Convert let to variable -->
  <xsl:template match="sch:let">
    <xsl2:variable name="{@name}" select="{@value}"/>
  </xsl:template>

  <!-- Convert assert to if -->
  <xsl:template match="sch:assert">
    <xsl2:if test="not({@test})">
      <svrl2:failed-assert location="{{path()}}">
        <svrl2:text>
          <xsl:apply-templates/>
        </svrl2:text>
      </svrl2:failed-assert>
    </xsl2:if>
  </xsl:template>

  <!-- Copy value-of -->
  <xsl:template match="sch:value-of">
    <xsl2:value-of select="{@select}"/>
  </xsl:template>

  <!-- Copy text content -->
  <xsl:template match="text()">
    <xsl:value-of select="normalize-space(.)"/>
  </xsl:template>

</xsl:stylesheet>
