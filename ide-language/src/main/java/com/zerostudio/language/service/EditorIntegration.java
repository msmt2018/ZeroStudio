package com.zerostudio.language.service;

import com.zerostudio.language.model.*;
import java.util.function.*;

public final class EditorIntegration {
    public interface OpenHandler {
        void open(OpenRequest req);
    }
    public static final class OpenRequest {
        public final String file;
        public final SourceRange range;
        public final String bufferContent; // null for real files
        public final boolean readOnly;
        public OpenRequest(String file, SourceRange range) {
            this(file, range, null, false);
        }
        public OpenRequest(String file, SourceRange range, String bufferContent, boolean readOnly) {
            this.file = file; this.range = range; this.bufferContent = bufferContent; this.readOnly = readOnly;
        }
    }

    private OpenHandler openHandler;

    public void setOpenHandler(OpenHandler h) { this.openHandler = h; }
    public void open(OpenRequest req) {
        if (openHandler != null) openHandler.open(req);
    }

    public void openRealFile(String path, SourceRange range) {
        open(new OpenRequest(path, range));
    }

    public void openVirtual(String displayPath, String sourceText, SourcePosition cursor) {
        open(new OpenRequest(displayPath, cursor != null ?
                new SourceRange(cursor, cursor) : null, sourceText, true));
    }
}