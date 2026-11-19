# ZeroStudio Editor Core

This directory is the independent replacement editor implementation. It must not depend on Sora Editor or its internal APIs.

## Modules

- editor-api: stable public contracts, state, configuration, commands
- editor-text: text buffer, indexing, edits and transactions
- editor-undo: history, undo/redo and transaction grouping
- editor-layout: visual lines, measurement and coordinate mapping
- editor-input: keyboard, IME, selection, cursor and gestures
- editor-render: text, line numbers, selections, cursor and decorations
- editor-compose: Compose host and UI
- editor-language: language service and syntax adapters
- editor-diagnostics: diagnostics and markers
- editor-extensions: extensibility and feature registration
- editor-testing: core and performance tests

Development is bottom-up: API -> text -> undo/layout -> input/render -> Compose -> language/diagnostics/extensions -> integration.

Existing `editor/` modules are retained temporarily because they contain language and Tree-sitter infrastructure in addition to the Sora-based editor. They are not dependencies of this new core. Migration and selective removal must happen after references are audited.
