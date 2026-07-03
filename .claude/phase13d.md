# Phase 13d: ShizukuConnection.attachViaBinder 真实现 - 留 TODO 文档化限制

**Commit**: (待 commit)
**日期**: 2026-07-03
**类型**: 文档化限制 + TODO 留待 Shizuku 14+ 实装

---

## 1. 真问题

`ShizukuConnection.attachViaBinder` 之前直接复用 `attachViaInHostPlugin` 同款实现
(因 Shizuku 13+ 限制, `transferFileDescriptor` 不可用), transport 名字保留 Binder
供 UI 显示。注释写得不够清晰, 后续维护者 (跟 Shizuku 14+ 真实现) 看不出"为什么
Binder 路径跟 InHostPlugin 路径是同款代码"。

---

## 2. 修法

更新 `ShizukuConnection.attachViaBinder` 注释, 清晰说明:

### 2.1 Phase 13d 限制 (留 TODO 文档化)

- Shizuku 13+ 把 `rikka.shizuku.Shizuku.transferFileDescriptor` 设 package-private,
  第三方 IDE 端不能直接调 (`ShizukuBinderClient.transferFileDescriptor` 抛
  `UnsupportedOperationException`)
- 所以 Binder 路径走 fallback: 复用 `attachViaInHostPlugin` 同款实装 (走
  `Shizuku.bindUserService` + host 端 `HostPluginService` reverse-connect 回
  IDE `LocalServerSocket`)
- 唯一区别: transport 名字保留 Binder 供 UI 显示 (跟 InHostPlugin 区分开),
  底层逻辑复用 InHostPlugin

### 2.2 Shizuku 14+ 真路径 TODO

1) host 端 user service (e.g. `BinderTransportService`) 跑 root 进程 attach
   host app 的 JDWP agent, open `/proc/<host_pid>/fd/<jdwp_socket>` 拿 fd
2) host 端 user service 把 fd 写回 Parcel
3) IDE 端 `ShizukuBinderClient.transferFileDescriptor` 走 Shizuku 14+ 公共 API
   (如果官方开放) 拿回 ParcelFileDescriptor
4) `ShizukuFdTransporter.toSocket(pfd)` 包成 `PfdSocket` (已实装, Phase 13a
   修的 `DefaultShizukuFdTransporter`)
5) 走 JDWP 握手 + VM.Version

优先级: 14+ 走真 transferFileDescriptor, 13+ 继续走 InHostPlugin fallback。

### 2.3 SocksServiceUserService adapter

Socks 路径的 user service adapter (`IdeShizukuSocksUserService`) 已在
Phase 12y + 13c 合并实装 (走 `ISocksControl` binder transact 协议传 port +
detach 释放)。Binder 路径 14+ 真实现后, 同样需要 `BinderTransportService`
user service (跟 Socks 路径 adapter 风格一致)。

---

## 3. 改动

只动了一个文件 + 注释:

- `core/app/.../impl/ShizukuConnection.kt` `attachViaBinder` 注释更新

代码无任何逻辑改动 (仍然复用 `attachViaInHostPlugin`), 只是文档化限制让后续
维护者清楚"为什么这样写"。

---

## 4. 限制

- 沙箱无 Shizuku 14+ aar, 14+ 公共 API 是否开放 transferFileDescriptor 待官方发布
- 当前 Shizuku 13.x 仍 fallback 走 InHostPlugin 路径, 行为不变
- `ShizukuBinderClient.transferFileDescriptor` 抛 `UnsupportedOperationException`,
  调用方需 catch 处理 (但 `attachViaBinder` 不调, 所以无影响)
- `PfdSocket` (`ShizukuFdTransporter.toSocket`) 已实装, 14+ 拿来即用

---

## 5. 关联 phase

- Phase 12p/12q: InHostPlugin 路径 (Phase 13d 的 fallback 实装)
- Phase 12y + 13c: Socks 路径 user service adapter (Phase 13d 同样需要这种风格)
- Phase 13a: 修了 `DefaultShizukuFdTransporter` + `PfdSocket`, 14+ 真路径拿来即用
- Phase 13l: SubPathCapability 测试验证 (P13d 修了 BinderCapability fallback 注释)
