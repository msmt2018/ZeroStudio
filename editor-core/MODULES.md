# Editor Core Modules and Responsibilities

The plan intentionally uses five coarse modules. Text buffer, undo, input, layout and rendering are internal subsystems of editor-engine, not separate Gradle modules. Unit/instrumentation tests are colocated with each module.

## 1. editor-engine — Sora-based editor core

**Purpose:** refactor Sora's core into a ZeroStudio-owned editing engine and progressively retire the legacy `:editor:impl`.

**Owns**
- Sora core adaptation/refactoring and editor API/internal boundaries.
- Document/buffer representation, text indexing and line mapping.
- Edit operations, transactions and change/revision tracking.
- Undo/redo and history grouping.
- Selection, cursor/caret and navigation.
- Keyboard input, IME/composing, touch/gesture edit behavior.
- Layout, visual lines, wrapping, measurement, viewport and coordinate mapping.
- Rendering, line numbers, selection/caret, decorations and drawing caches.
- Engine state/lifecycle, internal events, performance and memory protections.

**Does not own:** Compose UI screens, Tree-sitter grammar/framework, LSP protocol/server lifecycle, IDE project/file management.

**Tests:** text correctness, offset mapping, transactions, undo/redo, IME/input, layout/viewport, rendering behavior, performance and large-file tests.

## 2. editor-ui — Compose editor surface and IDE integration

**Purpose:** implement the user-facing editor surface and IDE-specific composition around editor-engine.

**Owns**
- Compose editor component and layout.
- Theme, typography, editor chrome and UI state.
- Focus, lifecycle, scrolling coordination and viewport presentation.
- Toolbars, context menus and editor actions UI.
- File/editor page integration and IDE navigation.

**Does not own:** text buffer, edit transactions, undo stack, parser internals or LSP transport/protocol.

**Tests:** Compose behavior, focus/lifecycle, UI actions and integration tests.

## 3. editor-language — Tree-sitter and language support

**Purpose:** own syntax analysis framework and language-specific parsing support.

**Owns**
- Tree-sitter framework refactoring and integration.
- Parser, tree/node/query lifecycle and incremental parsing.
- JNI/native bridge boundaries and native resource management.
- Grammar/query assets and language registration.
- Parse scheduling, cancellation and stale-result handling.
- Syntax highlighting capture-to-editor style conversion.
- Language-specific parser setup and large-file degradation strategies.

**Does not own:** editing transactions, editor UI, LSP transport.

**Tests:** grammar/query/parser correctness, incremental parsing, native/JNI lifecycle, language registration and memory/performance tests.

## 4. editor-lsp — LSP framework and language server integration

**Purpose:** refactor and centralize ZeroStudio's LSP infrastructure and language-server integrations.

**Owns**
- LSP API/framework changes and protocol models.
- JSON-RPC transport and request lifecycle.
- Language server client/server lifecycle and capability negotiation.
- Workspace/document lifecycle and text synchronization.
- Request/notification routing, cancellation and error handling.
- Diagnostics and adapters for completion, hover, definition, references, formatting, code actions and inlay hints.
- Language-server integration/configuration for supported languages.

**Does not own:** editor text storage, Compose UI or Tree-sitter parsing.

**Tests:** protocol/model tests, transport, lifecycle, synchronization, capability negotiation, cancellation, feature adapters and integration tests.

## 5. editor-extensions — internal extensibility

**Purpose:** provide controlled extension points for optional editor features.

**Owns**
- Feature/command registration and lifecycle.
- Key-binding and editor action extension contracts.
- Decoration and optional UI contribution contracts.
- Capability discovery and enable/disable behavior.
- Internal extension coordination.

**Initial scope:** statically registered/internal extensions only. Defer dynamic class loading, sandboxing and marketplace infrastructure.

## Cross-module rules

- Keep modules coarse. Extract a new module only when a real dependency/runtime/build boundary appears.
- Do not make the engine depend on Compose or IDE UI.
- Tree-sitter and LSP are service/integration layers, not text-engine dependencies.
- Tests stay with the module they exercise.
- Prefer explicit public contracts; avoid exposing private engine implementation types across modules.
