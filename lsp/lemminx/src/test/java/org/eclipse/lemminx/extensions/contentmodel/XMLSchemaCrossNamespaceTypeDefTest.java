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
package org.eclipse.lemminx.extensions.contentmodel;

import static org.eclipse.lemminx.XMLAssert.assertHover;
import static org.eclipse.lemminx.XMLAssert.c;
import static org.eclipse.lemminx.XMLAssert.ll;
import static org.eclipse.lemminx.XMLAssert.r;
import static org.eclipse.lemminx.XMLAssert.te;
import static org.eclipse.lemminx.XMLAssert.testCompletionFor;
import static org.eclipse.lemminx.XMLAssert.testTypeDefinitionFor;

import org.apache.xerces.impl.XMLEntityManager;
import org.apache.xerces.util.URI.MalformedURIException;
import org.eclipse.lemminx.AbstractCacheBasedTest;
import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lemminx.services.XMLLanguageService;
import org.junit.jupiter.api.Test;

public class XMLSchemaCrossNamespaceTypeDefTest extends AbstractCacheBasedTest {

    @Test
    public void crossNamespaceGlobalElementTypeDef() throws BadLocationException, MalformedURIException {
        String xmlFile = "src/test/resources/test.xml";
        String targetSchemaURI = XMLEntityManager.expandSystemId(
                "xsd/import-extended-attrs/product.xsd", xmlFile, true);

        String xml = "<root xmlns=\"http://example.com/main\"\r\n" +
                "      xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
                "      xmlns:product=\"http://example.com/product\"\r\n" +
                "      xsi:schemaLocation=\"http://example.com/main xsd/import-extended-attrs/main.xsd\">\r\n" +
                "  <product:Question|Value name=\"test\" />\r\n" +
                "</root>";

        XMLLanguageService xmlLanguageService = new XMLLanguageService();
        testTypeDefinitionFor(xmlLanguageService, xml, xmlFile,
                ll(targetSchemaURI, r(4, 3, 4, 24), r(9, 21, 9, 36)));
    }

    @Test
    public void issue1079ElementTypeDef() throws BadLocationException, MalformedURIException {
        String xmlFile = "src/test/resources/test.xml";
        String targetSchemaURI = XMLEntityManager.expandSystemId(
                "xsd/issue-1079/product-export-benefits.xsd", xmlFile, true);

        String xml = "<ExportDef xmlns=\"urn:Platform:Export\"\r\n" +
                "           xmlns:benefits=\"urn:Product:Export:Benefits\"\r\n" +
                "           xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
                "           xsi:schemaLocation=\"urn:Platform:Export xsd/issue-1079/export-model.xsd\">\r\n" +
                "  <benefits:Question|Value name=\"Address.Line1\" />\r\n" +
                "</ExportDef>";

        XMLLanguageService xmlLanguageService = new XMLLanguageService();
        testTypeDefinitionFor(xmlLanguageService, xml, xmlFile,
                ll(targetSchemaURI, r(4, 3, 4, 25), r(20, 20, 20, 35)));
    }

    @Test
    public void issue1079ElementHover() throws BadLocationException, MalformedURIException {
        String xmlFile = "src/test/resources/test.xml";
        String schemaURI = XMLEntityManager.expandSystemId(
                "xsd/issue-1079/product-export-benefits.xsd", xmlFile, true).replace("///", "/");
        String xml = "<ExportDef xmlns=\"urn:Platform:Export\"\r\n" +
                "           xmlns:benefits=\"urn:Product:Export:Benefits\"\r\n" +
                "           xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
                "           xsi:schemaLocation=\"urn:Platform:Export xsd/issue-1079/export-model.xsd\">\r\n" +
                "  <benefits:Question|Value name=\"Address.Line1\" />\r\n" +
                "</ExportDef>";
        assertHover(xml, xmlFile,
                "Defines a question value mapping for the export process." + //
                        System.lineSeparator() + //
                        System.lineSeparator() + "Source: [product-export-benefits.xsd](" + schemaURI + ")",
                r(4, 3, 4, 25));
    }

    @Test
    public void issue1079AttributeHover() throws BadLocationException, MalformedURIException {
        String xmlFile = "src/test/resources/test.xml";
        String schemaURI = XMLEntityManager.expandSystemId(
                "xsd/issue-1079/product-export-benefits.xsd", xmlFile, true).replace("///", "/");
        String xml = "<ExportDef xmlns=\"urn:Platform:Export\"\r\n" +
                "           xmlns:benefits=\"urn:Product:Export:Benefits\"\r\n" +
                "           xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
                "           xsi:schemaLocation=\"urn:Platform:Export xsd/issue-1079/export-model.xsd\">\r\n" +
                "  <benefits:QuestionValue na|me=\"Address.Line1\" />\r\n" +
                "</ExportDef>";
        assertHover(xml, xmlFile,
                "The fully qualified question name (e.g. Address.Line1, Contact.Email)." + //
                        System.lineSeparator() + //
                        System.lineSeparator() + "Source: [product-export-benefits.xsd](" + schemaURI + ")",
                r(4, 26, 4, 30));
    }

    @Test
    public void issue1079ElementCompletionWithSchemaLocation() throws BadLocationException {
        String xml = "<ExportDef xmlns=\"urn:Platform:Export\"\r\n" +
                "           xmlns:benefits=\"urn:Product:Export:Benefits\"\r\n" +
                "           xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
                "           xsi:schemaLocation=\"urn:Platform:Export xsd/issue-1079/export-model.xsd\">\r\n" +
                "  <|\r\n" +
                "</ExportDef>";
        testCompletionFor(xml, null, "src/test/resources/test.xml", null,
                c("benefits:QuestionValue", "<benefits:QuestionValue name=\"\" />"));
    }

    @Test
    public void issue1079ElementCompletionWithoutImportSchemaLocation() throws BadLocationException {
        // xs:import without schemaLocation — resolved only via xsi:schemaLocation
        String xml = "<ExportDef xmlns=\"urn:Platform:Export\"\r\n" +
                "           xmlns:benefits=\"urn:Product:Export:Benefits\"\r\n" +
                "           xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
                "           xsi:schemaLocation=\"urn:Platform:Export xsd/issue-1079/export-model-nolocation.xsd\r\n" +
                "                               urn:Product:Export:Benefits xsd/issue-1079/product-export-benefits.xsd\">\r\n" +
                "  <|\r\n" +
                "</ExportDef>";
        testCompletionFor(xml, null, "src/test/resources/test.xml", null,
                c("benefits:QuestionValue", "<benefits:QuestionValue name=\"\" />"));
    }

    @Test
    public void crossNamespaceAttributeTypeDef() throws BadLocationException, MalformedURIException {
        String xmlFile = "src/test/resources/test.xml";
        String targetSchemaURI = XMLEntityManager.expandSystemId(
                "xsd/import-extended-attrs/product.xsd", xmlFile, true);

        String xml = "<root xmlns=\"http://example.com/main\"\r\n" +
                "      xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
                "      xmlns:product=\"http://example.com/product\"\r\n" +
                "      xsi:schemaLocation=\"http://example.com/main xsd/import-extended-attrs/main.xsd\">\r\n" +
                "  <product:QuestionValue na|me=\"test\" />\r\n" +
                "</root>";

        XMLLanguageService xmlLanguageService = new XMLLanguageService();
        testTypeDefinitionFor(xmlLanguageService, xml, xmlFile,
                ll(targetSchemaURI, r(4, 25, 4, 29), r(13, 39, 13, 45)));
    }
}
