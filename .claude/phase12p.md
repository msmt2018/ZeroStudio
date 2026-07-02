
## 后续修复 (Phase 12p) - HostAttachAgentBootstrap 走公共 timeout + 资源关闭

### Phase 12p - ContentProvider 反连线程真 bug: 注释骗人 + 无 timeout + 资源泄漏 (commit d64e447c)

子项目 9c/10 `HostAttachAgentBootstrap.startBridgeThreadInternal` 三个真问题:

#### 12p.1 - 注释说走 `HostAttachAgent.connectToIdeLocalServer`, 实际裸 `new LocalSocket().connect()`

**真 bug**: 注释 (line 9-12, 198-202) 反复说"走 HostAttachAgent 的
connectToIdeLocalServer (有 connect timeout + retry + 重建 socket 机制, Phase 12f 加的)"。

但 line 203 实际写 `ide = HostAttachAgent.connectToIdeLocalServer(...)` 用了 helper,
**line 213 之前** 是裸 `new LocalSocket().connect()` 阻塞 + **无 timeout**。
IDE 端 LocalServerSocket 没启时 host 端永远卡, ContentProvider 早期 init
阶段没 UI 提示, daemon 线程吃死, 重启 host app 才能解。

**修法**: 全部走 helper (`connectToIdeLocalServer` / `openLocalAbstractJdwpSocket`),
都带 `HostAttachAgent.CONNECT_TIMEOUT_MS_PUBLIC` (10s) timeout,
与 Phase 12f 加的 retry + 重建 socket 机制一致。

#### 12p.2 - `HostAttachAgent.CONNECT_TIMEOUT_MS` 是 `private const val`, Java 端访问不到

`HostAttachAgent.kt` 的 `private const val CONNECT_TIMEOUT_MS: Long = 10_000L`
是模块私有, `HostAttachAgentBootstrap.java` 直接引用**编译失败**。

**修法**: 加 `@JvmField public val CONNECT_TIMEOUT_MS_PUBLIC: Long = CONNECT_TIMEOUT_MS`
公共访问器, Java 端通过 `HostAttachAgent.CONNECT_TIMEOUT_MS_PUBLIC` 拿。
内部 Kotlin 调用仍走 `CONNECT_TIMEOUT_MS` 不变 (private const 在编译期内联,
零开销)。

#### 12p.3 - connect 成功后 throw 资源泄漏

之前 line 198-218 在 `try` 块里 connect + 写 HELLO + open jdwp, **没有 finally**。
如果第 3 步 (open jdwp) 抛异常 (e.g. `HostAttachAgent.openLocalAbstractJdwpSocket`
超时), 第 1 步拿到的 `ide` socket **不会 close**, 文件描述符泄漏。
多次失败重启 host app 后 FDs 累积, 最终 `Too many open files` 进程被 SIGKILL。

**修法**: try-finally 资源关闭, 跟 `HostAttachAgentBridge` 风格一致:
```java
} finally {
    if (ide != null) { try { ide.close(); } catch (Throwable ignored) { } }
    if (jdwp != null) { try { jdwp.close(); } catch (Throwable ignored) { } }
}
```

不用 try-with-resources 是因为 flow 里有 4 步 (connect / write HELLO / open
jdwp / bridge), 局部变量作用域跨多步, try-with-resources 拆成多段反而更乱。
try-finally 跟现有 `HostAttachAgentBridge` 风格一致。

#### 12p.4 - 副作用与不变性

- ContentProvider 早期 init 行为不变: 没启 IDE 端时 fail-fast 退出 (10s 内
  `Log.w` + 退出 daemon 线程), 不会再永远卡
- 资源关闭保证: connect/throw 任何路径都不漏 FDs
- 多次 `startReverseConnectThread` 调用仍幂等 (Phase 12c 的 `AtomicBoolean`
  不动)

效果: host 端 ContentProvider 反连路径**真**用上 Phase 12f 加的 connect
timeout + retry + 重建机制, 端到端跑通 (无 IDE 端时 fail-fast, 有 IDE 端
时 1 次成功路径走完)。

## 新增/修改文件 (Phase 12p)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `ide-debugger-host/.../HostAttachAgent.kt` (+`@JvmField public val CONNECT_TIMEOUT_MS_PUBLIC`) |
| 改 | `ide-debugger-host/.../HostAttachAgentBootstrap.java` (改 `CONNECT_TIMEOUT_MS_PUBLIC` + try-finally 资源关闭) |
