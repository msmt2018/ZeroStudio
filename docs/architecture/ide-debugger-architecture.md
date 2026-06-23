# ZeroStudio IDE Debugger Architecture

## Overview

The debugger system consists of three main layers:
1. **IDE Application Layer** (core/app) - UI components and user interactions
2. **Debugger Engine Layer** (ide-debugger) - JDWP protocol implementation and debugging logic
3. **Target App Layer** (ide-log-plugin) - JDWP server running inside the debugged application

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         IDE Application (core/app)                           │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐  │
│  │ BreakpointSidebar│  │BreakpointList   │  │ BreakpointConditionDialog  │  │
│  │   (view)         │──│   Fragment      │──│   (condition/hitCount)     │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘  │
│           │                    │                         │                   │
│           ▼                    ▼                         ▼                   │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                     BreakpointListAdapter                            │    │
│  │              (ListAdapter + DiffUtil + state colors)                 │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐  │
│  │ VariablesAdapter│  │ WatchesAdapter  │  │   CallStackAdapter          │  │
│  │   (locals)      │  │  (expressions)  │  │      (frames)               │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘  │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                     DebuggerAccessibility                            │    │
│  │           (TalkBack contentDescription + custom actions)             │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Debugger API
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      ide-debugger Module                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                          API Layer                                     │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │  │
│  │  │  Debugger   │──│ Breakpoint  │──│ Breakpoint  │──│ DebugSession│   │  │
│  │  │  (facade)   │  │   Store     │  │   (model)   │  │  (state)    │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘   │  │
│  │        │                │                                     │       │  │
│  │        │                │                                     │       │  │
│  │        ▼                ▼                                     ▼       │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │  │
│  │  │  EvalEngine │──│ SourceLoca- │──│  Listener   │──│ SuspendInfo │   │  │
│  │  │  (eval)     │  │    tor      │  │  (callback) │  │  (event)    │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                          JDWP Layer                                    │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │  │
│  │  │ JdwpClient  │──│ JdwpPacket  │──│ JdwpPacket  │──│ JdwpPacket  │   │  │
│  │  │  (socket)   │  │   Codec     │  │   Reader    │  │   (data)    │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘   │  │
│  │        │                                                              │  │
│  │        ▼                                                              │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │  │
│  │  │  Heartbeat  │──│ CommandSet  │──│ EventKind   │──│  ModKind    │   │  │
│  │  │  (keepalive)│  │  /Codes     │  │  /Suspend   │  │  (modifiers)│   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                          Event Layer                                   │  │
│  │  ┌─────────────┐  ┌─────────────┐                                     │  │
│  │  │DebugEventBus│──│ DebugEvents │                                     │  │
│  │  │  (publish)  │  │  (types)    │                                     │  │
│  │  └─────────────┘  └─────────────┘                                     │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ JDWP Protocol (TCP socket)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      Target Application                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                      ide-log-plugin                                    │  │
│  │  ┌─────────────┐  ┌─────────────┐                                     │  │
│  │  │ JDWP Server │──│ Logwire     │                                     │  │
│  │  │ (in-process)│  │ Transport   │                                     │  │
│  │  └─────────────┘  └─────────────┘                                     │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                      utilities/logwire                                 │  │
│  │  ┌─────────────┐                                                      │  │
│  │  │  Protocol   │  LOGW magic + message types                          │  │
│  │  │  (shared)   │                                                      │  │
│  │  └─────────────┘                                                      │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Component Descriptions

### IDE Application Layer

| Component | Description |
|-----------|-------------|
| `BreakpointSidebar` | Visual gutter indicator for breakpoints in code editor |
| `BreakpointListFragment` | Fragment displaying list of all breakpoints |
| `BreakpointConditionDialog` | Dialog for setting condition, logpoint, hit count |
| `BreakpointListAdapter` | ListAdapter with DiffUtil for efficient updates |
| `VariablesAdapter` | Displays local variables in current frame |
| `WatchesAdapter` | Displays user-defined watch expressions |
| `CallStackAdapter` | Displays call stack frames |
| `DebuggerAccessibility` | TalkBack support with contentDescription |

### API Layer (ide-debugger)

| Component | Description |
|-----------|-------------|
| `Debugger` | Top-level facade, thread-safe API, lifecycle management |
| `BreakpointStore` | CRUD for breakpoints, persistence via SharedPreferences |
| `Breakpoint` | Model: sourceFile, line, condition, logMessage, hitCountMode |
| `DebugSession` | State machine: IDLE → CONNECTED → RUNNING → STEPPING → SUSPENDED |
| `EvalEngine` | Expression evaluator, JDWP method invocation |
| `SourceLocator` | Source file → JDWP location mapping, pending breakpoint retry |
| `Listener` | Callback interface for UI notifications |

### JDWP Layer

| Component | Description |
|-----------|-------------|
| `JdwpClient` | TCP socket connection, handshake, packet I/O |
| `JdwpPacket` | JDWP packet structure (length + id + flags + data) |
| `JdwpPacketCodec` | Encoder/decoder for JDWP packets |
| `JdwpPacketReader` | Async reader thread for incoming packets |
| `DebugSessionHeartbeat` | Periodic keepalive to detect disconnection |
| `CommandSet` / `CommandCodes` | JDWP command identifiers (VirtualMachine, ReferenceType, etc.) |
| `EventKind` / `ModKind` | JDWP event types and modifier kinds |

### Event Layer

| Component | Description |
|-----------|-------------|
| `DebugEventBus` | Publish/subscribe for debug events |
| `DebugEvents` | Event types: suspend, resume, breakpointHit, classPrepare |

## JDWP Command Flow

```
User adds breakpoint
        │
        ▼
Debugger.addBreakpoint(file, line)
        │
        ▼
SourceLocator.installBreakpoint(bp)
        │
        ├──────────────────────────────────────┐
        │                                      │
        ▼                                      ▼
ClassesBySignature(classSig)          [pending list if class not loaded]
        │                                      │
        ▼                                      │
SourceFile(classId)                    │
        │                                      │
        ▼                                      │
Methods(classId)                       │
        │                                      │
        ▼                                      │
LineTable(classId, methodId)           │
        │                                      │
        ▼                                      │
EventRequest.Set(BREAKPOINT, LOCATION) │
        │                                      │
        ▼                                      │
bp.state = VERIFIED                    │
        │                                      │
        │                                      │
        │        CLASS_PREPARE event           │
        │              │                       │
        │              ▼                       │
        │    SourceLocator.retryPending()      │
        │              │                       │
        │              ▼                       │
        │    installBreakpoint(bp)             │
        │              │                       │
        │              ▼                       │
        │    bp.state = VERIFIED               │
        │                                      │
        └──────────────────────────────────────┘
```

## Hit Count Modifier Flow

```
Breakpoint with hitCountMode=EQUAL, hitCount=5
        │
        ▼
SourceLocator.installBreakpointAt()
        │
        ▼
EventRequest.Set with modifiers:
  - COUNT modifier (kind=1, count=5)
  - LOCATION modifier (kind=7)
        │
        ▼
JDWP server suspends only on 5th hit
        │
        ▼
Debugger.onSuspend() receives breakpoint event
        │
        ▼
UI shows breakpoint hit with hitCountReceived=5
```

## Expression Evaluation Flow

```
User enters expression "x + y"
        │
        ▼
EvalEngine.evaluate(threadId, frameId, "x + y")
        │
        ▼
Parser.parse() → AST
        │
        ▼
Resolve identifiers:
  - SourceLocator.fetchLocal(threadId, frameId, "x")
  - SourceLocator.fetchLocal(threadId, frameId, "y")
        │
        ▼
JDWP method invocation:
  - CreateString("x + y")  [if needed]
  - ObjectReference.InvokeMethod(...)
        │
        ▼
EvalResult with value or error
```

## Connection Lifecycle

```
Debugger.connect(host, port)
        │
        ▼
JdwpClient.connect()
        │
        ├──────────────────────────────────────┐
        │                                      │
        ▼                                      ▼
JDWP handshake ("JDWP-Handshake")       [timeout → IOException]
        │                                      │
        ▼                                      │
DebugSession.setState(CONNECTED)        │
        │                                      │
        ▼                                      │
enableClassPrepare()                    │
enableBreakpointEvents()                │
enableSingleStepEvents()                │
        │                                      │
        ▼                                      │
Heartbeat.start()                       │
        │                                      │
        ▼                                      │
waitForVmStart()                        │
        │                                      │
        ▼                                      │
session.setState(RUNNING)               │
        │                                      │
        │                                      │
        │        [disconnection detected]      │
        │              │                       │
        │              ▼                       │
        │    Heartbeat.onTimeout()             │
        │              │                       │
        │              ▼                       │
        │    session.setState(IDLE)            │
        │    notifyConnectionChanged(false)    │
        │                                      │
        └──────────────────────────────────────┘
```

## Related Files

- PlantUML source: [ide-debugger-architecture.puml](./ide-debugger-architecture.puml)
- Debugger audit: [DEBUGGER_AUDIT.md](./DEBUGGER_AUDIT.md)