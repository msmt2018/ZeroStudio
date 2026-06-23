package com.zerostudio.language.service;

import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.ResolutionResult;
import com.zerostudio.language.model.SourceLocation;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.model.Symbol;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Editor-side adapter. The actual code editor (SoraEditor-based) is not
 * coupled to this library directly; it goes through this facade.
 *
 * <p>Why: keeps the language service free of UI dependencies, and lets the
 * editor swap implementations easily (e.g. a stubbed one in instrumented
 * tests).
 */
public final class EditorIntegration {

    private final LanguageService language;
    private final ExecutorService io =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "ide-language-io");
                t.setDaemon(true);
                return t;
            });

    public EditorIntegration(LanguageService language) {
        this.language = Objects.requireNonNull(language);
    }

    /**
     * Called by the editor on Ctrl+click or "Go to Definition" from the
     * long-press menu.
     */
    public CompletableFuture<ResolutionResult> goToDefinition(
            String filePath, int line, int column) {
        return CompletableFuture.supplyAsync(() -> {
            SourceLocation loc = new SourceLocation(
                    filePath, new SourcePosition(line, column));
            return language.goToDefinition().resolve(loc);
        }, io);
    }

    /**
     * Called by the editor on every keystroke (debounced) to keep the
     * background parser in sync with what's on screen.
     */
    public CompletableFuture<ParsedFile> reparse(File file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return language.parseFile(file);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }, io);
    }

    /**
     * Open the file in the editor at the given range. The default
     * implementation uses an {@link OpenHandler} supplied at construction
     * time, so this library does not depend on any specific editor.
     */
    public void openAt(ResolutionResult result) {
        if (result == null || !result.isResolved()) return;
        if (openHandler == null) return;
        openHandler.open(new OpenRequest(result.targetFile, result.targetRange));
    }

    private OpenHandler openHandler;

    public void setOpenHandler(OpenHandler h) { this.openHandler = h; }

    /** Receiver for "open this file at this range" requests. */
    public interface OpenHandler {
        void open(OpenRequest req);
    }

    public static final class OpenRequest {
        public final String file;
        public final SourceRange range;
        public OpenRequest(String file, SourceRange range) {
            this.file = file;
            this.range = range;
        }
    }

    public void shutdown() { io.shutdownNow(); }
}
