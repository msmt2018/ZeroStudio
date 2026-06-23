package com.zerostudio.language.service;

import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.ResolutionResult;
import com.zerostudio.language.model.SourceLocation;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates the bidirectional synchronisation between the host
 * application's UI and the code editor.
 *
 * <p>Lifecycle of a single "breakpoint hit" event:
 * <ol>
 *   <li>The debugger suspends the target VM at a frame
 *       {@code (file, line, column)}.</li>
 *   <li>The host UI is frozen via {@link #freeze()}.</li>
 *   <li>The editor opens the source file with the cursor at
 *       {@code (line, column)} via {@link #openAt}.</li>
 *   <li>The user can then click a token in the editor to invoke
 *       {@link #goToDefinition(SourceLocation)}, which uses
 *       {@link GoToDefinitionService} to find the declaration and call
 *       {@link #openAt(ResolutionResult)} again.</li>
 *   <li>Once the user resumes the host, {@link #unfreeze()} is called.</li>
 * </ol>
 *
 * <p>This class is the single integration point for everything that ties
 * the host UI, the editor and the language services together. It contains
 * no Android-specific code; the actual host / editor bridges are injected
 * through {@link HostControl} and {@link OpenHandler}.
 */
public final class DebugHostSync {

    private final LanguageService language;
    private final ExecutorService io;
    private final HostControl host;
    private final EditorIntegration.OpenHandler open;
    private final FreezeGate freeze = new FreezeGate();
    private final CursorTracker cursor = new CursorTracker();

    public DebugHostSync(LanguageService language,
                         HostControl host,
                         EditorIntegration.OpenHandler open) {
        this.language = Objects.requireNonNull(language);
        this.host = Objects.requireNonNull(host);
        this.open = Objects.requireNonNull(open);
        this.io = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "ide-debug-host-sync");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Called by the debugger when a breakpoint is hit. The host UI is
     * frozen, the source file is parsed (if needed), and the editor is
     * asked to jump to the location.
     *
     * @return a future that completes when the editor has acknowledged
     *         the request. Resolving a definition inside the editor is a
     *         separate future returned by
     *         {@link #goToDefinition(SourceLocation)}.
     */
    public CompletableFuture<SourceLocation> onBreakpointHit(
            String file, int line, int column) {
        SourceLocation hit = new SourceLocation(
                file, new SourcePosition(line, column));
        return CompletableFuture.runAsync(() -> {
            host.freeze("breakpoint@" + file + ":" + line);
            freeze.freeze("breakpoint@" + file + ":" + line);
            // Make sure the file is parsed and indexed.
            try {
                ParsedFile parsed = language.parseText(file,
                        readIfKnown(file), detectLanguage(file));
            } catch (Throwable t) {
                // Index failures are non-fatal: openAt still works.
            }
            open.open(new EditorIntegration.OpenRequest(file,
                    new SourceRange(line, column, line, column + 1)));
            cursor.moveTo(hit);
        }, io).thenApply(v -> hit);
    }

    /**
     * Resolve and jump to the declaration of a token the user clicked.
     *
     * <p>If the resolution target is a virtual file (decompiled class
     * from a class jar, or a source-jar entry), the editor is asked
     * to open the buffer with the decompiled / source-jar content
     * pre-loaded, so the user sees the decompiled code in a read-only
     * tab with a banner like {@code [decompiled from android.jar]}.
     */
    public CompletableFuture<ResolutionResult> goToDefinition(SourceLocation where) {
        return CompletableFuture.supplyAsync(
                () -> language.goToDefinition().resolve(where), io)
                .thenApply(result -> {
                    if (result != null && result.isResolved()) {
                        openResolved(result);
                        cursor.moveTo(new SourceLocation(
                                result.targetFile, result.targetRange.start));
                    }
                    return result;
                });
    }

    private void openResolved(ResolutionResult result) {
        // The result's targetFile is a display path. For workspace
        // sources it's a real file path; for decompiled / source-jar
        // sources it's a marker like "[decompiled from android.jar]".
        // Detect the marker and use the SourceResolver to obtain the
        // pre-loaded buffer content.
        if (result.targetFile == null) return;
        if (result.targetFile.startsWith("[")
                && language.sourceResolver() != null
                && result.targetSymbol != null
                && result.targetSymbol.fqn != null) {
            com.zerostudio.language.source.SourceResolver.ResolvedSource src =
                    language.sourceResolver().resolve(result.targetSymbol.fqn);
            if (src.isResolved()) {
                open.open(new EditorIntegration.OpenRequest(
                        src.displayPath, result.targetRange,
                        src.sourceText, /* readOnly= */ true));
                return;
            }
        }
        open.open(new EditorIntegration.OpenRequest(
                result.targetFile, result.targetRange));
    }

    /**
     * Move the cursor to the next breakpoint-style line in the file
     * currently open in the editor.
     */
    public CompletableFuture<SourceLocation> jumpToNextBreakpoint() {
        return CompletableFuture.supplyAsync(() -> {
            ParsedFile parsed = language.index().lookup()
                    .file(cursor.location.file);
            if (parsed == null) return null;
            int next = BreakpointNavigation.nextBreakpointLine(
                    parsed, cursor.location.position);
            if (next < 0) return null;
            SourceLocation dest = new SourceLocation(
                    cursor.location.file, new SourcePosition(next, 0));
            open.open(new EditorIntegration.OpenRequest(
                    dest.file, new SourceRange(next, 0, next, 1)));
            cursor.moveTo(dest);
            return dest;
        }, io);
    }

    public CompletableFuture<SourceLocation> jumpToPreviousBreakpoint() {
        return CompletableFuture.supplyAsync(() -> {
            ParsedFile parsed = language.index().lookup()
                    .file(cursor.location.file);
            if (parsed == null) return null;
            int prev = BreakpointNavigation.previousBreakpointLine(
                    parsed, cursor.location.position);
            if (prev < 0) return null;
            SourceLocation dest = new SourceLocation(
                    cursor.location.file, new SourcePosition(prev, 0));
            open.open(new EditorIntegration.OpenRequest(
                    dest.file, new SourceRange(prev, 0, prev, 1)));
            cursor.moveTo(dest);
            return dest;
        }, io);
    }

    /**
     * Resume the host application. The host UI thaws and the user can
     * interact with it again.
     */
    public void unfreeze() {
        host.thaw();
        freeze.thaw();
    }

    public boolean isFrozen() {
        return freeze.isFrozen() && host.isFrozen();
    }

    public SourceLocation currentCursor() {
        return cursor.location;
    }

    public void shutdown() {
        host.thaw();
        freeze.thaw();
        io.shutdownNow();
    }

    // ---------- internals ----------

    private com.zerostudio.language.model.LanguageId detectLanguage(String file) {
        String name = file;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        return com.zerostudio.language.model.LanguageId.fromExtension(name);
    }

    private String readIfKnown(String file) {
        ParsedFile existing = language.index().lookup().file(file);
        return existing == null ? "" : existing.sourceText;
    }

    /** Pluggable bridge to the host application. */
    public interface HostControl {
        void freeze(String reason);
        void thaw();
        boolean isFrozen();
    }

    /** Default no-op host (used in tests). */
    public static final HostControl NOOP_HOST = new HostControl() {
        @Override public void freeze(String reason) {}
        @Override public void thaw() {}
        @Override public boolean isFrozen() { return false; }
    };

    private static final class FreezeGate {
        private volatile boolean frozen;
        private volatile String reason;
        synchronized void freeze(String r) { frozen = true; reason = r; }
        synchronized void thaw() { frozen = false; reason = null; }
        synchronized boolean isFrozen() { return frozen; }
    }

    private static final class CursorTracker {
        volatile SourceLocation location = new SourceLocation(
                "", new SourcePosition(0, 0));
        void moveTo(SourceLocation l) { this.location = l; }
    }
}
