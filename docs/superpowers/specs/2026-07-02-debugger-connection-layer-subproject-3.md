# 断点调试器连接层 - 子项目 3: Shizuku

> 状态: 设计 + 骨架实现
> 父设计: [2026-07-02-debugger-connection-layer-design.md](./2026-07-02-debugger-connection-layer-design.md)
> 上一级: [子项目 2 (AIDL+Socket)](./2026-07-02-debugger-connection-layer-subproject-2.md)

## 1. 目标

把 4 个 Shizuku 子路径 (Auto / WifiAdb / Binder / InHostPlugin / Socks) 接入抽象层
`IDebugConnection`,作为 `ConnectionType.Shizuku` 的真实实现。让用户在
"Shizuku 已运行"的环境下,不依赖 root 也不依赖 host app 的任何主动配合,
就能完成断点调试 attach。

## 2. 总体方案

`ShizukuConnection` 内部按 `settings.shizuku.subPath` 走 4 条路径之一;
`Auto` 模式按 1→2→3→4 顺序探测,第一个能连通的就用。

每条子路径最终都拿到一个 `java.net.Socket`,协议层 (JDWP 握手 + VM.Version)
完全复用子项目 2 的 `AidlJdwpProtocol`,底层传输换成 Shizuku 自己的机制。

| 子路径     | 传输层机制                                    | host 进程要求 |
|-----------|--------------------------------------------|------------|
| WifiAdb   | ServerSocket(127.0.0.1) + startActivity + reverse-connect | 需要 host 已装 + 同意被调试 |
| Binder    | Shizuku `newProcess` 起 JDWP attach agent,  fd 传回 IDE   | 不需要 host 主动配合, 走 Shizuku 权限 |
| InHostPlugin | Shizuku `attachUserService` 把 host plugin 注入 host 进程, plugin 自己 reverse-connect | 需要 host 装了 plugin 版本的 ADRT runtime |
| Socks     | Shizuku 启动 SOCKS5 出口, IDE 走 SOCKS5 客户端连到 host JDWP 端口 | host JDWP 端口可达 |

## 3. 4 条子路径详解

### 3.1 WifiAdb (等同于 AIDL 方案走 adb wifi)

走 `AidlSocketConnection` 完全相同的代码 (ServerSocket + startActivity +
reverse-connect + JDWP 握手)。本项目里直接复用 `AidlSocketConnection` 实例,
不重复实现。

这条路径的存在意义: 即使 IDE 端没 root, 只要 host 端装了 IDE 的 host app,
且打开了 ADB WiFi 调试, 就能工作。走的是 AIDL 方案的代码, 只是入口
"Shizuku 方案的 WifiAdb" 在语义上表明 "我们正在借助 Shizuku 检查环境"。

**Shizuku 在这里的作用**: 仅做 `requireShizuku=true` 的环境校验 (Shizuku 必
须运行 + 授权), 不参与数据传输。

### 3.2 Binder (Shizuku 拿 root 起 attach agent)

走 `Shizuku.newProcess(["sh", "-c", "cmd ..."])` 在 Shizuku server 进程
跑命令 (Shizuku server 跑在 root 权限, 所以是 adb 级别 shell 但有限制)。

具体步骤:
1. IDE 端用 Shizuku 调 `newProcess` 在 host app 进程空间跑一个 attach agent
   (`kill -3` 或 `cmd activity` 触发 ANR + 读 traceview 都行, 这里是另一
   种, 通过 Shizuku 自己的 attachAgent 机制)
2. attach agent 后 host 进程的 JDWP socket 会出现在 `/proc/<host_pid>/fd/`,
   Shizuku server (有 root) 把它 open + 转成 FileDescriptor
3. Shizuku API 提供了 `transferFileDescriptor(binder, fd)`, 通过 Shizuku
   binder 把 fd 传给 IDE 端
4. IDE 端用 `ParcelFileDescriptor` 包成 `java.net.Socket` (用
   `Socket(SocketImpl)` 自定义或 `ParcelFileDescriptor.getFileDescriptor()`
   + `Socket(ParcelFileDescriptor)` 包装)
5. 走 JDWP 握手 + VM.Version

**与 AIDL 方案的关键区别**: 不需要 host app 主动 reverse-connect, 由 Shizuku
代为 attach 后取 fd。

### 3.3 InHostPlugin (Shizuku attachUserService 注入)

走 Shizuku 的 `attachUserService(binder, options)`:
1. IDE 端定义一个 `IShizukuServiceConnection` (binder)
2. IDE 端用 Shizuku 把这个 binder "attach" 到 host app 进程
3. host 进程里 plugin 端的 ADRT runtime 收到 attach 信号, 自己开一个
   LocalServerSocket + reverse-connect 到 IDE
4. IDE 端用 `AidlSocketConnection` 同款逻辑 accept + 握手

**好处**: 走 host app 自己的 user service 进程, 不依赖 Shizuku 持续在线 (Shizuku
挂掉后 service 还能跑), 而且能拿到 host 进程的所有权限 (包括 ContentProvider,
动态权限等)。

**坏处**: 需要 host 端有对应的 plugin 库 (子项目 8 host runtime 的一部分)。

### 3.4 Socks (SOCKS5 出口)

走 Shizuku 启动一个 SOCKS5 出口, IDE 端当 SOCKS5 客户端:
1. IDE 用 Shizuku newProcess 启动一个 SOCKS5 server, 监听本地端口
2. Shizuku 进程的 SOCKS5 server 通过自己的 root 权限把请求 forward 到
   host app 的 JDWP socket (`/data/local/tmp/jdwp/<pid>` 之类的命名空间
   socket, 或 `connectToHost`)
3. IDE 用 SOCKS5 协议 (`05 01 00 01 <host:port>`) 连过去
4. 走 JDWP 握手 + VM.Version

**好处**: 跟 host app 0 耦合, 任何能被 Shizuku 访问的 socket 都能连。
**坏处**: 走 SOCKS5 协议多一跳, 性能略差。

## 4. ShizukuConnection 真实实现结构

```
core/app/src/main/java/com/itsaky/androidide/debugger/connection/
  shizuku/
    ShizukuProbe.kt              # Shizuku 状态探测 (binder 拿到没 + 是否授权)
    ShizukuBinderClient.kt       # 调 IShizukuService.newProcess / checkSelfPermission 等
    ShizukuFdTransporter.kt      # FD 传输 (host 进程 JDWP socket -> IDE Socket)
    ShizukuSocksClient.kt        # SOCKS5 客户端 (Socks 路径用)
    ShizukuSubPathResolver.kt    # Auto 模式按 1→2→3→4 探测, 返回第一个能用的
  impl/
    ShizukuConnection.kt         # 真实实现 (替换 stub)
```

## 5. 与抽象层的钩子对齐

- `attachedSocket()`: 4 条路径最终都给一个 `java.net.Socket`
  - WifiAdb: 复用 `AidlSocketConnection.attachedSocket()`
  - Binder: `ParcelFileDescriptor -> Socket` 包装
  - InHostPlugin: 复用 `AidlSocketConnection.attachedSocket()` 模式
  - Socks: SOCKS5 negotiate 完毕后的内层 Socket
- `receiveJdwp()`: 同样可以用 SharedFlow, 但 Binder/Socks 路径下底层是
  `ParcelFileDescriptor` / `InputStream`, 单独写个 `ShizukuReadLoop` 即可

## 6. 风险

- **Shizuku 没运行**: resolve() 阶段就 `PermissionDenied`/`ShizukuNotRunning` 失败
- **Shizuku 没授权**: resolve() 阶段拉权限请求对话框, 超时返回 `PermissionDenied`
- **Binder 路径 attach agent 失败**: Shizuku 服务端沙箱, 部分设备不允许 attach
- **InHostPlugin 路径 host 没装 plugin**: 提示用户装 plugin 版本 host app
- **Socks 路径 SOCKS5 服务端进程被 OOM kill**: 自动重连

## 7. 测试

- `ShizukuProbeTest`: 测 Shizuku 状态探测 (用 fake IShizukuService)
- `ShizukuSocksClientTest`: 测 SOCKS5 握手 (用 fake server)
- `ShizukuSubPathResolverTest`: 测 Auto 模式按 1→2→3→4 探测
- `ShizukuConnectionTest`: 集成测试 (用 fake IShizukuService + 内存 socket)
- 真实 Shizuku 环境测试: 真机 + 安装 Shizuku.apk + 启动 Shizuku

## 8. 实现说明 (本次提交)

### 8.1 文件清单

```
core/app/src/main/java/com/itsaky/androidide/debugger/connection/
  shizuku/
    ShizukuProbe.kt
    ShizukuBinderClient.kt
    ShizukuFdTransporter.kt
    ShizukuSocksClient.kt
    ShizukuSubPathResolver.kt
  impl/
    ShizukuConnection.kt          # 真实实现 (替换 stub)
```

### 8.2 关键设计点

- **4 条子路径 + 1 个 Auto 模式**: 不重复实现 WifiAdb 和 InHostPlugin, 直接
  复用 `AidlSocketConnection` (ServerSocket + startActivity) + 通过 Shizuku
  做环境探测和权限获取
- **ShizukuProbe**: 探测 Shizuku 是否运行 + 是否授权, 单次调用, 不阻塞
- **ShizukuBinderClient**: 封装 IShizukuService binder 调用, 提供 suspend 包装
- **ShizukuFdTransporter**: 走 Shizuku 的 transferFileDescriptor 把 host
  JDWP socket fd 转回 IDE
- **ShizukuSocksClient**: SOCKS5 握手 + 通过 SOCKS5 server 转发到 host JDWP
- **ShizukuSubPathResolver**: Auto 模式按 WifiAdb → Binder → InHostPlugin →
  Socks 顺序探测, 第一个能用的就用

### 8.3 不在本子项目范围

- host 端 ADRT runtime: 子项目 8
- 真实 Shizuku 环境测试: CI 跑不了, 本地手验
- Shizuku 版本兼容性 (Shizuku 12 vs 13 API 差异): 后续 PR 单独处理

### 8.4 提交策略

本子项目作为独立 PR #446, 包含 ShizukuConnection 真实实现 + 5 个辅助类 + 单测。
本 PR 完成后, 5/6 种连接方式中 Shizuku 方案可用 (依赖 Shizuku 服务端和 host
runtime, host runtime 在子项目 8 完成后才能完整跑通 end-to-end)。
