# 断点调试器连接层 - 子项目 5: InnetVmSocks (虚拟机 SOCKS5 代理)

> 状态: 设计 + 真实实现
> 父设计: [2026-07-02-debugger-connection-layer-design.md](./2026-07-02-debugger-connection-layer-design.md)

## 1. 目标

把"应用内置虚拟机" (光速虚拟机 / VMOS / 虚拟大师 等) 的 SOCKS5 代理方案
接入抽象层 `IDebugConnection`,作为 `ConnectionType.InnetVmSocks` 的真实
实现。

## 2. 总体方案

`InnetVmSocksConnection` 走通用 SOCKS5 客户端协议 (RFC 1928):
1. resolve: TCP probe SOCKS5 server (`settings.innetSocks.host:port`)
2. connect: 不做事 (attach 时再握手)
3. attach:  SOCKS5 (no-auth) 握手 -> 转发到 host JDWP 端口 -> JDWP 握手 + VM.Version

## 3. 与 Shizuku Socks 路径的区别

| 维度         | InnetVmSocks              | Shizuku Socks 子路径          |
|------------|--------------------------|---------------------------|
| SOCKS5 server | 虚拟机自带, hardcoded 地址  | Shizuku user service 启的     |
| 探测/授权      | 不需要 (虚拟自带)            | 需要 Shizuku 已运行 + 授权      |
| 配置         | 偏好里手动填 host/port      | 走 settings.shizuku 自动配置   |
| 适用设备      | 装了"应用内置虚拟机" app       | 装了 Shizuku app + 启动      |

InnetVmSocks 完全不依赖 Shizuku, 是平行方案。 用户在偏好里手动配 SOCKS5
地址 (虚拟机的网络 IP + 端口), IDE 直接连过去。

## 4. 关键设计点

- 通用 Socks5Client 在 `connection/socks5/Socks5Client.kt` 里, 跟 Shizuku 子路径
  共用, 避免协议实现两套
- InnetVmSocksConnection 不依赖任何 host runtime, 不依赖 Shizuku
- SOCKS5 server 配置在 settings.innetSocks: host / port / (预留 username/password)

## 5. 测试

- `InnetVmSocksConnectionTest`: 用真 ServerSocket 假 SOCKS5 server 跑全流程
  + 错误分类 + 状态机
- `Socks5ClientTest`: 走通用 SOCKS5 协议 (单独测, 跟 InnetVmSocks 共享)

## 6. 风险

- SOCKS5 server 配置错误: 端口写错, IDE 立即报 IoFailure
- 虚拟机的网络隔离: 部分虚拟机的 SOCKS5 端口不对外暴露, 需要用户用 adb forward
- SOCKS5 server 端有 auth: 当前实现只支持 no-auth, 后续加

## 7. 实现说明 (本次提交)

### 7.1 文件清单

```
core/app/src/main/java/com/itsaky/androidide/debugger/connection/
  socks5/
    Socks5Client.kt                # 通用 SOCKS5 客户端 (RFC 1928)
  shizuku/
    ShizukuSocksClient.kt           # 改用通用 Socks5Client, 保留类名稳定
  impl/
    InnetVmSocksConnection.kt       # 替换 stub, 真实实现
```

### 7.2 关键设计点

- 复用 Socks5Client, 避免 Shizuku 子路径 / InnetVm 方案 重复实现 SOCKS5 协议
- resolve 阶段走 TCP probe (connect + close), 不发 SOCKS5 协议握手 (握手在 attach 阶段)
- SOCKS5 成功后直接走 JDWP 握手 + VM.Version
- 错误分类: SOCKS5 错误码 -> NetworkUnreachable; JDWP 握手失败 -> JdwpHandshakeFailed
