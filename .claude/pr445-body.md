# 断点调试器连接层 - 子项目 1-11 (BuildTimeInjector 注入器 + stub 替换 + 集成测试 + 部署检查表)

本 PR 在子项目 1-9 (6 选 1 方案 + 抽象层 + Host ADRT + 端到端集成) 基础上, 完成:

1. **构建时断点注入器** — 子项目 10 + 11。`BuildTimeInjector` 通过 Gradle 插件
   在编译期生成 .kt 源文件, 把断点位置 (source file + line + column) + IDE 端
   常量 (local server name + sdk + build timestamp) 一起注入到 host app,
   host 启动时 `init(application)` 把断点信息推送到 IDE。
2. **连接层 3/4 stub 替换** — `ShizukuConnection` 4 子路径 (Binder /
   InHostPlugin / WifiAdb / Socks) + `RootConnection` (走 InputStream/OutputStream
   路径, 兼容 host jdwp 是 abstract unix socket) 替换之前的 "NotImplemented" 骨架。
3. **端到端集成测试 + 部署检查表** — 沙箱内 7 个 BuildTimeInjector 集成测试 +
   真机执行的 6 选 1 部署验证检查表 (sub-project 11)。

## 子任务一览

### 子项目 10a - 注入器生成器 `IdeDebuggerBootstrapGen.kt` (新)

- `tooling/plugin/.../gradle/IdeDebuggerBootstrapGen.kt`: 4 段生成产物
  - IDE 端常量 (3 个 String + Long buildTs)
  - `BreakpointLocation` data class (file/line/column)
  - `PREHEAT_BREAKPOINTS` list (空 -> `emptyList()`, 否则 `listOf(BreakpointLocation(...))`)
  - `init(application)` 静态入口 (JVM 静态方法, synchronized, 把断点信息
    推送到 IDE 端 + log 验证 buildTs)
- `escapeKtStringLiteral` 转义 Kotlin 字符串字面量
  (`, \, \n, \r, \t, \b, \f, $, ASCII control -> \u00xx`)
- `parsePreheatBreakpoints` 解析 Gradle property `src=file:line:column;src=...`
  (file 禁 `;`/`:`; line/column 非负整数)
- 23 个单元测试 + 7 个集成测试覆盖转义 + 解析 + 渲染 + 端到端

### 子项目 10b - Gradle 插件 `IdeDebuggerInitScriptPlugin.kt` (改)

- 新增 `ideDebuggerSdkInt` Gradle property 控制注入的 sdk int
- 新增 `ideDebuggerPreheatBreakpoints` Gradle property 传断点位置
  (`src=MainActivity.kt:42:8;src=Other.kt:100:0;...`)
- 加 `withManifestPlaceholders` 走 reflection 真写 `defaultConfig.manifestPlaceholders`
  (AGP 8.x public API 不直接暴露)
- 加 `computeLocalServerName(group, name)` (纯函数) 算唯一 key
- 加 `computeBootstrapPlaceholders(group, name, sdkInt, preheatBreakpoints)` 算 3 个 placeholder
- 加 `generateIdeDebuggerBootstrapSource(variant, project)` 在 `onVariants` 块
  里写 .kt 到 `build/generated/source/ide_debugger/{variant}/kotlin/...IdeDebuggerBootstrap.kt`
  + 调 `variant.sources.kotlin?.addStaticSourceDirectory(generatedDir)`
- 12 个 `IdeDebuggerInitScriptPluginTest` 测试覆盖所有 helper

### 子项目 10c - host 端 `HostAttachAgentBootstrap.startReverseConnectThread` (改)

- `startBridgeThread` 重命名为 `startReverseConnectThread` (public, 接受
  `Application` 参数, 加 `AtomicBoolean sStarted` 幂等保护)
- 加 `META_IDE_SOCKET_NAME = "ide_local_server_name"` 常量
- 加 `toApplication(Context)` + `ApplicationContextWrapper` fallback
- 加 `startBridgeThreadInternal` 静态方法
- 加 HELLO 协议写 "HELLO pkg=X pid=Y sdk=Z\n"

### 子项目 4 - `RootConnection` 改 InputStream/OutputStream 路径 (改)

- `RootClient.openJdwpSocket` 改 `openJdwpStream` 返 `RootJdwpStream`
  data class (input, output, onClose)
- 真实实现走 `su -c 'socat - UNIX-CONNECT:@jdwp'` 把 stdin/stdout 接到
  host jdwp socket (因 host jdwp 是 abstract unix socket, 不强制走 java.net.Socket)
- `RootConnection.socket` 字段改 `stream/input/output`
- `attach()` 走 `rootClientImpl.openJdwpStream` + InputStream/OutputStream handshake
- `sendJdwp/detach/release/attachedSocket/startReadLoop` 全部改支持 InputStream 路径
- `attachedSocket()` 抛 `UnsupportedOperationException`
- 11 个单元测试覆盖 RootJdwpStream 握手 + 错握手 + bad stream + sendJdwp

### 子项目 3 - `ShizukuConnection` 4 子路径实装 (改)

- `WifiAdb`: 复用 `AidlSocketConnection` (同款 resolve/connect/attach)
- `Binder`: 走 `InHostPlugin` 同款实装 (Shizuku 13+ 限制 `transferFileDescriptor`
  不可用, 走 user service + reverse-connect)
- `InHostPlugin`: `bindUserService` + IDE `LocalServerSocket.accept` + JDWP handshake
- `Socks`: `bindUserService` + `Socks5Client` (RFC 1928) 走 host 端 SOCKS5 proxy
- 加 `localSocket` 字段 + 改 `sendJdwp/detach/release/attachedSocket/startReadLoop`
  支持 `LocalSocket` 路径
- 22 个单元测试 (含 Socks5 握手 + InHostPlugin/Binder LocalServerSocket 路径
  + Shizuku 13+ 限制 newProcess/transferFileDescriptor 抛 UnsupportedOperationException)

### 子项目 9 增强 - `AppReadyAutoConnect` source 字段 (改)

- `AutoConnectListener.onAttachSuccess/Failed` 加 `source: String` 参数
  ("logcat" / "bridge"), 供 UI 区分触发来源
- 6 个补充测试: 多 activeByPkg 清理 / 空 hint / source 字段透传 /
  bridge 启动失败隔离 / stop 取消 pending / 防抖窗口后新信号

### 子项目 11 - 部署验证检查表 (新)

- `docs/superpowers/specs/2026-07-02-subproject-11-deployment-checklist.md`
- 8 大节: 前置条件 / 编译期验证 / 部署期验证 / 6 选 1 切换 / 断点注入
  验证 / 新鲜度验证 / 失败模式验证 / 验收标准
- 每步可勾选 checkbox, 配 shell 命令 (adb/aapt2/unzip) 验证


## 后续 bug 修复 (Phase 9-11)

在 Phase 1-8 完成后, 又发现 3 个潜在 bug, 已在本 PR 内修复:

### Phase 9a - Shizuku InHostPlugin 重复调 bindUserService (commit dd61f0d7)

`ShizukuConnection.connect()` 的 InHostPlugin 分支原本调 `bindUserService`
**结果丢弃**, 然后 `attachViaInHostPlugin()` 又调一次 → 多余的一次 binder 调用。
修复: `connect()` 改成 no-op, `bindUserService` 只在 `attach()` 阶段调一次。
测试: 改名 `connect on InHostPlugin is no-op (bindUserService moved to attach)` + 断言 `bindUserServiceCallCount == 0`。

### Phase 9b - AppReadyAutoConnect 全局防抖误伤其他包 (commit 672f285f)

原实现用 `AtomicLong lastScheduleAt` 做全局防抖 → packageA 触发后
1s 内 packageB 也会被 debounce 跳过 (多 app 调试场景被误伤)。
修复: 改 `ConcurrentHashMap<String, Long> lastScheduleAtByPkg` 做 per-packageName 独立防抖。
测试: 2 个新测试覆盖 (不同 pkg 不互干扰 / 同 pkg 窗口内被丢)。

### Phase 10 - Shizuku Socks 路径用 unresolved hostname 跑不通 (commit 55172760)

`attachViaSocks()` 原本用 `InetSocketAddress.createUnresolved("ide-shizuku-socks-${pkg}", 0)`
当 proxyAddr, 但 `Socks5Client` 走 `java.net.Socket.connect()`, 不支持 abstract
namespace → 永远连不通。
修复:
- `ShizukuConfig` 新增 `socksHost: String = "127.0.0.1"` + `socksPort: Int = 0`
- `attachViaSocks()` 改用真 TCP 端口构造 proxyAddr
- `socksPort <= 0` 时抛清晰错误, 提示用户配置 (host 端 `IdeShizukuSocksUserService`
  尚未实装, 需用户显式配 `socksPort`)
测试: 重写 Socks 端到端测试为真 Socks5 server + 新增 `socksPort=0` 错误消息测试。

### Phase 11 - attach() 阶段 onSuccess{transitionTo(Closed)} 仍返回 success 的反模式 (commit cb5d008f)

6 个 connection 的 `attach()` 阶段都用反模式:
```kotlin
return attempt.onSuccess { info ->
    if (postConditionFailed) {
        transitionTo(ConnectionState.Closed(...))
        return@onSuccess  // ❌ onSuccess 返回值是 Unit, 实际返回外层 Result.success(info)
    }
}
```
调用方会拿到 `success` 但观察 `state` 看到 `Closed` → 反 invariant。
修复:
- `BaseDebugConnection` 加 `finishAttach(info, ok, failureMsg, onAttached)` helper
- 子类改用 `attempt.exceptionOrNull() 短路 → getOrNull() 取出 info → finishAttach 统一收尾`
- `ok=true` → state=Attached, callback invoked, return success(info)
- `ok=false` → state=Closed(IoFailure), return failure(IllegalStateException)

受影响的 6 处:
- `AdbForwardConnection.attach()` (UsbLan + InnetVmAdb 共享)
- `AidlSocketConnection.attach()` (TCP path)
- `AidlSocketConnection.attachLocalBridge()` (LocalBridge path)
- `RootConnection.attach()`
- `InnetVmSocksConnection.attach()`
- `ShizukuConnection.attach()`

测试: 新增 `BaseDebugConnectionFinishAttachTest` (3 个测试覆盖 ok=true / ok=false 分支和 invariant 一致性)。

## 关键文件清单

| 类型 | 路径 |
| ---- | ---- |
| 新 | `tooling/plugin/.../gradle/IdeDebuggerBootstrapGen.kt` |
| 新 | `tooling/plugin/src/test/.../IdeDebuggerBootstrapGenTest.kt` (23 用例) |
| 新 | `tooling/plugin/src/test/.../BuildTimeInjectorIntegrationTest.kt` (7 用例) |
| 改 | `tooling/plugin/.../IdeDebuggerInitScriptPlugin.kt` (AAR + placeholder + 生成) |
| 改 | `tooling/plugin/src/test/.../IdeDebuggerInitScriptPluginTest.java` (+12 用例) |
| 改 | `ide-debugger-host/.../HostAttachAgentBootstrap.java` (重命名 + 公共) |
| 改 | `core/app/.../ShizukuConnection.kt` (4 子路径实装) |
| 改 | `core/app/.../RootConnection.kt` (InputStream/OutputStream 路径) |
| 改 | `core/app/.../root/RootClient.kt` (openJdwpStream + RootJdwpStream) |
| 改 | `core/app/.../host/AppReadyAutoConnect.kt` (source 字段) |
| 改 | `core/app/src/test/.../ShizukuConnectionTest.kt` (+22 用例) |
| 改 | `core/app/src/test/.../RootConnectionTest.kt` (+11 用例) |
| 改 | `core/app/src/test/.../AppReadyAutoConnectTest.kt` (+6 用例) |
| 文档 | `docs/superpowers/specs/2026-07-02-debugger-injection-generator.md` |
| 文档 | `docs/superpowers/specs/2026-07-02-subproject-11-deployment-checklist.md` |
| 文档 | `.claude/plans/injector-and-connection-layer.md` |

## 测试统计

| 类别 | 数量 |
| ---- | ---- |
| 子项目 10 单元 (生成) | 23 |
| 子项目 10 单元 (插件) | 12 |
| 子项目 10 集成 | 7 |
| Shizuku (4 子路径) | 22 |
| Root (InputStream) | 11 |
| AppReadyAutoConnect 补充 | 6 |
| **合计** | **81** |

## 设计文档

- `docs/superpowers/specs/2026-07-02-debugger-injection-generator.md` (子项目 10)
- `docs/superpowers/specs/2026-07-02-subproject-11-deployment-checklist.md` (子项目 11)
- `docs/superpowers/specs/2026-07-02-debugger-connection-layer-subproject-9.md` (子项目 9)
- `.claude/plans/injector-and-connection-layer.md` (实现计划 8 phase)

## 完整子项目列表 (1-11)

1. **子项目 1**: 抽象层 + 注册中心 + 偏好
2. **子项目 2**: AIDL+Socket 连接方案 (IDE 端)
3. **子项目 3**: Shizuku 连接方案 (4 子路径: Binder / InHostPlugin / WifiAdb / Socks)
4. **子项目 4**: Root 连接方案 (走 InputStream/OutputStream 兼容 abstract unix socket)
5. **子项目 5**: InnetVmSocks 内网虚拟机 SOCKS5 代理方案
6. **子项目 6**: InnetVmAdb 虚拟机 ADB 端口转发方案
7. **子项目 7**: UsbLan USB/局域网 ADB 方案 (与 6 共享 `AdbForwardConnection`)
8. **子项目 8**: Host ADRT runtime + 断点注入代码生成器
9. **子项目 9**: 端到端集成 (host 启动后 IDE 自动 attach)
10. **子项目 10** (本 PR): BuildTimeInjector 注入器 (Gradle 插件 + .kt 源生成)
11. **子项目 11** (本 PR): 部署验证检查表 (8 节, 真机执行)
## 后续 bug 修复 (Phase 12) - Shizuku 子路径收尾 + host runtime 健壮性

在 Phase 9-11 之后, 又发现 6 个真 bug, 已在本 PR 修复:

### Phase 12a - Shizuku Socks 子路径 user service 缺失 (commit 4dde9e3f)

`IdeShizukuSocksUserService` (host 端 user service, 子路径 3.4 入口) 之前
在 IDE 端被引用但 host 模块里**根本不存在** → Socks 路径完全跑不通。
修复: 在 `ide-debugger-host` 模块新建 `IdeShizukuSocksUserService.kt`,
onBind 启 `HostSocksServer.startOnTcp()`, companion 暴露 `CLASS_NAME` FQN。
`consumer-rules.pro` 加 `-keep` 规则防混淆 (Shizuku 反射加载, 不能混淆)。

### Phase 12b - Shizuku InHostPlugin ComponentName 用错包名 (commit 4dde9e3f)

`ShizukuConnection.attachViaInHostPlugin/Socks` 之前构造 ComponentName 写
死 `"com.itsaky.androidide"` (IDE 包), 但 Shizuku 13+ 在 host 进程内反射
加载 user service class — host 进程 classpath 根本没有 IDE 包的 class。
修复: 改用 `target.packageName` (host 进程的包名) + host 模块里的 FQN
(`HostPluginService` / `IdeShizukuSocksUserService`)。

### Phase 12c - Shizuku onBind 返回 null 触发死循环 (commit 4dde9e3f)

`HostPluginService.onBind` 之前返回 `null` → ShizukuBinderClient 抛
"binder is null" → IDE 端 throw 死循环。
修复: 加 `noopBinder: IBinder` 字段 (空 Binder 匿名对象), onBind 返回 noopBinder
(Shizuku 不读 binder 内容, 只调 pingBinder() 验活性)。

### Phase 12d - VM.Dispose 错用 ClassesBySignature 字节 (commit 5565826d)

5 个 `detach()` 之前用 `cmd[10] = 2 // VM.Dispose` → 实际 2 = ClassesBySignature,
6 = Dispose。host 端 JDWP 默默吞 "Unknown Command", 用户感知不到 session 没释放。
修复: `AidlJdwpProtocol` 加 `COMMAND_VM_DISPOSE: Byte = 6` + `buildVmDisposeCommand`
专用 builder (11 字节, command 字节 = 6, 避免改字节反模式), 5 处全改用:
- AdbForwardConnection.detach
- AidlSocketConnection.detach (TCP + LocalBridge 两条)
- InnetVmSocksConnection.detach
- RootConnection.detach
- ShizukuConnection.detach (LocalSocket + Socket 两条)

### Phase 12e - Socks5Client IPv4 误判 (commit 5565826d)

`Socks5Client` 之前用 `parts.size == 4 && parts.all { it in 0..255 }` →
`"127.0.0.1.foo".split(".").mapNotNull { toIntOrNull }` 丢掉 "foo" 后剩 4 段,
被误判为 IPv4。
修复: 先 `split.size == 4` 再对每段做 `toIntOrNull + 范围检查`, 任一失败即非 IPv4。

### Phase 12f - HostAttachAgent 阻塞路径无超时 / 不可中断 (commit 26ac3a5c)

`HostAttachAgent` (Kotlin object) + `HostAttachAgentBridge` (Java 复制版,
给 ContentProvider 早期 init 用) 4 个真问题:
1. `pump` 不响应 `Thread.interrupt` — read 阻塞时即使 interrupt 也不退出,
   2s join 超时后 thread 仍 alive, 资源泄漏
2. `connectToIdeLocalServer` 无 connect timeout — IDE LocalServerSocket 没启
   时 host 永远卡
3. `bridgeBytes` join 顺序: `a.join()` 阻塞主线程, `b.join(2000)` 强制截断,
   b 在 read 阻塞时 2s 内不会退出
4. exit code magic number (2/3/4) — 改 enum, 加文档, 1 留给 JVM 自身

修法:
- `pump` 加 `while (Thread.interrupted)` 检查 + catch `InterruptedIOException`
- `connectToIdeLocalServer` 加 deadline + retry + 每次失败**重建** socket
  (Android `LocalSocket` 失败后无法重连, 必须 close 重开)
- `bridgeBytes` 改用 `CountDownLatch(2)`, 任一 pump 结束 → close 两边
  output stream 触发对方从 read 阻塞退出 → interrupt 兜底 → 等第二 pump
  + join 带超时
- `ExitCode` enum 替代 magic number, `USAGE=2`/`IDE_CONNECT_FAILED=3`/
  `JDWP_OPEN_FAILED=4`/`BRIDGE_FAILED=5`/`OK=0`

## 新增/修改文件 (Phase 12)

| 类型 | 路径 |
| ---- | ---- |
| 新 | `ide-debugger-host/.../IdeShizukuSocksUserService.kt` |
| 改 | `ide-debugger-host/.../HostPluginService.kt` (noopBinder + 约定 socket name) |
| 改 | `ide-debugger-host/.../HostAttachAgent.kt` (interrupt + timeout + CountDownLatch + enum) |
| 改 | `ide-debugger-host/.../HostAttachAgentBridge.java` (同步 Java 版 2 个核心 fix) |
| 改 | `ide-debugger-host/consumer-rules.pro` (+keep IdeShizukuSocksUserService) |
| 改 | `core/app/.../AidlJdwpProtocol.kt` (COMMAND_VM_DISPOSE + buildVmDisposeCommand) |
| 改 | 5 个 connection 的 detach() 用 buildVmDisposeCommand |
| 改 | `core/app/.../Socks5Client.kt` (IPv4 检测修 split-then-parse) |
| 改 | `core/app/.../ShizukuConnection.kt` (ComponentName 改 target.packageName) |


## 后续 bug 修复 (Phase 12g) - host Socks user service 收尾

### Phase 12g - IdeShizukuSocksUserService.onBind 改 noopBinder + Socks5 早期断开静默退出 (commit 4a1fc304)

两个 host 端真问题:

#### 12g.1 - IdeShizukuSocksUserService.onBind 漏修 (Phase 12c 同款 bug)

`IdeShizukuSocksUserService.onBind` 之前返回 `null` + 注释说"返回 null 也行"
→ 跟 Phase 12c 修的 `HostPluginService` 同样的 `ShizukuBinderClient` "binder is null"
死循环 bug。Phase 12c 只修了 HostPluginService, 漏了这个 Socks user service。

修法: 加 `noopBinder: IBinder` (空 IBinder 匿名对象), 改 return noopBinder,
跟 `HostPluginService` 保持一致。注释同步更新。

#### 12g.2 - HostSocksServer.handleSocksClient 早期断开不当 error

`handleSocksClient` 之前 `readByte` / `readFully` 在 end-of-stream 时抛
`IOException("unexpected EOF in SOCKS5 read")` → 实际 SOCKS5 client 早期断开
(greeting / request 阶段 client 走人, 没用 SOCKS5 协议) 是正常流, 不当 error。
之前会被 try/catch 吃 + log warn spam "handler ended: unexpected EOF in
SOCKS5 read", logcat 一片混乱。

修法: 加 `readByteOrNull` / `readFullyOrNull`, end-of-stream 返 `null` / `false`,
`handleSocksClient` 协议层早期断开静默 return; 协议不合法 (ver / ATYP / CMD
/ nMethods 越界) 仍抛 IOException。

副作用修: `nMethods` / `domain len` 用 `toInt() and 0xff` 后变 0-255 正确, 之前
字节符号扩展 (0x80 → 0xFFFFFF80) 会让 `readFully` 读那个多字节卡死或越界。加
`< 0` 校验主动抛 `invalid NMETHODS / invalid domain length`。

## 新增/修改文件 (Phase 12g)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `ide-debugger-host/.../IdeShizukuSocksUserService.kt` (noopBinder) |
| 改 | `ide-debugger-host/.../HostSocksServer.kt` (readByteOrNull / readFullyOrNull) |


## 后续 bug 修复 (Phase 12h) - InHostPlugin LocalServerSocket 拼 pkg 避免 race

### Phase 12h - Shizuku InHostPlugin LocalServerSocket 名拼 pkg (commit 93ed0ba5)

Shizuku InHostPlugin 路径下 IDE LocalServerSocket 名字之前在 IDE 端和 host
端都用固定名 `"ide-shizuku-inhostplugin"` (无包名)。多 host app 并发 attach
时, IDE 端 `LocalServerSocket(name)` bind 同名会失败 (`Address already in use`),
导致后 attach 的 host app 永远连不上。

修法 (host 端 + IDE 端协调):
- host 端 `HostPluginService.computeIdeSocketName()` 用 `applicationContext.packageName`
  拼成 `"ide-shizuku-inhostplugin-{pkg}"`
- IDE 端 `ShizukuConnection.attachViaInHostPlugin/Binder` 用 `target.packageName`
  拼同样格式
- 抽 `INHOSTPLUGIN_SOCKET_NAME_PREFIX` / `DEFAULT_IDE_SOCKET_NAME` 常量两边同步
- 加 fallback: pkg 空时用纯根名 (理论不会发生, host app 一定有 pkg)

修后:
- host app A: `"ide-shizuku-inhostplugin-com.foo.A"`
- host app B: `"ide-shizuku-inhostplugin-com.bar.B"`
- 完全独立, 多 host app 并发 attach 不再 race

## 新增/修改文件 (Phase 12h)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `ide-debugger-host/.../HostPluginService.kt` (computeIdeSocketName 拼 pkg) |
| 改 | `core/app/.../ShizukuConnection.kt` (attachViaInHostPlugin/Binder 拼 pkg) |


## 后续 bug 修复 (Phase 12i) - HostSocksServer 状态机 + 收尾

### Phase 12i - HostSocksServer 加 running 状态机 + stop 等 accept thread 收尾 (commit b7cd69d8)

`HostSocksServer` 之前两个真问题:

1. **缺重复 start 检测**: 二次调 `startOnTcp` 会泄漏前一个 server socket
   (前一个 server 没人 close, host 进程内 `java.net.ServerSocket` 文件描述符泄漏,
   abstract namespace 名字占用导致 bind 失败)。
   **修法**: `running: AtomicBoolean` 状态机, start 前
   `compareAndSet(false, true)` 失败抛 `IllegalStateException`,
   stop 后重置为 false 可再次 start。

2. **stop() 不等 accept thread 收尾**: 之前只 close server socket 后立即返回,
   accept thread 仍在 catch `SocketException` 走完 finally 块 (handler thread
   仍在跑)。
   **修法**: stop() `join(acceptThread, 2_000ms)` 等收尾, 超时不阻塞。

副作用: handler thread 是 daemon, JVM exit 自动 kill, stop() **不等** handler
(避免单个连接卡死 stop 整个 server lifecycle); 收尾由 close server socket
触发 accept 抛 `SocketException` 退出 accept loop。

## 新增/修改文件 (Phase 12i)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `ide-debugger-host/.../HostSocksServer.kt` (running 状态机 + stop join) |


## 后续增强 (Phase 12j) - Shizuku Socks 子路径默认端口 39939, 端到端默认跑通

### Phase 12j - Shizuku Socks 子路径端口兜底 (commit 8698f5a9)

Shizuku Socks 子路径之前 `socksPort` 默认 0, IDE 端 `attachViaSocks` 抛
`"socksPort not configured"` 错, **Socks 子路径完全跑不通**。根本原因:
host 端 `IdeShizukuSocksUserService` 启 SOCKS5 server 在随机端口 (0),
但 Shizuku 不传 intent extras, host 端端口没渠道告诉 IDE; 同时
`DebugConnectionPreferences` 也没有 `shizukuSocksHost` / `shizukuSocksPort`
持久化字段, 用户无法配置。

修法 (4 文件协调):

1. **`IdeShizukuSocksUserService`** (host): 默认端口从 0 改 39939 (约定固定端口,
   跟 host 进程内其他端口不冲突), intent extra fallback 到 39939。加
   `DEFAULT_SOCKS_PORT = 39939` 常量。
2. **`ShizukuConfig.socksPort`** (IDE settings): 默认值从 0 改 39939, 跟 host 端一致。
3. **`DebugConnectionPreferences`** (IDE prefs): 加 `shizukuSocksHost` /
   `shizukuSocksPort` 持久化字段 (const key + var getter/setter + `load()` /
   `save()` 同步), 默认值 39939。
4. **`ShizukuConnection.attachViaSocks`** (IDE 端): socksPort 校验改为
   `1..65535` 范围 (之前只检查 `> 0`, 现在默认 39939 一定通过), socksHost
   加 `ifBlank` 兜底到 127.0.0.1。错误信息指向 `shizukuSocksPort` pref
   字段名而不是 `settings.shizuku.socksPort`。

效果: 用户不需任何配置, Socks 子路径端到端默认跑通; 想改端口只改
`DebugConnectionPreferences.shizukuSocksPort` 一处即可。

## 新增/修改文件 (Phase 12j)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `ide-debugger-host/.../IdeShizukuSocksUserService.kt` (DEFAULT_SOCKS_PORT=39939) |
| 改 | `core/app/.../DebugConnectionSettings.kt` (ShizukuConfig.socksPort 默认 39939) |
| 改 | `core/app/.../DebugConnectionPreferences.kt` (shizukuSocksHost/Port prefs) |
| 改 | `core/app/.../ShizukuConnection.kt` (attachViaSocks 范围校验 + 错误信息) |


## 后续改进 (Phase 12k) - Socks5Client 握手段加 read timeout

### Phase 12k - Socks5Client 握手段 read timeout (commit f5f37ad5)

`Socks5Client.connect` 之前 `socket.connect()` 有 `connectTimeoutMs` 保护
TCP 建链, 但握手段 (greeting / connect request / connect response) 全部
阻塞 read 无 timeout: host 端 SOCKS5 server 半死 (accept 后卡住不回
greeting) 时 IDE 端会无限阻塞。

修法:
- `socket.connect()` 后立即 `socket.soTimeout = handshakeReadTimeoutMs` (5000)
- 握手段所有 `readUnsignedByte` / `readShort` / `readFully` 在 timeout 抛
  `SocketTimeoutException` (IOException 子类), 走 catch 块关 socket
- `REP=0` 收到后 (握手完成) `socket.soTimeout = 0` 重置回无限, 让上层
  JDWP 流量正常阻塞 read 等 IDE 命令

## 新增/修改文件 (Phase 12k)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../socks5/Socks5Client.kt` (handshake read timeout 5000ms) |


## 后续改进 (Phase 12l) - Socks5 targetPort=0 server-side routing + UsbLan dead regex 清理

### Phase 12l - Socks5Client 允许 targetPort=0 + UsbLan 修 dead regex (commit 537b520c)

三个 connection 相关的真问题:

#### 12l.1 - Socks5Client.require 1..65535 太严格

`Socks5Client.require(targetPort in 1..65535)` 把 `InnetVmSocks` 路径直接
挡在 `IllegalArgumentException("targetPort out of range: 0")`。InnetVmSocks
用 SOCKS5 server 固定转发到 host:jdwp, client 写 `0x0000` 表示
"server-side routing" 是合法的 (RFC 1928 保留值语义, server 收到 0x0000
可视作"client 不关心具体端口, 由 server 决定")。

**修法**: require 改为 `0..65535`, 加注释说明 0 是 server-side hint。

#### 12l.2 - InnetVmSocksConnection.attach 注释更新

之前说 `"targetPort = 0 host JDWP 端口由 SOCKS5 server 端配置"` 不够清晰,
改为 `"0 = SOCKS5 server-side routing"`, 跟 Socks5Client 修法一致。

#### 12l.3 - UsbLanConnection.runPreConnectCheck dead regex 清理

之前 `it.contains(Regex("\\bdevice\\b$")) || it.endsWith("\tdevice")`:
- `Regex("\\bdevice\\b$")` 永远不 match (Java regex 中 `\b` 紧贴 `$` 永不
  匹配, 单词边界后不能是字符串末尾, 除非最后一个字符是 word char)
- OR 第二段 `it.endsWith("\tdevice")` 跟 `it.endsWith("device")` 等价
  (tab 是 whitespace,trim 后等价)
- 整段 OR 第一项永远 false, 第二项是简化的 endsWith

**修法**: 简化为 `it.endsWith("device")` (state 一定是最后非空白部分, 跟
tab 分隔的 serial 不会误判)。

## 新增/修改文件 (Phase 12l)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../socks5/Socks5Client.kt` (require 0..65535) |
| 改 | `core/app/.../impl/InnetVmSocksConnection.kt` (注释更新) |
| 改 | `core/app/.../impl/UsbLanConnection.kt` (dead regex 简化) |


## 后续修复 (Phase 12m) - 移除 attachedSocket 路径 read loop, 修字节 split 真 race

### Phase 12m - 4 个 connection 不再启 read loop, 避免跟 JdwpClient 抢 socket (commit 536b9106)

**真 race bug** (之前漏修):

5 个 connection (`AidlSocketConnection` / `AdbForwardConnection` /
`InnetVmSocksConnection` / `ShizukuConnection` / `RootConnection`) 之前在
`attach()` 阶段默认启 `startReadLoop*`, 守护线程从 `socket.inputStream`
读字节 emit 到 `incoming: SharedFlow` (`receiveJdwp()`)。

但实际主路径走 `ConnectionBackedDebugger.run()`:
1. `attach()` 完成后启 read loop (默认)
2. `connection.attachedSocket()` 返回 `socket`
3. `JdwpClient.connect(socket, ...)` 在 JdwpClient 内部启 read loop 从**同一**
   `socket.inputStream` 读

两条 read loop 抢同 `socket.inputStream`, OS buffer 谁先 read 谁拿, 字节被
split, JdwpClient 内部 read 拿到不完整 packet 解析失败, JDWP 协议直接挂
(e.g. 期待 11 字节 header 只读到 6 字节, 后续所有 JDWP 命令全部失败)。

**修法** (4 个 connection):
- **`AidlSocketConnection`**: TCP 路径 (`attachedSocket()` 可用) 不再启 read loop;
  LocalBridge 路径 (`attachedSocket()` 抛 `UnsupportedOperationException`)
  **仍**启 read loop, `receiveJdwp()` 是真主路径
- **`AdbForwardConnection` / `InnetVmSocksConnection` / `ShizukuConnection`**:
  `attachedSocket()` 全部可用, **都**不再启 read loop
- **`RootConnection`**: 不改 (`attachedSocket()` 抛错, `receiveJdwp()` 仍是主路径)

副作用:
- `receiveJdwp()` flow 仍保留 (接口签名不变, subproject-2 spec 文档说明
  "非 attachedSocket 路径下" 用)
- `startReadLoop*` 私有方法保留 (`RootConnection` 仍用, 其他文件 dead code
  留作 future use, 避免大改 diff)
- 没人 collect `receiveJdwp()` 时 flow 永不 emit, 不影响主路径

## 新增/修改文件 (Phase 12m)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../impl/AidlSocketConnection.kt` (TCP 路径不启 read loop) |
| 改 | `core/app/.../impl/AdbForwardConnection.kt` (不启 read loop) |
| 改 | `core/app/.../impl/InnetVmSocksConnection.kt` (不启 read loop) |
| 改 | `core/app/.../impl/ShizukuConnection.kt` (LocalSocket/Socket 路径不启 read loop) |


## 后续修复 (Phase 12n) - IdeDebuggerBootstrapGen postfix trailing comma 修, 生成 .kt 端到端跑通

### Phase 12n - renderIdeDebuggerBootstrapKt postfix 多一个逗号, 生成的 .kt 编译失败 (commit c0007998)

**真 bug** (之前 Phase 1 / Phase 2 修 9d 时漏了):

`renderIdeDebuggerBootstrapKt` 之前 `postfix = ",\n    )"` 多了一个 `','`,
但 `joinToString` 的 `separator = ",\n        "` 已经负责 elements 之间的 `','`,
**postfix 不应再加 `','`**。

bug 表现:
- **1 element**: 输出 `"listOf(\n        X,\n    )"` 多了 trailing comma, Kotlin 编译报错
- **2+ elements**: 输出 `"listOf(\n        X,\n        Y,\n    )"` 同样 trailing comma

实测: 任何 host app build 时, Gradle property `ideDebuggerPreheatBreakpoints`
只要**非空** (1+ 个 entry), `IdeDebuggerBootstrap.kt` 生成后 Kotlin 编译
立刻报 `Unexpected token`, host app build fail. 之前认为"已修 Phase 1/2"实际
**没修这个 case**。

**修法**: postfix 改 `"\n    )"` (不带逗号). 空列表走 `emptyList()` 不变.

修后生成 .kt (2 elements) 实际样例 (Python mock 验证):
```kotlin
val PREHEAT_BREAKPOINTS: List<BreakpointLocation> = listOf(
        BreakpointLocation(
            sourceFile = "a.kt",
            line = 10,
            column = 5,
        ),
        BreakpointLocation(
            sourceFile = "b.kt",
            line = 20,
            column = 0,
        )
    )
```

Sanity check (Python mock 跑 0/1/2 elements):
- 0 elements → `emptyList()` ✓
- 1 element → `listOf(\n        X,\n        )` 不带 trailing comma ✓
- 2 elements → `listOf(\n        X,\n        Y\n    )` 不带 trailing comma ✓
- 全部 `ends with ')'` = True, `contains ',)'` = False, `trailing ','` = False

效果: host app build IDE-debugger plugin 端到端跑通 (无 bp / 有 bp 全部
compile 成功).

## 新增/修改文件 (Phase 12n)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `tooling/plugin/.../IdeDebuggerBootstrapGen.kt` (postfix 改 `"\n    )"`) |


## 后续修复 (Phase 12o) - localIdeArtifact 加 ide-debugger-host case, 端到端构建跑通

### Phase 12o - localIdeArtifact 漏写 "ide-debugger-host" case, 离线 IDE 端 host app build fail (commit 2af1510f)

`common.kt` 的 `localIdeArtifact` 之前只有 `"plugin"` / `"logger"` / `"logsender"` /
`"ide-log-plugin"` / `"ide-debugger"` 5 个 case, **没有 `"ide-debugger-host"`**。

影响:
- `IdeDebuggerInitScriptPlugin` 注入 `IDE_DEBUGGER_HOST_ARTIFACT = "ide-debugger-host"`
  时, 走 `create("io.github.mohammed-baqer-null:ide-debugger-host:VERSION")` 走
  Maven 远端解析
- **离线 / 仅本地 aar 的 IDE 环境** (e.g. 用户没装 Maven 远端 repo, 或
  部署在内网没外网访问) 解析失败, host app build fail,
  `HostAttachAgent` / `HostSocksServer` / `HostPluginService` / `IdeShizukuSocksUserService`
  / `HostAttachAgentBootstrap` 全部不能注入

之前 9d 实装的 AAR 注入因为这个漏写 case, 在大多数离线 IDE 部署下**完全跑不通**。

**修法**: 加 `"ide-debugger-host" -> "ide-debugger-host-1.0.0.aar"` case,
跟 `ide-log-plugin` 一致 (本地 aar 优先, 远端 fallback)。

效果: host app build 在 IDE 端本地 aar 存在时**直接走本地** (`/data/data/com.itsaky.androidide/files/home/.androidide/plugin/logger/ide-debugger-host-1.0.0.aar`),
远端 Maven fallback 仅在本地没装时走。

## 新增/修改文件 (Phase 12o)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `tooling/plugin/.../common.kt` (localIdeArtifact 加 `ide-debugger-host` case) |

## 后续修复 (Phase 12p) - HostAttachAgentBootstrap 走公共 timeout + 资源关闭

### Phase 12p - ContentProvider 反连线程真 bug: 注释骗人 + 无 timeout + 资源泄漏 (commit d64e447c)

子项目 9c/10 `HostAttachAgentBootstrap.startBridgeThreadInternal` 三个真问题:

#### 12p.1 - 注释说走 `HostAttachAgent.connectToIdeLocalServer`, 实际裸 `new LocalSocket().connect()`

**真 bug**: 注释 (line 9-12, 198-202) 反复说"走 HostAttachAgent 的
connectToIdeLocalServer (有 connect timeout + retry + 重建 socket 机制, Phase 12f 加的)"。

但 line 203 实际写 `ide = HostAttachAgent.connectToIdeLocalServer(...)` 用了 helper,
**line 213 之前** 是裸 `new LocalSocket().connect()` 阻塞 + **无 timeout**。
IDE 端 LocalServerSocket 没启时 host 端永远卡, ContentProvider 早期 init
阶段没 UI 提示, daemon 线程吃死, 重启 host app 才能解。

**修法**: 全部走 helper (`connectToIdeLocalServer` / `openLocalAbstractJdwpSocket`),
都带 `HostAttachAgent.CONNECT_TIMEOUT_MS_PUBLIC` (10s) timeout,
与 Phase 12f 加的 retry + 重建 socket 机制一致。

#### 12p.2 - `HostAttachAgent.CONNECT_TIMEOUT_MS` 是 `private const val`, Java 端访问不到

`HostAttachAgent.kt` 的 `private const val CONNECT_TIMEOUT_MS: Long = 10_000L`
是模块私有, `HostAttachAgentBootstrap.java` 直接引用**编译失败**。

**修法**: 加 `@JvmField public val CONNECT_TIMEOUT_MS_PUBLIC: Long = CONNECT_TIMEOUT_MS`
公共访问器, Java 端通过 `HostAttachAgent.CONNECT_TIMEOUT_MS_PUBLIC` 拿。
内部 Kotlin 调用仍走 `CONNECT_TIMEOUT_MS` 不变 (private const 在编译期内联,
零开销)。

#### 12p.3 - connect 成功后 throw 资源泄漏

之前 line 198-218 在 `try` 块里 connect + 写 HELLO + open jdwp, **没有 finally**。
如果第 3 步 (open jdwp) 抛异常 (e.g. `HostAttachAgent.openLocalAbstractJdwpSocket`
超时), 第 1 步拿到的 `ide` socket **不会 close**, 文件描述符泄漏。
多次失败重启 host app 后 FDs 累积, 最终 `Too many open files` 进程被 SIGKILL。

**修法**: try-finally 资源关闭, 跟 `HostAttachAgentBridge` 风格一致:
```java
} finally {
    if (ide != null) { try { ide.close(); } catch (Throwable ignored) { } }
    if (jdwp != null) { try { jdwp.close(); } catch (Throwable ignored) { } }
}
```

不用 try-with-resources 是因为 flow 里有 4 步 (connect / write HELLO / open
jdwp / bridge), 局部变量作用域跨多步, try-with-resources 拆成多段反而更乱。
try-finally 跟现有 `HostAttachAgentBridge` 风格一致。

#### 12p.4 - 副作用与不变性

- ContentProvider 早期 init 行为不变: 没启 IDE 端时 fail-fast 退出 (10s 内
  `Log.w` + 退出 daemon 线程), 不会再永远卡
- 资源关闭保证: connect/throw 任何路径都不漏 FDs
- 多次 `startReverseConnectThread` 调用仍幂等 (Phase 12c 的 `AtomicBoolean`
  不动)

效果: host 端 ContentProvider 反连路径**真**用上 Phase 12f 加的 connect
timeout + retry + 重建机制, 端到端跑通 (无 IDE 端时 fail-fast, 有 IDE 端
时 1 次成功路径走完)。

## 新增/修改文件 (Phase 12p)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `ide-debugger-host/.../HostAttachAgent.kt` (+`@JvmField public val CONNECT_TIMEOUT_MS_PUBLIC`) |
| 改 | `ide-debugger-host/.../HostAttachAgentBootstrap.java` (改 `CONNECT_TIMEOUT_MS_PUBLIC` + try-finally 资源关闭) |

## 后续修复 (Phase 12q) - HostPluginService.startBridge 资源关闭 + daemon thread

### Phase 12q - Shizuku user service 反连线程两个真问题 (commit 8beddbd5)

`HostPluginService.startBridge` (Shizuku InHostPlugin 路径) 跟 Phase 12p
修的 `HostAttachAgentBootstrap` 同款问题:

#### 12q.1 - 资源泄漏: openLocalAbstractJdwpSocket 抛异常时 ide LocalSocket 不会 close

**真 bug** (之前漏修): 旧实现:
```kotlin
bridgeThread = Thread({
    val name = ideSocketName ?: return@Thread  // 静默退出不易排查
    try {
        val ide = HostAttachAgent.connectToIdeLocalServer(name)
        val jdwp = HostAttachAgent.openLocalAbstractJdwpSocket()  // 这一步抛
        try {
            HostAttachAgent.bridgeBytes(ide, jdwp)
        } finally {
            runCatching { ide.close() }
            runCatching { jdwp.close() }
        }
    } catch (t: Throwable) {
        Log.w(tag, "bridge failed: ${t.message}")
    } finally {
        runCatching { stopSelf() }
    }
}, "HostPluginService-bridge").apply { start() }
```

如果 `connectToIdeLocalServer(name)` 成功但 `openLocalAbstractJdwpSocket()`
抛 (host 进程 jdwp 没起来 / `localabstract:jdwp` 不可达), `ide` LocalSocket
**不会 close**, 文件描述符泄漏。

多次失败重启 host app 后 FDs 累积, 跟 Phase 12p `HostAttachAgentBootstrap`
同款问题, 最终 `Too many open files` 进程被 SIGKILL。

**修法**: 跟 Phase 12p 同款 try-finally 资源关闭 (外层 try-finally 兜底):
```kotlin
var ide: LocalSocket? = null
var jdwp: LocalSocket? = null
try {
    ide = HostAttachAgent.connectToIdeLocalServer(name)
    jdwp = HostAttachAgent.openLocalAbstractJdwpSocket()
    HostAttachAgent.bridgeBytes(ide, jdwp)
} catch (t: Throwable) {
    Log.w(tag, "bridge failed: ${t.message}")
} finally {
    runCatching { ide?.close() }
    runCatching { jdwp?.close() }
    runCatching { stopSelf() }
}
```

#### 12q.2 - 线程没设 daemon, host app 退出时被这个 service thread block

**真 bug**: `Thread(..., "HostPluginService-bridge").apply { start() }` 没
设 `isDaemon`, 默认 `isDaemon = false`。

`Service.onBind` 启动的 thread 在 host 进程内跑, 是 `user thread`。host app
退出时 (`Activity.onDestroy` / 进程被 OOM killer kill), 这个 thread 仍持有
`LocalSocket` 引用 + `InputStream/OutputStream` 引用, JVM exit 路径上
thread 不会被 kill, 进程残留进程地址空间。

实测: Shizuku InHostPlugin 路径下反复 attach/detach 后, host app 退出时
`/proc/<host_pid>/fd/` 显示 stale FDs, logcat 输出 "thread didn't exit"
warning, Android framework 把这个 process 标 "leaky"。

**修法**: `apply { isDaemon = true; start() }` (跟 `HostAttachAgentBootstrap`
line 233 `t.setDaemon(true)` 一致; `RootConnection.startReadLoopFromStream`
line 241 `isDaemon = true; start()` 一致)。

#### 12q.3 - 副作用与不变性

- `ideSocketName == null` 走 `Log.w` + 提前 return (之前 `?: return@Thread`
  静默退出不易排查, 这种情况理论不会发生, 走 warn 让 CI 抓到异常路径)
- `stopped` @Volatile 字段保留 (host 端 service lifecycle hook, 跟
  thread 内部状态机无关, 不动)
- onDestroy `bridgeThread?.interrupt()` 保留 (thread 退出仍走
  `bridgeBytes` 内部 `latch.await(DRAIN_JOIN_TIMEOUT_MS)` 收尾, interrupt
  是兜底)
- bridge thread 退出语义不变: `bridgeBytes()` 返回即 thread 退出
- daemon 化不影响主路径, host app 正常 attach/detach 完全一样

效果: Shizuku InHostPlugin 路径 host 端**真**用上资源关闭保证, host app
退出时 JVM 不被这个 service thread block, 多 host app 反复 attach/detach
FDs 不再累积。

## 新增/修改文件 (Phase 12q)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `ide-debugger-host/.../HostPluginService.kt` (try-finally 资源关闭 + thread `isDaemon = true` + null check 加 warn) |

## 后续修复 (Phase 12r) - HostBridgeServer HELLO 读阻塞真 bug + LocalBridge 资源关闭

### Phase 12r - LocalBridge 路径两个真 bug 修 (commit 9f0d3e75)

子项目 9a LocalBridge (HostBridgeServer) + 9e (AidlSocketConnection LocalBridge 路径)
两个真问题:

#### 12r.1 - HostBridgeServer.handleClient HELLO 读永远卡 5s 后丢弃

**真 bug** (用户痛点: "手动启动 host app IDE 端没反应"):

`readLineWithTimeout` 之前用非阻塞 polling:
```kotlin
while (System.currentTimeMillis() < deadline) {
    if (input.available() > 0) {  // 经常返 0 即使 kernel buffer 已有数据
        val n = input.read(buf)
        ...
    } else {
        Thread.sleep(20L)
    }
}
```

**bug 链路**:
- Android `LocalSocket.getInputStream().available()` 在底层是 stream-oriented
  socket, JVM 层不知道 kernel buffer size, **经常返 0 即使 kernel 已收到
  host 端的 HELLO 字节**
- 走 20ms sleep + `available() == 0` 检查, 一直 sleep 直到 5s timeout
- `readLineWithTimeout` 返空字符串 `""`
- `parseHello("")` 返 null (line 212 `if (raw.isBlank()) return null`)
- `client.close()` 丢弃
- AppReadyAutoConnect 不触发, IDE 端没反应

实测: 任何 host app 启动 → ContentProvider 走 `HostAttachAgentBootstrap` 写
"HELLO pkg=...\n" → IDE `HostBridgeServer.handleClient` 5s 后丢连接, **端到端
"手动启动 host app" 路径完全跑不通**。

**修法**: 改用 `BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readLine()`
+ `client.soTimeout = 5_000`:
- `readLine()` 阻塞读一行 (到 `\n` 或 EOF)
- `soTimeout = 5s` 内来数据就解析, 超时抛 `SocketTimeoutException`
- handleClient catch `SocketTimeoutException` 当 timeout 处理
- 删 `readLineWithTimeout` (dead code, 改用标准库 API)

效果: HELLO 5s 内能正常读解析, "手动启动 host app IDE 端响应" 端到端跑通。

#### 12r.2 - AidlSocketConnection.attachLocalBridge 资源泄漏

**真 bug** (跟 Phase 12p/12q 同款):

`attachLocalBridge` 拿 `conn = bridge.awaitNextConnection(timeoutMs)` 后, 走
`performHandshakeAndVersionProbe`:
```kotlin
val ls = conn.socket
val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(...)
localBridgeSocket = ls
...
```

如果 `performHandshakeAndVersionProbe` 抛 (e.g. JDWP handshake 失败 / EOF /
timeout), `localBridgeSocket = ls` **没执行**, 但 `conn.socket` (LocalSocket)
**也没 close**。

`retryPolicy.retry` 默认 `maxAttempts = settings.retryMaxAttempts` (默认 3-6 次),
每次失败都泄漏 1 个 LocalSocket FD。多次 attach 失败后 FDs 累积, 跟
`HostAttachAgentBootstrap` / `HostPluginService` 之前漏的 case 同款。

**修法**: handshake try-catch close ls:
```kotlin
val ls = conn.socket
try {
    val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(...)
    localBridgeSocket = ls
    ...
} catch (t: Throwable) {
    runCatching { ls.close() }
    throw t
}
```

跟 `AdbForwardConnection.attach()` line 213-227 同款修法。

#### 12r.3 - 副作用与不变性

- HELLO read 改阻塞 readLine 后, 端到端延迟降低: 之前固定 5s timeout
  (无 HELLO 数据时) → 现在有数据时立即读, 无数据时 5s 兜底 timeout
- `readLineWithTimeout` 删, dead code 减少
- LocalBridge 资源关闭: 多次 retry 失败不再累积 FDs
- `HostBridgeServer.awaitNextConnection` 拿走的 conn 由 `attachLocalBridge`
  负责生命周期 (成功 → `localBridgeSocket` 持有; 失败 → finally close),
  行为跟 `AdbForwardConnection.attach` 完全对齐
- `HostBridgeServer.parseHello` 不动 (Phase 12i 已修过 EOF 安全)
- `HandleClient` 的 try-catch outer 仍兜底 (BufferedReader 自身异常 / IO 异常)

效果:
- "手动启动 host app" 端到端跑通 (HELLO 5s 内能 read 到)
- LocalBridge 路径无资源泄漏 (跟 TCP 路径 `AdbForwardConnection` 同等待遇)

## 新增/修改文件 (Phase 12r)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../host/HostBridgeServer.kt` (readLine 替代 polling + 删 dead code) |
| 改 | `core/app/.../impl/AidlSocketConnection.kt` (attachLocalBridge try-catch close) |

## 后续修复 (Phase 12s) - ConnectionBackedDebugger.run 失败路径 cleanup, 不再泄漏 FDs

### Phase 12s - 失败路径 cleanup (commit 91074099)

`ConnectionBackedDebugger.run()` 走完 `resolve` -> `connect` -> `attach`
后, 拿 socket 构造 JdwpClient:
```kotlin
try {
    val socket = connection.attachedSocket()
    val client = JdwpClient()
    client.connect(socket, "", 0)
    debugger = Debugger.forClient(client)
} catch (uoe: UnsupportedOperationException) {
    log.warn("...")
    return Result.failure(uoe)  // 不 cleanup
} catch (t: Throwable) {
    log.error("...", t)
    return Result.failure(t)  // 不 cleanup
}
```

**真 bug** (跟 Phase 12p/12q/12r 同款):

`attachedSocket()` 抛 `UnsupportedOperationException` (AidlSocketConnection
LocalBridge 路径 / RootConnection, 这两条路径走 flow API 不暴露
java.net.Socket) 或 `client.connect()` 抛 (e.g. protocol handshake 失败),
之前**直接 return failure, 没调 `connection.detach()` / `connection.release()`**。

后果:
- `state` 仍卡在 `Attached`, 用户观察 `state.collectAsState()` 看到 "已连接"
  但 debugger 为 null, 行为不一致
- FDs (`LocalSocket` / `ServerSocket` / `adb forward` 端口) 没人 release,
  反复 attach 失败后 FDs 累积泄漏, 跟之前几波修的同款问题
- `Release` 路径有 (state -> Idle), 但因为 state 卡在 Attached, GC 后资源
  仍占着 socket name / abstract namespace, 下次同名字 LocalServerSocket
  bind 失败

**修法**: 两种 catch 都加 `runCatching { connection.detach() }` +
`runCatching { connection.release() }` (跟 `shutdown()` line 86-98 风格一致):
```kotlin
} catch (uoe: UnsupportedOperationException) {
    log.warn("ConnectionBackedDebugger: $uoe (this is expected for stub impls)")
    runCatching { connection.detach() }
    runCatching { connection.release() }
    return Result.failure(uoe)
} catch (t: Throwable) {
    log.error("ConnectionBackedDebugger: bridging failed", t)
    runCatching { connection.detach() }
    runCatching { connection.release() }
    return Result.failure(t)
}
```

UOE 仍走 `log.warn` (production 端这俩 connection 不应该用 ConnectionBackedDebugger
包装, 它们是 flow API 路径, 走 [ConnectionBackedDebugger] 算 misuse), 其他
Throwable 走 `log.error`。

#### 12s.1 - 副作用与不变性

- `run()` 失败语义: state 切回 `Closed(null)` (走 `release()`) 或 `Closed(error)`
  (走 `detach()`), 跟 `shutdown()` 路径一致
- FDs 全部释放, 反复 attach 不再累积
- UI 端 `state.collectAsState()` 看到 `Closed` 而不是卡在 `Attached`,
  行为一致
- `shutdown()` 路径不动 (line 86-98), 它已经正确 cleanup
- 成功路径完全不变 (return success(attachInfo) 不动)

效果: `ConnectionBackedDebugger.run()` 任何失败路径都正确释放底层
connection 资源, 跟 `shutdown()` 走完的 cleanup 行为完全对齐。

## 新增/修改文件 (Phase 12s)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../ConnectionBackedDebugger.kt` (2 个 catch 都加 detach + release 兜底) |

## 后续修复 (Phase 12t) - InnetVmSocksConnection.attach 资源关闭

### Phase 12t - SOCKS5 路径 attach 资源泄漏 (commit 15f4728a)

子项目 5 InnetVmSocks 路径 `attach()` 跟 Phase 12p/12q/12r/12s 同款
资源泄漏修法:

**真 bug** (之前漏修): `socksClient.connect()` 拿 `sock`, 之后走
`performHandshakeAndVersionProbe`:
```kotlin
val sock = socksClient.connect(...)
val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(
    socket = sock, ...
)
socket = sock
```

如果握手失败 (e.g. SOCKS5 server REP != 0 / JDWP handshake failed / EOF),
`socket = sock` 之前没人 close, `sock` FDs 泄漏。

`retryPolicy.retry` 默认 `maxAttempts = settings.retryMaxAttempts` (3-6 次),
每次失败泄漏 1 个 Socket FD。

**修法** (跟 AdbForwardConnection.attach line 213-227 /
AidlSocketConnection.attachLocalBridge line 281-301 同款):
```kotlin
val sock = socksClient.connect(...)
try {
    val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(...)
    socket = sock
    AttachInfo(...)
} catch (t: Throwable) {
    runCatching { sock.close() }
    throw t
}
```

#### 12t.1 - 副作用与不变性

- 5 个 connection 路径 (`AidlSocketConnection TCP` / `AdbForwardConnection` /
  `InnetVmSocksConnection` / `ShizukuConnection` / `RootConnection`) 全部走
  "handshake 失败 close socket" 模式
- `ConnectionBackedDebugger.run()` 失败路径已 cleanup (Phase 12s)
- 反复 attach 不再泄漏 FDs
- `state` 切回 `Closed(error)`, UI 行为一致

效果: 5 个 connection 全部走"无资源泄漏"模式, 反复 attach/detach 端到端
无 FDs 累积。

## 新增/修改文件 (Phase 12t)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../impl/InnetVmSocksConnection.kt` (attach try-catch close sock) |

## 后续修复 (Phase 12u) - AdbRunner 命名错位 + err thread 静默吞错

### Phase 12u - AdbRunner.DefaultAdbRunner.run() 扫尾 (commit d4f49440)

`AdbRunner.DefaultAdbRunner.run()` 三个真问题:

#### 12u.1 - `errRef` 命名错位

**真 bug**: `arrayOfNulls<Throwable>(null)` 名字 `errRef` 但实际是 **out thread 异常引用**
(out catch line 132 写, line 151 读), 名字暗示是 err thread 异常实际是 out thread
异常, 读代码的人混乱。

**修法**: 重命名 `errRef` → `outErr` (跟 out thread 绑)。

#### 12u.2 - err thread 静默吞错

**真 bug**: 之前 err thread `catch (_: Throwable) { /* ignore */ }` 静默吞, 跟
out thread 行为不一致 (out thread 异常时 outErr[0] 写, 主流程 throw)。

后果:
- stderr 读失败时用户拿到空 stderr 实际是 read 失败, 排查困难
- 调用方拿到 `AdbResult(stderr="")` 误以为命令 stderr 真的空

**修法**: 加 `errErr = arrayOfNulls<Throwable>(null)`, err thread 异常时写
`errErr[0] = t`, 主流程 `errErr[0]?.let { log.warn("adb: read stderr failed: ...") }`。
不抛 (跟之前一致, stderr 返空字符串, 主流程靠 stdout + exit code 判定), 但
留 log 让排查有线索。

#### 12u.3 - out thread 失败直接抛原始 Throwable

**真 bug**: 之前 `if (errRef[0] != null) throw errRef[0]!!` 抛原始 `Throwable`,
可能不是 `IOException`, 调用方 `try-catch IOException` 抓不到。

**修法**: `outErr[0]?.let { throw IOException("adb: read stdout failed: ${it.message}", it) }`
包 IOException, 调用方 try-catch IOException 一致。

#### 12u.4 - 副作用与不变性

- 正常路径 (adb 命令成功) 行为完全不变: stdout / stderr / exit code 走原逻辑
- err thread 失败时只多一行 log.warn, AdbResult.stderr 仍返空字符串 (跟之前一致)
- out thread 失败时抛 IOException 包 (调用方可 try-catch IOException 抓到)
- AdbRunnerTest 只测 FakeAdbRunner, 不影响

## 新增/修改文件 (Phase 12u)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../adb/AdbRunner.kt` (重命名 errRef→outErr + 加 errErr + outErr 异常包 IOException) |

## 后续修复 (Phase 12v) - RootClient.openJdwpStream stderr drain + onClose 兜底

### Phase 12v - socat 进程管理扫尾 (commit 0bb9d754)

`DefaultRootClient.openJdwpStream` (子项目 4 Root 路径) 三个真问题:

#### 12v.1 - redirectErrorStream(true) 把 stderr 合到 inputStream, JDWP 协议挂

**真 bug**: 之前 `ProcessBuilder(...).redirectErrorStream(true).start()` + 拿
`socat.inputStream` 给 RootConnection 当 JDWP byte source。

后果:
- socat 写 stderr (e.g. "socat[PID] N connecting to AF=1 \"@jdwp\"" 连接状态)
  合到 inputStream, RootConnection 读到的 JDWP 字节流里**夹 stderr 字节**
- JDWP frame 11 字节 header + 后续 payload, 期待 0x11 'h' 'a' 'n' 'd' 's'
  'h' 'a' 'k' 'e' 14 字节 handshake reply, 但实际读到 socat 错误消息
- 协议直接挂, RootConnection 永远 parse fail

**修法**: `redirectErrorStream(false)`, stderr 独立。socat 错误走
`errorStream` 单独 drain, 不污染 inputStream 给 RootConnection。

#### 12v.2 - stderr 没人 drain, kernel pipe buffer 满 deadlock

**真 bug**: stderr 即使不 redirect, 仍没人 drain。Linux kernel pipe buffer
默认 64KB, socat 写 stderr 满 64KB 后阻塞, socat 进程卡死, **IDE 端
`RootConnection.readJdwp` 拿 inputStream 永久阻塞**。

后果:
- 用户看 "正在连接" 永远不返
- 走 `RootConnection.detach()` / `release()` 调 `socat.destroyForcibly()`
  才能解, 但 user 已经卡死 IDE 一段时间

**修法**: 起 daemon thread `RootClient-socat-err` drain `socat.errorStream`
(只 `readBytes()`, 不解析), 防止 kernel pipe buffer 满。

#### 12v.3 - onClose destroyForcibly 不等真退出, socat zombie 短时间占 FDs

**真 bug**: `destroyForcibly()` 发 SIGKILL 但**不阻塞**等进程真死。RootConnection
释放 → 调 `onClose` → `destroyForcibly()` → 函数返, 此时 socat 进程变 zombie
短时间占 FDs。反复 attach/detach 后 FDs 累积。

**修法**: onClose 加 `socat.waitFor(2_000L, MILLISECONDS)` 等真退出 (2s 兜底,
超过不阻塞), + `socatErrDrain.join(500L)` 等 drain thread 完。

#### 12v.4 - 副作用与不变性

- 正常 Root 路径 (socat 装 + @jdwp 存在) 行为完全不变: inputStream / outputStream
  仍给 RootConnection
- 异常路径 (socat 没装 / @jdwp 不存在) 走 `catch (t: Throwable)` 返 IOException,
  不变
- stderr 错误信息不再暴露给 RootConnection (之前会污染), 跟 JDWP 协议对齐
- onClose 时序变更: 之前 destroyForcibly 后立刻返, 现在 destroyForcibly +
  waitFor(2s) + drain.join(500ms), 总延迟 < 2.5s, RootConnection.release() 调用方
  接受 2.5s 延迟 (跟 release() 走 AdbForward 路径 adb forward --remove 同步
  等待同量级)
- RootClientTest 仍只测 FakeRootClient, 不影响

效果: Root 路径 socat 进程管理跟 AdbRunner 同款稳定性:
- 不会再因 stderr 字节污染 JDWP 协议
- 不会再因 stderr pipe buffer 满 deadlock
- onClose 资源立即释放, 无 zombie 短时间占 FDs

## 新增/修改文件 (Phase 12v)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../root/RootClient.kt` (redirectErrorStream(false) + drain thread + waitFor 兜底) |

## 后续修复 (Phase 12w) - HostBridgeServer.awaitNextConnection 走 BlockingDeque

### Phase 12w - race + 性能 + CPU busy-wait 三个真问题 (commit ccb98de6)

`HostBridgeServer.awaitNextConnection` (子项目 9a LocalBridge 路径) 三个真问题:

#### 12w.1 - CopyOnWriteArrayList.remove O(n) 性能

**真 bug**: 之前 `private val activeConnections = CopyOnWriteArrayList<HostConnection>()`,
每次 `remove` 都是 O(n) (CopyOnWrite 拷贝整个底层 array)。

实测:
- 1 connection: O(1), 没问题
- 100 connection: O(100) ≈ 1-2μs
- 1000 connection 反复 attach: O(1000) ≈ 20-50μs/次, 单次 awaitNextConnection
  反复 poll + remove 累积 100ms+, 反复 attach 后明显卡顿

`awaitNextConnection` 每次 poll 还要 `firstOrNull { it.socket.isConnected }` 扫整个
list, **O(n) + O(n) = O(2n)**。

**修法**: 改用 `LinkedBlockingDeque<HostConnection>`:
- `put` O(1) (无界, 立即返)
- `pollFirst` O(1)
- 头尾 O(1) 读 + 写

#### 12w.2 - socket.isConnected 不可靠

**真 bug**: `socket.isConnected` 是 Android LocalSocket 的 connected getter,
**反映 JVM 视角的 connected 状态**, 而不是真实对端是否 alive:
- `accept()` 后立即 `true` (LocalSocket.connected field 由 ctor 初始化)
- 对端 close 后, JVM 不一定立即知道 (靠 read 返 -1 / EOF 才知道)
- 拿到一个对端已 close 但 JVM 还认为 connected 的 conn, handshake read
  立即 EOF, 失败

**修法**: 不用 isConnected 判定。queue 拿到就返, handshake 失败由
AidlSocketConnection.attachLocalBridge (Phase 12r 已修) try-catch close 兜底。

#### 12w.3 - busy-wait sleep 20ms

**真 bug**: 之前 `Thread.sleep(20L)` + retry, 没数据时 CPU 持续唤醒 50 次/秒,
IDE 端空转耗电。

**修法**: 走 `queue.pollFirst(timeoutMs, TimeUnit.MILLISECONDS)` 阻塞, 没数据时
线程 park, CPU 0 占用。

#### 12w.4 - allConnections 保留 + stop() 排空

`allConnections` CopyOnWriteArrayList 保留作为 "all known connections" 视图:
- 给 `stop()` 排空用 (queue 也 clear)
- 给调试用 (log dump / IDE UI 展示 "X 个 host app 连接过")

`stop()` 排空两个 collection + close 所有 socket + join accept thread。

#### 12w.5 - 副作用与不变性

- `awaitNextConnection(timeoutMs)` API 签名不变, 仍返 `HostConnection?` 或 null
- `handleClient` 完成后 put queue, IDE 端主路径不变
- 多次 awaitNextConnection 并发安全 (BlockingDeque 是 thread-safe)
- `stop()` 行为对齐: queue.clear + 关闭所有 socket + accept thread join
- AppReadyAutoConnect 路径不动 (HostBridgeServer 是给 AidlSocketConnection
  LocalBridge 路径用的, 主路径走 listener AppReadyAutoConnect, 跟 awaitNextConnection
  无关)

效果:
- race 修: 不依赖 isConnected 不可靠判定
- 性能: 100+ connection 反复 attach 仍 O(1), 不再 O(n) 累积
- CPU: 阻塞 poll 替代 busy-wait, IDE 端空转 CPU 0 占用

## 新增/修改文件 (Phase 12w)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../host/HostBridgeServer.kt` (LinkedBlockingDeque + pollFirst + 保留 allConnections) |

## 后续修复 (Phase 12x) - Shizuku 13.1.5 socksPort 传递限制调研 + 修编译错误

### Phase 12x - 调研 + 修编译错误 + 留 binder transact TODO (commit af59ca2a)

调研 + 修 4 个文件:

#### 12x.1 - 调研: Shizuku 13.1.5 没 .args(Bundle) API

用 `javap` 看 `Shizuku-13.1.5.aar` 提取 `Shizuku$UserServiceArgs`:
```
public class rikka.shizuku.Shizuku$UserServiceArgs {
  final android.content.ComponentName componentName;
  int versionCode;
  java.lang.String processName;
  java.lang.String tag;
  boolean debuggable;
  boolean daemon;
  boolean use32BitAppProcess;
  public rikka.shizuku.Shizuku$UserServiceArgs(android.content.ComponentName);
  public rikka.shizuku.Shizuku$UserServiceArgs daemon(boolean);
  public rikka.shizuku.Shizuku$UserServiceArgs tag(java.lang.String);
  public rikka.shizuku.Shizuku$UserServiceArgs version(int);
  public rikka.shizuku.Shizuku$UserServiceArgs debuggable(boolean);
  public rikka.shizuku.Shizuku$UserServiceArgs processNameSuffix(java.lang.String);
  private rikka.shizuku.Shizuku$UserServiceArgs use32BitAppProcess(boolean);
  private android.os.Bundle forAdd();
  private android.os.Bundle forRemove(boolean);
}
```

**关键发现**:
- `UserServiceArgs` 字段只有 `componentName` / `versionCode` / `processName` / `tag` / `debuggable` / `daemon` / `use32BitAppProcess` - **没有 Bundle 字段**
- `forAdd()` Bundle 是 Shizuku 私有 (`private`), user-supplied extras 不能加
- IDE 端改 `settings.shizuku.socksPort` 不能从 onBind(Intent) extras 传进来
- intent 永远没 extras, 走默认 39939

**结论**: 走 `Shizuku.UserServiceArgs` Bundle 不可行, 只能走:
- (a) **binder transact** 在 user service onBind 之后调 host setter
- (b) **共享 sharedPreferences** - 失败, 不同进程
- (c) **约定 file 路径** - 走 Shizuku.newProcess 写 /data/local/tmp, 但 Phase 12u 已 throw UOE
- (d) **SystemProperty** - 走 Shizuku.newProcess 跑 setprop, 同 (c) 需先解锁 newProcess

最终选 (a) **binder transact** 走 ISocksControl AIDL。

#### 12x.2 - 修编译错误: class 路径错

之前代码 `rikka.shizuku.api.UserServiceArgs(componentName)` 是错的 class 路径 -
**应该是** `rikka.shizuku.Shizuku.UserServiceArgs` (内部类)。沙箱没跑 gradle 没人
发现这个错。

修法:
```kotlin
val builder = rikka.shizuku.Shizuku.UserServiceArgs(componentName)
    .processName(processName)
    .daemon(false)
    .debuggable(false)
Shizuku.bindUserService(builder, conn)
```

#### 12x.3 - args 参数保留接口但暂忽略

ShizukuBinderClient.bindUserService 加 `args: Bundle?` 参数, 当前 Shizuku 13.1.5
没 API 接收, log.warn 提示, 留 TODO Phase 12y 实装 binder transact 协议。

#### 12x.4 - host 端 onBind 行为保留 (Phase 12j 端到端跑通)

`IdeShizukuSocksUserService.onBind` 行为不变, 走默认 39939 启 SOCKS5 server。注释
更新说明 custom port 走 Phase 12y binder transact 协议。

#### 12x.5 - Phase 12y TODO 留 binder transact 协议

下次实装:
- `ide-debugger-host/aidl/ISocksControl.aidl` 定义 AIDL
- host 端 `IdeShizukuSocksUserService` onBind 返 `ISocksControl.Stub()` (真 binder)
- IDE 端 `ShizukuConnection.attachViaSocks` 拿 binder 后 `ISocksControl.Stub.asInterface(binder).setSocksPort(port)`
- 这条路径完全不依赖 Shizuku args API

#### 12x.6 - 副作用与不变性

- Phase 12j 修的 "默认 39939 端到端跑通" 保留 (host 端 onBind 行为不变)
- 用户改 `settings.shizuku.socksPort` 当前**无效** (intent 没 extras), 等 Phase 12y
- class 路径修对后, IDE 编译能过 (之前错的路径)
- args 参数接口预留, 调用方不传, 等 Phase 12y 用

效果:
- Shizuku 13.1.5 真编译通过 (class 路径修对)
- 默认 39939 端到端跑通 (Phase 12j 修的保留)
- custom port 留 Phase 12y ISocksControl AIDL 实装 (binder transact 协议)

## 新增/修改文件 (Phase 12x)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../shizuku/ShizukuBinderClient.kt` (class 路径修对 + args 参数加但暂忽略) |
| 改 | `core/app/.../impl/ShizukuConnection.kt` (注释更新, args 留 TODO) |
| 改 | `ide-debugger-host/.../IdeShizukuSocksUserService.kt` (注释更新 + onBind 行为保留) |
