package com.zerostudio.language.javascript;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import org.junit.Test;
import static org.junit.Assert.*;

public class JsSymbolExtractorTest {

    @Test
    public void extractsClassAndInheritance() {
        JsSymbolExtractor ext = new JsSymbolExtractor(LanguageId.TYPESCRIPT);
        String src = "import { Component } from 'react';\n" +
                "export class MyComp extends Component implements Renderable {\n" +
                "    render() { return null; }\n" +
                "}\n";
        ParsedFile pf = ext.extract("MyComp.tsx", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("MyComp") && r.kind == Reference.ReferenceKind.CLASS));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Component")));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("react") && r.kind == Reference.ReferenceKind.IMPORT));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Renderable")));
        assertEquals(LanguageId.TYPESCRIPT, pf.language);
    }

    @Test
    public void extractsFunctionAndArrow() {
        JsSymbolExtractor ext = new JsSymbolExtractor();
        String src = "export function foo(x) { return x; }\n" +
                "const bar = async () => 42;\n";
        ParsedFile pf = ext.extract("util.js", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("foo") && r.kind == Reference.ReferenceKind.METHOD));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("bar") && r.kind == Reference.ReferenceKind.METHOD));
    }

    @Test
    public void extractsRequireAndDestructuring() {
        JsSymbolExtractor ext = new JsSymbolExtractor();
        String src = "const fs = require('fs');\n" +
                "const { readFile, writeFile } = require('fs/promises');\n";
        ParsedFile pf = ext.extract("io.js", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("fs") && r.kind == Reference.ReferenceKind.IMPORT));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("fs/promises") && r.kind == Reference.ReferenceKind.IMPORT));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("fs") && r.kind == Reference.ReferenceKind.VARIABLE));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("readFile") && r.kind == Reference.ReferenceKind.VARIABLE));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("writeFile") && r.kind == Reference.ReferenceKind.VARIABLE));
    }

    @Test
    public void extractsInterfaceAndTypeAliasAndEnum() {
        JsSymbolExtractor ext = new JsSymbolExtractor(LanguageId.TYPESCRIPT);
        String src = "export interface User extends Base { id: number; }\n" +
                "export type ID = string | number;\n" +
                "export enum Color { Red, Green, Blue }\n";
        ParsedFile pf = ext.extract("types.ts", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("User") && r.kind == Reference.ReferenceKind.CLASS));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Base")));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("ID") && r.kind == Reference.ReferenceKind.TYPE));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Color") && r.kind == Reference.ReferenceKind.TYPE));
    }

    @Test
    public void extractsTopLevelVar() {
        JsSymbolExtractor ext = new JsSymbolExtractor();
        String src = "var count = 0;\nlet name = 'x';\nconst PI = 3.14;\n";
        ParsedFile pf = ext.extract("a.js", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("count") && r.kind == Reference.ReferenceKind.VARIABLE));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("name") && r.kind == Reference.ReferenceKind.VARIABLE));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("PI") && r.kind == Reference.ReferenceKind.VARIABLE));
    }

    @Test
    public void handlesEmptyFile() {
        ParsedFile pf = new JsSymbolExtractor().extract("a.js", "");
        assertNotNull(pf);
        assertEquals(LanguageId.JAVASCRIPT, pf.language);
        assertTrue(pf.references.isEmpty());
    }
}
