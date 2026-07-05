# 子项目 11 真实部署验证检查表

> 适用版本: ZeroStudio IDE PR #445 (子项目 1-10 + Phase 12-13 bug fix)
> 验证目标: BuildTimeInjector 注入器 + 6 选 1 连接方案 端到端
> 所需环境: 1 台调试手机 (Android 7.0+), 1 台 IDE 端, ADB 已连
>
> **更新记录**:
> - 2026-07-03: 加 §9 Phase 13a-13k + §10 Phase 5/6 refactor 验证章节, 反映
>   19 个 phase 修完后的最新 commit 状态 (commit ba7a144b ~ 264be3ed)

## 0. 前置条件

- [ ] IDE 端: `ZeroStudio` 从 PR #445 build 出来的 APK
- [ ] 手机端: 准备一个最简单的 Android Studio 项目 (空 `MainActivity.kt`)
- [ ] 手机端: 项目根 `build.gradle.kts` 加上 BuildTimeInjector 插件:
      ```kotlin
      plugins {
          id("com.itsaky.androidide.gradle.ide-debugger") version "1.0.0"
      }
      ```
- [ ] 手机端: `gradle.properties` 加上 (可空):
      ```
      ideDebuggerSdkInt=34
      ideDebuggerPreheatBreakpoints=src=app/src/main/java/com/example/MainActivity.kt:42:8
      ```
- [ ] IDE 端 -> 手机端: `adb devices` 显示 `device` (非 unauthorized)
- [ ] IDE 端: 启 `HostBridgeServer` (默认随 IDE 启动, 在调试器面板)

## 1. 编译期验证 (BuildTimeInjector 注入)

- [ ] 手机端 build 一次: `./gradlew :app:assembleDebug`
- [ ] 在手机端项目根 `find` 找生成产物:
      ```bash
      find app/build/generated/source/ide_debugger -name "IdeDebuggerBootstrap.kt"
      ```
      应输出 `app/build/generated/source/ide_debugger/debug/kotlin/com/itsaky/androidide/zerostudio/ide/debugger/host/generated/IdeDebuggerBootstrap.kt`
- [ ] 打开该 .kt 文件, 验证 4 段结构:
      - [ ] `IDE_DEBUGGER_VERSION` = IDE 端 `BuildInfo.VERSION_NAME`
      - [ ] `LOCAL_SERVER_NAME` = `ide-debug-bridge-{group}-{name}` (跟 Manifest placeholder 同步)
      - [ ] `HELLO_PROTOCOL_EXTRA_FIELDS` = `"sdk=34"`
      - [ ] `BUILD_TIMESTAMP_MS` 是当前 build 时间戳 (ms)
      - [ ] `BreakpointLocation(sourceFile, line, column)` data class
      - [ ] `PREHEAT_BREAKPOINTS` 含配置的 bp (`MainActivity.kt:42:8`)
      - [ ] `init(application)` 调 `HostAttachAgentBootstrap.startReverseConnectThread`
- [ ] 验证 Manifest placeholder 注入:
      ```bash
      aapt2 dump xmltree app/build/outputs/apk/debug/app-debug.apk --file AndroidManifest.xml | grep ide_local_server_name
      ```
      应输出 `meta-data android:name="ide_local_server_name" android:value="ide-debug-bridge-com.example-..."`
- [ ] 验证 host AAR 注入 (sub-project 9d):
      ```bash
      unzip -l app/build/outputs/apk/debug/app-debug.apk | grep HostAttachAgentBootstrap
      ```
      应输出 classes.dex 里有 `HostAttachAgentBootstrap`

## 2. 部署期验证 (HostAttachAgentBootstrap 反连)

- [ ] 手机端 install APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- [ ] 启 IDE 端 HostBridgeServer (默认随 IDE 启, log 里有 `HostBridgeServer: started` 一行)
- [ ] 手机端启动 host app: `adb shell am start -n com.example/.MainActivity`
- [ ] 30s 内, IDE 端 logcat 应有:
      ```
      HostBridgeServer: accept() -> connection from /jdt-bridge-XXXX
      HostBridgeServer: HELLO received: pkg=com.example pid=YYYY sdk=ZZ
      AppReadyAutoConnect: [bridge] attached to com.example (jdwp=DalvikVM 11.2)
      ```
- [ ] 同时, host app 端 logcat 应有:
      ```
      HostAttachAgentBootstrap: startReverseConnectThread: socket=ide-debug-bridge-...
      HostAttachAgentBootstrap: reverse-connect ok; bridging IDE <-> jdwp
      IdeDebuggerBootstrap: init: ideDebuggerVersion=... localServer=... extras=sdk=34 buildTs=... preheatBpCount=1
      ```

## 3. 6 选 1 连接方案验证 (依次切换, 每次需重启 host app + IDE 端)

### 3.1 AidlSocket (默认)
- [ ] IDE 端: 不改任何配置, 默认走 AIDL+Socket
- [ ] 手机端: 启动 app
- [ ] 验证: logcat 出现 `AppReadyAutoConnect: pickConnectionType=AidlSocket`

### 3.2 Shizuku/WifiAdb
- [ ] 手机端: install Shizuku + 启动
- [ ] IDE 端: 偏好里选 `Shizuku/WifiAdb`
- [ ] 重启 host app
- [ ] 验证: logcat 出现 `ShizukuConnection: subPath=WifiAdb`
- [ ] 验证: IDE 端 attach 成功 (debugger 面板显示 attached)

### 3.3 Shizuku/InHostPlugin
- [ ] 手机端: Shizuku running + 授权
- [ ] IDE 端: 偏好选 `Shizuku/InHostPlugin`
- [ ] 重启 host app
- [ ] 验证: logcat 出现 `ShizukuConnection: subPath=InHostPlugin bindUserService=...`

### 3.4 Shizuku/Socks
- [ ] 手机端: Shizuku running
- [ ] IDE 端: 偏好选 `Shizuku/Socks`
- [ ] 重启 host app
- [ ] 验证: logcat 出现 `ShizukuConnection: subPath=Socks socks5 connect=...`
- [ ] 验证: host 端 `HostSocksServer` log 收到 SOCKS5 CONNECT 请求

### 3.5 Root
- [ ] 手机端: rooted (Magisk/SuperSU)
- [ ] IDE 端: 偏好选 `Root`
- [ ] 重启 host app
- [ ] 验证: logcat 出现 `RootConnection: openJdwpStream su=/system/bin/su`
- [ ] 验证: shell 端 `socat` 被调用 (`ps -A | grep socat`)

### 3.6 InnetVmSocks
- [ ] 手机端: 启 SOCKS5 proxy on local port (e.g. `ssh -D 1080`)
- [ ] IDE 端: 偏好选 `InnetVmSocks` + 填 proxy host/port
- [ ] 重启 host app
- [ ] 验证: logcat 出现 `InnetVmSocksConnection: socks5 connect=127.0.0.1:1080`

### 3.7 InnetVmAdb
- [ ] 手机端: ADB port forward 已建 (`adb forward tcp:8888 jdwp:pid`)
- [ ] IDE 端: 偏好选 `InnetVmAdb` + 填 port 8888
- [ ] 重启 host app
- [ ] 验证: logcat 出现 `InnetVmAdbConnection: adb forward accept`

### 3.8 UsbLan
- [ ] 手机端: USB 连接 + `adb reverse tcp:8888 localabstract:jdwp`
- [ ] IDE 端: 偏好选 `UsbLan`
- [ ] 重启 host app
- [ ] 验证: logcat 出现 `UsbLanConnection: usb reverse connect`

## 4. 断点注入验证 (BuildTimeInjector 核心)

- [ ] IDE 端: 在 host app 源码里设置 1 个 bp (任意 line)
- [ ] 验证 `ideDebuggerPreheatBreakpoints` 也含这 bp:
      ```
      ideDebuggerPreheatBreakpoints=src=app/src/main/java/com/example/MainActivity.kt:42:8
      ```
- [ ] 重 build + 重启 host app
- [ ] 验证: logcat 出现 `IdeDebuggerBootstrap: init: ... preheatBpCount=1`
- [ ] 验证: host app 启动到 42:8 行时, IDE 端 debugger 停在 bp 上
- [ ] 验证: bp 包含 column 信息 (host app 8 字节对齐的 column 0/4/8/12 都正确)

## 5. 新鲜度验证 (build 多次, 验证 IDE 端常量更新)

- [ ] IDE 端: 改 BuildInfo.VERSION_NAME 1.0.0 -> 1.0.1
- [ ] host app: 重 build (触发 IdeDebuggerBootstrap.kt 重新生成)
- [ ] 验证: 新生成的文件 `IDE_DEBUGGER_VERSION` = `1.0.1`
- [ ] 验证: 新生成的文件 `BUILD_TIMESTAMP_MS` 是新的 (跟旧 build 不同)
- [ ] 验证: 重启 host app, logcat `init: ideDebuggerVersion=1.0.1 ...`

## 6. 失败模式验证

- [ ] 手机端 uninstall host app, 验证 IDE 端 onAttachFailed 回调 + 不 crash
- [ ] IDE 端关掉 HostBridgeServer, 验证 host app 正常启动 (反连失败 -> log warn -> 不抛)
- [ ] host app 改了 group/name (manifestPlaceholders 跟着变), 验证 IDE 端 LOCAL_SERVER_NAME 跟新 group/name 匹配
- [ ] 手机端 kill host app, 验证 IDE 端 onDetach 回调

## 7. 自动化

- [ ] 上述 1-6 步可以录屏 + 截图存档
- [ ] 所有 6 选 1 连接方案跑通, 截图存档
- [ ] 报告: `docs/superpowers/specs/2026-07-02-subproject-11-deployment-report.md`
      含: 测试时间, 测试设备 (型号 + Android 版本), 每步 pass/fail, 已知问题

## 8. 验收标准 (Definition of Done)

- [ ] 所有 1-6 步 pass
- [ ] 报告归档
- [ ] PR #445 标 "Ready for Review" 等待 merge
- [ ] merge 后: 子项目 1-11 全完工, ZeroStudio IDE 端到端断点调试器可用

---

## 9. Phase 13a-13k 新增验证 (commit e38a326f ~ d8fe3c08)

### 9.1 Phase 13a: Shizuku Auto subPath 真探测 (commit e38a326f)

- [ ] IDE 端: 偏好选 `Shizuku` + `auto resolve`
- [ ] 验证: logcat 出现 `ShizukuConnection: subPath=auto picked=WifiAdb|InHostPlugin|Socks|Binder` 之一
- [ ] 验证: 设备无 adb 串号时, 选 `InHostPlugin` / `Socks` (host 装 plugin 才可用)
- [ ] 验证: 旧设备 (Shizuku 13-) 走 fallback `InHostPlugin`, 13+ 走 fallback, 14+ (待实装) 走真 Binder

### 9.2 Phase 13b: HostAttachAgent.bridgeBytes daemon (commit 8f8cb89a)

- [ ] 手机端: 启 host app, 启 IDE 端 HostBridgeServer
- [ ] IDE 端 logcat: `HostAttachAgent: bridge started, isDaemon=true`
- [ ] 手机端: kill host app
- [ ] 验证: host app 进程立即 exit (不卡几秒等 thread), `ps -A | grep com.example` 0 个进程

### 9.3 Phase 12y + 13c: Socks 路径走 ISocksControl binder transact (commit ba7a144b)

- [ ] IDE 端: 偏好选 `Shizuku/Socks` + `shizukuSocksPort = 50000`
- [ ] 验证: logcat 出现 `SocksControl.setSocksPort(50000) -> actualPort=50000` (旧 39939)
- [ ] 验证: host 端 SOCKS5 server 启在 50000 (旧 39939)
- [ ] 验证: IDE 端 attach 成功, Socks5Client 连 50000
- [ ] detach 后再 attach: 验证 host 端 SOCKS5 server 真正释放 (旧 leak)
- [ ] 多 IDE 同一 host app 验证: 动态 port, 不冲突 (旧 39939 冲突)

### 9.4 Phase 13d: attachViaBinder TODO 文档化 (commit 8f8288ab)

- [ ] 验证: 选 `Shizuku/Binder` 子路径, 走 fallback InHostPlugin (13+ 限制)
- [ ] logcat: `ShizukuConnection: subPath=Binder (fallback InHostPlugin, Shizuku 14+ 待实装)`

### 9.5 Phase 13e: InnetVmAdbConnection VM 慢启动 5s poll (commit 364a794c)

- [ ] 手机端: 启光速 VM/VMOS/虚拟大师 (慢启动)
- [ ] IDE 端: 偏好选 `InnetVmAdb`
- [ ] 验证: logcat `InnetVmAdbConnection: adb connect ok on attempt 1-10` (5s 内)
- [ ] 验证: VM 完全没启, 5s 后报 `VM adb did not come up within 5000ms` (走上层 retry)

### 9.6 Phase 13f: UsbLan 多 transport 列解析 + unauthorized 友好错误 (commit 82d00a2f)

- [ ] Android 11+ 设备: `adb devices` 输出 `<serial> <state> <transport-id>` (3 列)
- [ ] 验证: IDE 端 `UsbLan` 偏好, attach 成功 (split 拿 [0] serial + [1] state)
- [ ] 验证: 设备未授权, 报 `adb devices: serial 'XXX' is 'unauthorized'. Tap 'Always allow from this computer' on the device's 'Allow USB debugging' dialog, then re-try.` (旧 generic error)
- [ ] 验证: 设备 offline, 报 `adb devices: serial 'XXX' is 'offline'. Re-plug the USB cable ...`
- [ ] 验证: Linux udev rules 缺失, 报 `adb devices: serial 'XXX' is 'no permissions'. Check \`adb kill-server && adb start-server\` as root`

### 9.7 Phase 13g: InnetVmSocks SOCKS5 错误细分 (commit 0a38de3d)

- [ ] SOCKS5 server 不可达: `Connection refused` -> `IoFailure` (retryable)
- [ ] SOCKS5 server 拒绝 `REP=0x05` -> `IoFailure` (host 端 jdwp 端口没开)
- [ ] SOCKS5 server 拒绝 `REP=0x03/0x04` -> `NetworkUnreachable` (Network/Host unreachable)
- [ ] SOCKS5 server 拒绝 `REP=0x06` -> `Timeout` (TTL expired)
- [ ] SOCKS5 server 协议错 (VER / ATYP / domain too long) -> `IoFailure`
- [ ] SOCKS5 server 要 auth (method != 0x00) -> `PermissionDenied`

### 9.8 Phase 13j: HELLO 协议 process= 字段 (commit 0312d0b7)

- [ ] 标准 host app (ContentProvider 跑主进程): logcat 走默认, 无 process warn
- [ ] multi-process host app (ContentProvider 跑 :debug / :remote):
  - [ ] 验证 IDE 端 log warn: `HostBridgeServer: HELLO from non-main process 'com.example:debug' (pkg=com.example pid=XXX). ContentProvider is in :debug / :remote process; JDWP attach will fail. Fix host app Manifest: move ContentProvider declaration to default process.`
  - [ ] 验证: 上层 AppReadyAutoConnect 走 reject + UI 提示 (Phase 10 e2e 实装, 当前仅 log warn)

### 9.9 Phase 13k: HELLO 协议 buildVersion= 字段 (commit d8fe3c08)

- [ ] 验证 IDE 端 parse: `buildVersion=v1`
- [ ] IDE 端 log: `HostBridgeServer: HELLO pkg=com.example pid=XXX process=com.example sdk=34 buildVersion=v1`
- [ ] 跟 IDE 端配置对账: 排查 host 端 aar 是否对齐

---

## 10. Phase 5/6 refactor 验证 (commit 805728f0 + 264be3ed)

### 10.1 Phase 5: 5 connection startReadLoop 死代码清理

- [ ] 验证: 3 个 connection (InnetVmSocks / Shizuku / AdbForward) startReadLoop
      死代码清理完, 走 JdwpClient 内部 read 不冲突
- [ ] 编译: Phase 5 commit 后无编译错误 (沙箱跑不了, 留 CI verify)

### 10.2 Phase 6: 5 connection mapXxxError 同款细分模式

- [ ] 验证: 5 connection 错误分类一致
  - NetworkUnreachable (ConnectException / NoRouteToHostException)
  - AddressInUse (端口已占用)
  - DeviceNotFound (adb 设备未找到)
  - PermissionDenied (unauthorized / su denied / shizuku denied)
  - JdwpHandshakeFailed (14 字节握手失败)
  - Timeout (SocketTimeout / TTL expired)
  - IoFailure (其他 IOException)
  - Unknown
- [ ] UI 提示跟 ConnectionError 类型对应, 不再 "IO 失败" 干巴一行

---

## 11. 限制与待办

- Phase 7: BuildTimeInjector 端到端验证需 CI 跑 gradle (沙箱无 gradle)
- Phase 10: 真机/模拟器 e2e 测试矩阵需开发者真机
- Phase 13l: SubPathCapability 跑单元测试 (沙箱无 test runner)
- Shizuku 14+ transferFileDescriptor 真实现: 等官方 release
- 多 IDE 同 host app 端口冲突: P12y 修, 大规模验证留真机
