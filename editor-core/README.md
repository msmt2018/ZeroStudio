# ZeroStudio Editor Core

ZeroStudio is rebuilding its editor architecture around the Sora Editor core as a foundation. This is a **refactor and re-development of Sora-based editor internals**, not a from-scratch text engine and not a UI-only replacement.

The long-term goal is to own the editor APIs, internal behavior, integration and maintenance, while removing the legacy `:editor:impl` module after the new implementation is migrated and verified.

## Planned structure

```
editor-core/
├── README.md
├── ARCHITECTURE.md
├── MODULES.md
├── ROADMAP.md
├── editor-engine/       # Sora-based core refactor: text, edits, input, layout, rendering, history
├── editor-ui/           # Compose editor UI and IDE-facing integration
├── editor-language/      # Tree-sitter framework, parsing, language registration and highlighting
├── editor-lsp/          # LSP framework, client/server lifecycle, JSON-RPC and language-server integration
└── editor-extensions/   # Internal extension points and optional editor capabilities
```

Tests live alongside their owners in `src/test` and `src/androidTest`; there is no separate testing Gradle module in this plan.

## Module responsibilities

- **editor-engine:** Sora-based editor core and core behavior. Consolidates text buffer, edits/transactions, undo-redo, cursor/selection, input/IME, layout, viewport, rendering, decorations, caches and performance/memory work.
- **editor-ui:** Jetpack Compose surfaces, editor composition, theming, toolbars/context actions, focus/lifecycle, and IDE page integration. It delegates editing to editor-engine.
- **editor-language:** Tree-sitter framework/JNI integration, parser/query lifecycle, incremental parsing, grammar and language registration, syntax-highlight data adapters.
- **editor-lsp:** LSP framework/API changes, JSON-RPC, client/server lifecycle, capabilities, document synchronization, requests/notifications/cancellation, diagnostics and language-server integration.
- **editor-extensions:** internal feature/command/key-binding/decorations registration and lifecycle. Start with controlled internal extension points; do not build a plugin marketplace or dynamic sandbox prematurely.

## Architecture rules

1. Keep modules coarse-grained; use packages and internal subcomponents for cohesive engine concerns.
2. Do not make editor-engine depend on Compose or IDE UI.
3. Tree-sitter and LSP remain separate services. Neither is embedded as a hard dependency of the text-editing core.
4. Language analysis and LSP results must be adapted into editor-facing contracts rather than coupled to private engine internals.
5. Reuse and refactor existing parser, JNI, grammar and language-server infrastructure only after auditing ownership and dependencies. Avoid rewriting useful infrastructure without evidence.
6. The legacy `:editor:impl` is transitional. The new engine must progressively replace its responsibilities; the legacy module is removed only after references, functionality, tests and build configuration are migrated.
7. Work bottom-up: core contracts and engine behavior first, then language/LSP integration, then UI and IDE integration.
8. Tests belong to the module/component they validate, not a standalone production module.

## Documents

- [Architecture](ARCHITECTURE.md): system boundaries and data flow.
- [Modules](MODULES.md): concrete module responsibilities and ownership.
- [Roadmap](ROADMAP.md): migration order and exit criteria.

## Status

The initial 11-module split has been superseded by this simpler 5-module design. The previous scaffold is being reconciled with these documents; modules are not yet considered implemented or build-verified.
