/*******************************************************************************
* Copyright (c) 2026 Red Hat Inc. and others.
* All rights reserved. This program and the accompanying materials
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v20.html
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     Red Hat Inc. - initial API and implementation
*******************************************************************************/
package org.eclipse.lemminx.extensions.generators.xml2rnc;

import static java.lang.System.lineSeparator;
import static org.eclipse.lemminx.XMLAssert.assertGrammarGenerator;

import java.io.IOException;

import org.eclipse.lemminx.AbstractCacheBasedTest;
import org.junit.jupiter.api.Test;

/**
 * Tests for generating RelaxNG Compact Syntax (.rnc) schema from XML source.
 *
 */
public class XML2RelaxNGCompactGeneratorTest extends AbstractCacheBasedTest {

	@Test
	public void basic() throws IOException {
		String xml = "<note>\r\n" + //
				"	<to>Tove</to>\r\n" + //
				"	<from>Jani</from>\r\n" + //
				"	<heading>Reminder</heading>\r\n" + //
				"	<body>Don't forget me this weekend!</body>\r\n" + //
				"</note>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = noteContent" + lineSeparator() + //
				lineSeparator() + //
				"noteContent = element note {" + lineSeparator() + //
				"  toContent," + lineSeparator() + //
				"  fromContent," + lineSeparator() + //
				"  headingContent," + lineSeparator() + //
				"  bodyContent" + lineSeparator() + //
				"}" + lineSeparator() + //
				"toContent = element to { text }" + lineSeparator() + //
				"fromContent = element from { text }" + lineSeparator() + //
				"headingContent = element heading { text }" + lineSeparator() + //
				"bodyContent = element body { text }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void basicWithNS() throws IOException {
		String xml = "<note xmlns=\"https://www.w3schools.com\">\r\n" + //
				"	<to>Tove</to>\r\n" + //
				"	<from>Jani</from>\r\n" + //
				"	<heading>Reminder</heading>\r\n" + //
				"	<body>Don't forget me this weekend!</body>\r\n" + //
				"</note>";
		String rnc = "default namespace = \"https://www.w3schools.com\"" + lineSeparator() + //
				"datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = noteContent" + lineSeparator() + //
				lineSeparator() + //
				"noteContent = element note {" + lineSeparator() + //
				"  toContent," + lineSeparator() + //
				"  fromContent," + lineSeparator() + //
				"  headingContent," + lineSeparator() + //
				"  bodyContent" + lineSeparator() + //
				"}" + lineSeparator() + //
				"toContent = element to { text }" + lineSeparator() + //
				"fromContent = element from { text }" + lineSeparator() + //
				"headingContent = element heading { text }" + lineSeparator() + //
				"bodyContent = element body { text }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void emptyElement() throws IOException {
		String xml = "<root />";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = rootContent" + lineSeparator() + //
				lineSeparator() + //
				"rootContent = element root { empty }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void textOnlyElement() throws IOException {
		String xml = "<root>Hello</root>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = rootContent" + lineSeparator() + //
				lineSeparator() + //
				"rootContent = element root { text }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void decimalAttr() throws IOException {
		String xml = "<note version=\"1.2\" />";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = noteContent" + lineSeparator() + //
				lineSeparator() + //
				"noteContent = element note {" + lineSeparator() + //
				"  attribute version { xsd:decimal }" + lineSeparator() + //
				"}" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void decimalAttrAndContent() throws IOException {
		String xml = "<note version=\"1.2\" >ABCD</note>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = noteContent" + lineSeparator() + //
				lineSeparator() + //
				"noteContent = element note {" + lineSeparator() + //
				"  text," + lineSeparator() + //
				"  attribute version { xsd:decimal }" + lineSeparator() + //
				"}" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void decimalAttrAndMixedContent() throws IOException {
		String xml = "<note version=\"1.2\" >AB<C/>D</note>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = noteContent" + lineSeparator() + //
				lineSeparator() + //
				"noteContent = element note {" + lineSeparator() + //
				"  mixed {" + lineSeparator() + //
				"    CContent" + lineSeparator() + //
				"  }," + lineSeparator() + //
				"  attribute version { xsd:decimal }" + lineSeparator() + //
				"}" + lineSeparator() + //
				"CContent = element C { empty }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void multipleAttrs() throws IOException {
		String xml = "<note version=\"1.2\" >\r\n" + //
				"	<to attr1=\"abcd\" attr2=\"efgh\">Tove</to>\r\n" + //
				"	<from>Jani</from>\r\n" + //
				"	<heading>Reminder</heading>\r\n" + //
				"	<body>Don't forget me this weekend!</body>\r\n" + //
				"</note>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = noteContent" + lineSeparator() + //
				lineSeparator() + //
				"noteContent = element note {" + lineSeparator() + //
				"  toContent," + lineSeparator() + //
				"  fromContent," + lineSeparator() + //
				"  headingContent," + lineSeparator() + //
				"  bodyContent," + lineSeparator() + //
				"  attribute version { xsd:decimal }" + lineSeparator() + //
				"}" + lineSeparator() + //
				"toContent = element to {" + lineSeparator() + //
				"  text," + lineSeparator() + //
				"  attribute attr1 { text }," + lineSeparator() + //
				"  attribute attr2 { text }" + lineSeparator() + //
				"}" + lineSeparator() + //
				"fromContent = element from { text }" + lineSeparator() + //
				"headingContent = element heading { text }" + lineSeparator() + //
				"bodyContent = element body { text }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void mixedContent() {
		String xml = "<a><b/>text</a>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = aContent" + lineSeparator() + //
				lineSeparator() + //
				"aContent = element a {" + lineSeparator() + //
				"  mixed {" + lineSeparator() + //
				"    bContent" + lineSeparator() + //
				"  }" + lineSeparator() + //
				"}" + lineSeparator() + //
				"bContent = element b { empty }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void threeLevel() {
		String xml = "<a>\r\n" + //
				"	<b>\r\n" + //
				"		<c/>\r\n" + //
				"	</b>\r\n" + //
				"</a>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = aContent" + lineSeparator() + //
				lineSeparator() + //
				"aContent = element a {" + lineSeparator() + //
				"  bContent" + lineSeparator() + //
				"}" + lineSeparator() + //
				"bContent = element b {" + lineSeparator() + //
				"  cContent" + lineSeparator() + //
				"}" + lineSeparator() + //
				"cContent = element c { empty }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void threeLevelAndText() {
		String xml = "<a>\r\n" + //
				"	<b>\r\n" + //
				"		<c />\r\n" + //
				"		<d>X</d>\r\n" + //
				"	</b>\r\n" + //
				"</a>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = aContent" + lineSeparator() + //
				lineSeparator() + //
				"aContent = element a {" + lineSeparator() + //
				"  bContent" + lineSeparator() + //
				"}" + lineSeparator() + //
				"bContent = element b {" + lineSeparator() + //
				"  cContent," + lineSeparator() + //
				"  dContent" + lineSeparator() + //
				"}" + lineSeparator() + //
				"cContent = element c { empty }" + lineSeparator() + //
				"dContent = element d { text }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void occurrences() {
		String xml = "<invoice xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" + //
				"  xsi:noNamespaceSchemaLocation=\"grocery-invoice.xsd\">\r\n" + //
				"  <item name=\"Rice\" price=\"4.99\" />\r\n" + //
				"  <item name=\"Baked Beans\" price=\"1.99\" />\r\n" + //
				"  <item name=\"Salad\" price=\"5.99\" />\r\n" + //
				"</invoice>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = invoiceContent" + lineSeparator() + //
				lineSeparator() + //
				"invoiceContent = element invoice {" + lineSeparator() + //
				"  itemContent+" + lineSeparator() + //
				"}" + lineSeparator() + //
				"itemContent = element item {" + lineSeparator() + //
				"  attribute name { text }," + lineSeparator() + //
				"  attribute price { xsd:decimal }" + lineSeparator() + //
				"}" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void oneZeroOccurrences() {
		String xml = "<root>\r\n" + //
				"    <a>\r\n" + //
				"        <b />\r\n" + //
				"    </a>\r\n" + //
				"    <a></a>\r\n" + //
				"</root>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = rootContent" + lineSeparator() + //
				lineSeparator() + //
				"rootContent = element root {" + lineSeparator() + //
				"  aContent+" + lineSeparator() + //
				"}" + lineSeparator() + //
				"aContent = element a {" + lineSeparator() + //
				"  bContent?" + lineSeparator() + //
				"}" + lineSeparator() + //
				"bContent = element b { empty }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void oneZeroOccurrences2() {
		String xml = "<root>\r\n" + //
				"    <a>\r\n" + //
				"        <b />\r\n" + //
				"    </a>\r\n" + //
				"    <a>\r\n" + //
				"        <c />\r\n" + //
				"    </a>\r\n" + //
				"    <a>\r\n" + //
				"        <b />\r\n" + //
				"        <c />\r\n" + //
				"    </a>\r\n" + //
				"    <a></a>\r\n" + //
				"</root>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = rootContent" + lineSeparator() + //
				lineSeparator() + //
				"rootContent = element root {" + lineSeparator() + //
				"  aContent+" + lineSeparator() + //
				"}" + lineSeparator() + //
				"aContent = element a {" + lineSeparator() + //
				"  bContent?," + lineSeparator() + //
				"  cContent?" + lineSeparator() + //
				"}" + lineSeparator() + //
				"bContent = element b { empty }" + lineSeparator() + //
				"cContent = element c { empty }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void interleave() {
		String xml = "<root>\r\n" + //
				"	<a>\r\n" + //
				"		<c />\r\n" + //
				"		<b />\r\n" + //
				"	</a>\r\n" + //
				"	<a>\r\n" + //
				"		<b />\r\n" + //
				"		<c />\r\n" + //
				"	</a>\r\n" + //
				"</root>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = rootContent" + lineSeparator() + //
				lineSeparator() + //
				"rootContent = element root {" + lineSeparator() + //
				"  aContent+" + lineSeparator() + //
				"}" + lineSeparator() + //
				"aContent = element a {" + lineSeparator() + //
				"  cContent &" + lineSeparator() + //
				"  bContent" + lineSeparator() + //
				"}" + lineSeparator() + //
				"cContent = element c { empty }" + lineSeparator() + //
				"bContent = element b { empty }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void optionalAttribute() {
		String xml = "<root>\r\n" + //
				"	<item attr1=\"\" attr2=\"\"/>\r\n" + //
				"	<item attr1=\"\" />\r\n" + //
				"</root>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = rootContent" + lineSeparator() + //
				lineSeparator() + //
				"rootContent = element root {" + lineSeparator() + //
				"  itemContent+" + lineSeparator() + //
				"}" + lineSeparator() + //
				"itemContent = element item {" + lineSeparator() + //
				"  attribute attr1 { text }," + lineSeparator() + //
				"  attribute attr2 { text }?" + lineSeparator() + //
				"}" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void attrIDsAndFixed() {
		String xml = "<root>\r\n" + //
				"	<item attr1=\"id1\" attr2=\"A\" />\r\n" + //
				"	<item attr1=\"id2\" attr2=\"A\" />\r\n" + //
				"	<item attr1=\"id3\" attr2=\"A\" />\r\n" + //
				"	<item attr1=\"id4\" attr2=\"A\" />\r\n" + //
				"	<item attr1=\"id5\" attr2=\"A\" />\r\n" + //
				"	<item attr1=\"id6\" attr2=\"A\" />\r\n" + //
				"	<item attr1=\"id7\" attr2=\"A\" />\r\n" + //
				"	<item attr1=\"id8\" attr2=\"A\" />\r\n" + //
				"	<item attr1=\"id9\" attr2=\"A\" />\r\n" + //
				"	<item attr1=\"id10\" attr2=\"A\" />\r\n" + //
				"</root>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = rootContent" + lineSeparator() + //
				lineSeparator() + //
				"rootContent = element root {" + lineSeparator() + //
				"  itemContent+" + lineSeparator() + //
				"}" + lineSeparator() + //
				"itemContent = element item {" + lineSeparator() + //
				"  attribute attr1 { text }," + lineSeparator() + //
				"  attribute attr2 { \"A\" }" + lineSeparator() + //
				"}" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void attrIDsAndEnums() {
		String xml = "<root>\r\n" + //
				"	<item attr1=\"id1\" attr2=\"A\" />\r\n" + //
				"	<item attr1=\"id2\" attr2=\"A\" />\r\n" + //
				"	<item attr1=\"id3\" attr2=\"A\" />\r\n" + //
				"	<item attr1=\"id4\" attr2=\"A\" />\r\n" + //
				"	<item attr1=\"id5\" attr2=\"A\" />\r\n" + //
				"	<item attr1=\"id6\" attr2=\"B\" />\r\n" + //
				"	<item attr1=\"id7\" attr2=\"B\" />\r\n" + //
				"	<item attr1=\"id8\" attr2=\"B\" />\r\n" + //
				"	<item attr1=\"id9\" attr2=\"B\" />\r\n" + //
				"	<item attr1=\"id10\" attr2=\"B\" />\r\n" + //
				"</root>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = rootContent" + lineSeparator() + //
				lineSeparator() + //
				"rootContent = element root {" + lineSeparator() + //
				"  itemContent+" + lineSeparator() + //
				"}" + lineSeparator() + //
				"itemContent = element item {" + lineSeparator() + //
				"  attribute attr1 { text }," + lineSeparator() + //
				"  attribute attr2 { \"A\" | \"B\" }" + lineSeparator() + //
				"}" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void attrTypes() {
		String xml = "<root>\r\n" + //
				"	<item dateTime=\"2001-10-26T21:32:52+02:00\"\r\n" + //
				"		  date=\"2001-10-26\"\r\n" + //
				"         boolean=\"true\"\r\n" + //
				"         integer=\"1\"\r\n" + //
				"         decimal=\"1.2\"  />\r\n" + //
				"</root>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = rootContent" + lineSeparator() + //
				lineSeparator() + //
				"rootContent = element root {" + lineSeparator() + //
				"  itemContent" + lineSeparator() + //
				"}" + lineSeparator() + //
				"itemContent = element item {" + lineSeparator() + //
				"  attribute dateTime { xsd:dateTime }," + lineSeparator() + //
				"  attribute date { xsd:date }," + lineSeparator() + //
				"  attribute boolean { xsd:boolean }," + lineSeparator() + //
				"  attribute integer { xsd:integer }," + lineSeparator() + //
				"  attribute decimal { xsd:decimal }" + lineSeparator() + //
				"}" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void attrTypesWith2Occurs() {
		String xml = "<root>\r\n" + //
				"	<item dateTime=\"2001-10-26T21:32:52+02:00\"\r\n" + //
				"		  date=\"2001-10-26\"\r\n" + //
				"         boolean=\"true\"\r\n" + //
				"         integer=\"1\"\r\n" + //
				"         decimal=\"1.2\"  />\r\n" + //
				"	<item dateTime=\"2001-10-26T21:32:52+02:00\"\r\n" + //
				"		  date=\"2001-10-26\"\r\n" + //
				"         boolean=\"true\"\r\n" + //
				"         integer=\"1\"\r\n" + //
				"         decimal=\"XXXXXXXXXXXXXXXXXXXXXX\"  />\r\n" + //
				"</root>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = rootContent" + lineSeparator() + //
				lineSeparator() + //
				"rootContent = element root {" + lineSeparator() + //
				"  itemContent+" + lineSeparator() + //
				"}" + lineSeparator() + //
				"itemContent = element item {" + lineSeparator() + //
				"  attribute dateTime { xsd:dateTime }," + lineSeparator() + //
				"  attribute date { xsd:date }," + lineSeparator() + //
				"  attribute boolean { xsd:boolean }," + lineSeparator() + //
				"  attribute integer { xsd:integer }," + lineSeparator() + //
				"  attribute decimal { text }" + lineSeparator() + //
				"}" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void singleEmptyChild() {
		String xml = "<root><child/></root>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = rootContent" + lineSeparator() + //
				lineSeparator() + //
				"rootContent = element root {" + lineSeparator() + //
				"  childContent" + lineSeparator() + //
				"}" + lineSeparator() + //
				"childContent = element child { empty }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void attrOnlyElement() {
		String xml = "<config debug=\"true\" version=\"2\" />";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = configContent" + lineSeparator() + //
				lineSeparator() + //
				"configContent = element config {" + lineSeparator() + //
				"  attribute debug { xsd:boolean }," + lineSeparator() + //
				"  attribute version { xsd:integer }" + lineSeparator() + //
				"}" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void deepNesting() {
		String xml = "<a>\r\n" + //
				"	<b>\r\n" + //
				"		<c>\r\n" + //
				"			<d>leaf</d>\r\n" + //
				"		</c>\r\n" + //
				"	</b>\r\n" + //
				"</a>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = aContent" + lineSeparator() + //
				lineSeparator() + //
				"aContent = element a {" + lineSeparator() + //
				"  bContent" + lineSeparator() + //
				"}" + lineSeparator() + //
				"bContent = element b {" + lineSeparator() + //
				"  cContent" + lineSeparator() + //
				"}" + lineSeparator() + //
				"cContent = element c {" + lineSeparator() + //
				"  dContent" + lineSeparator() + //
				"}" + lineSeparator() + //
				"dContent = element d { text }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void multipleChildrenWithAttrs() {
		String xml = "<catalog>\r\n" + //
				"	<book id=\"1\" title=\"XML Guide\" />\r\n" + //
				"	<book id=\"2\" title=\"Java Guide\" />\r\n" + //
				"</catalog>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = catalogContent" + lineSeparator() + //
				lineSeparator() + //
				"catalogContent = element catalog {" + lineSeparator() + //
				"  bookContent+" + lineSeparator() + //
				"}" + lineSeparator() + //
				"bookContent = element book {" + lineSeparator() + //
				"  attribute id { xsd:integer }," + lineSeparator() + //
				"  attribute title { text }" + lineSeparator() + //
				"}" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void mixedContentMultipleChildren() {
		String xml = "<p>Hello <b>bold</b> and <i>italic</i> text</p>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = pContent" + lineSeparator() + //
				lineSeparator() + //
				"pContent = element p {" + lineSeparator() + //
				"  mixed {" + lineSeparator() + //
				"    bContent," + lineSeparator() + //
				"    iContent" + lineSeparator() + //
				"  }" + lineSeparator() + //
				"}" + lineSeparator() + //
				"bContent = element b { text }" + lineSeparator() + //
				"iContent = element i { text }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}

	@Test
	public void siblingDifferentTypes() {
		String xml = "<root>\r\n" + //
				"	<name>John</name>\r\n" + //
				"	<age />\r\n" + //
				"	<address>123 Main St</address>\r\n" + //
				"</root>";
		String rnc = "datatypes xsd = \"http://www.w3.org/2001/XMLSchema-datatypes\"" + lineSeparator() + //
				lineSeparator() + //
				"start = rootContent" + lineSeparator() + //
				lineSeparator() + //
				"rootContent = element root {" + lineSeparator() + //
				"  nameContent," + lineSeparator() + //
				"  ageContent," + lineSeparator() + //
				"  addressContent" + lineSeparator() + //
				"}" + lineSeparator() + //
				"nameContent = element name { text }" + lineSeparator() + //
				"ageContent = element age { empty }" + lineSeparator() + //
				"addressContent = element address { text }" + lineSeparator();
		assertGrammarGenerator(xml, new RelaxNGCompactGeneratorSettings(), rnc);
	}
}
