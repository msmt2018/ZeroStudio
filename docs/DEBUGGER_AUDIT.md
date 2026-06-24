# ZeroStudio 断点调试器 完整架构审计 (2026-06-23)

## 1. 已完成功能清单 (PR-1 ~ PR-9 已合并到 main)

### 1.1 协议层 (`ide-debugger/.../jdwp/`)
| 文件 | 状态 |
|---|---|
| `JdwpPacket.java` | ✅ JDWP 包编码/解码 |
| `JdwpPacketCodec.java` | ✅ ByteBuf ⇄ JdwpPacket |
| `CommandSet.java` | ✅ 14 个 command set 常量 |
| `CommandCodes.java` | ✅ 各 command set 下的 command 编号 |
| `ModKind.java` | ✅ EventRequest modifier 种类 |
| `SuspendPolicy.java` | ✅ NONE/EVENT_THREAD/ALL |
| `EventKind.java` | ✅ SINGLE_STEP/EXCEPTION/BP/... |
| `JdwpError.java` | ✅ JDWP 错误码常量 |
| `JdwpEvents.java` | ✅ EventRequest.Set 编码工具 |

### 1.2 连接层 (`jdwp/JdwpClient.java`, `jdwp/JdwpPacketReader.java`)
- ✅ TCP socket + handshake
- ✅ 异步读线程
- ✅ 事件分发
- ✅ CommandSet 9 (ObjectReference) GetValues/InvokeMethod 完整实现
- ⚠️ **缺**: 自动重连、心跳、长空闲超时保护

### 1.3 通讯层
- ✅ `JdwpClient.sendCommand()` 同步调用 + reply future
- ✅ `sendCommandNoReply()` (事件订阅用)
- ✅ WriteLock 串行化
- ⚠️ **缺**: connection 异常后只 fire onDisconnected 一次,不重连

### 1.4 应用层 (`api/`)
- ✅ `Debugger.java` — 顶层 facade,断点管理,事件订阅
- ✅ `Breakpoint.java` — POJO with condition/logMessage
- ✅ `StackFrameInfo.java` — 栈帧信息
- ✅ `SuspendInfo.java` — 挂起信息
- ✅ `VariableInfo.java` — 变量信息
- ✅ `EvalResult.java` — 求值结果 (Tag, value, typeSignature, objectId, error)

### 1.5 服务层 (`model/`)
- ✅ `SourceLocator.java` — 源码→类映射,断点安装/卸载,变量读取
- ✅ `BreakpointStore.java` — 断点存储 (in-memory)
- ✅ `DebugSession.java` — 调试会话状态
- ✅ `EvalEngine.java` — 表达式求值(PR-9 已支持方法带参)
- ✅ `BreakpointManager.java` (core/app) — IDE 端断点管理 + JSON 持久化
- ✅ `IdeBreakpoint.java` (core/app) — 7 状态 POJO
- ✅ `LogStore.java` (core/app) — 日志点输出环形缓冲
- ✅ `WatchStore.java` (core/app) — 监视表达式存储
- ✅ `DebugSessionState.java` (core/app) — 跨 fragment 共享状态

### 1.6 事件总线 (`event/`)
- ✅ `DebugEventBus.java` — 进程内 pub/sub
- ✅ `DebugEvents.java` — Type 枚举 (VM_START/SUSPEND/RESUME/BP_HIT/LOGPOINT) + 工厂

### 1.7 IDE 集成 (`core/app/.../debugger/`)
- ✅ `DebuggerController.java` — 桥接 Debugger ↔ UI
- ✅ `BreakpointGutterManager.java` — 编辑器 gutter 集成
- ✅ `BreakpointSidebar.java` — 自定义 View 画断点图标
- ✅ 5 个 Fragment (Variables/Watches/CallStack/Logpoint/BreakpointList)
- ✅ Adapters (CallStackAdapter 等)
- ✅ EditorBottomSheetTabAdapter 注册 4 个 tab

### 1.8 代码编辑器层
- ✅ `CodeEditor` (sora editor) 集成通过 `BreakpointGutterManager` + `BreakpointSidebar`
- ✅ 断点点击跳转 (`BreakpointListFragment.navigateToBreakpoint`)
- ✅ `openFileAndSelect()` 打开文件并定位到行

### 1.9 应用程序断点控制
- ✅ stepOver/stepInto/stepOut/pause/resume/runToCursor 已实现
- ✅ onSuspend 派发到 listener

### 1.10 测试
- ✅ `EvalEngineTest` (30+ 解析器测试)
- ✅ `EvalResultTest` (6 工厂方法)
- ✅ `EvalEngineEvaluateTest` (19 端到端)
- ✅ `EvalEngineHelpersTest` (16 纯函数)
- ✅ `SourceLocatorFetchLocalTest` (9 case)
- ✅ `DebuggerIsTruthyTest` (19 case)
- ⚠️ **缺**: JdwpClient/Codec/BreakpointStore/DebugSession 单元测试

---

## 2. 关键缺口 (核心架构缺失)

### 2.1 ❌ 完全没有的:JDWP 服务端 + Gradle 插件 + Log 传输

`docs/TODO/新功能概念/跨进程与断点调试器.md` 规范定义的完整链路:

```
IDE 端 (已实现)
   ↓ (网络)
目标 APK 端 (基本没实现)
   ├── JDWP 监听 (JdwpServer.java 有,但依赖的类都没)
   ├── 注入到用户 APK (IdeLogInitScriptPlugin 没写)
   └── 日志回传 (LogSocketServer/LogCaptureService/LogBuffer/WireConstants 全没)
```

具体缺失的类和模块:

| 类 | 状态 | 用途 |
|---|---|---|
| `com.zerostudio.logwire.WireConstants` | ❌ 缺 | 日志传输协议常量 |
| `com.zerostudio.logplugin.api.LogPayload` | ❌ 缺 | 单条日志的 wire format |
| `com.zerostudio.logplugin.api.LogTransportType` | ❌ 缺 | 传输类型 (TCP/Unix/...) |
| `com.zerostudio.logplugin.api.LogLevel` | ❌ 缺 | 日志级别 |
| `com.zerostudio.logplugin.util.LogBuffer` | ❌ 缺 | 环形缓冲 |
| `com.zerostudio.logplugin.transport.LogSocketServer` | ❌ 缺 | 目标 APK 内的 socket server |
| `com.zerostudio.logplugin.capture.LogCaptureService` | ❌ 缺 | 抓取 logcat 输出 |
| `com.zerostudio.logplugin.plugin.IdeLogInitScriptPlugin` | ❌ 缺 | Gradle init script plugin |
| `com.zerostudio.debugger.IdeDebuggerInitScriptPlugin` | ❌ 缺 | 调试器的 init script plugin (PR-2 注释掉了) |
| `ide-log-plugin/build.gradle.kts` | ❌ 缺 | 整个模块没 build 文件 |
| `utilities/logwire/` 模块 | ❌ 缺 | 整个模块不存在 |

### 2.2 ⚠️ EvalEngine 表达式求值功能不足

| 表达式 | 状态 |
|---|---|
| identifier / this | ✅ |
| field access | ✅ |
| method call (0-arg) | ✅ |
| method call (有参) | ✅ (PR-9) |
| string literal (CreateString) | ✅ |
| int/double literal | ✅ |
| 字段调用 + 方法 | ✅ |
| 分组 ( (expr) ) | ✅ |
| 链式 ( a.b.c() ) | ✅ |
| **static field access** (Foo.COUNT) | ❌ |
| **static method call** (Math.max(a,b)) | ❌ |
| **array index** (arr[0]) | ❌ |
| **算术运算** (a + b, a * 2) | ❌ |
| **字符串拼接** ("a=" + a) | ❌ |
| **三元/比较** (a > 0 ? "x" : "y") | ❌ |
| **lambda 表达式** | ❌ |
| **null 安全** (a == null) | ❌ |

### 2.3 ⚠️ JDWP 协议覆盖不完整

| JDWP 功能 | 状态 |
|---|---|
| 事件订阅 (BP/STEP/EXCEPTION/CLASS_PREPARE) | ⚠️ 只实现了 BP/STEP |
| CLASS_PREPARE 事件 | ❌ |
| CLASS_UNLOAD 事件 | ❌ |
| METHOD_ENTRY/EXIT | ❌ |
| THREAD_START/DEATH | ❌ |
| EXCEPTION 事件 + catch 过滤 | ❌ |
| ForceEarlyReturn | ❌ |
| 栈帧创建深度/大小控制 | ❌ |
| 数组引用 (ArrayReference) | ❌ |
| 字符串引用 (StringReference) | ❌ |
| ThreadGroup 引用 | ❌ |
| 重新订阅 events after reconnect | ❌ |

### 2.4 ⚠️ SourceLocator 粗糙

```java
// 当前实现:只用文件名 basename 反推
private @NonNull String guessClassSignature(String sourceFile) {
    int dot = sourceFile.lastIndexOf('.');
    String basename = (dot > 0) ? sourceFile.substring(0, dot) : sourceFile;
    int slash = Math.max(basename.lastIndexOf('/'), basename.lastIndexOf('\\'));
    if (slash >= 0) basename = basename.substring(slash + 1);
    return "L" + basename + ";";
}
```

问题:
- ❌ 包名无法推断(用文件 basename 冒充 FQCN)
- ❌ inner class ($1, $Inner) 没法处理
- ❌ Kotlin 文件 (Foo.kt -> FooKt?) 没法处理
- ❌ 没读 .java/.class 文件真正解析
- ❌ 没缓存

应该的:
- 扫描项目源文件提取 class signature
- 用 javac/JavaParser 解析 .java 文件
- 用 javap/asm 读 .class 文件
- 维护 sourceFile -> List<classSignature> 映射

### 2.5 ⚠️ 持久化 / 多项目

- ❌ `BreakpointStore` JSON 持久化没 schema 版本
- ❌ 断点与项目路径绑定,但项目搬移后失效
- ❌ 没支持多项目同时打开的断点

### 2.6 ⚠️ UI 缺

- ❌ WatchesFragment 在 UI 线程调 `dbg.eval()` (ANR 风险)
- ❌ BreakpointListFragment 用系统 input dialog,体验差
- ❌ 一些 Adapter (CallStackAdapter / VariablesAdapter) 可能未实现
- ❌ 暗色主题适配
- ❌ 国际化 (i18n)
- ❌ 无障碍 (TalkBack)

### 2.7 ⚠️ DebuggerController 缺

- ❌ `stop()` 是 no-op
- ❌ 没有 connect/disconnect 入口
- ❌ 没有 auto-attach / auto-discover
- ❌ 启动后没有自动启动 socket server

### 2.8 ❌ 完全没有的:构建/安装/启动/Attach 集成

规范要求:"jdwp协议 + run-as 实现的合法跨进程调试"

缺失:
- ❌ IDE 没有"调试"按钮触发 build + install + launch
- ❌ 没有"等应用启动后自动 attach"流程
- ❌ 没有 shizuku 集成(优先)
- ❌ 没有 run-as 集成(备选)
- ❌ 没有 `android:debuggable="true"` 自动注入
- ❌ 没有 JDWP listener 自动启动
- ❌ 没有"目标应用已就绪"信号机制

### 2.9 ❌ 完全没有的:CI / 文档

- ❌ 没有 `.github/workflows/` 跑测试
- ❌ 没有 architecture diagram
- ❌ 没有 JDWP 协议说明
- ❌ 没有贡献指南

---

## 3. 推荐开发顺序

按重要性 + 可行性排序:

### Phase A: 表达式求值补全 (低风险,高收益)
- A1. Arithmetic operators (`+ - * / %`)
- A2. Comparison + logical operators (`== != < > <= >= && ||`)
- A3. String concatenation
- A4. Static field access (`Foo.COUNT`)
- A5. Static method call (`Math.max(a, b)`)
- A6. Array index (`arr[0]`, `arr.length`)
- A7. 三元运算符 (`a > 0 ? "x" : "y"`)
- A8. 单元测试覆盖所有新功能

### Phase B: JDWP 协议补全
- B1. ClassPrepare 事件订阅(用于延迟安装断点)
- B2. Exception 事件 (catch/uncaught 过滤)
- B3. ArrayReference.GetValues/SetValues
- B4. StringReference.Value
- B5. ForceEarlyReturn (方法强制返回)
- B6. 自动重连 + 重新订阅 events

### Phase C: 目标应用端 (完全没做,需新建模块)
- C1. `utilities/logwire` 模块 (wire protocol 共享)
- C2. `ide-log-plugin` 模块 build 脚本
- C3. `IdeLogInitScriptPlugin` - 注入到用户项目
- C4. `JdwpServer` 依赖的 7 个类
- C5. `LogCaptureService` - 抓 logcat
- C6. `LogSocketServer` - 把日志送到 IDE
- C7. `IdeDebuggerInitScriptPlugin` (PR-2 注释的)

### Phase D: IDE 集成 + 端到端流程
- D1. Debug 入口(按钮 + 菜单)
- D2. Build + install + launch 流程
- D3. shizuku 集成(优先)
- D4. run-as 备选
- D5. 自动 attach + 心跳
- D6. 远程设备发现(adb)
- D7. 应用已就绪信号

### Phase E: UI 打磨
- E1. VariablesAdapter / WatchesAdapter / CallStackAdapter 完整实现
- E2. 自定义条件断点对话框
- E3. 暗色主题
- E4. 国际化
- E5. 无障碍

### Phase F: 测试 / CI / 文档
- F1. JdwpClient/Codec 单元测试
- F2. BreakpointStore 单元测试
- F3. DebugSession 单元测试
- F4. SourceLocator 单元测试(非 fetchLocal 部分)
- F5. CI workflow (GitHub Actions)
- F6. Architecture diagram
- F7. JDWP 协议说明
- F8. 贡献指南

### Phase G: SourceLocator 升级
- G1. 用 JavaParser 解析 .java 文件
- G2. 用 ASM/javap 读 .class 文件
- G3. 维护 sourceFile → classSignature 映射
- G4. 处理 inner class / Kotlin
- G5. 处理 lambda / anonymous class

### Phase H: 性能 / 稳定性
- H1. 批量 GetValues (一次拿多个 slot,减少 roundtrip)
- H2. UI 线程 ANR 防护
- H3. 长空闲断连检测
- H4. 大项目断点安装并发优化
