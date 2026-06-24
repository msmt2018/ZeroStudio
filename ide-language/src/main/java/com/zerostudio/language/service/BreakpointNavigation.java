package com.zerostudio.language.service;

import com.zerostudio.language.model.*;
import java.util.*;

public final class BreakpointNavigation {
    private final LanguageService language;

    public BreakpointNavigation(LanguageService language) { this.language = language; }

    public Optional<SourcePosition> nextBreakpoint(String filePath, String text, int currentLine) {
        ParsedFile parsed = language.parse(filePath, text).orElse(null);
        if (parsed == null) return Optional.empty();
        // simple line-based navigation - find next line with code
        String[] lines = text.split("\n");
        for (int i = currentLine; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty() && !lines[i].trim().startsWith("//")) {
                return Optional.of(new SourcePosition(filePath, i + 1, 1));
            }
        }
        return Optional.empty();
    }

    public Optional<SourcePosition> prevBreakpoint(String filePath, String text, int currentLine) {
        ParsedFile parsed = language.parse(filePath, text).orElse(null);
        if (parsed == null) return Optional.empty();
        String[] lines = text.split("\n");
        for (int i = currentLine - 2; i >= 0; i--) {
            if (!lines[i].trim().isEmpty() && !lines[i].trim().startsWith("//")) {
                return Optional.of(new SourcePosition(filePath, i + 1, 1));
            }
        }
        return Optional.empty();
    }
}