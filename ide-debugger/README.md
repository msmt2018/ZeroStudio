# ide-debugger

ZeroStudio's pure-Kotlin JDWP debugger engine. Runs on the IDE side and talks JDWP to the host application's JDWP server (which is started by the ide-log-plugin AAR injected into the debug variant of every project built with ZeroStudio).

## Features

- **JDWP Protocol Implementation**: Full JDWP 1.6 protocol support
- **Breakpoint Management**: Source-level breakpoints with conditions, logpoints, hit counts
- **Expression Evaluation**: Evaluate expressions in suspended frames
- **Call Stack Inspection**: Navigate stack frames and inspect locals
- **Step Operations**: Step into, over, out
- **Event Handling**: Breakpoint hit, class prepare, exception, single step
- **Connection Management**: Auto-reconnect, heartbeat keepalive
- **Accessibility**: TalkBack support for debugger UI

## Architecture

See [Architecture Documentation](../../docs/architecture/ide-debugger-architecture.md) for detailed component diagrams.

## JDWP Protocol

### Packet Structure

```
┌─────────────┬─────────────┬─────────────┬─────────────┐
│  length(4)  │    id(4)    │  flags(1)   │   data(N)   │
└─────────────┴─────────────┴─────────────┴─────────────┘

Command packet:
  data = [commandSet(1)] + [command(1)] + [payload]

Reply packet:
  data = [errorCode(2)] + [payload]
```

### Command Sets Used

| Command Set | ID | Commands Used |
|-------------|----|---------------|
| VirtualMachine | 1 | Version, ClassesBySignature, Resume, Suspend, CreateString |
| ReferenceType | 2 | Signature, Methods, SourceFile, Fields |
| Method | 6 | LineTable, VariableTable |
| ObjectReference | 9 | ReferenceType, GetValues, SetValues, InvokeMethod |
| ThreadReference | 11 | Frames, Suspend, Resume, ForceEarlyReturn |
| ArrayReference | 13 | Length, GetValues |
| StringReference | 10 | Value |
| StackFrame | 16 | GetValues, SetValues, ThisObject, PopFrames |
| EventRequest | 15 | Set, Clear, ClearAllBreakpoints |

### Event Kinds

| Event Kind | ID | Description |
|------------|----|-------------|
| VM_START | 0x40 | VM initialized |
| VM_DEATH | 0x41 | VM terminated |
| THREAD_START | 0x42 | Thread started |
| THREAD_DEATH | 0x43 | Thread terminated |
| CLASS_PREPARE | 0x44 | Class loaded |
| BREAKPOINT | 0x46 | Breakpoint hit |
| EXCEPTION | 0x47 | Exception thrown |
| SINGLE_STEP | 0x4A | Step completed |

### Modifier Kinds (EventRequest.Set)

| ModKind | ID | Description |
|---------|----|-------------|
| COUNT | 1 | Hit count threshold |
| CONDITIONAL | 2 | Conditional expression (not used) |
| THREAD_ONLY | 3 | Restrict to specific thread |
| CLASS_ONLY | 4 | Restrict to specific class |
| LOCATION | 7 | Location-specific (breakpoint) |
| STEP | 10 | Step modifier |

## API Usage

### Basic Setup

```kotlin
// Create debugger instance
val debugger = Debugger()

// Add listener for events
debugger.addListener(object : Debugger.Listener {
    override fun onSuspend(info: SuspendInfo) {
        // Handle breakpoint hit / step
        val frames = debugger.getStackFrames(info.threadId, 0, 10)
        // Display frames in UI
    }
    override fun onResumed() {
        // VM resumed
    }
    override fun onConnectionChanged(connected: Boolean) {
        // Connection status changed
    }
})

// Connect to JDWP server
debugger.connect("127.0.0.1", 5005)
debugger.waitForVmStart()
```

### Adding Breakpoints

```kotlin
// Simple breakpoint
val bp = debugger.addBreakpoint("MainActivity.java", 42)

// Conditional breakpoint
val bp = debugger.addBreakpoint("MainActivity.java", 42, "x > 10")

// Logpoint
val bp = debugger.addBreakpoint("MainActivity.java", 42, null, "\"x=\" + x")

// Hit count breakpoint (suspend on 5th hit)
val bp = debugger.addBreakpoint("MainActivity.java", 42, null, null,
    Breakpoint.HitCountMode.EQUAL, 5)
```

### Expression Evaluation

```kotlin
// Evaluate expression in current frame
val result = debugger.eval().evaluate(threadId, frameId, "x + y")
if (result.isSuccess) {
    println("Result: ${result.value}")
} else {
    println("Error: ${result.error}")
}
```

### Stack Frame Inspection

```kotlin
// Get stack frames
val frames = debugger.getStackFrames(threadId, 0, 10)
for (frame in frames) {
    println("${frame.methodName} at ${frame.sourceFile}:${frame.line}")
    for (var in frame.variables) {
        println("  ${var.name}: ${var.value}")
    }
}
```

### Step Operations

```kotlin
debugger.stepInto(threadId)   // Step into method
debugger.stepOver(threadId)   // Step over line
debugger.stepOut(threadId)    // Step out of method
```

## Testing

The module has comprehensive unit tests covering:

- `JdwpPacketCodec` - Packet encoding/decoding
- `BreakpointStore` - Breakpoint CRUD operations
- `DebugSession` - State machine transitions
- `SourceLocator` - Breakpoint installation paths
- `EvalEngine` - Expression evaluation

Run tests:
```bash
gradle :ide-debugger:testDebugUnitTest
```

## Dependencies

- `utilities/logwire` - Shared transport protocol
- `androidx.annotation` - Nullability annotations
- `kotlin.stdlib` - Kotlin standard library
- `kotlinx.coroutines` - Async operations

## Related Modules

- `ide-log-plugin` - JDWP server injected into target app
- `core/app` - IDE UI layer using this module
- `utilities/logwire` - Shared protocol definitions

## License

GPL-3.0-or-later (same as AndroidIDE)