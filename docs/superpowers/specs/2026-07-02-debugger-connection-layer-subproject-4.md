# 断点调试器连接层 - 子项目 4: Root

> 状态: 设计 + 骨架实现
> 父设计: [2026-07-02-debugger-connection-layer-design.md](./2026-07-02-debugger-connection-layer-design.md)

## 1. 目标

把 Root 方案接入抽象层 `IDebugConnection`,作为 `ConnectionType.Root` 的真实
实现。让用户在 root 设备上, 不依赖 host app 任何主动配合, 就能完成断点
调试 attach。

## 2. 总体方案

`RootConnection` 内部走 `su -c` 起 root 进程做以下事:
1. `pidof <packageName>` 拿 host app pid
2. `cat /proc/<host_pid>/net/unix` 找 host JDWP socket path
3. `socat - UNIX-CONNECT:<path>` 把 socket 转成 stdin/stdout, IDE 端用
   Process 的 stdin/stdout 当 Socket 用
4. 走 JDWP 握手 + VM.Version

## 3. 与其它方案的对比

| 方案       | 需要 root | 需要 host 配合 | 需要 Shizuku | 需要 plugin | 速度 |
|----------|---------|-------------|-----------|-----------|----|
| AidlSocket |  否     | 是 (reverse-connect) | 否  |  否  | 中 |
| Shizuku   |  否     | 部分        | 是         | 部分      | 中 |
| **Root**  |  **是** |  **否**     |  **否**    |  **否**   |  快 |
| InnetVmSocks | 否   | 否 (SOCKS5 出口) | 否 | 否 | 慢 |
| InnetVmAdb | 否     | 是 (adb forward) | 否 | 否 | 中 |
| UsbLan    | 否     | 是          | 否         | 否        | 快 |

Root 方案的速度最快 (不走虚拟化, 直连 host), 但需要 root。

## 4. RootConnection 真实实现结构

```
core/app/src/main/java/com/itsaky/androidide/debugger/connection/
  root/
    RootProbe.kt                  # 探测 su 是否可用
    RootClient.kt                 # 走 su -c 起 root 进程 (找 pid + open JDWP)
  impl/
    RootConnection.kt             # 真实实现 (替换 stub)
```

## 5. 测试

- `RootConnectionTest`: 状态机 + 错误分类 (fake probe + fake client)
- `RootProbeTest`: 探测 su 可用性 (mock ProcessBuilder)
- `RootClientTest`: 找 pid + open JDWP (mock ProcessBuilder + socat)
- 真机集成测试: root 设备 + host app debug build, 跑全流程

## 6. 风险

- su binary 路径不固定: 兼容 `/system/bin/su`, `/system/xbin/su`, `/sbin/su`
- Magisk 模块 root 隐藏 (Magisk Hide / Zygisk DenyList): 探测 `pidof` 可能不
  返回预期 pid, 改用 `pgrep -f <packageName>` 兜底
- KernelSU / APatch: 也是 root 但 su 路径不同, 需探测多个常见路径
- `socat` 不在所有 root 设备上: fallback 用 `nc -U` 或自写 su exec 桥

## 7. 实现说明 (本次提交)

### 7.1 文件清单

```
core/app/src/main/java/com/itsaky/androidide/debugger/connection/
  root/
    RootProbe.kt
    RootClient.kt
  impl/
    RootConnection.kt
```

### 7.2 关键设计点

- **RootProbe**: 跑 `su -c 'id'` 看是否拿到 root, 单次调用
- **RootClient.findProcessId**: 走 `pidof` / `pgrep -f` 双路探测
- **RootClient.openJdwpSocket**: 留 stub, 等子项目 8 host runtime (attach
  agent + socat) 一起提供
- **状态机**: resolve (probe root) -> connect (找 pid) -> attach (拿 socket)
  走标准 ConnectionRetryPolicy

### 7.3 依赖子项目 8 才能跑通

- host 端 attach agent 命令: `kill -3 <pid>` 触发 ANR + 取 trace, 或
  `cmd activity broadcast` 触发启动 JDWP listener
- socat 或等价工具: 把 UNIX socket 转成 stdin/stdout
- 这部分等子项目 8 一起提交, 当前 stub 抛 NotImplemented
