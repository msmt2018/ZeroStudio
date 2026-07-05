
## 后续修复 (Phase 12u) - AdbRunner 命名错位 + err thread 静默吞错

### Phase 12u - AdbRunner.DefaultAdbRunner.run() 扫尾 (commit d4f49440)

`AdbRunner.DefaultAdbRunner.run()` 三个真问题:

#### 12u.1 - `errRef` 命名错位

**真 bug**: `arrayOfNulls<Throwable>(null)` 名字 `errRef` 但实际是 **out thread 异常引用**
(out catch line 132 写, line 151 读), 名字暗示是 err thread 异常实际是 out thread
异常, 读代码的人混乱。

**修法**: 重命名 `errRef` → `outErr` (跟 out thread 绑)。

#### 12u.2 - err thread 静默吞错

**真 bug**: 之前 err thread `catch (_: Throwable) { /* ignore */ }` 静默吞, 跟
out thread 行为不一致 (out thread 异常时 outErr[0] 写, 主流程 throw)。

后果:
- stderr 读失败时用户拿到空 stderr 实际是 read 失败, 排查困难
- 调用方拿到 `AdbResult(stderr="")` 误以为命令 stderr 真的空

**修法**: 加 `errErr = arrayOfNulls<Throwable>(null)`, err thread 异常时写
`errErr[0] = t`, 主流程 `errErr[0]?.let { log.warn("adb: read stderr failed: ...") }`。
不抛 (跟之前一致, stderr 返空字符串, 主流程靠 stdout + exit code 判定), 但
留 log 让排查有线索。

#### 12u.3 - out thread 失败直接抛原始 Throwable

**真 bug**: 之前 `if (errRef[0] != null) throw errRef[0]!!` 抛原始 `Throwable`,
可能不是 `IOException`, 调用方 `try-catch IOException` 抓不到。

**修法**: `outErr[0]?.let { throw IOException("adb: read stdout failed: ${it.message}", it) }`
包 IOException, 调用方 try-catch IOException 一致。

#### 12u.4 - 副作用与不变性

- 正常路径 (adb 命令成功) 行为完全不变: stdout / stderr / exit code 走原逻辑
- err thread 失败时只多一行 log.warn, AdbResult.stderr 仍返空字符串 (跟之前一致)
- out thread 失败时抛 IOException 包 (调用方可 try-catch IOException 抓到)
- AdbRunnerTest 只测 FakeAdbRunner, 不影响

## 新增/修改文件 (Phase 12u)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../adb/AdbRunner.kt` (重命名 errRef→outErr + 加 errErr + outErr 异常包 IOException) |
