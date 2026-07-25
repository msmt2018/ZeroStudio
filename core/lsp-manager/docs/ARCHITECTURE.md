# Language Server Prompt Integration Architecture

ZeroStudio uses an MVM/IVM split for editor/LSP integration:

1. `:editor:editor-lsp` owns the Sora `CodeEditor` integration and editor-facing UI delegates.
2. `:core:lsp-api` remains the protocol API module for editor-core models, LSP4J request/response routing, JSON-RPC client/server boundaries, and feature contracts.
3. `:core:lsp-manager` is the orchestration module added for pluggable server registration, session lifecycle, editor binding, window events, and UI contribution discovery.

The manager exposes plug-in boundaries for server descriptors and UI contributions so completion, outline, documentation, symbols, diagnostics, progress, and window surfaces can be replaced independently while preserving LSP4J transport compatibility.
