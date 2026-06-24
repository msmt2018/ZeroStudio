package com.zerostudio.language.parser;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;

import java.io.File;
import java.io.IOException;

/** Parser interface. */
public interface Parser {
    /** Parse the contents of the given file. */
    ParsedFile parse(File file) throws IOException;

    /** Parse the given text (path is for identification only). */
    ParsedFile parse(String path, String text);

    LanguageId language();
}
