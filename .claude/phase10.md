# Phase 10 - 真机/模拟器 e2e 测试矩阵 (TODO)

## 状态

**未完成** - 沙箱无设备, 跑不了真机/模拟器 e2e 测试。

## 背景

6 个 ConnectionType (AidlSocket / Shizuku / Root / InnetVmSocks / InnetVmAdb /
UsbLan) + Shizuku 4 子路径 (WifiAdb / Binder / InHostPlugin / Socks) 共 9 个 attach
路径, 端到端真机验证矩阵:
- Android 8 / 11 / 14 (3 个 SDK)
- 物理机 + 模拟器
- 有 root / 无 root
- Shizuku 已装 + 未装
- 局域网 + USB

Phase 10 目标: 跑通 9 × 3 × 2 = 54 个 e2e case, 验证 attach + 断点 + 步进 + detach。

## 沙箱限制

- 无 adb / fastboot
- 无 Android 模拟器 (qemu / emulator)
- 无物理机
- 无显示器

## e2e 测试矩阵 (留给有环境的开发者)

### 矩阵

| ConnectionType | 物理机 Android 8 | 物理机 Android 11 | 物理机 Android 14 | 模拟器 Android 14 |
| -------------- | --------------- | ---------------- | ---------------- | ----------------- |
| AidlSocket | TBD | TBD | TBD | TBD |
| Shizuku/WifiAdb | TBD | TBD | TBD | TBD |
| Shizuku/Binder | TBD | TBD | TBD | TBD |
| Shizuku/InHostPlugin | TBD | TBD | TBD | TBD |
| Shizuku/Socks | TBD | TBD | TBD | TBD |
| Root | TBD | TBD | TBD | N/A |
| InnetVmSocks | N/A | TBD | TBD | TBD |
| InnetVmAdb | N/A | TBD | TBD | TBD |
| UsbLan/USB | TBD | TBD | TBD | TBD |
| UsbLan/LAN | TBD | TBD | TBD | TBD |

### 每个 case 验证步骤

1. **Pre-attach check**: 触发 `Connection.runPreConnectCheck()`
   - 期望: 0 错误, 通过
2. **Attach**: 触发 `Connection.attach()`
   - 期望: JdwpClient 拿到 socket, 14 字节握手成功
3. **Set breakpoint**: 触发 `BreakpointManager.setBreakpoint(file, line)`
   - 期望: JDWP EventRequest.SET 成功
4. **Resume VM**: 触发 `JdwpClient.vmResume()`
   - 期望: VM 继续跑, 命中断点
5. **Step over**: 触发 `JdwpClient.stepOver(threadId)`
   - 期望: VM 步进一行
6. **Inspect variable**: 触发 `StackFrame.getValue(varRef)`
   - 期望: 拿到变量值
7. **Detach**: 触发 `Connection.detach()`
   - 期望: socket 关闭, VM 继续, 不 crash

### 已知风险 (按 case 分类)

#### AidlSocket
- Android 14 foreground service 类型限制: 需要 `dataSync` / `connectedDevice` 类型
- 双进程 IDE: socket 路径不能跨进程 (走 abstract namespace)

#### Shizuku/WifiAdb
- adb 端口冲突 (5037): 多个 IDE 同 host 端口冲突
- adb 没 PATH: 探测失败, fallback 到 Binder

#### Shizuku/Binder
- Shizuku 13 binder 死链: pingBinder() 不可靠
- transferFileDescriptor 13+ 限制: 走 Socks fallback (Phase 12y+13c)

#### Shizuku/InHostPlugin
- host app 没装: 探测失败
- host app ContentProvider 在 :debug 进程: Phase 13j 已加 process= 字段
- multi-process host app: Phase 13j 已 log warn

#### Shizuku/Socks
- 端口 39939 占用: Phase 12y+13c 走 dynamic port 解决
- SOCKS5 server 跨进程不通: P12j 实际没真机验证, 留 Phase 10 验证
- SOCKS5 协议错 (VER/ATYP/domain): Phase 13g 已细分错误

#### Root
- su 不可用: 探测失败
- socat 没装: 探测失败 (Phase 6 已加 IoFailure 错误提示)
- 没有 jdwp socket: 探测失败

#### InnetVmSocks
- VM 慢启动: Phase 13e 已加 5s poll
- SOCKS5 server 不通: Phase 13g 已细分错误

#### InnetVmAdb
- VM 慢启动: Phase 13e 已加 5s poll
- adb serial 重复: 多 VM 场景

#### UsbLan/USB
- unauthorized: Phase 13f 已加友好错误
- offline: Phase 13f 已加友好错误
- no permissions: Phase 13f 已加友好错误 (udev rules)
- transport-id 解析: Phase 13f 已用 Regex \s+ split

#### UsbLan/LAN
- 端口 5555 没开: adb tcpip 5555
- 防火墙: localhost-only reverse

### CI 跑 (GitHub Actions + emulator)

```yaml
- uses: reactivecircus/android-emulator-runner@v2
  with:
    api-level: 34
    arch: x86_64
    script: ./gradlew :app:connectedAndroidTest
```

### 手动跑 (有物理机的开发者)

```bash
# 1. 编译 APK
./gradlew :app:assembleDebug
./gradlew :ide-debugger-host:assembleDebug

# 2. 装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r ide-debugger-host/build/outputs/apk/debug/ide-debugger-host-debug.apk

# 3. 跑 IDE
# - 启动 AppCodeAndroid
# - 选 ConnectionType = AidlSocket
# - attach

# 4. 看 logcat
adb logcat -s "AndroidIDE-Debug:*" "HostAttachAgent:*"
```

## 限制

- 沙箱无设备
- Phase 10 留给有真机 / 模拟器的开发者
- 任何 ConnectionType 加新 feature 都要先在矩阵里 verify

## 相关文件

- `core/app/.../connection/Connection.kt` (interface, 6 个实现)
- `core/app/.../shizuku/capability/` (4 capability)
- `ide-debugger-host/.../HostAttachAgentBootstrap.java`
- `docs/superpowers/specs/2026-07-02-subproject-11-deployment-checklist.md` (部署检查表)

## 后续

- Phase 7 跑通 build 后, Phase 10 才能跑 e2e
- Phase 13l 跑通 unit test 后, Phase 10 减少重复 case
