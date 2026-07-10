# ide-log-plugin

ZeroStudio 的宿主端日志与 JDWP 服务插件。在用户项目的 debug variant 编译时
自动注入到宿主应用程序,在宿主进程内启动 JDWP server 和 logcat 流捕获,
供 IDE 连接调试和查看日志。

> 与 [`ide-debugger-host`](../../Breakpoint-debugger/ide-debugger-host/README.md)
> 共存于宿主 app:本模块负责启动 JDWP server + 捕获 logcat + 发送 READY 信号;
> `ide-debugger-host` 负责反向连接 IDE 建立字节桥。

## 核心职责

1. **启动 JDWP server** — 在宿主进程的 loopback 接口监听,供 IDE 主动连接
2. **捕获 logcat** — 流式捕获宿主进程日志,通过 logwire 协议发送给 IDE
3. **发送 READY 信号** — 通过 logcat 发送 `READY pkg=... jdwp=PORT` 信号,
   IDE 端 `AppReadySignalWatcher` 监听到后触发自动 attach
4. **暴露端口查询** — 通过 `ContentProvider.call()` 让 IDE 查询 JDWP / logcat 端口

## 关键组件

| 类 | 职责 |
| --- | --- |
| [`DebuggerBootstrapProvider`](src/main/java/com/zerostudio/logplugin/bootstrap/DebuggerBootstrapProvider.java) | ContentProvider,在 `Application.onCreate()` 之前被实例化。启动 JdwpServer + LogCaptureService,发送 READY 信号 |
| [`JdwpServer`](src/main/java/com/zerostudio/logplugin/jdwp/JdwpServer.java) | JDWP server,在 127.0.0.1 随机端口监听,`startAndRegister(0)` 启动 |
| [`LogCaptureService`](src/main/java/com/zerostudio/logplugin/capture/LogCaptureService.java) | logcat 流捕获,`startLogcat(0)` 启动,单例 |
| [`LogSocketServer`](src/main/java/com/zerostudio/logplugin/transport/LogSocketServer.java) | 通过 logwire 协议把日志流发送给 IDE |
| [`LogBuffer`](src/main/java/com/zerostudio/logplugin/util/LogBuffer.java) | 日志环形缓冲,防止 IDE 连接前的日志丢失 |
| `api/LogTransportType` / `LogPayload` / `LogLevel` | 日志 API 数据模型 |

## READY 信号

宿主 app 启动后,`DebuggerBootstrapProvider.onCreate()` 通过 logcat 发送:

```
ZeroStudioDebug: READY pkg=<packageName> jdwp=<port>
```

IDE 端 `AppReadySignalWatcher` 监听 `logcat -s ZeroStudioDebug:V`,匹配到
该信号后触发 `AppReadyAutoConnect` 自动 attach。

## 端口查询 API

IDE 可以通过 ContentResolver 查询宿主 app 的 JDWP / logcat 端口:

```kotlin
val bundle = context.contentResolver.call(
    Uri.parse("content://com.zerostudio.debugger.bootstrap"),
    "getJdwpPort",  // 或 "getLogcatPort"
    null, null
)
val port = bundle?.getInt("port") ?: -1
```

## AndroidManifest

本模块不需要在宿主 app 的 manifest 中手动声明 provider — `IdeDebuggerInitScriptPlugin`
会在 build time 自动注入 manifest placeholder 和 provider 注册:

- authority: `com.zerostudio.debugger.bootstrap`
- provider class: `com.zerostudio.logplugin.bootstrap.DebuggerBootstrapProvider`

## 注入机制

本模块通过两个 Gradle init-script 插件注入到用户项目的 debug variant:

- [`IdeLogInitScriptPlugin`](../../tooling/plugin/src/main/java/com/itsaky/androidide/gradle/IdeLogInitScriptPlugin.kt) — 注入 `ide-log-plugin` AAR
- [`IdeDebuggerInitScriptPlugin`](../../tooling/plugin/src/main/java/com/itsaky/androidide/gradle/IdeDebuggerInitScriptPlugin.kt) — 注入 `ide-log-plugin` + `ide-debugger-host` AAR + manifest placeholder

仅 debuggable variant 会被注入,release variant 不受影响。

## 依赖

- `androidx.annotation` (compileOnly)
- [`logwire`](../logwire/README.md) — 共享传输协议 (IDE 与宿主端共用)

本模块 **不依赖** `ide-debugger` (IDE 端引擎),保持宿主端与 IDE 端解耦。

## 相关模块

- [`logwire`](../logwire/README.md) — 共享传输协议
- [`ide-debugger-host`](../../Breakpoint-debugger/ide-debugger-host/README.md) — 宿主端反连桥,与本模块共存
- [`ide-debugger`](../../Breakpoint-debugger/ide-debugger/README.md) — IDE 端 JDWP 客户端引擎

## License

GPL-3.0-or-later (same as AndroidIDE)
