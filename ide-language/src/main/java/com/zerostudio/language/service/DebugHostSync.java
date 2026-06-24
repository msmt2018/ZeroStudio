package com.zerostudio.language.service;

import com.zerostudio.language.model.*;
import com.zerostudio.language.source.SourceResolver;
import java.util.function.*;

public final class DebugHostSync {
    public interface HostControl {
        void freezeEditor();
        void thawEditor();
        void highlightRange(SourceRange range);
    }

    private HostControl control;
    private EditorIntegration open;
    private LanguageService language;
    private boolean frozen = false;

    public DebugHostSync(EditorIntegration open) { this.open = open; }

    public void setLanguageService(LanguageService svc) { this.language = svc; }
    public void setHostControl(HostControl ctrl) { this.control = ctrl; }
    public void setOpenHandler(EditorIntegration.OpenHandler h) { open.setOpenHandler(h); }

    public boolean isFrozen() { return frozen; }

    public void onBreakpointHit(String filePath, int line) {
        freezeEditor();
    }

    public void freezeEditor() {
        frozen = true;
        if (control != null) control.freezeEditor();
    }

    public void thawEditor() {
        frozen = false;
        if (control != null) control.thawEditor();
    }

    public void openResolved(ResolutionResult result) {
        if (!result.isResolved()) return;
        if (result.targetFile != null && result.targetFile.startsWith("[")
                && language != null && language.sourceResolver() != null
                && result.targetSymbol != null && result.targetSymbol.fqn != null) {
            SourceResolver.ResolvedSource src = language.sourceResolver().resolve(result.targetSymbol.fqn);
            if (src.isResolved()) {
                SourceRange range = result.targetRange != null ? result.targetRange
                        : (src.kind == SourceResolver.Kind.WORKSPACE_SOURCE ? null
                                : new SourceRange(new SourcePosition(src.displayPath, 1, 1),
                                        new SourcePosition(src.displayPath, 1, 1)));
                open.open(new EditorIntegration.OpenRequest(src.displayPath, range, src.sourceText, true));
                return;
            }
        }
        open.open(new EditorIntegration.OpenRequest(result.targetFile, result.targetRange));
    }
}