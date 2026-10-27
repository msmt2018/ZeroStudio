/*******************************************************************************
* Copyright (c) 2022 Red Hat Inc. and others.
* All rights reserved. This program and the accompanying materials
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v20.html
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     Red Hat Inc. - initial API and implementation
*******************************************************************************/
package org.eclipse.lemminx.extensions.references;

import static org.eclipse.lemminx.XMLAssert.hl;
import static org.eclipse.lemminx.XMLAssert.r;
import static org.eclipse.lsp4j.DocumentHighlightKind.Read;
import static org.eclipse.lsp4j.DocumentHighlightKind.Write;

import org.eclipse.lemminx.AbstractCacheBasedTest;
import org.eclipse.lemminx.XMLAssert;
import org.eclipse.lemminx.XMLAssert.SettingsSaveContext;
import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.eclipse.lsp4j.DocumentHighlight;
import org.junit.jupiter.api.Test;

/**
 * XML references highlighting tests.
 *
 * @author Angelo ZERR
 */
public class XMLReferencesHighlightingExtensionsTest extends AbstractCacheBasedTest {

	@Test
	public void teiOnCorresp() throws BadLocationException {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
				// + "<?xml-model
				// href=\"http://www.tei-c.org/release/xml/tei/custom/schema/relaxng/tei_lite.rng\"
				// type=\"application/xml\"
				// schematypens=\"http://relaxng.org/ns/structure/1.0\"?>\r\n"
				+ "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">\r\n"
				+ "  <teiHeader>  \r\n"
				+ "    <fileDesc>\r\n"
				+ "      <titleStmt>\r\n"
				+ "        <title>Title</title>\r\n"
				+ "      </titleStmt>\r\n"
				+ "      <publicationStmt>\r\n"
				+ "        <p>Publication information</p>  \r\n"
				+ "      </publicationStmt>\r\n"
				+ "      <sourceDesc>\r\n"
				+ "        <p>Information about the source</p>\r\n"
				+ "      </sourceDesc>\r\n"
				+ "    </fileDesc>\r\n"
				+ "  </teiHeader>\r\n"
				+ "  <text>\r\n"
				+ "    <body xml:id=\"body-id\">\r\n"
				+ "      <p xml:id=\"p-id\" >Some text here.</p>\r\n"
				+ "      <anchor corresp=\"#bo|dy-id\"></anchor>\r\n" // <-- // highlighting here should highlight
																		// body/@xml-id="body-id"
				+ "      <anchor corresp=\"#body-id\"></anchor>\r\n"
				+ "    </body>\r\n"
				+ "  </text>\r\n"
				+ "</TEI>";
		testHighlightsFor(xml, "file:///test/tei.xml", //
				hl(r(16, 18, 16, 25), Write), //
				hl(r(18, 23, 18, 31), Read), //
				hl(r(19, 23, 19, 31), Read));
	}

	@Test
	public void teiOnXMLId() throws BadLocationException {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
				// + "<?xml-model
				// href=\"http://www.tei-c.org/release/xml/tei/custom/schema/relaxng/tei_lite.rng\"
				// type=\"application/xml\"
				// schematypens=\"http://relaxng.org/ns/structure/1.0\"?>\r\n"
				+ "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">\r\n"
				+ "  <teiHeader>  \r\n"
				+ "    <fileDesc>\r\n"
				+ "      <titleStmt>\r\n"
				+ "        <title>Title</title>\r\n"
				+ "      </titleStmt>\r\n"
				+ "      <publicationStmt>\r\n"
				+ "        <p>Publication information</p>  \r\n"
				+ "      </publicationStmt>\r\n"
				+ "      <sourceDesc>\r\n"
				+ "        <p>Information about the source</p>\r\n"
				+ "      </sourceDesc>\r\n"
				+ "    </fileDesc>\r\n"
				+ "  </teiHeader>\r\n"
				+ "  <text>\r\n"
				+ "    <body xml:id=\"bod|y-id\">\r\n" // <-- // highlighting here should highlight the 2
														// anchor/@corresp attributes which reference this xml:id
				+ "      <p xml:id=\"p-id\" >Some text here.</p>\r\n"
				+ "      <anchor corresp=\"#body-id\"></anchor>\r\n"
				+ "      <anchor corresp=\"#body-id\"></anchor>\r\n"
				+ "    </body>\r\n"
				+ "  </text>\r\n"
				+ "</TEI>";
		testHighlightsFor(xml, "file:///test/tei.xml", //
				hl(r(16, 18, 16, 25), Write), //
				hl(r(18, 23, 18, 31), Read), //
				hl(r(19, 23, 19, 31), Read));
	}

	@Test
	public void teiTargetMulti() throws BadLocationException {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
				// + "<?xml-model
				// href=\"http://www.tei-c.org/release/xml/tei/custom/schema/relaxng/tei_all.rng\"
				// type=\"application/xml\"
				// schematypens=\"http://relaxng.org/ns/structure/1.0\"?>\r\n"
				+ "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">\r\n"
				+ "	<teiHeader></teiHeader>\r\n"
				+ "	<text>\r\n"
				+ "		<body>\r\n"
				+ "			<p xml:id=\"A\" />\r\n"
				+ "			<p xml:id=\"B|\" />\r\n" // [0]
				+ "		</body>\r\n"
				+ "		<link target=\"#A #B\" />\r\n" // [1]
				+ "		<link target=\"#A #B #C\" />\r\n" // [2]
				+ "		<link target=\"#A\" />\r\n"
				+ "		<link target=\"#B\" />\r\n" // [3]
				+ "		<link target2=\"#B\" />\r\n"
				+ "	</text>\r\n"
				+ "</TEI>";
		testHighlightsFor(xml, "file:///test/tei.xml", //
				hl(r(6, 14, 6, 15), Write), // [0]
				hl(r(8, 19, 8, 21), Read), // [1]
				hl(r(9, 19, 9, 21), Read), // [2]
				hl(r(11, 16, 11, 18), Read)); // [3]
	}

	@Test
	public void docbook() throws BadLocationException {
		// highlighting on define/@name
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
				// + "<!DOCTYPE book PUBLIC \"-//OASIS//DTD DocBook XML V4.4//EN\"
				// \"http://www.docbook.org/xml/4.4/docbookx.dtd\">\r\n"
				+ "<book>\r\n"
				+ "    <chapter id=\"chapter-1\">\r\n"
				+ "\r\n"
				+ "        <xref linkend=\"chapt|er-1\" />\r\n" // <-- // highlighting here should highlight
				// chapter/@id="chapter-1"
				+ "\r\n"
				+ "    </chapter>\r\n"
				+ "\r\n"
				+ "    <chapter id=\"chapter-2\">\r\n"
				+ "\r\n"
				+ "    </chapter>\r\n"
				+ "</book>";
		testHighlightsFor(xml, "file:///test/docbook.xml", //
				hl(r(2, 17, 2, 26), Write),
				hl(r(4, 23, 4, 32), Read));
	}

	@Test
	public void webOnServletMapping() throws BadLocationException {
		String xml = "<web-app xmlns=\"http://xmlns.jcp.org/xml/ns/javaee\"\r\n"
				+ "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n"
				// + " xsi:schemaLocation=\"http://xmlns.jcp.org/xml/ns/javaee
				// http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd\"\r\n"
				+ "  version=\"3.1\">\r\n"
				+ "  <servlet>\r\n"
				+ "    <servlet-name>comingsoon</servlet-name>\r\n"
				+ "    <servlet-class>mysite.server.ComingSoonServlet</servlet-class>\r\n"
				+ "  </servlet>\r\n"
				+ "  <servlet-mapping>\r\n"
				+ "    <servlet-name>co|mingsoon</servlet-name>\r\n" // <-- highlight on servlet-mapping/servlet-name
																		// text
				+ "    <url-pattern>/*</url-pattern>\r\n"
				+ "  </servlet-mapping>\r\n"
				+ "  <servlet-mapping>\r\n"
				+ "    <servlet-name>comingsoon</servlet-name>\r\n"
				+ "    <url-pattern>/*</url-pattern>\r\n"
				+ "  </servlet-mapping>\r\n"
				+ "</web-app>\r\n"
				+ "";
		testHighlightsFor(xml, "file:///test/web.xml", //
				hl(r(4, 18, 4, 28), Write), //
				hl(r(8, 18, 8, 28), Read),
				hl(r(12, 18, 12, 28), Read));
	}

	@Test
	public void webOnServlet() throws BadLocationException {
		String xml = "<web-app xmlns=\"http://xmlns.jcp.org/xml/ns/javaee\"\r\n"
				+ "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n"
				// + " xsi:schemaLocation=\"http://xmlns.jcp.org/xml/ns/javaee
				// http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd\"\r\n"
				+ "  version=\"3.1\">\r\n"
				+ "  <servlet>\r\n"
				+ "    <servlet-name>comi|ngsoon</servlet-name>\r\n" // <-- highlight on servlet/servlet-name
				// text should highlight the two servlet-mapping/servlet-name text nodes
				+ "    <servlet-class>mysite.server.ComingSoonServlet</servlet-class>\r\n"
				+ "  </servlet>\r\n"
				+ "  <servlet-mapping>\r\n"
				+ "    <servlet-name>comingsoon</servlet-name>\r\n"
				+ "    <url-pattern>/*</url-pattern>\r\n"
				+ "  </servlet-mapping>\r\n"
				+ "  <servlet-mapping>\r\n"
				+ "    <servlet-name>comingsoon</servlet-name>\r\n"
				+ "    <url-pattern>/*</url-pattern>\r\n"
				+ "  </servlet-mapping>\r\n"
				+ "</web-app>\r\n"
				+ "";
		testHighlightsFor(xml, "file:///test/web.xml", //
				hl(r(4, 18, 4, 28), Write), //
				hl(r(8, 18, 8, 28), Read),
				hl(r(12, 18, 12, 28), Read));
	}

	private static void testHighlightsFor(String value, String fileURI,
			DocumentHighlight... expected)
			throws BadLocationException {
		XMLLanguageService xmlLanguageService = new XMLLanguageService();
		xmlLanguageService.getExtensions();
		xmlLanguageService.doSave(new SettingsSaveContext(XMLReferencesSettingsForTest.createXMLReferencesSettings()));

		XMLAssert.testHighlightsFor(xmlLanguageService, value, fileURI, expected);
	}

}
