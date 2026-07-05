# 断点调试器连接层 - 子项目 7: UsbLan 方案

> 日期: 2026-07-02
> 子项目 ID: 7
> 对应 ConnectionType: `UsbLan`
> 状态: ✅ IDE 端实现 + 单测 + 与子项目 6 共享 AdbForwardConnection 抽象

## 目标

实现"USB / 局域网 ADB 端口转发"方案的 IDE 端。

跟子项目 6 (InnetVmAdb) 走同一条主路径 (adb forward 通道), 差异点仅在
connect 阶段的前置 adb 检查:

- InnetVmAdb: 先 `adb connect <vm>:<port>` 把 VM 加到 adb server
- UsbLan: 先 `adb devices` 校验目标设备在 adb server 列表

抽 `AdbForwardConnection` 共享基类避免代码重复。

## 设计

### 共享 AdbForwardConnection

```kotlin
abstract class AdbForwardConnection(
    type: ConnectionType,         // InnetVmAdb / UsbLan
    target: DebugTarget,
    settings: DebugConnectionSettings,
    adbRunner: AdbRunner? = null,
    retryPolicy: ConnectionRetryPolicy = ...,
) : BaseDebugConnection(type, target) {
    protected abstract val resolveInfoKind: String
    protected abstract val adbHost: String
    protected abstract val adbPort: Int
    protected abstract val adbSerial: String?
    protected abstract val connectTimeoutMs: Long
    
    protected abstract fun runPreConnectCheck()  // 子类各自实现
    
    // 共享实现 resolve/connect/attach/detach/sendJdwp/receiveJdwp/release
}
```

子类只实现 4 个属性 + 1 个方法, 重用所有共享逻辑。

### UsbLanConnection.runPreConnectCheck

走 `adb devices` 校验目标设备在 adb server 列表里:

1. 失败 (exit != 0) -> IOException
2. 没设备 (空列表) -> IOException "no devices attached"
3. 没配 adbSerial: 至少一台状态是 "device" -> OK
4. 配了 adbSerial: 那台必须存在且状态是 "device" -> OK

`adb devices` 输出格式:
```
List of devices attached
emulator-5554    device
physical-device-2    unauthorized
```

### InnetVmAdbConnection.runPreConnectCheck

走 `adb connect <adbHost>:<adbPort>` 把 VM 加到 adb server 列表:

1. 失败 (exit != 0) -> IOException
2. 成功 -> 继续后续 adb shell pidof / adb forward

注: 即使 VM 已在列表里, `adb connect` 也是幂等的 (返回 "already connected")。

## 6 状态生命周期

跟子项目 6 一致:
```
Idle -> Resolving -> Connecting -> Handshaking -> Attached -> Closed
```

## AdbRunner 复用

子项目 6 引入的 `AdbRunner` 直接复用:
- `AdbRunner.run(args, timeoutMs)`: 走 adb binary
- `AdbRunner.runOnSerial(serial, args, timeoutMs)`: 走指定 serial

`UsbLanConfig.adbSerial` 非空时, `AdbForwardConnection.runAdb` 会在 args
前加 `["-s", serial]`。

## 与其他子项目的差异

| 维度 | InnetVmSocks (5) | InnetVmAdb (6) | UsbLan (7) |
|---|---|---|---|
| 通道 | SOCKS5 server | ADB forward | ADB forward |
| 协议 | RFC 1928 SOCKS5 | adb command | adb command |
| 需 host runtime | 否 | 否 | 否 |
| 需 adb binary | 否 | 是 | 是 |
| 需 root / shizuku | 否 | 否 | 否 |
| 默认 adbPort | N/A | 5555 (VM daemon) | 5037 (本地 adb server) |
| 前置检查 | 无 | adb connect | adb devices |

## 文件清单

### 新增
- `core/app/src/main/java/com/itsaky/androidide/debugger/connection/impl/AdbForwardConnection.kt`
  - 抽象基类, 包含所有共享的 resolve/connect/attach/detach/release
- `core/app/src/test/java/com/itsaky/androidide/debugger/connection/impl/UsbLanConnectionTest.kt`
  - 测 UsbLanConnection 的状态机 + adb devices 验证 + serial 过滤

### 修改
- `core/app/src/main/java/com/itsaky/androidide/debugger/connection/impl/InnetVmAdbConnection.kt`
  - 重构为 AdbForwardConnection 的子类, 移出重复逻辑, 只保留 adb connect 前置检查
- `core/app/src/main/java/com/itsaky/androidide/debugger/connection/impl/UsbLanConnection.kt`
  - 从 stub 重写为 AdbForwardConnection 子类, 走 adb devices 前置检查

## 依赖

无新增外部依赖。`com.itsaky.androidide.utils.ILogger` 沿用既有导入。
`AdbRunner` 沿用子项目 6 引入的接口。

## 已知限制 / 后续

- 多设备场景: 通过 adbSerial 锁定; 没配 serial 时默认选第一台 ready device
- USB 设备授权: 若设备显示 `unauthorized`, 提示用户在设备上点"允许 USB 调试"
- `adb devices` 输出解析: 当前仅按 `\s+` split, 对异常格式 (有空格 serial) 鲁棒
- 子项目 8 (Host ADRT) 完成前, adb forward 走 localabstract:jdwp-<pid> 通道
  要求 host app 已经被调试器连接过 (即 VM 进程是 debuggable 状态)
