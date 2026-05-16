# BSP Migration Plan

This repository has started migrating the Gradle tooling transport layer from custom LSP4J JSON-RPC contracts to the industry-standard Build Server Protocol (BSP).

## Implemented in this change

- Added BSP dependency (`ch.epfl.scala:bsp4j`) to:
  - `tooling/api`
  - `tooling/model`
  - `core/projects`
  - `core/app`
- Added shared BSP protocol constants at `tooling/api/.../BspProtocol.kt`.
- Added BSP connection descriptor templates:
  - `.bsp/androidide.json`
  - `core/app/src/main/assets/bsp/androidide.json`
- Added `BspConnectionSettings` model in `core/projects` for server bootstrap wiring.

## Next steps

1. Replace `IToolingApiServer` and `IToolingApiClient` RPC contracts with BSP endpoints (`build/initialize`, `workspace/buildTargets`, `buildTarget/compile`, `buildTarget/test`).
2. Replace launcher wiring in `ToolingApiLauncher` with BSP endpoint handshake + lifecycle.
3. Add Gradle task (`gradlew bsp`) that launches the BSP server process.
4. Keep a temporary compatibility bridge until all `tooling/model` interfaces are switched.

## Upstream BSP sources

Attempted to clone upstream BSP spec repository:

```bash
git clone https://github.com/build-server-protocol/build-server-protocol third_party/build-server-protocol
```

The current environment returned HTTP 403 for GitHub connectivity, so vendoring upstream sources must be retried in a network-enabled runner.
