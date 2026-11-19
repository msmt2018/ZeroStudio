# Editor Core Bootstrap and Refactor Roadmap

## Phase 0 — module bootstrap and source audit

- [x] Establish the five approved empty Android library modules: editor-core, editor-language, editor-lsp, editor-analysis and editor-dual.
- [x] Document module responsibilities and ownership boundaries.
- [ ] Audit legacy `:editor:impl`, `:editor:api`, lexers, Tree-sitter/JNI/native, existing LSP modules and their consumers.
- [ ] Map Sora classes/APIs, ZeroStudio custom changes and current dependency relationships to the five target modules.
- [ ] Record a build baseline before migrating implementation code.

**Exit criteria:** source-to-target migration map and dependency map are documented. No destructive source deletion.

## Phase 1 — editor-core foundation

- [ ] Audit Sora's document, editing, input, layout and rendering internals.
- [ ] Define editor View API, state, events, document/change/version contracts and lifecycle.
- [ ] Refactor text editing, transactions, undo/redo, cursor and selection incrementally.
- [ ] Refactor input/IME, layout, viewport, rendering and editor interaction.
- [ ] Establish deterministic tests for editing correctness, input, layout, rendering and large documents.

**Exit criteria:** editor-core exposes a usable integrated editor View and passes focused tests.

## Phase 2 — editor-language

- [ ] Audit existing Tree-sitter Java/Kotlin/JNI/native code and grammar/query assets.
- [ ] Refactor parser/tree/query lifecycle and native resource ownership.
- [ ] Implement incremental parse scheduling, cancellation and revision-aware result handling.
- [ ] Connect syntax-highlight data to editor-core through stable result/decorations contracts.
- [ ] Validate representative languages and large-file memory behavior.

**Exit criteria:** representative source files parse and highlight correctly without stale results corrupting current editor state.

## Phase 3 — editor-lsp

- [ ] Audit existing LSP APIs, models, transports, clients, servers and language integrations.
- [ ] Refactor JSON-RPC, server lifecycle, capabilities, cancellation and error handling.
- [ ] Establish version-aware document synchronization.
- [ ] Integrate diagnostics, then completion/hover/navigation/formatting/code actions/inlay features.
- [ ] Validate at least one representative language server end-to-end.

**Exit criteria:** server lifecycle and document synchronization are reliable; diagnostics and interactive language features work through editor contracts.

## Phase 4 — editor-analysis

- [ ] Define analysis provider and unified diagnostic/result contracts.
- [ ] Integrate Tree-sitter and LSP diagnostic sources without duplicating their implementations.
- [ ] Implement task scheduling, cancellation, severity aggregation and stale-result filtering.
- [ ] Validate result mapping against document revisions and editor positions.

**Exit criteria:** multiple analysis sources can publish consistent, version-correct results.

## Phase 5 — editor-dual

- [ ] Define dual-editor pair and lifecycle APIs over two editor-core instances.
- [ ] Implement split orientation, pane sizing, active-editor and focus coordination.
- [ ] Add optional viewport/scroll/cursor synchronization policies.
- [ ] Define external comparison-data mapping hooks; do not implement Git or Diff algorithms here.
- [ ] Validate independent documents and comparison workflows.

**Exit criteria:** two independent editor instances can be hosted and coordinated without duplicating editor-core implementation.

## Phase 6 — ZeroStudio integration and legacy removal

- [ ] Integrate the editor View into Activity-owned IDE layouts.
- [ ] Connect project/file open-save flows, language parsing, LSP and analysis results.
- [ ] Integrate dual-editor workflows where required by the IDE.
- [ ] Migrate all consumers, resources, dependencies and tests from legacy editor implementation.
- [ ] Remove `:editor:impl` only after no consumers or package references remain and clean builds/tests pass.

## Work discipline

Keep bootstrap modules empty until their contracts and source ownership are ready. Do not add speculative dependencies or placeholder implementation classes. Work bottom-up in small, buildable slices; preserve useful Sora, Tree-sitter, JNI/native and LSP infrastructure after auditing it rather than blindly rewriting or deleting it.
