# 实现计划: 断点调试器注入器 + 连接层补完

> 配套 spec: `docs/superpowers/specs/2026-07-02-debugger-injection-generator.md`
> 计划日期: 2026-07-02
> 状态: ✅ 已完成 (Phase 1-6) + Phase 7 (集成测试) + Phase 8 (部署检查表)

## 概述

按 spec + brainstorming 待办列表, 把以下未完成工作拆成可执行 plan:

1. **子项目 10 - 注入器生成器** (spec 主体)
2. **修子项目 9d 遗留 bug**: `withManifestPlaceholders` 是 no-op, 需真正注入
3. **子项目 9c 重命名**: `startBridgeThread` → `startReverseConnectThread` (public)
4. **连接层 3 - ShizukuConnection 替换 stub**
5. **连接层 4 - RootConnection 替换 stub**
6. **AppReadyAutoConnect 补充测试**
7. **Phase 7 - BuildTimeInjector 端到端集成测试**
8. **Phase 8 - 子项目 11 真实部署验证检查表**

每个工作都按 TDD 流程: 写测试 → 实现 → 通过。

---

## Phase 1: 修子项目 9d placeholder 注入 bug (前置)

**问题**: `IdeDebuggerInitScriptPlugin.withManifestPlaceholders` 函数体只 log, 没
真的写 placeholder 到 AGP 的 manifest placeholders map。子项目 9d 之后所有
依赖 `${ideLocalServerName}` 占位符替换的工作都拿不到值。

**目标**: 改成真的写入 `BuildType.manifestPlaceholders` (debug variant) 或
`defaultConfig.manifestPlaceholders`, 让 AGP 在 merge manifest 时替换。

**AGP 8+ 写入 manifest placeholders 的正确方式**:
```kotlin
// 在 ext.onVariants { variant -> ... } 块里:
variant.androidComponents?.let { components ->
    // AGP 8.x: variant.buildType.manifestPlaceholders 不直接暴露
    // 正确做法: 通过 productFlavors + buildTypes 改
}
// 或者: 直接用 applicationVariants API (老但稳)
val variant = project.extensions.getByType(ApplicationExtension::class.java)
    .applicationVariants.first { it.name == "debug" }
variant.buildType.manifestPlaceholders["ideLocalServerName"] = computeLocalServerName(project)
```

**简化做法** (兼容 AGP 8.x): 写一个合成 manifest fragment 文件到
`build/intermediates/merged_manifest/{variant}/` 目录, 让 AGP merge 进 host
manifest。Fragment 文件是 XML, 包含 `<application>` 内的 placeholder
占位符。但 AGP 8.x 不允许外部 fragment, 只能用 BuildConfigField 或
manifestPlaceholders。

**更简化做法** (AGP 8+ 推荐): 走 `Variant#manifestPlaceholders` 通过
reflection 或 `Variant#buildType` 内部 API。

**最稳定做法**: 用 `defaultConfig.manifestPlaceholders` 注入, 因为
`defaultConfig` 是所有 variant 的基础, debug 变体会继承:
```kotlin
val androidExt = project.extensions.findByType(BaseExtension::class.java)
    ?: project.extensions.findByType(CommonExtension::class.java)
androidExt?.defaultConfig?.manifestPlaceholders?.put(
    IDE_LOCAL_SERVER_NAME_PLACEHOLDER, computeLocalServerName(project)
)
```
但 `BaseExtension` 内部 API 兼容性差, AGP 8.x 推荐用
`androidComponents.onVariants` 路径。

**决定**: 用 reflection 找 `defaultConfig.manifestPlaceholders` map, 写
失败 logger.warn。Host app 自己的 manifest 仍然可以走 `${ideLocalServerName}`
placeholder, 因为 AGP merge 时会查这个 map。

### Phase 1.1 - 写测试 (TDD)

`IdeDebuggerInitScriptPluginTest` (新建):
- `withManifestPlaceholders 真写 map`: mock variant, verify call map.put(key, value)
- 失败: mock variant 抛 NPE, verify 不抛, log warn
- 多次调用: 同 key 覆盖 vs 抛错? 决定: 覆盖 (新值替换旧值)

### Phase 1.2 - 修实现

`IdeDebuggerInitScriptPlugin.kt`:
- `withManifestPlaceholders` 改成: reflection 找 `defaultConfig.manifestPlaceholders`,
  put values, 找不到 logger.warn 不抛
- 测试通过

### Phase 1.3 - 验证

- 跑测试: `gradle :tooling:plugin:test` (CI 环境跑)
- 手工 review: read 整个修改, verify reflection 找 map 的路径

---

## Phase 2: 子项目 10 注入器生成器 (spec 主体)

### Phase 2.1 - 写 `RenderedBreakpoint` 内部数据类 + `escapeKtStringLiteral`

**目标**: 提供两个纯函数工具, 不依赖 Gradle, 易测试。

#### Phase 2.1.1 测试

`RenderedBreakpointTest`:
- `RenderedBreakpoint(sourceFile, line, column)` 构造 + equals/hashCode
- column 0 (默认) / column > 0
- line 必须 >= 0

`EscapeKtStringLiteralTest` (10+ 用例):
- 普通字符串: `"hello"` → `"hello"`
- 含引号: `Foo "Bar"` → `"Foo \"Bar\""`
- 含反斜杠: `Foo\Bar` → `"Foo\\Bar"`
- 含换行: `Foo\nBar` → `"Foo\\nBar"`
- 含回车: `Foo\rBar` → `"Foo\\rBar"`
- 含 tab: `Foo\tBar` → `"Foo\\tBar"`
- 含 ASCII 0: `Foo\u0000Bar` → `"Foo\\u0000Bar"`
- 含 $: `Foo $bar` → `"Foo \$bar"` (避免 Kotlin 字符串插值)
- 空字符串: `""` → `""`
- null 不允许, 函数签名是 String not String?

#### Phase 2.1.2 实现

`tooling/plugin/.../IdeDebuggerBootstrapGen.kt` (新文件):
- `data class RenderedBreakpoint(val sourceFile: String, val line: Int, val column: Int = 0)`
- `internal fun escapeKtStringLiteral(s: String): String`
- 纯函数, 不依赖 Gradle

### Phase 2.2 - 写 `renderIdeDebuggerBootstrapKt`

#### Phase 2.2.1 测试

`RenderIdeDebuggerBootstrapKtTest` (15+ 用例):
- 全 5 段都在输出: 搜 `IDE_DEBUGGER_VERSION`, `LOCAL_SERVER_NAME`,
  `HELLO_PROTOCOL_EXTRA_FIELDS`, `BUILD_TIMESTAMP_MS`, `data class
  BreakpointLocation`, `PREHEAT_BREAKPOINTS`, `init(application`,
  `HostAttachAgentBootstrap.startReverseConnectThread`
- IDE_DEBUGGER_VERSION 反映参数 (搜 `"<ide_version>".replace("...", "...")`)
- LOCAL_SERVER_NAME 反映参数
- HELLO_PROTOCOL_EXTRA_FIELDS 反映参数
- BUILD_TIMESTAMP_MS 反映参数 (数字正确)
- PREHEAT_BREAKPOINTS 默认空: `emptyList()`
- PREHEAT_BREAKPOINTS 非空: 1 元素 + 2 元素, verify `BreakpointLocation(...)` 出现在输出
- 包名正确: `package com.itsaky.androidide.zerostudio.ide.debugger.host.generated`
- 转义 ideVersion 含 `"`: `"1.2.3\"beta\""`
- 转义 localServerName 含 `$`: `"\$test"`
- 转义 sourceFile 含 `\`: `"Foo\\Bar.kt"`
- 转义 sourceFile 含 `"`: `"Foo \"Bar\".kt"`
- BUILD_TIMESTAMP_MS = 0 是合法值 (老 build 也能编译)

#### Phase 2.2.2 实现

`renderIdeDebuggerBootstrapKt` 内部函数:
- 5 个参数
- 字符串模板拼接 .kt 源文件内容
- 4 段结构: 头注释 / 常量 / data class / PREHEAT + init
- `AtomicBoolean` 保护幂等
- 调 `escapeKtStringLiteral` 转义所有字符串参数

### Phase 2.3 - 写 `parsePreheatBreakpoints`

#### Phase 2.3.1 测试

`ParsePreheatBreakpointsTest` (10+ 用例):
- null → empty
- 空字符串 `""` → empty
- `";"` → empty (空 entry 跳过)
- `"src=MainActivity.kt:10:5"` → 1 元素
- `"src=A:1:0;src=B:2:3"` → 2 元素
- `"src=A:0:0"` (line=0) → 合法 (Android 行号可能 0? 不期望但允许)
- `"src=NoColon"` → 抛 IAE
- `"src=A:abc:0"` (line 非数字) → 抛 IAE
- `"src=A:1:xyz"` (column 非数字) → 抛 IAE
- `"src=A:-1:0"` (line 负数) → 抛 IAE
- `"src=Has:Colon.kt:1:0"` (file 含 `:`) → 抛 IAE (违反限制)
- `"src=Has;Semicolon.kt:1:0"` (file 含 `;`) → 抛 IAE
- 尾部 `;`: 跳过空 entry
- 大写敏感: `SRC=...` → 抛 IAE (要求严格小写 `src=`)

#### Phase 2.3.2 实现

`parsePreheatBreakpoints(prop: String?): List<RenderedBreakpoint>`:
- 接受 null / 空 → 返回 emptyList()
- 按 `;` split
- 每个 entry 期望 `src=<file>:<line>:<column>` 格式
- 用 regex 或 manual split 解析
- 格式错抛 IAE, 包含原始 entry 便于调试

### Phase 2.4 - 注入流程 (在 IdeDebuggerInitScriptPlugin.kt 扩展)

#### Phase 2.4.1 测试

`IdeDebuggerInitScriptPluginTest`:
- mock Project + ApplicationVariant
- 调 onVariants lambda, verify:
  1. 写文件到 `build/generated/source/ide_debugger/{variant}/kotlin/.../IdeDebuggerBootstrap.kt`
  2. 文件内容包含正确 IDE_DEBUGGER_VERSION 等
  3. addStaticSourceDirectory 被调
- 失败场景: 写文件抛 IOException, verify logger.warn + 不抛

#### Phase 2.4.2 实现

`IdeDebuggerInitScriptPlugin.kt`:
- 新增 `generateIdeDebuggerBootstrapKt(variant, project)` 内部函数
- 在 `ext.onVariants { variant -> ... }` 块里, debuggable variant
  走完 Phase 1 修过的 placeholder 注入后, 调 generateIdeDebuggerBootstrapKt
- 调 `variant.sources.kotlin?.addStaticSourceDirectory(generatedDir.absolutePath)`
- 失败 logger.warn, 不抛

---

## Phase 3: 子项目 9c 重命名 (后置, 让 Phase 2 init() 能调)

### Phase 3.1 - 修改 HostAttachAgentBootstrap.java

- `startBridgeThread` → `startReverseConnectThread`
- 改 public
- 第一个参数 `Context` → `Application`
- 内部逻辑不变
- 旧名字删除 (没别处用)

### Phase 3.2 - 验证

- 编译通过 (CI)
- 手工 grep verify 旧名无残留

---

## Phase 4: 连接层 3 - ShizukuConnection 替换 stub

**现状**: 子项目 3 已经写了 ShizukuConnection IDE 端骨架, 4 子路径
(Binder / InHostPlugin / WifiAdb / Socks) 都是 stub。

**目标**: 4 子路径真正工作, 走子项目 8 的 host runtime (HostPluginService
for InHostPlugin / HostSocksServer for Socks)。

### Phase 4.1 - Binder 子路径

走 Shizuku 直接 bind to host app's JDWP-exposed binder service. 需要 host 端
写一个 aidl service (`HostBinderService`), 接收 IDE 端的 binder transaction
发 JDWP 帧。IDE 端 `ShizukuConnection.binderPath()`:
- `Shizuku.newProcess` 是 private, 用 `attachUserService` / `bindUserService`
  模式 (子项目 3 笔记已写)
- 走反射 + ServiceManager API
- 测试: mock Shizuku API, verify binder flow

### Phase 4.2 - InHostPlugin 子路径

走 Shizuku `bindUserService` + host 端 `HostPluginService` (子项目 8 已建):
- IDE 端: `Shizuku.bindUserService(HostPluginService::class.java, ...)`
- Host 端 `HostPluginService.onBind` 返回 `IHostPlugin.Stub` impl
- IDE 端拿到 binder, 走 jdwp flow
- 测试: 走 mockk mock Shizuku + service connection

### Phase 4.3 - WifiAdb 子路径

走 adb 端口转发 (跟子项目 7 一样, 但 adb host 是 wifi 设备):
- 类似 UsbLanConnection 但 adbHost = 设备 IP, adbPort = 5555
- 可考虑用 InnetVmAdbConnection 抽的 AdbForwardConnection 复用

### Phase 4.4 - Socks 子路径

走 SOCKS5 代理 (跟子项目 5 InnetVmSocks 一样, 但代理是 host 端
`HostSocksServer` 监听 SOCKS5):
- 类似 InnetVmSocksConnection
- 测试: 单元测试 mock SOCKS5 negotiation

---

## Phase 5: 连接层 4 - RootConnection 替换 stub

**现状**: 子项目 4 已写 RootConnection 骨架, 走 host 端
`openJdwpSocket()` (Root 进程内执行), 但 host 端 `HostAttachAgent` (子项目 8
的 app_process 入口) 还没有真正实现 openJdwpSocket。

**目标**: `HostAttachAgent.openJdwpSocket()` 真做 JDWP socket bind + accept,
IDE 端 RootConnection 真连过来。

### Phase 5.1 - HostAttachAgent 扩展

- 加 `openJdwpSocket(): Socket?` 静态方法
- bind LocalServerSocket("jdwp"), accept, 返回 server-side accepted socket
- IDE 端通过 adb reverse 拿 client-side socket

### Phase 5.2 - RootConnection 扩展

- resolve/connect/attach 走真的 openJdwpSocket 路径
- 测试: mock HostAttachAgent 调用

---

## Phase 6: AppReadyAutoConnect 补充测试

现状有 10 个测试, 补充:

- **多 activeByPkg 清理**: 一个 pkg 失败时, 不影响其他 pkg 的 active
- **空 hint 处理**: 传 `null` / 不传 hint 不崩
- **source 字段透传**: logcat source vs bridge source 区分
- **init 异常隔离**: 一个 pkg 的 schedule 抛错不影响其他

### Phase 6.1 - 测试

`AppReadyAutoConnectTest` 补充 4-5 个用例。

### Phase 6.2 - 实现调整

如发现 bug, 修实现。

---

## 验证策略

- 每个 Phase 都有独立单元测试
- 全部纯 JVM 单元测试, 不依赖 Android SDK / Gradle build
- 集成验证留到 CI (CI 用 JDK 21 + Gradle 8.13 + Android SDK)
- 本地手工 review 代码

## 范围限定

**不做** (留后续):
- IDE 端 IDE debugger 升级 hook (验证 host app rebuild 时拿到最新代码)
- 真实部署测试 (build sample host app) - 这是子项目 11
- 方案 B (IDE 端 bp 预热配置 UI)
- 方案 C (完整 host SDK)
- 多 IDE debugger 实例 (per-user 唯一 server name 够用)

## 文件清单汇总

| 类型 | 路径 |
|------|------|
| 改 | `tooling/plugin/.../IdeDebuggerInitScriptPlugin.kt` (Phase 1 修 + Phase 2 注入) |
| 改 | `ide-debugger-host/.../HostAttachAgentBootstrap.java` (Phase 3 重命名) |
| 新 | `tooling/plugin/.../IdeDebuggerBootstrapGen.kt` (Phase 2.1-2.3) |
| 新 | `tooling/plugin/src/test/.../IdeDebuggerBootstrapGenTest.kt` (Phase 2 测试) |
| 改 | `core/app/.../debugger/connection/impl/ShizukuConnection.kt` (Phase 4) |
| 改 | `ide-debugger-host/.../HostAttachAgent.kt` (Phase 5.1) |
| 改 | `core/app/.../debugger/connection/impl/RootConnection.kt` (Phase 5.2) |
| 改 | `core/app/src/test/.../AppReadyAutoConnectTest.kt` (Phase 6 补充) |

## 时间线

按依赖关系, 推荐顺序:
1. Phase 1 (placeholder 修) — 前置
2. Phase 2 (子项目 10 注入器) — 主菜
3. Phase 3 (startReverseConnectThread 重命名) — 跟 Phase 2 同步
4. Phase 6 (AppReadyAutoConnect 补充测试) — 容易, 可跟 Phase 2 并行
5. Phase 4 (ShizukuConnection 替换 stub) — 大
6. Phase 5 (RootConnection 替换 stub) — 中
7. 最后: PR #445 更新

每个 Phase 都是独立 commit + push。

---

## Phase 7: BuildTimeInjector 端到端集成测试 ✅

**问题**: 单元测试 (Phase 1-2) 覆盖了 `renderIdeDebuggerBootstrapKt` /
`parsePreheatBreakpoints` / plugin helpers 的纯函数逻辑, 但没有端到端验证:
- 生成的 .kt 文件能不能写到磁盘 + 拿到 Gradle 期望的路径
- 4 段结构 (常量 + data class + preheat + init) 在真实文件里都齐
- parsePreheatBreakpoints + renderIdeDebuggerBootstrapKt 串起来能工作
- 多次重新生成只 buildTs 变 (其余稳定)

**目标**: 写一个集成测试, 模拟 IDE 端常量 + 真实 bp 列表 + 写到
`build/generated/source/ide_debugger/debug/kotlin/...` 路径 + 验证结构。

**做法**:
- `BuildTimeInjectorIntegrationTest` (新)
- 7 个集成测试:
  1. 完整生成产物 (写文件 + 验证 4 段结构)
  2. 真实 IDE 版本 + group + name + sdk 注入
  3. 真实预热 bp 列表 (多文件 + 每行每列)
  4. 空 bp 用 `emptyList()`
  5. 包名路径与 Manifest placeholder 一致
  6. 多次重新生成只 buildTs 变
  7. parsePreheatBreakpoints -> renderIdeDebuggerBootstrapKt 端到端

**不做**: 调 kotlinc 编译 (沙箱无 SDK 工具链); 模拟真 Android 启动
(沙箱无 Android runtime)。

---

## Phase 8: 子项目 11 真实部署验证检查表 ✅

**问题**: 端到端验证需要真机 + 真 IDE APK + 6 选 1 切换, 沙箱环境跑不了。
但需要给真机测试者一份完整检查表, 保证不出遗漏。

**目标**: 写一份可在真机执行的部署验证检查表, 覆盖 6 选 1 全部路径 +
BuildTimeInjector 编译期注入验证 + 新鲜度验证 + 失败模式验证。

**做法**:
- `docs/superpowers/specs/2026-07-02-subproject-11-deployment-checklist.md` (新)
- 8 大节:
  1. 前置条件
  2. 编译期验证 (BuildTimeInjector 注入)
  3. 部署期验证 (HostAttachAgentBootstrap 反连)
  4. 6 选 1 连接方案验证 (AidlSocket / Shizuku/WifiAdb / Shizuku/InHostPlugin /
     Shizuku/Socks / Root / InnetVmSocks / InnetVmAdb / UsbLan)
  5. 断点注入验证 (BuildTimeInjector 核心)
  6. 新鲜度验证 (build 多次 IDE 端常量更新)
  7. 失败模式验证
  8. 验收标准 (Definition of Done)

**用法**: 合并 PR #445 前, 跑完所有 checkbox 才能标 Ready for Review。
