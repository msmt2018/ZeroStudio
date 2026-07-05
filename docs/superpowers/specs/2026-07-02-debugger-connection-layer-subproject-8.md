# 断点调试器连接层 - 子项目 8: Host ADRT runtime + 断点注入代码生成器

> 日期: 2026-07-02
> 子项目 ID: 8
> 状态: ✅ 新建 `ide-debugger-host` 模块 + HostAttachAgent / HostPluginService / HostSocksServer + IDE 端 BreakpointInjectionCodeGen + 单测

## 目标

实现"Host 端 ADRT (Android Debug Runtime) runtime" + "断点注入代码生成器",
补齐子项目 3/4 留下的 host runtime 缺口:

| 方案 | 子项目 | 缺失的部分 | 子项目 8 补齐 |
|---|---|---|---|
| Shizuku InHostPlugin | 3 | `IdeShizukuUserService` user service 启动 + 反向连 | `HostPluginService` |
| Shizuku Socks | 3 | host 端 SOCKS5 server | `HostSocksServer` |
| Shizuku Binder | 3 | host 端 attach-agent | `HostAttachAgent` |
| Root | 4 | `RootClient.openJdwpSocket` (socat 路径) | `HostAttachAgent` 配套 |
| 通用 | - | 断点注入代码生成 | `BreakpointInjectionCodeGen` |

## 架构

### 新建模块 `ide-debugger-host`

独立 Android library 模块, 在 host 进程 (用户 App) 内运行, 暴露:

```
ide-debugger-host/
├── build.gradle.kts
├── consumer-rules.pro       (keep HostAttachAgent / HostPluginService)
├── src/main/AndroidManifest.xml (空 manifest, 零权限)
└── src/main/java/com/itsaky/androidide/zerostudio/ide/debugger/host/
    ├── HostAttachAgent.kt      - 主入口 + 字节桥
    ├── HostPluginService.kt    - Shizuku user service 实现
    └── HostSocksServer.kt      - 抽象 + TCP SOCKS5 server
```

### HostAttachAgent

主入口, 走 `app_process` 启动, 在 host 进程内:

1. 反向连 IDE 的 `LocalServerSocket` (name 通过 argv[0] 传入)
2. 打开 `localabstract:jdwp` (本进程 JDWP server)
3. 双向 forward 字节 (8KB buffer, 两个 daemon 线程)
4. 任意一侧 EOF/异常 -> 关掉另一侧 + 主线程 join

3 个 public 方法:
- `main(args)`: 标准 Java main, `args[0] = ideSocketName`
- `connectToIdeLocalServer(name)`: 走 `android.net.LocalSocket` + `LocalSocketAddress.ABSTRACT`
- `openLocalAbstractJdwpSocket()`: 走 `android.net.LocalSocket.connect(abstract:jdwp)`
- `bridgeBytes(ide, jdwp)`: LocalSocket overload
- `bridgeBytes(ideIn, ideOut, jdwpIn, jdwpOut)`: Stream overload, 给 HostSocksServer 用

### HostPluginService

Shizuku user service (在 host 进程内被注入), 实现 `Service.onBind`:

1. 从 Intent 拿 IDE 端 LocalServerSocket 名字
2. 启动一个 daemon 线程: `connectToIdeLocalServer + openLocalAbstractJdwpSocket + bridgeBytes`
3. service 生命周期: `onBind` 启动, `onUnbind` 设 stopped, `onDestroy` interrupt 线程

**已知限制**: Shizuku 13+ 的 user service 是基于 IUserService aidl 的; IDE 端
注入时要保证 service 类的 classloader 是 IDE 自己的 (Shizuku 会处理)。

### HostSocksServer

RFC 1928 SOCKS5 server, 把所有 CONNECT 转到 `localabstract:jdwp`:

- `startOnAbstract(name)`: 监听 abstract namespace (Shizuku InHostPlugin 路径)
- `startOnTcp(host, port)`: 监听 TCP (InnetVmSocks VM 内部署)

只支持 `no-auth (0x00)` + `CONNECT (0x01)` + `ATYP=01/03/04` (IPv4/domain/IPv6),
跟子项目 5 的 `Socks5Client` 配对。

### BreakpointInjectionCodeGen (IDE 端)

`core/app/src/main/java/com/itsaky/androidide/debugger/connection/host/BreakpointInjectionCodeGen.kt`

给定 (target, breakpoints, jdwpPath) 生成一段 Kotlin 源文件, 包含:

1. `Jdwp` constants object (CommandSet / Command / EventKind / SuspendPolicy)
2. `JdwpClient`: 自包含的 JDWP client 子集
   - handshake (14 字节)
   - VM.Version
   - ReferenceType.Signature
   - ReferenceType.ClassesBySignature
   - Method.LineTable
   - EventRequest.Set (BREAKPOINT)
3. `BreakpointResolver`: 把 (sourceFile, line) -> (refTypeId, methodId, codeIndex)
4. `BreakpointListener`: 维护 breakpoint 列表, hit count
5. `GeneratedBreakpoint`: data class
6. `main(args)`: 走 jdwp path + handshake + report heartbeat

**当前限制**: 代码生成框架, 编译 / DEX 转换 / 注入 host 进程留到后续
(子项目 9+)。当前生成的代码能作为模板, 后续会接入 ASM/KotlinPoet。

## 测试

`core/app/src/test/java/com/itsaky/androidide/debugger/connection/host/BreakpointInjectionCodeGenTest.kt`:

- 生成代码非空, 包含 package / class / 函数标记
- 多个断点都正确生成
- sourceFile / condition 含特殊字符 (双引号) 时正确转义
- null condition 渲染为 `null` 字面量
- 自定义 jdwp path 反映在 header
- target.packageName / target.activityClass 反映在 header
- 所有主要类名 (JdwpClient / BreakpointResolver / BreakpointListener / main / openJdwp) 都出现

## 与子项目 3/4 的对接

### ShizukuConnection.attachViaInHostPlugin

替换原 stub:
```kotlin
private suspend fun attachViaInHostPlugin(): AttachInfo {
    val service = binderImpl.bindUserService(hostPlugin, target.packageName)
    // service 启动后, 它在 host 进程内会反向连 IDE
    // IDE 端已经有 LocalServerSocket 在 listen, 这里只需要 accept
    val sock = ideLocalServerSocket.accept()  // 由 AidlSocketConnection 共享
    val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(sock, ...)
    return AttachInfo(...)
}
```

### ShizukuConnection.attachViaSocks

替换原 stub:
```kotlin
private suspend fun attachViaSocks(): AttachInfo {
    // host 端 SOCKS5 server 已经在 host 进程内启动 (由 Shizuku newProcess 或 InHostPlugin 启动)
    val proxyAddr = InetSocketAddress(settings.shizuku.socksHost, settings.shizuku.socksPort)
    val sock = ShizukuSocksClient().connect(proxyAddr, "127.0.0.1", 0)
    val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(sock, ...)
    return AttachInfo(...)
}
```

### RootConnection.openJdwpSocket

替换原 stub:
```kotlin
// RootClient.openJdwpSocket 走 su -c app_process 启动 HostAttachAgent
// HostAttachAgent 反向连 IDE LocalServerSocket
fun openJdwpSocket(hostPid: Int, suBin: String, timeoutMs: Long): Socket {
    val localSockName = "ide-debug-root-$hostPid-${System.currentTimeMillis()}"
    // 1) IDE 端 bind LocalServerSocket
    val lss = LocalServerSocket(localSockName)
    // 2) su -c app_process ... HostAttachAgent <localSockName>
    val cmd = listOf(suBin, "-c",
        "app_process -Djava.class.path=$hostJarPath /system/bin " +
        "com.itsaky.androidide.zerostudio.ide.debugger.host.HostAttachAgent $localSockName")
    val proc = ProcessBuilder(cmd).start()
    // 3) accept + 走 JDWP
    val sock = lss.accept()  // Socket 包装 LocalSocket
    return sock
}
```

## 文件清单

### 新增
- `ide-debugger-host/build.gradle.kts`
- `ide-debugger-host/consumer-rules.pro`
- `ide-debugger-host/src/main/AndroidManifest.xml`
- `ide-debugger-host/src/main/java/com/itsaky/androidide/zerostudio/ide/debugger/host/HostAttachAgent.kt`
- `ide-debugger-host/src/main/java/com/itsaky/androidide/zerostudio/ide/debugger/host/HostPluginService.kt`
- `ide-debugger-host/src/main/java/com/itsaky/androidide/zerostudio/ide/debugger/host/HostSocksServer.kt`
- `core/app/src/main/java/com/itsaky/androidide/debugger/connection/host/BreakpointInjectionCodeGen.kt`
- `core/app/src/test/java/com/itsaky/androidide/debugger/connection/host/BreakpointInjectionCodeGenTest.kt`
- `docs/superpowers/specs/2026-07-02-debugger-connection-layer-subproject-8.md`

### 修改
- `settings.gradle.kts`: 注册 `ide-debugger-host` 模块

## 已知限制 / 后续

- 生成的 Kotlin 源代码需要被 KotlinPoet/ASM 编译成 DEX, 然后 adb push +
  app_process 注入 host 进程 — 留到子项目 9 (host plugin assemble 阶段)
- `HostAttachAgent.openLocalAbstractJdwpSocket` 没做握手, 直接 open — 真实
  VM 端会发 14 字节 handshake 立即等待 reply, 我们的 IDE 端 JdwpClient
  在 connect 后立即写 handshake, 双方都满足
- 多断点并发: 当前代码生成框架只保留 listener 框架, 真正 EventRequest.Set
  在 IDE 端 JDWP 客户端完成 (本模块不重复), host 端只做心跳 + 状态回报
- `HostPluginService` 走 Shizuku IUserService aidl, 完整实现要 IDE 的
  service factory + bind user service 协议
