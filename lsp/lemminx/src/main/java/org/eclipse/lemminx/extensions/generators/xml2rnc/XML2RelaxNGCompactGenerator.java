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

import java.util.Collection;
import java.util.Set;

import org.eclipse.lemminx.extensions.generators.AbstractXML2GrammarGenerator;
import org.eclipse.lemminx.extensions.generators.AttributeDeclaration;
import org.eclipse.lemminx.extensions.generators.AttributeDeclaration.DataType;
import org.eclipse.lemminx.extensions.generators.Cardinality;
import org.eclipse.lemminx.extensions.generators.ElementDeclaration;
import org.eclipse.lemminx.extensions.generators.Grammar;
import org.eclipse.lemminx.services.IXMLFullFormatter;
import org.eclipse.lemminx.settings.SharedSettings;
import org.eclipse.lemminx.utils.StringUtils;
import org.eclipse.lemminx.utils.XMLBuilder;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.w3c.dom.Document;

/**
 * File Generator implementation to generate RelaxNG Compact Syntax (.rnc) from
 * a given XML source.
 *
 */
public class XML2RelaxNGCompactGenerator extends AbstractXML2GrammarGenerator<RelaxNGCompactGeneratorSettings> {

	private static final String XML_SCHEMA_DATATYPES = "http://www.w3.org/2001/XMLSchema-datatypes";

	private static final String NL = System.lineSeparator();

	@Override
	public String generate(Document prototypeDocument, SharedSettings sharedSettings,
			RelaxNGCompactGeneratorSettings generatorSettings, IXMLFullFormatter formatter,
			CancelChecker cancelChecker) {
		// RNC is not XML, skip the XML formatter
		return super.generate(prototypeDocument, sharedSettings, generatorSettings, null, cancelChecker);
	}

	@Override
	protected void generate(Grammar grammar, RelaxNGCompactGeneratorSettings settings, XMLBuilder rnc,
			CancelChecker cancelChecker) {
		String targetNamespace = grammar.getDefaultNamespace();
		if (!StringUtils.isEmpty(targetNamespace)) {
			rnc.append("default namespace = \"");
			rnc.append(targetNamespace);
			rnc.append("\"");
			rnc.append(NL);
		}
		rnc.append("datatypes xsd = \"");
		rnc.append(XML_SCHEMA_DATATYPES);
		rnc.append("\"");
		rnc.append(NL);

		ElementDeclaration rootElement = grammar.getElements().stream().findFirst().orElse(null);
		String rootElementName = rootElement == null ? "" : rootElement.getName();
		rnc.append(NL);
		rnc.append("start = ");
		rnc.append(rootElementName);
		rnc.append("Content");
		rnc.append(NL);

		for (ElementDeclaration element : grammar.getElements()) {
			cancelChecker.checkCanceled();
			rnc.append(NL);
			generateRncElement(rnc, element, settings, rootElementName + "Content", cancelChecker);
		}
	}

	private void generateRncElement(XMLBuilder rnc, ElementDeclaration elementDecl,
			RelaxNGCompactGeneratorSettings settings, String patternName, CancelChecker cancelChecker) {
		Collection<ElementDeclaration> children = elementDecl.getElements();
		Collection<AttributeDeclaration> attributes = elementDecl.getAttributes();
		boolean hasChildren = !children.isEmpty();
		boolean hasAttributes = !attributes.isEmpty();
		boolean hasCharacterContent = elementDecl.hasCharacterContent();
		boolean mixedContent = hasChildren && hasCharacterContent;

		String name = elementDecl.getName();

		rnc.append(patternName);
		rnc.append(" = element ");
		rnc.append(name);
		rnc.append(" {");

		if (!hasChildren && !hasAttributes) {
			if (hasCharacterContent) {
				rnc.append(" text");
			} else {
				rnc.append(" empty");
			}
			rnc.append(" }");
			rnc.append(NL);
		} else {
			rnc.append(NL);
			boolean hasContent = false;

			if (mixedContent) {
				rnc.append("  mixed {");
				rnc.append(NL);
				generateChildRefs(rnc, elementDecl, children, true, cancelChecker);
				rnc.append(NL);
				rnc.append("  }");
				hasContent = true;
			} else if (hasCharacterContent && !hasChildren) {
				rnc.append("  text");
				hasContent = true;
			}

			if (hasChildren && !mixedContent) {
				generateChildRefs(rnc, elementDecl, children, false, cancelChecker);
				hasContent = true;
			}

			if (hasAttributes) {
				generateAttributes(rnc, attributes, settings, hasContent, cancelChecker);
			}

			rnc.append(NL);
			rnc.append("}");
			rnc.append(NL);

			for (ElementDeclaration child : children) {
				generateRncElement(rnc, child, settings, child.getName() + "Content", cancelChecker);
			}
		}
	}

	private void generateChildRefs(XMLBuilder rnc, ElementDeclaration elementDecl,
			Collection<ElementDeclaration> children, boolean mixedContent, CancelChecker cancelChecker) {
		boolean sequenced = elementDecl.getChildrenProperties().isSequenced();
		String indent = mixedContent ? "    " : "  ";
		String separator = sequenced ? "," : " &";

		boolean first = true;
		for (ElementDeclaration child : children) {
			cancelChecker.checkCanceled();
			String childName = child.getName();
			Cardinality childCardinality = elementDecl.getChildrenProperties().getCardinalities().get(childName);

			if (!first) {
				rnc.append(separator);
				rnc.append(NL);
			}
			first = false;

			rnc.append(indent);
			rnc.append(childName);
			rnc.append("Content");

			if (childCardinality != null) {
				if (childCardinality.getMin() == 0) {
					rnc.append("?");
				} else if (childCardinality.getMax() > 1) {
					rnc.append("+");
				}
			}
		}
	}

	private void generateAttributes(XMLBuilder rnc, Collection<AttributeDeclaration> attributes,
			RelaxNGCompactGeneratorSettings settings, boolean needsCommaBefore, CancelChecker cancelChecker) {
		boolean needsSeparator = needsCommaBefore;
		for (AttributeDeclaration attribute : attributes) {
			cancelChecker.checkCanceled();

			boolean required = attribute.isRequired();
			boolean fixed = attribute.isFixedValue(settings);
			boolean enums = attribute.isEnums(settings);
			String rncType = getRncType(attribute.getDataType());

			if (needsSeparator) {
				rnc.append(",");
				rnc.append(NL);
			}
			needsSeparator = true;

			rnc.append("  attribute ");
			rnc.append(attribute.getName());
			rnc.append(" {");

			if (enums && !fixed) {
				Set<String> values = attribute.getValues();
				rnc.append(" ");
				boolean firstValue = true;
				for (String value : values) {
					cancelChecker.checkCanceled();
					if (!firstValue) {
						rnc.append(" | ");
					}
					firstValue = false;
					rnc.append("\"");
					rnc.append(value);
					rnc.append("\"");
				}
				rnc.append(" }");
			} else if (fixed) {
				String value = attribute.getValues().stream().findFirst().orElse(null);
				rnc.append(" \"");
				rnc.append(value);
				rnc.append("\" }");
			} else if (rncType != null) {
				rnc.append(" ");
				rnc.append(rncType);
				rnc.append(" }");
			} else {
				rnc.append(" text }");
			}

			if (!required) {
				rnc.append("?");
			}
		}
	}

	private static String getRncType(DataType dataType) {
		switch (dataType) {
			case DATE:
				return "xsd:date";
			case DATE_TIME:
				return "xsd:dateTime";
			case INTEGER:
				return "xsd:integer";
			case DECIMAL:
				return "xsd:decimal";
			case BOOLEAN:
				return "xsd:boolean";
			default:
				return null;
		}
	}

	@Override
	protected String getFileExtension() {
		return "rnc";
	}
}
