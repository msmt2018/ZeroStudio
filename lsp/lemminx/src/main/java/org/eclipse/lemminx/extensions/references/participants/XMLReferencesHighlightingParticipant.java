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
package org.eclipse.lemminx.extensions.references.participants;

import java.util.List;

import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.extensions.references.XMLReferencesPlugin;
import org.eclipse.lemminx.extensions.references.search.SearchEngine;
import org.eclipse.lemminx.extensions.references.search.SearchQuery;
import org.eclipse.lemminx.extensions.references.search.SearchQueryFactory;
import org.eclipse.lemminx.services.extensions.IHighlightingParticipant;
import org.eclipse.lemminx.utils.XMLPositionUtility;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

/**
 * XML references highlight participant
 * 
 * @author Angelo ZERR
 *
 */
public class XMLReferencesHighlightingParticipant implements IHighlightingParticipant {

	private final XMLReferencesPlugin plugin;

	public XMLReferencesHighlightingParticipant(XMLReferencesPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public void findDocumentHighlights(DOMNode node, Position position, int offset, List<DocumentHighlight> highlights,
			CancelChecker cancelChecker) {
		// Create the from query for the node which needs to perform the search.
		SearchQuery query = SearchQueryFactory.createToQueryByRetrievingToBefore(node, offset,
				plugin.getReferencesSettings(), cancelChecker);
		if (query == null) {
			// The query cannot be created because:
			// - the node is neither a text nor an attribute
			// - it doesn't exists some expressions for the DOM document of the node.
			// - there are none expressions which matches the node.
			return;
		}

		query.setMatchNode(true);
		query.setSearchInIncludedFiles(false);

		// Highlight the 'to' node
		highlights.add(new DocumentHighlight(
				XMLPositionUtility.createRange(query.getSearchNode()),
				DocumentHighlightKind.Write));

		// Highlight the referenced 'from' nodes
		SearchEngine.getInstance().search(query,
				(fromSearchNode, toSearchNode, expression) -> {
					highlights.add(new DocumentHighlight(
							XMLPositionUtility.createRange(fromSearchNode),
							DocumentHighlightKind.Read));
				}, cancelChecker);

	}

}
