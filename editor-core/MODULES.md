# Editor Core Modules and Responsibilities

The initial structure contains five intentionally empty Android library modules. These are the only modules approved for the current bootstrap. Internal editor subsystems are packages inside their owning module, not separate Gradle projects.

## 1. editor-core — integrated editor control

**Purpose:** provide the complete ZeroStudio-owned code editor Android View and its editing engine, refactoring Sora-based capabilities incrementally.

**Owns**
- Editor View, controller-facing API, configuration, state, events and lifecycle.
- Text storage, indexing, line mapping, edit operations and transactions.
- Undo/redo and edit-history grouping.
- Cursor, caret, selection and navigation within the document.
- Keyboard, IME/composing and input handling.
- Text measurement, line layout, wrapping, viewport and coordinate mapping.
- Rendering, scrolling, gutter, line numbers, folding and decorations.
- Clipboard, search/replace, completion interaction, snippets and formatting-result application.
- Performance, caching, memory safeguards and large-document editing behavior.

**Does not own:** Activity/IDE page UI, project file management, Git operations, Tree-sitter implementation or LSP protocol transport.

## 2. editor-language — language parsing framework

**Purpose:** own source-language parsing and syntax understanding.

**Owns**
- Language registration and language configuration.
- Tree-sitter framework integration/refactoring.
- Parser, syntax tree, node and query lifecycle.
- Grammar/query resources and capture processing.
- JNI/native bridge boundaries and native resource lifecycle.
- Incremental parsing, scheduling, cancellation and stale-result handling.
- Syntax-highlight data generation and style classification.
- Parser caches, memory budgets and large-file degradation.

**Does not own:** text editing transactions, editor View, LSP transport or IDE UI.

## 3. editor-lsp — language server framework

**Purpose:** own protocol communication and language-server integration.

**Owns**
- LSP protocol models and framework APIs.
- JSON-RPC messages, transport and request lifecycle.
- Language-server client/server lifecycle, registry and configuration.
- Capability negotiation and workspace management.
- Document open/change/save/close synchronization.
- Requests, notifications, cancellation, timeout and error handling.
- Adapters for diagnostics, completion, hover, definition, references, formatting, code actions, rename, signature help and inlay hints.

**Does not own:** editor rendering, text storage implementation, Tree-sitter parsing or Activity UI.

## 4. editor-analysis — code analysis framework

**Purpose:** unify analysis execution and analysis results from multiple providers.

**Owns**
- Analysis provider/rule contracts and configuration.
- Analysis task scheduling, prioritization and cancellation.
- Unified diagnostic/result models and severity levels.
- Aggregation and filtering of Tree-sitter, local analyzer and LSP-originated results.
- Document range/position mapping and revision/version association.
- Stale-result rejection, caching and result lifecycle.

**Does not own:** parser implementation, LSP transport, editor diagnostic UI or project-wide build orchestration.

## 5. editor-dual — dual-editor API framework

**Purpose:** compose and coordinate two independent editor-core instances for side-by-side or stacked editing/comparison workflows.

**Owns**
- Two editor instance creation/attachment/release and pair lifecycle.
- Independent document binding for each editor.
- Split orientation, pane sizing and ratio state/API.
- Active editor, focus transfer and command routing.
- Optional scroll/viewport/cursor synchronization policies.
- Comparison-data anchors and mapping hooks supplied by an external source.

**Does not own:** Diff algorithms, Git repository/status/diff operations, file-version storage or IDE page UI. Git integration supplies comparison data; this module coordinates the two editor instances that display it.

## Dependency and scope rules

- No module is given speculative dependencies during bootstrap; each starts as an empty Android library.
- `editor-dual` will depend on the public API of `editor-core` when its implementation begins.
- Language and LSP services integrate with the editor through stable document/change/result contracts; avoid direct dependencies on private View internals.
- Analysis consumes provider results and exposes normalized analysis data; it must not duplicate parser or protocol implementations.
- Do not add separate `editor-ui`, `editor-text`, `editor-document`, `editor-completion`, `editor-format`, `editor-search`, `editor-snippets` or `editor-diff` modules.
- Tests will be colocated with their owning modules.
