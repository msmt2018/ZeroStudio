# ide-debugger-host

ZeroStudio 的宿主端 Android Debug Runtime (ADRT)。运行在 **宿主应用程序**
进程内,负责把宿主 app 的 JDWP socket 反向桥接到 IDE。

> 本模块是 IDE 端 [`ide-debugger`](../ide-debugger/README.md) 的宿主端对应物。
> `ide-debugger` 是 IDE 进程内的 JDWP **客户端**引擎;`ide-debugger-host`
> 是宿主进程内的 **桥接**运行时,两者通过抽象 namespace socket 通讯。

## 核心职责

1. **反向连接 IDE** — 宿主 app 启动后主动连接 IDE 的 `LocalServerSocket`
   (名字 `ide-debug-bridge`,由 build-time manifest placeholder 注入)
2. **字节桥** — 在 IDE 连接和宿主 `localabstract:jdwp` 之间双向转发字节
3. **多路径支持** — 提供 Shizuku / Root / SOCKS 多种注入路径的宿主端实现

## 关键组件

| 类 | 职责 |
| --- | --- |
| [`HostAttachAgentBootstrap`](src/main/java/com/itsaky/androidide/zerostudio/ide/debugger/host/HostAttachAgentBootstrap.java) | ContentProvider,宿主 app 启动时自启动反连线程。读 manifest placeholder `ide_local_server_name`,反连 IDE,发 HELLO 协议头,建立字节桥 |
| [`HostAttachAgent`](src/main/java/com/itsaky/androidide/zerostudio/ide/debugger/host/HostAttachAgent.kt) | 命令行入口 (经 `app_process` 启动),Shizuku / Root 路径用。提供 `connectToIdeLocalServer` / `openLocalAbstractJdwpSocket` / `bridgeBytes` |
| [`HostAttachAgentBridge`](src/main/java/com/itsaky/androidide/zerostudio/ide/debugger/host/HostAttachAgentBridge.java) | Java 版双向字节桥 (CountDownLatch 同步) |
| [`HostPluginService`](src/main/java/com/itsaky/androidide/zerostudio/ide/debugger/host/HostPluginService.kt) | Shizuku user service 形式,在 Shizuku InHostPlugin 子路径中运行 |
| [`HostSocksServer`](src/main/java/com/itsaky/androidide/zerostudio/ide/debugger/host/HostSocksServer.kt) | SOCKS5 代理服务器 (RFC 1928),把宿主 JDWP socket 暴露为 SOCKS5 目标 |
| [`IdeShizukuSocksUserService`](src/main/java/com/itsaky/androidide/zerostudio/ide/debugger/host/IdeShizukuSocksUserService.kt) | Shizuku Socks 子路径的 user service |

## HELLO 协议

宿主端反连 IDE 后,发送一行 HELLO 协议头 (UTF-8,以 `\n` 结尾):

```
HELLO pkg=<packageName> pid=<pid> process=<processName> sdk=<sdkInt> buildVersion=<v>
```

IDE 端 [`HostBridgeServer`](../../core/app/src/main/java/com/itsaky/androidide/debugger/connection/host/HostBridgeServer.kt)
解析后触发 `AppReadyAutoConnect` 自动 attach。

## AndroidManifest

本模块的 `AndroidManifest.xml` 声明了 `HostAttachAgentBootstrap` ContentProvider:

```xml
<provider
    android:name="com.itsaky.androidide.zerostudio.ide.debugger.host.HostAttachAgentBootstrap"
    android:authorities="com.zerostudio.host.attachagent"
    android:exported="false"
    android:initOrder="100">
    <meta-data
        android:name="ide_local_server_name"
        android:value="${ideLocalServerName}" />
</provider>
```

`${ideLocalServerName}` 由 [`IdeDebuggerInitScriptPlugin`](../../tooling/plugin/src/main/java/com/itsaky/androidide/gradle/IdeDebuggerInitScriptPlugin.kt)
在 build time 注入为固定常量 `ide-debug-bridge`。

## 注入机制

本模块通过 `IdeDebuggerInitScriptPlugin` (Gradle init-script 插件) 在用户项目
的 debug variant 编译时自动注入到宿主 app 的 runtimeClasspath。**不注入**
`ide-debugger` (IDE 端引擎) — 那是 IDE 进程内的代码,不应打进宿主 app。

## 6 种连接方式中的角色

| 连接方式 | 宿主端组件 |
| --- | --- |
| AidlSocket (默认) | `HostAttachAgentBootstrap` (ContentProvider 自启动反连) |
| Shizuku / InHostPlugin | `HostPluginService` |
| Shizuku / Socks | `HostSocksServer` + `IdeShizukuSocksUserService` |
| Root | `HostAttachAgent` (经 `su -c app_process` 启动) |
| InnetVmSocks / InnetVmAdb / UsbLan | `HostAttachAgent` (经 app_process 或 ADB forward) |

## 依赖

- `androidx.annotation` (compileOnly)
- `androidx.core.ktx` (compileOnly)
- `kotlin-coroutines-android`

本模块 **不依赖** `ide-debugger` (IDE 端引擎),保持宿主端与 IDE 端解耦。

## 相关模块

- [`ide-log-plugin`](../ide-log-plugin/README.md) — 宿主端 JdwpServer + LogCapture,与本模块共存于宿主 app
- [`ide-debugger`](../ide-debugger/README.md) — IDE 端 JDWP 客户端引擎
- [`logwire`](../logwire/README.md) — 共享传输协议

## License

GPL-3.0-or-later (same as AndroidIDE)
