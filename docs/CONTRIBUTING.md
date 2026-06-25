# ZeroStudio IDE 贡献指南

> Phase F4 — 给想给 ZeroStudio IDE 调试器 (ide-debugger /
> ide-log-plugin / utilities/logwire) 贡献代码的开发者。

## 1. 工作流程

### 1.1 Fork & Branch

```bash
# 1. fork
# 2. clone
git clone https://github.com/<you>/ZeroStudio.git
cd ZeroStudio
git remote add upstream https://github.com/itsaky/AndroidIDE.git
git fetch upstream

# 3. branch
git checkout -b feature/my-debugger-improvement
```

### 1.2 Commit message

我们用 **Conventional Commits**：

```
<type>(<scope>): <subject>

<body>

<footer>
```

- type: `feat` / `fix` / `docs` / `refactor` / `test` / `chore` / `perf`
- scope: 影响的模块，例如 `ide-debugger` / `ide-log-plugin` /
  `utilities/logwire` / `core/app/debugger`
- subject: 中文 50 字以内；动词开头

示例：

```
feat(ide-debugger): Phase G.4 AstIndex 跨文件符号索引

- AstIndex: 按 source file + symbol name 索引
- ReferenceFinder: findUsages / peekDefinition 基于 AstIndex
- 单测: AstIndexTest 覆盖 build/clear/lookup
```

### 1.3 Pull Request

1. 推到自己 fork：`git push origin feature/...`
2. 在 GitHub 上发起 PR 到 `main`。
3. 标题遵循 commit message 风格。
4. 描述里关联 issue 编号：`Fixes #123` / `Refs #456`。
5. 等 CI (ide-debugger-tests.yml) 全绿。
6. 至少一个 reviewer LGTM 后 merge (squash)。

## 2. 开发环境

| 工具 | 版本 |
|------|------|
| JDK | 17 或 21 (LTS) |
| Android SDK | API 34 |
| Gradle | 8.7 (项目默认) / 8.13 (CI 测试) |
| Android Studio | Hedgehog (2023.1.1) 或更新 |
| Git | 2.40+ |
| Node | 18+ (前端 / docs 用) |

## 3. 编码规范

### 3.1 Java

- Google Java Style (4 空格缩进)
- 行宽 120
- `@NonNull` / `@Nullable` 必须 (来自 `androidx.annotation`)
- 单元测试用 JUnit 4
- 单测覆盖率：核心逻辑 ≥ 80%

### 3.2 Kotlin

- ktlint 默认规则
- 扩展函数放到 `extensions/` 包
- 优先 val 而非 var

### 3.3 注释

- 类 / 公共方法必须有 Javadoc
- 注释用中文 (与本项目目标用户一致)
- 关键决策用 `// PHASE-X: ...` 标注 (例：`// PHASE-G4: 处理 .kt 回退`)

## 4. 模块结构

```
ide-debugger/             # IDE 端 JDWP 客户端 + SourceLocator
  src/main/java/com/zerostudio/debugger/
    api/                  # 公共 API (Breakpoint, VariableInfo, ...)
    jdwp/                 # JDWP 协议层
    model/                # 状态机 + SourceLocator + Parser
    event/                # DebugEventBus
    util/                 # ByteBuf 等小工具
  src/test/...            # JUnit 4 单测
  build.gradle.kts

ide-log-plugin/           # 目标端 (注入到用户 app)
  src/main/java/com/zerostudio/logplugin/
    api/                  # LogPayload, LogLevel, LogTransportType
    bootstrap/            # DebuggerBootstrapProvider (ContentProvider)
    capture/              # LogCaptureService
    jdwp/                 # JdwpServer
    transport/            # LogSocketServer
    util/                 # LogBuffer ring
  src/test/...

utilities/logwire/        # logwire 协议 (Client/Server/Frame/Codec)
  src/main/java/com/itsaky/androidide/logwire/
    LogWireClient.java
    FrameCodec.java
    Handshake.java
    ErrorPayload.java
    LogPayload.java
    LogLevel.java
    WireConstants.java
  src/test/...

tooling/plugin/           # AGP init script plugin
  src/main/java/com/itsaky/androidide/gradle/
    IdeDebuggerInitScriptPlugin.kt
    IdeLogInitScriptPlugin.kt

core/app/src/main/java/com/itsaky/androidide/debugger/   # IDE 端高层
  DebugSessionLauncher.java
  AutoAttachManager.java
  AppReadySignalWatcher.java
  DebuggerController.java
  DebugSessionState.java
  LogcatReader.java
  JdwpPortResolver.java
  ShizukuBridge.java
  RunAsBridge.java
  RemoteDeviceScanner.java
  JdwpPortResolver.java
  ...
```

## 5. 测试要求

### 5.1 必须写的测试

- 任何新增 public API
- 任何 bug fix 至少一条回归测试
- 协议层 (logwire / JDWP codec) 100% 覆盖
- SourceLocator 解析逻辑 100% 覆盖 (JavaParser/ASM 都是确定性输出)

### 5.2 跑测试

```bash
# 单测
./gradlew :ide-debugger:testDebugUnitTest

# 集成测试 (需要 device/emulator)
./gradlew :ide-debugger:connectedDebugAndroidTest

# 全量
./gradlew test
```

### 5.3 不通过 CI 的常见原因

- 编译警告 (配置 `-Werror`)
- 静态字段命名不符合 final+ALL_CAPS
- `assertThat` 而不是 `assertEquals`
- 注释里出现 emoji (请用文字描述)
- 删了一个 public API 没在 RELEASE_NOTES.md 标记

## 6. Release 流程

1. 在 `docs/RELEASE_NOTES.md` 里把 `## [Unreleased]` 改成 `## [vX.Y.Z] - YYYY-MM-DD`。
2. 更新根 `build.gradle.kts` 的 `versionName` / `versionCode`。
3. `git tag vX.Y.Z && git push upstream vX.Y.Z`。
4. CI 会构建 signed APK 上传到 release draft。
5. 编辑 release notes，发布。

## 7. 沟通

- GitHub Issues: bug 报告 / 特性请求
- Discord: `#ide-debugger` 频道 (开发者讨论)
- 邮件: <ide@zerostudio.dev> (安全相关请用此通道)

## 8. 行为准则

- 友善、包容、专业
- 隐私优先：用户代码不上传
- 协议稳定：JDWP 协议层一旦发布不破坏向后兼容
- 安全优先：loopback-only，不主动开 LAN 端口

## 9. License

提交 PR 即表示同意按 Apache 2.0 协议授权。
