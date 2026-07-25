# Language Server Prompt Integration Architecture

ZeroStudio uses an MVM/IVM split for editor/LSP integration:

1. `:editor:editor-lsp` owns the Sora `CodeEditor` integration and editor-facing UI delegates.
2. `:core:lsp-api` remains the protocol API module for editor-core models, LSP4J request/response routing, JSON-RPC client/server boundaries, and feature contracts.
3. `:core:lsp-manager` is the orchestration module added for pluggable server registration, session lifecycle, editor binding, window events, protocol routing, and UI contribution discovery.

## Core flow

1. A language module registers an `LspServerDescriptor` with supported language IDs, file extensions, feature groups, and a factory that creates a sora-editor-lsp `LanguageServerDefinition`.
2. The editor opens a document and calls `LspEditorCoordinator.attachEditor`, which asks `LspSessionManager` to resolve the best descriptor and create a session.
3. Protocol requests and notifications are routed through `LspProtocolRouter` using stable LSP method names. The payload remains type-erased so each boundary can carry LSP4J objects, JSON objects, or ZeroStudio editor DTOs.
4. Window, progress, diagnostics, completion, outline, documentation, symbols, and status bar surfaces are exposed through `LspWindowEventBus` and `LspUiRegistry`, making UI/UX replacements independent from server lifecycle code.

## Extension points

- Server plug-ins: register/unregister `LspServerDescriptor` at runtime.
- Protocol plug-ins: register/unregister handlers by `LspProtocolMethod`.
- UI plug-ins: register/unregister `LspUiContribution` by `LspUiPlacement`.
- Editor plug-ins: bind any Sora `CodeEditor` to a managed LSP session through `LspEditorCoordinator`.
