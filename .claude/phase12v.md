
## 后续修复 (Phase 12v) - RootClient.openJdwpStream stderr drain + onClose 兜底

### Phase 12v - socat 进程管理扫尾 (commit 0bb9d754)

`DefaultRootClient.openJdwpStream` (子项目 4 Root 路径) 三个真问题:

#### 12v.1 - redirectErrorStream(true) 把 stderr 合到 inputStream, JDWP 协议挂

**真 bug**: 之前 `ProcessBuilder(...).redirectErrorStream(true).start()` + 拿
`socat.inputStream` 给 RootConnection 当 JDWP byte source。

后果:
- socat 写 stderr (e.g. "socat[PID] N connecting to AF=1 \"@jdwp\"" 连接状态)
  合到 inputStream, RootConnection 读到的 JDWP 字节流里**夹 stderr 字节**
- JDWP frame 11 字节 header + 后续 payload, 期待 0x11 'h' 'a' 'n' 'd' 's'
  'h' 'a' 'k' 'e' 14 字节 handshake reply, 但实际读到 socat 错误消息
- 协议直接挂, RootConnection 永远 parse fail

**修法**: `redirectErrorStream(false)`, stderr 独立。socat 错误走
`errorStream` 单独 drain, 不污染 inputStream 给 RootConnection。

#### 12v.2 - stderr 没人 drain, kernel pipe buffer 满 deadlock

**真 bug**: stderr 即使不 redirect, 仍没人 drain。Linux kernel pipe buffer
默认 64KB, socat 写 stderr 满 64KB 后阻塞, socat 进程卡死, **IDE 端
`RootConnection.readJdwp` 拿 inputStream 永久阻塞**。

后果:
- 用户看 "正在连接" 永远不返
- 走 `RootConnection.detach()` / `release()` 调 `socat.destroyForcibly()`
  才能解, 但 user 已经卡死 IDE 一段时间

**修法**: 起 daemon thread `RootClient-socat-err` drain `socat.errorStream`
(只 `readBytes()`, 不解析), 防止 kernel pipe buffer 满。

#### 12v.3 - onClose destroyForcibly 不等真退出, socat zombie 短时间占 FDs

**真 bug**: `destroyForcibly()` 发 SIGKILL 但**不阻塞**等进程真死。RootConnection
释放 → 调 `onClose` → `destroyForcibly()` → 函数返, 此时 socat 进程变 zombie
短时间占 FDs。反复 attach/detach 后 FDs 累积。

**修法**: onClose 加 `socat.waitFor(2_000L, MILLISECONDS)` 等真退出 (2s 兜底,
超过不阻塞), + `socatErrDrain.join(500L)` 等 drain thread 完。

#### 12v.4 - 副作用与不变性

- 正常 Root 路径 (socat 装 + @jdwp 存在) 行为完全不变: inputStream / outputStream
  仍给 RootConnection
- 异常路径 (socat 没装 / @jdwp 不存在) 走 `catch (t: Throwable)` 返 IOException,
  不变
- stderr 错误信息不再暴露给 RootConnection (之前会污染), 跟 JDWP 协议对齐
- onClose 时序变更: 之前 destroyForcibly 后立刻返, 现在 destroyForcibly +
  waitFor(2s) + drain.join(500ms), 总延迟 < 2.5s, RootConnection.release() 调用方
  接受 2.5s 延迟 (跟 release() 走 AdbForward 路径 adb forward --remove 同步
  等待同量级)
- RootClientTest 仍只测 FakeRootClient, 不影响

效果: Root 路径 socat 进程管理跟 AdbRunner 同款稳定性:
- 不会再因 stderr 字节污染 JDWP 协议
- 不会再因 stderr pipe buffer 满 deadlock
- onClose 资源立即释放, 无 zombie 短时间占 FDs

## 新增/修改文件 (Phase 12v)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../root/RootClient.kt` (redirectErrorStream(false) + drain thread + waitFor 兜底) |
