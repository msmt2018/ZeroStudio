/*******************************************************************************
* Copyright (c) 2019 Red Hat Inc. and others.
* All rights reserved. This program and the accompanying materials
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v20.html
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     Red Hat Inc. - initial API and implementation
*******************************************************************************/
package org.eclipse.lemminx.utils;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * IO utilities class
 *
 * @author Angelo ZERR
 *
 */
public class IOUtils {

	/**
	 * Convert the given {@link InputStream} into a String. The source InputStream
	 * will then be closed.
	 *
	 * @param is the input stream
	 * @return the given input stream in a String.
	 */
	public static String convertStreamToString(InputStream is) {
		try (Scanner s = new java.util.Scanner(is)) {
			s.useDelimiter("\\A");
			return s.hasNext() ? s.next() : "";
		}
	}

	/**
	 * Detect the encoding declared in the XML prolog of the given bytes.
	 *
	 * <p>
	 * Scans only the first 200 bytes char-by-char for the
	 * {@code encoding="..."} attribute in {@code <?xml ...?>}.
	 * Returns UTF-8 if no prolog, no encoding attribute, or
	 * an unsupported encoding name is found.
	 * </p>
	 *
	 * @param bytes the raw bytes of an XML document.
	 * @return the detected {@link Charset}, never null.
	 */
	public static Charset detectXmlEncoding(byte[] bytes) {
		int len = Math.min(bytes.length, 200);
		int i = 0;

		// Skip UTF-8 BOM (EF BB BF)
		if (len >= 3
				&& (bytes[0] & 0xFF) == 0xEF
				&& (bytes[1] & 0xFF) == 0xBB
				&& (bytes[2] & 0xFF) == 0xBF) {
			i = 3;
		}

		// Skip leading whitespace
		while (i < len && isXmlWhitespace(bytes[i])) {
			i++;
		}

		// Check for <?xml followed by whitespace
		if (i + 6 > len
				|| bytes[i] != '<'
				|| bytes[i + 1] != '?'
				|| bytes[i + 2] != 'x'
				|| bytes[i + 3] != 'm'
				|| bytes[i + 4] != 'l'
				|| !isXmlWhitespace(bytes[i + 5])) {
			return StandardCharsets.UTF_8;
		}
		i += 6;

		// Scan for encoding="..." within the XML declaration
		while (i < len - 1) {
			if (bytes[i] == '?' && bytes[i + 1] == '>') {
				break;
			}

			// Skip quoted attribute values (e.g. version="1.0")
			if (bytes[i] == '"' || bytes[i] == '\'') {
				byte quote = bytes[i];
				i++;
				while (i < len && bytes[i] != quote) {
					i++;
				}
				if (i < len) {
					i++;
				}
				continue;
			}

			// Match 'encoding' keyword
			if (i + 8 <= len
					&& bytes[i] == 'e'
					&& bytes[i + 1] == 'n'
					&& bytes[i + 2] == 'c'
					&& bytes[i + 3] == 'o'
					&& bytes[i + 4] == 'd'
					&& bytes[i + 5] == 'i'
					&& bytes[i + 6] == 'n'
					&& bytes[i + 7] == 'g') {
				i += 8;
				// Skip whitespace before '='
				while (i < len && isXmlWhitespace(bytes[i])) {
					i++;
				}
				if (i >= len || bytes[i] != '=') {
					break;
				}
				i++;
				// Skip whitespace after '='
				while (i < len && isXmlWhitespace(bytes[i])) {
					i++;
				}
				if (i >= len) {
					break;
				}
				byte quote = bytes[i];
				if (quote != '"' && quote != '\'') {
					break;
				}
				i++;
				int start = i;
				while (i < len && bytes[i] != quote) {
					i++;
				}
				if (i >= len) {
					break;
				}
				String encodingName = new String(bytes, start, i - start, StandardCharsets.US_ASCII);
				try {
					return Charset.forName(encodingName);
				} catch (Exception e) {
					return StandardCharsets.UTF_8;
				}
			}
			i++;
		}
		return StandardCharsets.UTF_8;
	}

	private static boolean isXmlWhitespace(byte b) {
		return b == ' ' || b == '\t' || b == '\r' || b == '\n';
	}
}
