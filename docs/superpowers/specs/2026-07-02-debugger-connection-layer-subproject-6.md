# 断点调试器连接层 - 子项目 6: InnetVmAdb 方案

> 日期: 2026-07-02
> 子项目 ID: 6
> 对应 ConnectionType: `InnetVmAdb`
> 状态: ✅ IDE 端实现 + 单测 + AdbRunner 抽象

## 目标

实现"内网虚拟机 (光速虚拟机/VMOS/虚拟大师) ADB 端口转发"方案的 IDE 端。

不走 SOCKS5 (子项目 5), 不走 Shizuku/Root, 走 `adb connect + adb forward` 把 VM
内部的 `localabstract:jdwp-<pid>` 套接字反向代理到 IDE 端 localhost 端口, 然后
IDE 端走标准 JDWP 协议。

## 设计

### 6 状态生命周期

```
Idle -> Resolving -> Connecting -> Handshaking -> Attached -> Closed
```

### 阶段 1: resolve

只做一件事: TCP 探测 `settings.innetAdb.adbHost:adbPort` 可达。

* 不可达 -> `Closed(NetworkUnreachable)` (走 retry 3 次 + 指数退避)
* 可达 -> `Connecting`

不真起 adb client; 不读 adb server 协议 - 只要 TCP 端口开着就认为 OK (adb server
的协议很复杂, 真正的握手在 `connect` 阶段做)。

### 阶段 2: connect

走 `AdbRunner` (抽象 adb 命令执行, 详见后) 串 4 步:

1. `adb connect <adbHost>:<adbPort>` - 拿到 device
2. `adb [-s serial] shell pidof -s <packageName>` - 拿 host 进程 PID
   (如果 `adbSerial` 配置了就加 `-s` 前缀)
3. `ServerSocket().bind(127.0.0.1, 0)` 拿本地端口 L (OS 随机)
4. `adb [-s serial] forward tcp:L localabstract:jdwp-<pid>` - 建立反向代理

任何一步失败:
* 关闭 ServerSocket (避免端口泄漏)
* 清空 localPort
* 抛 IOException -> `Closed(IoFailure)`

### 阶段 3: attach

`ServerSocket.accept()` 等 adb proxy 的反向连接 (设了 `soTimeout = 10s`,
3 次重试共约 30s)。收到连接后:
* 复用 `AidlJdwpProtocol.performHandshakeAndVersionProbe` 走标准 14 字节
  handshake + VM.Version
* 拿到 `VmVersionInfo` 后构造 `AttachInfo(pid=hostPid, jdwpSessionId, ...)`
* 启动守护线程做后续字节读取 -> `incoming` flow
* 状态 -> `Attached`

### 阶段 4: detach

1. 发 VM.Dispose 命令 (JDWP 1/2) 通知 host 端 VM 释放
2. 关闭 client socket
3. `adb forward --remove tcp:L` 清理 forward
4. 关闭 ServerSocket
5. 状态 -> `Closed(null)`

### 字节流

跟 `AidlSocketConnection` / `InnetVmSocksConnection` 一致:
* `sendJdwp` 走 `clientSocket.outputStream` 同步锁
* `receiveJdwp` 返回 `incoming` SharedFlow
* `attachedSocket` 返回 clientSocket (供 `ConnectionBackedDebugger` 复用)

### 错误分类

* `PortResolveFailed`: 配置不全 (host 空白 / port <= 0)
* `NetworkUnreachable` (retryable): TCP 探测失败
* `IoFailure` (retryable): adb 命令 exit != 0 / IOException
* `JdwpHandshakeFailed` (retryable): handshake 不匹配
* `Timeout` (retryable): accept 超时

## AdbRunner 抽象

子项目 6 + 7 (UsbLan) 都依赖 adb 命令, 抽接口避免在 IDE 端直接拼 `Runtime.exec`:

```kotlin
interface AdbRunner {
    fun run(args: List<String>, timeoutMs: Long): AdbResult
    fun runOnSerial(serial: String, args: List<String>, timeoutMs: Long): AdbResult
    fun getAdbBinaryPath(): String
    companion object { fun create(): AdbRunner = DefaultAdbRunner() }
}

data class AdbResult(val exitCode: Int, val stdout: String, val stderr: String = "") {
    val isSuccess: Boolean get() = exitCode == 0
}
```

### DefaultAdbRunner

* adb binary 路径按顺序尝试: `ANDROID_ADB_PATH` -> `IDE_ADB_PATH` ->
  `/data/data/com.itsaky.androidide/files/usr/bin/adb` -> `/system/bin/adb` ->
  `adb` (PATH 兜底)
* `ProcessBuilder` + 2 个守护线程 (stdout / stderr) + `waitFor` + `CountDownLatch`
* `timeoutMs` 超时 -> `destroyForcibly` + 抛 IOException

### FakeAdbRunner (测试用)

* `respond(matcher, producer)`: 预置 (args 匹配 -> AdbResult)
* matcher factory: `contains(keyword)` / `equalsTo(expected)`
* `callHistory: List<List<String>>`: 记录每次 `run` 的 args, 便于测试断言
* 没匹配到 -> `AdbResult(1, "", "FakeAdbRunner: no fake response for $args")`

## 与其他子项目的差异

| 维度 | InnetVmSocks (5) | InnetVmAdb (6) | UsbLan (7) |
|---|---|---|---|
| 通道 | SOCKS5 server | ADB forward | ADB forward |
| 协议 | RFC 1928 SOCKS5 | adb command | adb command |
| 需 host runtime | 否 (SOCKS5 由 VM 提供) | 否 (用 adb binary) | 否 (用 adb binary) |
| 需 adb binary | 否 | 是 | 是 |
| 需 root / shizuku | 否 | 否 | 否 |
| 探测目标 | SOCKS5 host:port | ADB host:port | ADB host:port |

## 文件清单

### 新增
- `core/app/src/main/java/com/itsaky/androidide/debugger/connection/adb/AdbRunner.kt`
  - `AdbResult` data class
  - `AdbRunner` interface + `DefaultAdbRunner` + `FakeAdbRunner`
- `core/app/src/main/java/com/itsaky/androidide/debugger/connection/impl/InnetVmAdbConnection.kt`
  - 替换原 stub, 实现 resolve/connect/attach/detach/release
- `core/app/src/test/java/com/itsaky/androidide/debugger/connection/adb/AdbRunnerTest.kt`
  - 测 FakeAdbRunner 的 matcher + callHistory + runOnSerial
- `core/app/src/test/java/com/itsaky/androidide/debugger/connection/impl/InnetVmAdbConnectionTest.kt`
  - 测 InnetVmAdbConnection 的状态机 + adb 命令序列

### 修改
- `core/app/src/main/java/com/itsaky/androidide/debugger/connection/impl/InnetVmSocksConnection.kt`
  - 修复 `cfg.host` / `cfg.port` -> `cfg.socksHost` / `cfg.socksPort` (编译错)
- `core/app/src/test/java/com/itsaky/androidide/debugger/connection/impl/InnetVmSocksConnectionTest.kt`
  - 同步修复 InnetSocksConfig 构造参数名

## 依赖

无新增外部依赖。`com.itsaky.androidide.utils.ILogger` 沿用既有导入。

## 已知限制 / 后续

- adb binary 路径假定 IDE 已经打包 platform-tools/adb; 实际部署时需要在
  `app/build.gradle.kts` 配 `assets/platform-tools/adb` 拷贝 (子项目 8 完善)
- 多设备 adbSerial 锁定: 已实现, 测过
- `adb connect` 后 adb server 不会自动断开 - 这是 adb 行为, 我们不主动清理
- 没有 adb binary 时的降级方案: 当前会抛 IOException, UI 显示 "请安装 ADB"
  (后续可在 resolve 阶段预检测 adb binary 是否存在)
