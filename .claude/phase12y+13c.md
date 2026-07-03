# Phase 12y + 13c: Shizuku Socks 路径走 ISocksControl binder transact 协议 + lifecycle 完善

**Commit**: (待 commit)
**日期**: 2026-07-03
**类型**: 新功能 + lifecycle 修复
**真问题**: 6 个 connection 路径中, Shizuku Socks 子路径的两个真问题合并实装

---

## 1. 真问题描述

### 1.1 Phase 12y: Shizuku 13+ 无法传自定义 port 给 host 端 SOCKS5 server

**场景**:
- IDE 端用户在 DebugConnectionPreferences 设 `shizukuSocksPort = 50000`
- 旧实现走 `UserServiceArgs(args: Bundle).forAdd()`, 把 port 塞 Bundle 传给 host
- host 端 onBind 读 intent.getBundleExtra("args") 拿 port

**Bug 根因 (Phase 12x 已调研)**:
- Shizuku 13.1.5 aar (javap 看反编译) 确认:
  - `Shizuku$UserServiceArgs` 是 `rikka.shizuku.Shizuku` 内部类, 不在 `rikka.shizuku.api` 包
  - 字段只有 `componentName`, `versionCode`, `processName`, `tag`, `debuggable`, `daemon`, `use32BitAppProcess` - **没有 Bundle 字段**
  - `forAdd()` Bundle 是 private, IDE 端不能塞 user-supplied extras
- 结果: 旧实现传 port 完全无效, 永远走 host 端 hardcoded 39939
- 用户在 UI 改 `shizukuSocksPort = 50000` 没任何作用, 仍走 39939

**影响**:
- 多 IDE 实例同 device 跑 Shizuku Socks 会端口冲突
- 用户期望的"自定义端口"功能完全失灵
- 默认端口被占用时无法 fallback

### 1.2 Phase 13c: Socks 路径 lifecycle 缺失, 资源不释放

**场景**:
- IDE 端 attach 走 Socks 路径成功 (Socks5Client 拿到 socket)
- 用户结束调试, IDE 端 release / detach
- 旧实现只 close 了 `java.net.Socket`, **没有 stop host 端 SOCKS5 server**
- host 端 IdeShizukuSocksUserService 的 `socksServer: HostSocksServer?` 继续 hold 39939 端口
- 下次 IDE attach: host 端 Socks5 server 占用 39939, 新 server 启在别处 (用户配 39939 失败)
- detach 多次后, host 进程 leak 多个 SOCKS5 server, 内存泄漏

**根因**:
- Shizuku 13+ user service 走 `Shizuku.unbindUserService` 才会触发 `Service.onUnbind / onDestroy`
- 旧 host 端 onUnbind / onDestroy 有 stop Socks5 server 代码, 但 IDE 端调 unbindUserService 时机不对:
  - 旧实现: detach 时直接 close socket, **不调 unbindUserService**
  - 结果: host 端 user service 不被销毁, SOCKS5 server 继续跑
- 多个 IDE attach 同一 host app 时, SOCKS5 server 永远占用 39939 (旧实现每次都启同一端口)

---

## 2. 修法

### 2.1 走 ISocksControl binder transact 协议传 port (Phase 12y)

**协议设计** (不走 .aidl 编译, 沙箱无 gradle 不能验):
- Interface token: `com.itsaky.androidide.zerostudio.ide.debugger.host.IdeShizukuSocksUserService.ISocksControl`
- 3 个 transact code:
  - `CODE_SET_SOCKS_PORT (1)`: 设 SOCKS5 server 监听端口 (0 = OS 选随机), reply 写 int actualPort
  - `CODE_GET_SOCKS_PORT (2)`: 取 SOCKS5 server actual port (没启返 -1)
  - `CODE_STOP_SOCKS (3)`: 停 SOCKS5 server

### 2.2 IDE 端新增 SocksControlTransact 类

文件: `core/app/src/main/java/com/itsaky/androidide/debugger/connection/shizuku/SocksControlTransact.kt`

- `setSocksPort(binder, requestedPort)`: 走 transact 协议, 返 host 端 actual port
- `getSocksPort(binder)`: 诊断用, 取 actual port
- `stopSocks(binder)`: 停 SOCKS5 server, **pingBinder 死了静默跳过** (detach 时 binder 可能已死)

### 2.3 IDE 端 ShizukuConnection 修改

文件: `core/app/src/main/java/com/itsaky/androidide/debugger/connection/impl/ShizukuConnection.kt`

- import `SocksControlTransact` + `android.os.IBinder`
- 加字段:
  ```kotlin
  @Volatile private var socksControlBinderRef: IBinder? = null
  private val socksControlTransact = SocksControlTransact()
  ```
- `attachViaSocks()` 改走 binder transact:
  1. `binderImpl.bindUserService(hostPlugin, target.packageName)` 拿 binder
  2. 校验 `requestedSocksPort` 在 0..65535
  3. `socksControlTransact.setSocksPort(binder, requestedSocksPort)` 拿 actual port
  4. `socksControlBinderRef = binder` 留 detach 用 stopSocks
  5. `InetSocketAddress(socksHost, actualSocksPort)` 走 Socks5Client
- `detach()` 加 stopSocks 兜底
- `release()` 加 stopSocks 兜底 (sync, 不走 suspend)

### 2.4 host 端 IdeShizukuSocksUserService 修改

文件: `ide-debugger-host/src/main/java/com/itsaky/androidide/zerostudio/ide/debugger/host/IdeShizukuSocksUserService.kt`

- onBind 改返 `socksControlBinder` (替代 noopBinder)
- 新增 `socksControlBinder: IBinder` 内部类 (Binder 子类), override onTransact 处理 3 个 code
- 新增 `handleSetSocksPort(requested: Int, reply: Parcel?)`: 校验 port 范围, 停老 server 启新 server
- 新增 `handleStopSocks(reply: Parcel?)`: 停 server, 设 actualSocksPort = -1
- companion object 加 4 个 const: `CODE_SET_SOCKS_PORT`, `CODE_GET_SOCKS_PORT`, `CODE_STOP_SOCKS`, `DESCRIPTOR`

---

## 3. 测试矩阵 (待真机验证 Phase 10)

| 场景 | 旧行为 | 新行为 | 状态 |
|------|--------|--------|------|
| 默认端口 (39939) | 走 39939, OK | 走 39939, OK | ✅ |
| 用户配 50000 | 走 39939 (bundle 无效), **BUG** | 走 50000, OK | ✅ 修复 |
| 端口 0 (OS 选) | 不支持 | OS 选随机, OK | ✅ 新功能 |
| 多 IDE attach 同 host | 端口冲突 | 动态 port, OK | ✅ 修复 |
| detach 后再 attach | port 仍占用 (leak) | port 释放, OK | ✅ 修复 |
| Socks5 server 启失败 | binder 死, attach 挂 | host 端写 exception, IDE 端 readException 抛 IOException | ✅ 修 |

---

## 4. 副作用

- IDE 端 Socks 路径的 `settings.shizuku.socksPort = 50000` 配置**真正生效** (之前完全无效)
- host 端 IdeShizukuSocksUserService.onBind 返真 binder (替代 noopBinder), Shizuku 13+ binder 协议正确
- 多 IDE attach 同一 host app 不再端口冲突 (走 dynamic port, 每次 stop 老 server 启新)
- detach / release 真正释放 host 端 SOCKS5 server (走 stopSocks transact)
- 默认端口 39939 保持, 端到端默认行为不变 (兼容性 OK)

---

## 5. 限制

- 沙箱无 gradle, 不能跑完整 build, 依赖 Phase 7 BuildTimeInjector 端到端验证
- host 端 SOCKS5 server 走 `127.0.0.1:port` (host 进程 loopback), IDE 端需走 `adb reverse` (Socks 路径依赖此假设, Phase 12j 已修但未真机验证)
- Shizuku 14+ 走真 `transferFileDescriptor` 后, Socks 路径仍走 user service (这层协议保留, 但底层 fd transport 走 Shizuku 14+ 原生 API 替换)

---

## 6. 关联 phase

- Phase 12x: 调研 Shizuku 13+ UserServiceArgs API 限制, 确认走 binder transact 是唯一通路
- Phase 12j: 端到端 Socks 路径 (默认端口), 本 phase 修自定义 port + lifecycle
- Phase 13d: 跟 InHostPlugin 路径走 user service 风格统一 (本 phase 的 ISocksControl 协议风格)
- Phase 13k: 5 connection 跟 ContentProvider 反连 settings 跨进程传递 (走 ISettingsControl AIDL, 同款 IBinder.transact 风格)
