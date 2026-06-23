package com.zerostudio.language.rust;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import org.junit.Test;
import static org.junit.Assert.*;

public class RustSymbolExtractorTest {

    @Test
    public void extractsUse() {
        RustSymbolExtractor ext = new RustSymbolExtractor();
        String src = "use std::collections::HashMap;\n" +
                "use std::io::{Read, Write} as io;\n" +
                "pub use crate::module::foo as bar;\n";
        ParsedFile pf = ext.extract("lib.rs", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("std::collections::HashMap") && r.kind == Reference.ReferenceKind.IMPORT));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("std::io::{Read, Write} as io") || r.name.startsWith("std::io")));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Read") && r.kind == Reference.ReferenceKind.TYPE));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Write") && r.kind == Reference.ReferenceKind.TYPE));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("crate::module::foo") && r.kind == Reference.ReferenceKind.IMPORT));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("bar") && r.kind == Reference.ReferenceKind.TYPE));
    }

    @Test
    public void extractsStructEnumTrait() {
        RustSymbolExtractor ext = new RustSymbolExtractor();
        String src = "pub struct User { name: String }\n" +
                "pub enum Color { Red, Green, Blue }\n" +
                "pub trait Greet { fn hello(&self); }\n";
        ParsedFile pf = ext.extract("types.rs", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("User") && r.kind == Reference.ReferenceKind.CLASS));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Color") && r.kind == Reference.ReferenceKind.TYPE));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Greet") && r.kind == Reference.ReferenceKind.CLASS));
    }

    @Test
    public void extractsFunctions() {
        RustSymbolExtractor ext = new RustSymbolExtractor();
        String src = "pub fn hello() {}\n" +
                "async fn fetch() {}\n" +
                "const fn calc() {}\n" +
                "unsafe fn danger() {}\n";
        ParsedFile pf = ext.extract("fn.rs", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("hello") && r.kind == Reference.ReferenceKind.METHOD));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("fetch") && r.kind == Reference.ReferenceKind.METHOD));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("calc") && r.kind == Reference.ReferenceKind.METHOD));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("danger") && r.kind == Reference.ReferenceKind.METHOD));
    }

    @Test
    public void extractsImpl() {
        RustSymbolExtractor ext = new RustSymbolExtractor();
        String src = "impl User {\n" +
                "    pub fn new() -> Self { User }\n" +
                "}\n" +
                "impl Greet for User {\n" +
                "    fn hello(&self) {}\n" +
                "}\n";
        ParsedFile pf = ext.extract("impl.rs", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("User") && r.kind == Reference.ReferenceKind.CLASS));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Greet") && r.kind == Reference.ReferenceKind.CLASS));
    }

    @Test
    public void extractsModTypeConstStatic() {
        RustSymbolExtractor ext = new RustSymbolExtractor();
        String src = "pub mod inner;\n" +
                "type Alias = String;\n" +
                "const MAX: u32 = 100;\n" +
                "static NAME: &str = \"x\";\n" +
                "static mut COUNTER: u32 = 0;\n";
        ParsedFile pf = ext.extract("mod.rs", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("inner") && r.kind == Reference.ReferenceKind.CLASS));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Alias") && r.kind == Reference.ReferenceKind.TYPE));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("MAX") && r.kind == Reference.ReferenceKind.VARIABLE));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("NAME") && r.kind == Reference.ReferenceKind.VARIABLE));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("COUNTER") && r.kind == Reference.ReferenceKind.VARIABLE));
    }

    @Test
    public void handlesEmptyFile() {
        ParsedFile pf = new RustSymbolExtractor().extract("a.rs", "");
        assertNotNull(pf);
        assertEquals(LanguageId.RUST, pf.language);
        assertTrue(pf.references.isEmpty());
    }
}
