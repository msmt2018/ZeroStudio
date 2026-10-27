/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others. All rights reserved. This program
 * and the accompanying materials which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors: Red Hat Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.lemminx.customservice;

import org.eclipse.lsp4j.Range;

/**
 * @deprecated Use {@link AutoInsertResponse} instead.
 */
@Deprecated
public class AutoCloseTagResponse extends AutoInsertResponse {

	public AutoCloseTagResponse(String snippet, Range range) {
		super(snippet, range);
	}

	public AutoCloseTagResponse(String snippet) {
		super(snippet);
	}
}
