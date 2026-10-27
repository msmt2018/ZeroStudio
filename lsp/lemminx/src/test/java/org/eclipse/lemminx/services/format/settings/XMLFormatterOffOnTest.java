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
package org.eclipse.lemminx.services.format.settings;

import static java.lang.System.lineSeparator;
import static org.eclipse.lemminx.XMLAssert.c;
import static org.eclipse.lemminx.XMLAssert.testCompletionFor;

import org.eclipse.lemminx.AbstractCacheBasedTest;
import org.eclipse.lemminx.XMLAssert;
import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;

/**
 * Tests for &lt;!-- @formatter:off --&gt; / &lt;!-- @formatter:on --&gt;
 * support.
 *
 * @see <a href="https://github.com/eclipse-lemminx/lemminx/issues/1648">lemminx#1648</a>
 */
public class XMLFormatterOffOnTest extends AbstractCacheBasedTest {

	// https://github.com/eclipse-lemminx/lemminx/issues/1648
	@Test
	public void formatterOffOnPreservesContent() throws BadLocationException {
		String content = "<root>" + lineSeparator() + //
				"  <!-- @formatter:off -->" + lineSeparator() + //
				"      <badly-indented>" + lineSeparator() + //
				"    <child />" + lineSeparator() + //
				"          </badly-indented>" + lineSeparator() + //
				"  <!-- @formatter:on -->" + lineSeparator() + //
				"  <normal>" + lineSeparator() + //
				"    <child />" + lineSeparator() + //
				"  </normal>" + lineSeparator() + //
				"</root>";
		String expected = content;
		assertFormat(content, expected);
	}

	@Test
	public void formatterOffOnFormatsCommentAndResumes() throws BadLocationException {
		String content = "<root>" + lineSeparator() + //
				"<!-- @formatter:off -->" + lineSeparator() + //
				"      <badly-indented />" + lineSeparator() + //
				"<!-- @formatter:on -->" + lineSeparator() + //
				"      <normal />" + lineSeparator() + //
				"</root>";
		String expected = "<root>" + lineSeparator() + //
				"  <!-- @formatter:off -->" + lineSeparator() + //
				"      <badly-indented />" + lineSeparator() + //
				"<!-- @formatter:on -->" + lineSeparator() + //
				"  <normal />" + lineSeparator() + //
				"</root>";
		assertFormat(content, expected, (TextEdit[]) null);
	}

	@Test
	public void formatterOffWithoutOn() throws BadLocationException {
		String content = "<root>" + lineSeparator() + //
				"  <!-- @formatter:off -->" + lineSeparator() + //
				"      <badly-indented>" + lineSeparator() + //
				"    <child />" + lineSeparator() + //
				"          </badly-indented>" + lineSeparator() + //
				"</root>";
		String expected = content;
		assertFormat(content, expected);
	}

	@Test
	public void formatterOffOnIdempotent() throws BadLocationException {
		String content = "<root>" + lineSeparator() + //
				"  <!-- @formatter:off -->" + lineSeparator() + //
				"      <badly-indented>" + lineSeparator() + //
				"    <child />" + lineSeparator() + //
				"          </badly-indented>" + lineSeparator() + //
				"  <!-- @formatter:on -->" + lineSeparator() + //
				"  <normal>" + lineSeparator() + //
				"    <child />" + lineSeparator() + //
				"  </normal>" + lineSeparator() + //
				"</root>";
		String expected = content;
		assertFormat(content, expected);
		assertFormat(expected, expected);
	}

	@Test
	public void formatterOffOnMultipleRegions() throws BadLocationException {
		String content = "<root>" + lineSeparator() + //
				"  <!-- @formatter:off -->" + lineSeparator() + //
				"    <a>preserve   this</a>" + lineSeparator() + //
				"  <!-- @formatter:on -->" + lineSeparator() + //
				"  <b>format this</b>" + lineSeparator() + //
				"  <!-- @formatter:off -->" + lineSeparator() + //
				"    <c>preserve   this   too</c>" + lineSeparator() + //
				"  <!-- @formatter:on -->" + lineSeparator() + //
				"  <d>format this too</d>" + lineSeparator() + //
				"</root>";
		String expected = content;
		assertFormat(content, expected);
	}

	@Test
	public void formatterOffOnWithExtraSpacesInComment() throws BadLocationException {
		String content = "<root>" + lineSeparator() + //
				"  <!--   @formatter:off   -->" + lineSeparator() + //
				"      <badly-indented />" + lineSeparator() + //
				"  <!--   @formatter:on   -->" + lineSeparator() + //
				"  <normal />" + lineSeparator() + //
				"</root>";
		String expected = content;
		assertFormat(content, expected);
	}

	@Test
	public void formatterOffOnNoEffectOnNormalComments() throws BadLocationException {
		String content = "<root>" + lineSeparator() + //
				"      <!-- normal comment -->" + lineSeparator() + //
				"      <element />" + lineSeparator() + //
				"</root>";
		String expected = "<root>" + lineSeparator() + //
				"  <!-- normal comment -->" + lineSeparator() + //
				"  <element />" + lineSeparator() + //
				"</root>";
		assertFormat(content, expected, (TextEdit[]) null);
	}

	@Test
	public void formatterOffOnNestedElements() throws BadLocationException {
		String content = "<root>" + lineSeparator() + //
				"  <!-- @formatter:off -->" + lineSeparator() + //
				"  <parent>" + lineSeparator() + //
				"        <child>" + lineSeparator() + //
				"  <grandchild />" + lineSeparator() + //
				"        </child>" + lineSeparator() + //
				"  </parent>" + lineSeparator() + //
				"  <!-- @formatter:on -->" + lineSeparator() + //
				"</root>";
		String expected = content;
		assertFormat(content, expected);
	}

	@Test
	public void formatterOffOnPreservesTextContent() throws BadLocationException {
		String content = "<root>" + lineSeparator() + //
				"  <!-- @formatter:off -->" + lineSeparator() + //
				"  <pre>  multiple   spaces   preserved  </pre>" + lineSeparator() + //
				"  <!-- @formatter:on -->" + lineSeparator() + //
				"</root>";
		String expected = content;
		assertFormat(content, expected);
	}

	@Test
	public void formatterOffInsideElement() throws BadLocationException {
		String content = "<records>" + lineSeparator() + //
				"  <record>" + lineSeparator() + //
				"    <name></name>" + lineSeparator() + //
				"    <!-- @formatter:off -->" + lineSeparator() + //
				"                          <email></email>" + lineSeparator() + //
				"                                    </record>" + lineSeparator() + //
				"            <record>" + lineSeparator() + //
				"              <name></name>" + lineSeparator() + //
				"            </record>" + lineSeparator() + //
				"  <!-- @formatter:on -->" + lineSeparator() + //
				"  <record>" + lineSeparator() + //
				"    <name></name>" + lineSeparator() + //
				"  </record>" + lineSeparator() + //
				"</records>";
		String expected = content;
		assertFormat(content, expected);
	}
	@Test
	public void completionFormatterOffOn() throws BadLocationException {
		String xml = "<root>\r\n" + //
				"  |" + //
				"</root>";
		testCompletionFor(xml, //
				c("@formatter:off", "<!-- @formatter:off -->", "<!-- @formatter:off"), //
				c("@formatter:on", "<!-- @formatter:on -->", "<!-- @formatter:on"));
	}

	private static void assertFormat(String unformatted, String expected, TextEdit... expectedEdits)
			throws BadLocationException {
		assertFormat(unformatted, expected, new SharedSettings(), expectedEdits);
	}

	private static void assertFormat(String unformatted, String expected, SharedSettings sharedSettings,
			TextEdit... expectedEdits) throws BadLocationException {
		XMLAssert.assertFormat(null, unformatted, expected, sharedSettings, "test.xml", Boolean.FALSE, expectedEdits);
	}
}
