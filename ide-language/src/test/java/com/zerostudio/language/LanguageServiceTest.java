package com.zerostudio.language;

import com.zerostudio.language.index.InMemoryProjectIndex;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.service.LanguageService;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LanguageServiceTest {

    @Test
    public void parsesAndCachesByPath() throws Exception {
        File tmp = File.createTempFile("Hello", ".java");
        tmp.deleteOnExit();
        try (Writer w = new FileWriter(tmp)) {
            w.write("public class Hello { void hi() {} }\n");
        }
        LanguageService svc = new LanguageService(new InMemoryProjectIndex());
        ParsedFile first = svc.parseFile(tmp);
        ParsedFile second = svc.parseFile(tmp);
        assertEquals(first.path, second.path);
        assertEquals(1, svc.index().fileCount());
        assertTrue(svc.index().lookup().byName("Hello").size() >= 1);
    }
}
