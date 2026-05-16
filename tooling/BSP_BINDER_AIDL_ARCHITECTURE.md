# BSP + AIDL/Binder + Event Subscription Architecture

## Goals

- Keep **BSP** as build semantic protocol (initialize/sync/compile/test/cancel/shutdown).
- Replace single JSON-RPC hotspot with **Binder control-plane + subscribed event channel**.
- Support large project payloads (targets, metadata, caches, sources) through **tokenized binary-friendly transport**.

## Layered transport

1. **App/UI process**
   - Binds `IBspSessionService`.
   - Subscribes `IBspSessionCallback` for async events.
2. **BSP session service (Binder service)**
   - Executes BSP calls, manages task lifecycle.
   - Emits throttled build/progress/log events to callbacks.
3. **Build daemon / tooling process**
   - Runs BSP server endpoints.

## Why this scales better

- Binder avoids single text-only stream bottleneck for control-plane calls.
- Callback subscription decouples producer and UI consumer pace.
- Large payload strategy: Binder only carries compact JSON envelopes with references
  (`content://`, file id, cache key), while bulk bytes stay off-channel.
- Supports future protobuf framing for data-plane blobs without changing BSP semantics.

## Performance policy (implemented baseline)

- Event throttling in app build service output path.
- Cached `workspace/buildTargets` reuse.
- Single-thread execution lane for build requests to reduce contention.
- Unified cancel/shutdown cleanup.

## Next implementation phases

1. Replace stub responses in `BspSessionBinderService` with direct `BspBuildService` wiring.
2. Add chunked/protobuf payload codec for very large metadata snapshots.
3. Introduce multi-queue priorities: UI-critical, progress, bulk-index metadata.
4. Add metrics: queue depth, event lag, payload bytes, GC pressure.
