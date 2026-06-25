# ZeroStudio IDE — Release Notes

> Phase F5 — 涵盖 PR-D6 / PR-D7 / PR-D8 / PR-D9 / Phase E UI / Phase C/D/F/G/H 的完整 changelog。

## [Unreleased] — Phase H 锦上添花

### Added

- Phase C.1: `utilities/logwire` 协议补全 (Handshake / ErrorPayload / FrameCodec / LogWireClient)
- Phase C.2: AGP variant API 注入 ide-log-plugin AAR (tooling/plugin/IdeLogInitScriptPlugin)
- Phase C.3: 目标端 LogCaptureService + LogSocketServer (ide-log-plugin)
- Phase C.5: DebuggerBootstrapProvider (ContentProvider) + Manifest placeholder 注入 (IdeDebuggerInitScriptPlugin)
- Phase D.1: DebugSessionLauncher 端到端 (build → install → launch → resolve port → connect)
- Phase D.2: AutoAttachManager 800ms 防抖窗口 + cancelPending
- Phase D.3: RemoteDeviceScanner 16 路并发 + 250ms per-host timeout
- Phase F.1: CI workflow 完善 (JDK 17/21 × Gradle 8.7/8.13 matrix)
- Phase F.2: `docs/architecture/DEBUGGER.md` Mermaid 架构图
- Phase F.3: `docs/protocol/JDWP.md` JDWP 协议说明
- Phase F.4: `docs/CONTRIBUTING.md` 贡献指南
- Phase F.5: 本文件 (RELEASE_NOTES.md)
- Phase G.1: JavaSourceParser Kotlin (.kt) 文件回退支持
- Phase G.2: ClassFileReader inner class / lambda 标识
- Phase G.3: SourceLocatorCache LRU + 失效策略
- Phase G.4: AstIndex 跨文件符号索引 (ide-ast 库初始)
- Phase G.5: ReferenceFinder findUsages / peekDefinition
- Phase H.1: BatchGetValues 批量 frame 变量读取
- Phase H.2: DebuggerWatchdog ANR 防护 (30s timeout)
- Phase H.3: 长空闲断连 (5min idle → suspend session)

### Changed

- AutoAttachManager 800ms 防抖 (在 [PR-D5] 基础上加)
- DebugSessionLauncher 重写 runInstall 等待逻辑 (Thread.sleep → EventBus latch)

### Fixed

- Phase D.2: 短时间多次 maybeAutoAttach 不再叠加 1.5s 延迟
- Phase D.1: install 失败显式 fail 而不是默默 timeout
- Phase G.4: Kotlin .kt 文件回退到 .class file parser (而不是 basename heuristic)

## [v0.9.0] — 2026-04-15 — PR-D6 + PR-D7 + PR-D8 + PR-D9

### PR-D6 (PR 419 batch 1/3) — P0 关键修复

- DebugSessionLauncher 重构: build/install/launch/connect 端到端
- LogStore 持久化
- Variables set-value 真正生效
- LogpointFragment 与 BreakpointTypePicker 整合
- `runInstall` 等待逻辑: `Thread.sleep(2_000L)` → `EventBus + CountDownLatch`
- `runResolvePort` 失败: `runAs` probe fallback
- `launch` 成功后写 `targetPackage` 到 `DebuggerController`
- `JdwpPortResolver` poll ContentProvider 拿真实端口

### PR-D7 (PR 419 batch 2/3) — 性能 / 启动 / a11y / 触觉 / 快捷键 (10 项 P1)

- `AutoAttachManager` SharedPreferences 持久化
- `AppReadySignalWatcher` logcat "ZeroStudioDebug READY" 信号
- `RemoteDeviceScanner` 16 路并发 + 250ms timeout
- 启动器启动时间优化
- a11y: TalkBack contentDescription 全覆盖
- 触觉: 断点命中 View.performHapticFeedback
- 快捷键: 步进 / 继续 / 停止的 Ctrl+ 键
- 断点类型选择器
- Logcat 流式输出 (FrameCodec-based)
- IDE 状态条 Debugger indicator

### PR-D8 (PR 419 batch 3/3) — 锦上添花 P2

- IDE 主题感知 (深色 / 浅色 / 跟随系统)
- 国际化 (i18n) 5 种语言
- 单测覆盖率从 65% 提升到 82%
- 性能 baseline: install + launch + connect < 5s
- 错误消息本地化

### PR-D9 — 锦上添花 5 项

- 性能 (H.1/H.2/H.3 雏形)
- 文档: DEBUGGER_FULL_AUDIT.md §7-§10
- 单测补全
- 主题支持
- i18n 翻译

### Phase E — UI 打磨

- Variables 折叠 / 展开动画
- Call stack 滚动性能
- Breakpoint gutter 边距视觉
- 长变量值的可滚动预览
- 主题色一致性
- 错误状态的红 / 绿 / 黄三色系统

## [v0.8.0] — 2026-02-01 — 调试器框架

### Added

- JDWP client 端基础实现 (ide-debugger)
- JDWP server 端基础实现 (ide-log-plugin)
- Breakpoint / Variable / Frame 基本类型
- BuildService + ApkInstaller 集成
- ContentProvider 注入 (ide-log-plugin → user app)
- Logcat 集成
- 基础 UI (CallStack / Variables / Watches / Breakpoint list)

## 版本号规范

- MAJOR: 协议破坏性变更
- MINOR: 新功能
- PATCH: bug fix

JDWP / logwire 协议的 wire format 变更 = MAJOR 升级。
