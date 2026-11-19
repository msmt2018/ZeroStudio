# Editor Core Refactor Roadmap

## Phase 0 — Architecture and source audit

- [ ] Replace the superseded 11-module scaffold with the 5-module plan documented here.
- [ ] Inventory legacy `:editor:impl`, `:editor/api`, lexers, Tree-sitter/JNI, LSP and UI consumers.
- [ ] Map each Sora class/API to current callers and identify custom ZeroStudio patches.
- [ ] Identify Tree-sitter code/assets/native build pieces that should be retained or refactored.
- [ ] Inventory LSP framework/server/client implementation and its consumers.
- [ ] Define migration ownership and dependency boundaries; document any exceptions.

**Exit criteria:** a source-to-target migration map, dependency map and build baseline exist. No destructive source deletion.

## Phase 1 — editor-engine foundation

- [ ] Establish engine-owned public contracts and internal package structure.
- [ ] Refactor Sora core incrementally into ZeroStudio-owned boundaries.
- [ ] Document document/change/selection/viewport contracts.
- [ ] Establish tests for editing operations, offsets, selection and history.
- [ ] Verify the module builds independently within the repository.

**Exit criteria:** basic document editing, cursor/selection, edit transactions and undo/redo have deterministic tests.

## Phase 2 — Tree-sitter framework (editor-language)

- [ ] Audit current Tree-sitter Java/Kotlin/JNI/native modules and grammar assets.
- [ ] Refactor framework lifecycle/resource ownership and public integration contracts.
- [ ] Validate parser/query/capture behavior and incremental parse handling.
- [ ] Define parse result/version handling so stale analysis cannot overwrite newer document state.
- [ ] Integrate syntax results with editor-engine decorations/styles without engine-to-parser dependencies.
- [ ] Test native resource cleanup and large-document degradation.

**Exit criteria:** at least one representative language parses and highlights using the new contracts; existing useful grammar/native infrastructure remains working.

## Phase 3 — LSP framework (editor-lsp)

- [ ] Audit existing LSP API/model, transports, clients, servers and language integrations.
- [ ] Refactor JSON-RPC lifecycle, capability negotiation, cancellation and error handling.
- [ ] Establish document synchronization/change version contracts.
- [ ] Implement diagnostics integration first.
- [ ] Add feature adapters in slices: completion/hover, navigation, formatting/code actions/inlay hints.
- [ ] Validate a representative server end-to-end.

**Exit criteria:** server starts/stops reliably, documents synchronize correctly, diagnostics and at least one interactive feature work end-to-end.

## Phase 4 — editor-ui and IDE integration

- [ ] Build Compose editor surface using editor-engine contracts.
- [ ] Implement theme, focus, lifecycle, scrolling and editor actions.
- [ ] Integrate Tree-sitter and LSP results without coupling the UI to their internals.
- [ ] Integrate project/file open-save workflows.
- [ ] Validate editor behavior on supported Android API levels and device configurations.

**Exit criteria:** project files can be opened, edited, saved, highlighted, diagnosed and navigated with the new editor path.

## Phase 5 — extensions and stabilization

- [ ] Define internal extension registration contracts.
- [ ] Migrate optional editor capabilities to extensions where useful.
- [ ] Validate extension lifecycle and conflict handling.
- [ ] Run unit, instrumentation, integration and performance tests.
- [ ] Review memory usage, responsiveness and large-file behavior.

## Phase 6 — remove legacy editor implementation

Removal of `:editor:impl` is allowed only after:
- [ ] All direct and transitive consumers are migrated.
- [ ] Required editor behavior is covered by the new engine.
- [ ] Compose/UI and IDE integrations no longer import legacy implementation types.
- [ ] Tree-sitter, LSP and native components do not rely on old implementation internals.
- [ ] Build files, dependency substitutions, resources and tests are migrated.
- [ ] Clean build and targeted tests pass.
- [ ] No remaining references to legacy implementation packages/modules exist.

Then remove the legacy module and obsolete dependencies, rerun full validation, and record the migration commit.

## Work discipline

Work in small, reviewable changes. Do not jump directly to Compose controls while engine contracts and underlying behavior remain unsettled. Do not rewrite Tree-sitter or LSP blindly: inspect current implementations and modify the framework at its real bottlenecks and integration boundaries.
