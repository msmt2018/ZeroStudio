/**
 *  Copyright (c) 2024 Red Hat Inc. and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v2.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *  Red Hat Inc. - initial API and implementation
 */
package org.eclipse.lemminx.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class IOUtilsTest {

	@Test
	public void noProlog() {
		assertEncoding("<root/>", StandardCharsets.UTF_8);
	}

	@Test
	public void emptyBytes() {
		assertEncoding("", StandardCharsets.UTF_8);
	}

	@Test
	public void prologWithoutEncoding() {
		assertEncoding("<?xml version=\"1.0\"?>", StandardCharsets.UTF_8);
	}

	@Test
	public void prologWithUtf8() {
		assertEncoding("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", StandardCharsets.UTF_8);
	}

	@Test
	public void prologWithIso88591() {
		assertEncoding("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>",
				Charset.forName("ISO-8859-1"));
	}

	@Test
	public void prologWithSingleQuotes() {
		assertEncoding("<?xml version='1.0' encoding='ISO-8859-1'?>",
				Charset.forName("ISO-8859-1"));
	}

	@Test
	public void prologWithSpacesAroundEquals() {
		assertEncoding("<?xml version = \"1.0\" encoding = \"ISO-8859-1\" ?>",
				Charset.forName("ISO-8859-1"));
	}

	@Test
	public void prologWithUtf8Bom() {
		byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
		byte[] xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>".getBytes(StandardCharsets.US_ASCII);
		byte[] bytes = new byte[bom.length + xml.length];
		System.arraycopy(bom, 0, bytes, 0, bom.length);
		System.arraycopy(xml, 0, bytes, bom.length, xml.length);
		assertEquals(StandardCharsets.UTF_8, IOUtils.detectXmlEncoding(bytes));
	}

	@Test
	public void prologWithUtf8BomAndIso88591() {
		byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
		byte[] xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>".getBytes(StandardCharsets.US_ASCII);
		byte[] bytes = new byte[bom.length + xml.length];
		System.arraycopy(bom, 0, bytes, 0, bom.length);
		System.arraycopy(xml, 0, bytes, bom.length, xml.length);
		assertEquals(Charset.forName("ISO-8859-1"), IOUtils.detectXmlEncoding(bytes));
	}

	@Test
	public void invalidEncodingFallsBackToUtf8() {
		assertEncoding("<?xml version=\"1.0\" encoding=\"INVALID-ENCODING-XYZ\"?>",
				StandardCharsets.UTF_8);
	}

	@Test
	public void processingInstructionNotProlog() {
		assertEncoding("<?xml-stylesheet type=\"text/xsl\" href=\"style.xsl\"?>",
				StandardCharsets.UTF_8);
	}

	@Test
	public void prologWithLeadingWhitespace() {
		assertEncoding("  \t\n<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>",
				Charset.forName("ISO-8859-1"));
	}

	@Test
	public void prologEncodingOnly() {
		assertEncoding("<?xml encoding=\"ISO-8859-1\"?>",
				Charset.forName("ISO-8859-1"));
	}

	@Test
	public void prologWithWindows1252() {
		assertEncoding("<?xml version=\"1.0\" encoding=\"windows-1252\"?>",
				Charset.forName("windows-1252"));
	}

	@Test
	public void truncatedPrologNoClosingQuote() {
		assertEncoding("<?xml version=\"1.0\" encoding=\"UTF-8", StandardCharsets.UTF_8);
	}

	@Test
	public void truncatedPrologNoEquals() {
		assertEncoding("<?xml version=\"1.0\" encoding", StandardCharsets.UTF_8);
	}

	private static void assertEncoding(String xml, Charset expected) {
		assertEquals(expected, IOUtils.detectXmlEncoding(xml.getBytes(StandardCharsets.US_ASCII)));
	}
}
