# ZeroStudio IDE Debugger 架构

> Phase F2 — 整体架构图 (Mermaid)。覆盖 IDE ↔ 目标应用 (target app) 的
> 端到端流程、模块边界与数据流。

## 1. 顶层视图

```mermaid
flowchart LR
  subgraph IDE[ZeroStudio IDE - core/app]
    UI[Debugger UI Fragments]
    DSL[DebugSessionLauncher]
    AAM[AutoAttachManager]
    ARSW[AppReadySignalWatcher]
    RDS[RemoteDeviceScanner]
    DC[DebuggerController]
    SL[SourceLocator]
    SLC[SourceLocatorCache]
    JSP[JavaSourceParser]
    CFR[ClassFileReader]
    LWC[LogWireClient]
  end

  subgraph WIRE[Wire Protocols]
    JDWP[JDWP over TCP loopback]
    LOGW[logwire 0x01..0x05]
    ARSW_SIG[ZeroStudioDebug logcat tag]
    SHIZ[Shizuku user-mode APIs]
  end

  subgraph TARGET[Target App - user app + ide-log-plugin AAR]
    JVM[ART/JVM]
    DBP[DebuggerBootstrapProvider]
    LCS[LogCaptureService]
    LSS[LogSocketServer]
    JDWPS[JdwpServer]
    LB[LogBuffer ring]
  end

  UI --> DSL
  UI --> AAM
  UI --> RDS
  DSL --> DC
  DSL -->|adb| TARGET
  AAM --> DC
  ARSW -->|onAppReady| DSL
  DC --> JDWP
  SL --> JSP
  SL --> CFR
  SL --> SLC
  DC -->|JDWP frames| SL
  LWC -->|TCP| LSS
  DSL -. shizuku install .-> SHIZ
  ARSW -. logcat -s .-> ARSW_SIG
  JDWPS --> JVM
  DBP --> LCS
  DBP --> JDWPS
  LCS --> LSS
  LSS --> LB
  JDWPS --> LCS
```

## 2. 启动一次 debug session 的完整流程

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant UI as IDE UI
  participant DSL as DebugSessionLauncher
  participant AGP as AGP / build
  participant PI as PackageInstaller
  participant APP as Target App
  participant DBP as DebuggerBootstrapProvider
  participant JDWPS as JdwpServer
  participant LCS as LogCaptureService
  participant ARSW as AppReadySignalWatcher
  participant JPR as JdwpPortResolver
  participant DC as DebuggerController

  U->>UI: 点 "🪲 开始调试"
  UI->>DSL: start(actionData)
  DSL->>AGP: assembleDebug
  AGP-->>DSL: APK
  DSL->>PI: installApk(APK)
  PI-->>DSL: InstallationResultEvent
  DSL->>APP: launchApp(pkg)
  APP->>DBP: onCreate() (ContentProvider)
  DBP->>LCS: startLogcat(0)
  DBP->>JDWPS: startAndRegister(0)
  DBP->>APP: logcat "ZeroStudioDebug READY pkg=.. jdwp=.."
  ARSW->>APP: logcat -s ZeroStudioDebug:V
  ARSW-->>DSL: onAppReady(pkg, port)
  DSL->>JPR: awaitJdwpPort(pkg)
  JPR-->>DSL: 127.0.0.1:port
  DSL->>DC: connect(127.0.0.1, port)
  DC->>JDWPS: JDWP handshake (14 bytes)
  JDWPS-->>DC: 14 bytes echo
  DC->>JDWPS: VM.Version
  JDWPS-->>DC: reply
  DC-->>UI: Listener.onConnected(host, port)
```

## 3. Source location 解析路径

```mermaid
flowchart TB
  BP[Breakpoint sourceFile + line] --> CACHE{source cache hit?}
  CACHE -- hit --> PARSED[ParsedSource]
  CACHE -- miss --> JSP[JavaSourceParser.parse]
  JSP --> PARSED
  PARSED --> SIG[top-level JVM signature]
  SIG --> JDWP[JDWP ClassesBySignature]
  JDWP --> CLASS[refTypeId]
  CLASS --> LT[Method LineTable scan]
  LT --> EVT[EventRequest.Set BREAKPOINT]
  EVT --> REQ[requestId]
```

## 4. logwire 协议消息流

```mermaid
sequenceDiagram
  participant IDE as IDE (LogWireClient)
  participant TCP as TCP socket (loopback)
  participant TGT as Target App (LogSocketServer)

  Note over IDE,TGT: 5 字节 magic "LOGW" + 1 字节 type + 4 字节 length + payload
  TGT->>IDE: HANDSHAKE (version + pid + pkg + sessionId)
  loop every 5s
    TGT->>IDE: HEARTBEAT
  end
  loop on android.util.Log call
    TGT->>IDE: LOG_PAYLOAD (level + ts + thread + tag + msg + throwable)
  end
  TGT-->>IDE: BYE
```

## 5. 模块清单

| 模块 | 路径 | 角色 |
|------|------|------|
| `core/app` | `core/app/src/main/java/com/itsaky/androidide/debugger/` | IDE 端 — UI/launcher/attach |
| `ide-debugger` | `ide-debugger/src/main/java/com/zerostudio/debugger/` | IDE 端 — JDWP client + SourceLocator |
| `utilities/logwire` | `utilities/logwire/src/main/java/com/itsaky/androidide/logwire/` | 协议 — 帧编解码 + 客户端骨架 |
| `ide-log-plugin` | `ide-log-plugin/src/main/java/com/zerostudio/logplugin/` | 目标端 — JDWP server + log capture |
| `tooling/plugin` | `tooling/plugin/src/main/java/com/itsaky/androidide/gradle/` | 构建端 — AGP 注入 |

## 6. 线程模型

| 模块 | 线程 | 备注 |
|------|------|------|
| DebugSessionLauncher | bgExecutor | 不在 UI 线程跑 build/install/launch |
| AutoAttachManager | mainHandler | 1.5s 防抖后切到 probe worker |
| JdwpClient | 单一 reader 线程 + worker pool | reader 收包;worker 解析 |
| LogWireClient | 单一 reader 线程 | callbacks 在 reader 线程派发 |
| LogSocketServer | accept thread + heartbeat executor | accept + 5s 周期心跳 |
| JdwpServer | accept + per-connection handler | 一连接一线程 |

## 7. 错误恢复策略

| 失败阶段 | 行为 | UI 反馈 |
|----------|------|---------|
| Build | 立刻 fail(Step.BUILD) | FlashBar "构建失败" |
| Install | 60s timeout, fail(Step.INSTALL) | FlashBar + logcat 链接 |
| Launch | fail(Step.LAUNCH) | Toast |
| Resolve port | runAs probe fallback, fail(Step.RESOLVE_PORT) | FlashBar "请确认 app 已就绪" |
| Connect | DebuggerController.connect throws | 红色 snackbar |

## 8. 安全考虑

- 所有 JDWP / logwire 流量绑定 loopback (`127.0.0.1`)。
- 远程连接走 Shizuku，UID 验证后才允许 adb。
- 帧头 magic `LOGW` 校验失败立即断开。
- 协议版本不匹配时 `Handshake.read` 抛 `IllegalArgumentException`。
