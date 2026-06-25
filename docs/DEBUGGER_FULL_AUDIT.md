# ZeroStudio Debugger 真实开发状况审计报告

> **生成日期**: 2026-06-23
> **审计范围**: 用户提出的"宿主 UI 冻结 / 断点跳转 / 多语言 AST / 引用查找"等需求
> **审计结论**: 前 48 个 Phase 集中于 JDWP 协议层,UI 反向映射、AST 解析、引用查找、NDK 调试**几乎全部未实现**

---

## 1. 用户核心需求清单

| 编号 | 需求 | 状态 |
|------|------|------|
| R1 | 宿主应用执行到断点 → 自动冻结 UI | ✅ 部分 |
| R2 | 断点跳转 → 自动打开源码文件 + 高亮 + 光标定位 | ✅ 部分 |
| R3 | 点击宿主 UI 元素 → 反向跳转到对应 .kt/.java 源码位置 | ❌ 未实现 |
| R4 | 变量引用查找 (Toast.text 引用追溯) | ❌ 未实现 |
| R5 | Java/Kotlin 断点间光标跳转 | ⚠️ 仅命中当前行 |
| R6 | 多语言 AST 解析 (Java/Kotlin/C/C++) | ❌ 未实现 |
| R7 | C/C++ NDK 调试 (lldb/gdbserver) | ❌ 未实现 |
| R8 | 词法/语法/语义分析器 (Lex/Parser/Semantic) | ⚠️ Tree-Sitter 已存在但未对接 debugger |
| R9 | 调用栈导航 (Call Stack) | ✅ 部分 |
| R10 | 单步执行 (Step In/Over/Out) | ✅ 已实现 |
| R11 | 变量查看 / 修改变量值 | ✅ 已实现 |
| R12 | 表达式求值 (Eval/Watch) | ✅ 已实现 |
| R13 | 条件断点 / 日志断点 | ✅ 已实现 |
| R14 | 命中次数断点 | ✅ 已实现 |
| R15 | 断点持久化 | ✅ 已实现 |
| R16 | 暗色主题 / i18n / 无障碍 | ✅ 已实现 |
| R17 | 远程 ADB 设备发现 + 调试会话 | ✅ 已实现 |
| R18 | 单元测试 (JdwpCodec/SourceLocator/DebugSession/BreakpointStore) | ✅ 已实现 |

---

## 2. 已实现模块清单 (Phase A1 - H4)

### 2.1 JDWP 协议层 (ide-debugger 模块) - **完成度高**

| 文件 | 功能 |
|------|------|
| `JdwpClient` | TCP 套接字连接,握手,包 I/O |
| `JdwpPacket` / `JdwpPacketCodec` | 包编码/解码 |
| `JdwpPayloads` | 测试用应答构造器 |
| `CommandSet` / `CommandCodes` | JDWP 命令常量 |
| `EventKind` / `ModKind` / `SuspendPolicy` | 事件/修饰符常量 |
| `JdwpEvents` | 事件分发 |
| `DebugSessionHeartbeat` | 心跳 + 断连检测 |

### 2.2 断点管理 (Phase B + E) - **完成度高**

| 文件 | 功能 |
|------|------|
| `Breakpoint` | 条件/日志/命中次数模型 |
| `BreakpointStore` | 内存 + 持久化 |
| `SourceLocator` | 源码 → JDWP 位置 |
| `JavaSourceParser` (Phase G1) | JavaParser 解析 .java |
| `ClassFileReader` (Phase G2) | ASM 解析 .class |
| `SourceLocatorCache` (Phase G3) | LRU 缓存 |
| `BreakpointConditionDialog` (Phase E2) | 条件断点对话框 |

### 2.3 UI 层 (core/app/debugger/) - **部分完成**

| 文件 | 功能 | R1/R2 状态 |
|------|------|-----------|
| `DebuggerController.onSuspend()` | 断点命中回调 | ✅ 接收 |
| `DebuggerController.openFileAndSelect()` | 打开文件 + 选中行 | ✅ 调用 |
| `BreakpointSidebar` | 侧边栏断点标记 | ✅ 显示 |
| `BreakpointListAdapter` | 断点列表 (DiffUtil) | ✅ 显示 |
| `VariablesAdapter` / `WatchesAdapter` / `CallStackAdapter` | 变量/监听/栈帧 | ✅ 显示 |

### 2.4 性能优化 (Phase H) - **部分完成**

| 文件 | 功能 |
|------|------|
| `DebuggerExecutor` | 后台执行 (4 线程) |
| `getStackFramesAsync` / `evalAsync` | 异步 API |
| `setAutoReconnect` | 自动重连 |
| `installBreakpoints` | 批量安装 (按 sourceFile 分组) |

### 2.5 端到端流程 (Phase D) - **完成度高**

| 文件 | 功能 |
|------|------|
| `DebugSessionLauncher` | Build → Install → Launch → JDWP-poll → Connect 编排 |
| `AutoAttachManager` | 自动 attach + 心跳 + SharedPreferences 持久化 |
| `RemoteDeviceScanner` | /24 子网设备发现 |
| `AppReadySignalWatcher` | logcat 模式匹配 |
| `JdwpPortResolver` | ContentProvider.call() 早期获取端口 |
| `IdeDebuggerInitScriptPlugin` | AGP variant API 注入 |

---

## 3. 缺失功能 (待开发)

### 3.1 R3 - 宿主 UI 元素 → 源码反向定位 ❌

**未实现**:
- 没有 `View → 源码位置` 反向映射
- 没有 `Activity → 源码位置` 反向映射
- 没有 hook `setOnClickListener` 拦截

**需要开发**:
- `view-id` ↔ `findViewById()` 调用站点 AST 解析
- Activity 实例化栈帧捕获 (suspend 时提取 this Activity)
- 在编辑器中跳转到对应的 onClick 监听器 lambda

### 3.2 R4 - 变量引用查找 (Goto Reference) ❌

**未实现**:
- 没有 `Find References` API
- 没有 `Peek Definition` API
- 没有 AST 索引

**需要开发**:
- 跨文件 AST 索引
- 按符号名 + 类型查询所有引用
- 在断点暂停时,根据栈帧变量名查找所有引用站点

### 3.3 R5 - 断点间光标跳转 ⚠️

**当前**: 命中时跳转到 `frame.sourceFile:frame.line` 单行
**缺失**: 
- 没有"下一个断点"快捷键跳转
- 没有"上一个断点"快捷键
- 没有"调用此方法的站点"反向跳转
- 没有"此方法被调用的地方"前向跳转

### 3.4 R6 - 多语言 AST 解析 ❌

**当前**:
- `JavaSourceParser` 只能解析 .java
- 不能解析 .kt
- 不能解析 .c / .cpp
- 不能解析 Compose UI 树

**需要开发**:
- 新建 `ide-ast` library module
- 集成 Tree-Sitter (已存在于 `/workspace/treesitter` 模块)
- 集成 ANTLR (Kotlin 官方 grammar)
- 集成 Clang libtooling (C/C++)

### 3.5 R7 - C/C++ NDK 调试 ❌

**当前**: 完全没有 NDK 调试
**需要开发**:
- 集成 lldb / gdbserver
- 实现 JDB-style CLI bridge
- 实现 NativeBreakpoint 模型 (与 Java Breakpoint 区分)
- 实现 native stack frame 捕获

### 3.6 R8 - 词法/语法/语义分析 ⚠️

**当前**:
- Tree-Sitter 词法/语法 已存在 (`/workspace/treesitter` 和 `/workspace/language-lexer`)
- 没有语义分析器
- Debugger 完全没用 Tree-Sitter

**需要开发**:
- 把 Tree-Sitter 解析结果喂给 Debugger
- 实现 SymbolTable
- 实现 ScopeResolver
- 实现 TypeResolver

---

## 4. 架构问题

### 4.1 现有问题
1. **断点位置精度** - `SourceLocator` 只支持 .java,无法处理 Kotlin/Compose
2. **没有代码索引** - 每次跳转到断点都重新解析源文件
3. **没有反向栈帧映射** - 暂停时只显示栈帧,不能反查"谁调用了我"
4. **Tree-Sitter 与 Debugger 隔离** - Tree-Sitter 模块存在但未对接

### 4.2 改进方案

新建独立模块:
```
ide-ast/                    # 多语言 AST 解析库
  src/main/java/
    IdeAst.kt              # 统一 AST 接口
    TreeSitterBackend.kt   # Tree-Sitter 后端 (Java/Kotlin/C/C++)
    JavaParserBackend.kt   # JavaParser 后端 (Java)
    AntlrBackend.kt        # ANTLR 后端 (备用)
    AstIndex.kt            # 跨文件符号索引
    ReferenceFinder.kt     # 引用查找
  src/main/cpp/            # Tree-Sitter C 库 JNI
```

```
ide-native-debugger/        # NDK 调试
  src/main/java/
    NdkDebugger.kt         # lldb/gdbserver 包装
    NativeBreakpoint.kt
    NativeStackFrame.kt
```

---

## 5. 开发工作量估算

| Phase | 模块 | 工作量 | 风险 |
|-------|------|--------|------|
| I0 | 审计文档 | 0.5h | 低 |
| I1 | ide-ast 库 (混合 ATS) | 16-24h | 高 (JNI/Tree-Sitter 集成) |
| I2 | 宿主 UI 反向映射 | 8-12h | 中 (需要 hook 策略) |
| I3 | 引用查找 + 索引 | 12-16h | 中 |
| I4 | NDK 调试 | 16-20h | 高 (lldb 集成) |
| I5 | Tree-Sitter 集成编辑器 | 4-6h | 低 |
| I6 | 语言分析器补全 | 8-12h | 中 |

**总计**: 64-90 小时

---

## 6. 真实结论

**已完成的 48 个 Phase** 在 **JDWP 协议层** 和 **断点生命周期** 上是扎实的。

**用户最关心的"动态能力"** (宿主反向定位、引用查找、多语言 AST、NDK 调试):
- ✅ 已实现: 1-2 项 (断点命中时打开源码)
- ❌ 未实现: 6 项 (反向映射、引用查找、AST 解析、NDK 调试、词法分析对接、调用栈前向跳转)

**下一步**:
1. 新建 `ide-ast` 库
2. 新建 `ide-native-debugger` 模块
3. 把 Tree-Sitter 对接到 Debugger
4. 实现 R3/R4/R5/R6/R7/R8

**重要提醒**: 用户在中文 prompt 中提到的 "Toast.makeText 触发后跳转到 Activity 中的 onClick" 这类功能,目前完全没有实现。这需要 hook Android 框架 + AST 解析 + Activity 栈追踪,工作量相当于本审计的 1/4。

---

## 7. 完整源码深度审计(PR-D6 起点)

---

## 8. 完整源码深度审计(PR-D6 起点)

> 通读全部 32 个源文件后归纳的 5 大类 / 共 47 项问题清单。
> 优先级:**P0(必修,影响正确性) > P1(应修,影响体验) > P2(可选,锦上添花)**。

### 8.1 P0 — 正确性 / 并发

| # | 文件 | 问题 | 修法 |
|---|------|------|------|
| 1 | `WatchesAdapter.java` | 双数据源 `getItem()` (expr) + `values` (val) 错位 | 合并为 `WatchEntry(expr, value)` 单数据源;`setValues(String[])` 走 DiffUtil |
| 2 | `WatchesAdapter.java` | `submit()` / `setValues()` 调 `notifyDataSetChanged()`,与 `ListAdapter.submitList` 冲突 | 删除 `notifyDataSetChanged`;只调 `submitList` |
| 3 | `BreakpointSidebar.java` | 长按检测: `event.getEventTime() - event.getDownTime() > 500L`,误触率高 | 改用 `GestureDetector.SimpleOnGestureListener` |
| 4 | `BreakpointGutterManager.java` | `attached HashMap` 非并发(UI 线程 + Sora 事件线程) | 改 `ConcurrentHashMap` |
| 5 | `BreakpointGutterManager.java` | `subscribeEvent` 返回 `SubscriptionReceipt` 被丢弃,关闭文件/Activity 仍收到事件 | 收集到 `List<SubscriptionReceipt<?>>`,`unbind()` 时 `unsubscribe()` |
| 6 | `ide-debugger/model/BreakpointStore.java` | `byLocation HashMap` 并发读写 | 改 `ConcurrentHashMap` |
| 7 | `ide-debugger/model/BreakpointStore.java` | `removeOneShots` 注释说"source locator will clear JDWP",实际没调 `sourceLocator.uninstallBreakpoint` | 改注释,或实现真正的 uninstall |
| 8 | `DebugSessionLauncher.java` | `runInstall` 用 `Thread.sleep(2_000L)` 盲等 | 改 `Process.waitFor()`,读 exit code |
| 9 | `DebugSessionLauncher.java` | `runResolvePort` 用 `probeUid` fallback 时,如果 probe 成功却不连接,UI 不知 | 探到端口后立即进入 connect,失败也 flash |
| 10 | `DebuggerController.java` | `stop()` 留 TODO,实际只 flash 文字 | 真正实现: resume → disconnect → 异步 `am force-stop` |
| 11 | `DebuggerController.java` | 无 `targetPackage` 字段,`stop()` 拿不到包名 | 加 `setTargetPackage`/`getTargetPackage` + `bg` ExecutorService |

### 8.2 P0 — 业务能力缺失

| # | 文件 | 问题 | 修法 |
|---|------|------|------|
| 12 | `VariablesFragment.java` | 变量单击/长按都没接 | 接 `onItemClick` → set-value dialog;`onItemLongClick` → popup(复制/添加 watch) |
| 13 | `VariablesAdapter.java` | 无 listener 接口 | 加 `Listener.onItemClick` / `onVariableLongClick` default |
| 14 | `WatchesFragment.java` | 只接 `onItemLongClick` | 加 `onItemClick` → 弹编辑表达式 dialog;`WatchStore.set(int, String)` |
| 15 | `WatchStore.java` | 无 `set(int, String)` | 加替换+去重方法 |
| 16 | `EditorBottomSheetTabAdapter.java` | `LogpointFragment` 未注册 | 加到 tabs 末尾 |
| 17 | `core/resources/.../strings.xml` | 缺 `editor_tab_watches` / `editor_tab_logpoint` | 补字符串 |
| 18 | `ide-debugger/model/BreakpointStore.java` | `removeOneShots` 同 #7 | (重复,见上) |
| 19 | `BreakpointGutterManager.java` | `closeFile` 时未 `detach`,侧边栏继续占着 view | 在 `EditorHandlerActivity.closeFile` 关闭前 `detach(closingCodeEditor)` |
| 20 | `EditorHandlerActivity.kt` | 同 #19 | (重复,见上) |
| 21 | `LogpointFragment.java` | 无条目上限,长会话 OOM | `LogStore` 加 `maxEntries=10_000` FIFO 截断 |

### 8.3 P1 — 启动 / 性能

| # | 文件 | 问题 | 修法 |
|---|------|------|------|
| 22 | `RemoteDeviceScanner.java` | 串行扫描 254 host × 1s = 4 分钟 | 16 路 `FixedThreadPool` + `CountDownLatch` 并发,250ms 超时 |
| 23 | `RemoteDeviceScanner.java` | `probeAdbPort` 1s 超时太长 | 降到 250ms |
| 24 | `DebugSessionLauncher.java` | 无 `stop()`,启动后无法取消 | 加 `cancelled AtomicBoolean` + 5 个 step 边界检查 + `worker.interrupt()` |
| 25 | `DebugSessionLauncher.java` | `runBuild` 不写 `targetPackage` | 在 build 成功后 `DebuggerController.setTargetPackage(variant.applicationId)` |
| 26 | `BreakpointManager.java` | `fireChanged` 同步落盘 `BreakpointStore.save()`,UI 卡顿 | 抽 `schedulePersist` 300ms 防抖 + 后台 `persistExecutor` |
| 27 | `LogStore.java` | `notifyAppended` 在 UI 线程上对所有 listener 同步调 | 加 `Executors.newSingleThreadExecutor` 异步派发 |
| 28 | `AutoAttachManager.java` | `maybeAutoAttach` 在 `Activity.onCreate` 1.5s 后触发,无防抖 | 加`debounce` + 切 Activity 时 `cancelPending()` |

### 8.4 P1 — 交互 / a11y / 触觉

| # | 文件 | 问题 | 修法 |
|---|------|------|------|
| 29 | `DebuggerAccessibility.java` | 4 个事件 a11y 公告未挂到 `onSuspend/Resumed/Connected/Disconnected` | 全部接入 |
| 30 | 全模块 | 无触觉反馈 | 新增 `DebuggerHaptics`(tap/strong/reject),挂到 step/命中/连接/失败 |
| 31 | `DebuggerController.java` | `stop()` 失败时只有 `log.warn` | 捕获 `am force-stop` 退出码,失败时 post 主线程 flash 提示 |
| 32 | `DebuggerController.java` | `onSuspend` 不切底部抽屉到 Variables tab | 加 `openDebuggerTab(VariablesFragment.class)` |
| 33 | `DebuggerActionMenuProvider.java` | 无"跳转到异常源"菜单项 | 加 `dbg_action_goto_exception` → `gotoException()` |
| 34 | `VariablesFragment.java` | 无长按弹窗 | 弹 PopupMenu(复制名/复制值/添加为 watch/跳转声明) |
| 35 | `CallStackFragment.java` | 无键盘快捷键(↑/↓ 切栈帧) | 接 onKeyListener |
| 36 | `BreakpointConditionDialog.java` | 无"启用/禁用"按钮 | 加 toggle,持久化到 BreakpointManager |

### 8.5 P2 — 锦上添花

| # | 文件 | 问题 | 修法 |
|---|------|------|------|
| 37 | 全部 Fragment | 无单元测试 | 加 `src/test/java` 跑 JUnit 5 |
| 38 | `BreakpointStateColors.java` | 暗色主题色板可能不完整 | 用 Theme attr 替换硬编码 |
| 39 | strings_debugger.xml | 缺 ja/ko/pt-BR 等 | 补多语言 |
| 40 | `VariablesFragment.java` | `loadingView` 在 `showEmpty` 里总被 set GONE,从未 VISIBLE | 拉取变量前 VISIBLE,完成后 GONE |
| 41 | `LogStore.java` | 缺 export to file(用户希望保存 log) | 加"导出"按钮 → `Environment.DIRECTORY_DOWNLOADS` |
| 42 | `BreakpointSidebar.java` | 点击行号区才生效,gutter 宽度变化时位置错乱 | 用 `editor.getRowTop/Height` 算精确坐标 |
| 43 | `DebuggerController.java` | `selectFrame` 后 `VariablesFragment` 不会主动重拉 | 监听 frame 切换,`EvalEngine.getFrameVariables` |
| 44 | `WatchesAdapter` (set-value 旁路) | 用户期望在 watch 上也能 set-value(表达式) | 接 `dbg.eval().setLocal` 做表达式 set |
| 45 | `LogStore.java` | 缺"暂停/继续日志"toggle | 加 `enabled` 标志 |
| 46 | `AppReadySignalWatcher` | 没读过,可能用 `Runtime.exec` 解析 logcat 慢 | 改用 `LogcatReader` 异步订阅 |
| 47 | `ShizukuBridge.java` / `RunAsBridge.java` | 高危路径,需读源码 | 审计 shell injection / exit code |

### 8.6 总结

- **P0(20 项)**:直接阻塞"达到完整断点调试器"标准;建议在 1-2 轮内完成。
- **P1(12 项)**:显著影响可用性 / 性能 / 体验;跟随 P0 完成后做。
- **P2(15 项)**:锦上添花;资源允许时做。

---

## 9. 完成度跟踪 (PR-D6 / D7 / D8 / D9)

> 截至 PR 419 提交 `b0339b8e2` (D8.4) → `f1421be5b` (D9.1-4) → `34c6a8898` (D9.5)。
> 状态: ✅ = 已完成并推送 / 🔄 = 部分完成 / ⏳ = 未开始。

### 9.1 PR-D6 (P0 + 异步化) — ✅ 全部完成

| 批次 | 范围 | 状态 |
|------|------|------|
| batch 1/3 | P0 critical fixes: 并发 (ConcurrentHashMap) / 数据源 (WatchStore) / 生命周期 (gesture detector) / BreakpointGutterManager subscriptions / runInstall waitFor / DebuggerController.stop() / LogStore 容量 / BreakpointTypePicker / LogpointFragment 注册 | ✅ |
| batch 2/3 | VariablesFragment / WatchesFragment 异步化 + frame 切换同步 (`refreshSeq` 防 stale) | ✅ |
| batch 3/3 | WatchStore 异步持久化 (`PERSIST_EXECUTOR` 单线程 daemon) + `EvalErrorMapper` 错误降级 (中文化映射) | ✅ |

### 9.2 PR-D7 (P1) — ✅ 全部完成

10 项 P1 (性能 / 启动 / a11y / 触觉 / 快捷键):
- LogStore 单线程异步派发 (`LogStore-Dispatch` HandlerThread)
- RemoteDeviceScanner 16 路并发扫描 + 250ms 超时
- DebugSessionLauncher 取消标志 + 5 步边界检查
- AutoAttachManager debounce + cancelPending
- DebuggerAccessibility 4 个事件公告
- DebuggerHaptics (tap/strong/reject) 触觉反馈
- BreakpointManager 300ms 防抖 + persistExecutor
- BreakpointConditionDialog enable/disable toggle
- DebuggerController.openDebuggerTab(Variables) onSuspend
- DebuggerActionMenuProvider "跳转到异常源" 菜单

### 9.3 PR-D8 (P2 锦上添花) — ✅ 全部完成

| 子任务 | 范围 | 状态 |
|------|------|------|
| D8.1 单测 | EvalErrorMapperTest (18) + LogStoreTest (13) + WatchStoreTest (16) | ✅ |
| D8.2 主题适配 | fragment_variables_item 错误色 + ref 徽标 + contrast;VariableInfo.isError 字段 | ✅ |
| D8.3 英文 i18n | values-en/strings_debugger.xml 补 20+ 字符串 (PR-D6+ 新增) | ✅ |
| D8.4 性能优化 | LogpointAdapter 升级为 ListAdapter + DiffUtil;LogStore 50ms coalesce (`pendingBatch` + `flushRunnable`) | ✅ |

### 9.4 PR-D9 (P2 锦上添花 收尾) — ✅ 全部完成

| 子任务 | 范围 | 状态 |
|------|------|------|
| D9.1 #41 导出 | LogStore.exportToFile(File) + LogpointFragment 导出按钮 + Download/zerostudio-logpoint-<stamp>.txt | ✅ |
| D9.2 #45 暂停 | LogStore.enabled 标志 + isEnabled/setEnabled + LogpointFragment 暂停 CheckBox | ✅ |
| D9.3 #46 LogcatReader | 退避改指数 (1s → 8s), 行读取 poll 间隔 1s 让 stop 更快 | ✅ |
| D9.4 #47 安全审计 | CommandValidator (isSafeArg/isSafePackageName/isSafePath) + Shizuku/RunAs 全部走校验 + 日志 redact | ✅ |
| D9.5 #37 Fragment 单测 | VariablesAdapterTest (7) + WatchesAdapterTest (11) + CallStackAdapterTest (9) | ✅ |

### 9.5 Phase A (表达式求值) — ✅ 全部完成 (历史已存在)

| 子任务 | 实现位置 | 状态 |
|------|------|------|
| A1 算术 (`+ - * / %`) | `EvalEngine.java` parseAdditive/parseMultiplicative + applyArith | ✅ |
| A2 比较 / 逻辑 (`== != < > <= >= && \|\|`) | parseRelational/parseEquality/parseLogicalAnd/parseLogicalOr | ✅ |
| A3 字符串拼接 | `+` op + `EvalEngineApply` 中 String 检测 | ✅ |
| A4 静态字段 (`Foo.COUNT`) | resolveAndEval 路径,Tag.CLASS receiver | ✅ |
| A5 静态方法 (`Math.max(a, b)`) | 同上,InvokeMethod | ✅ |
| A6 数组 (`arr[0]`, `arr.length`) | INDEX 节点 + ArrayReference.GetValues | ✅ |
| A7 三元 (`a > 0 ? x : y`) | parseTernary + resolveAndEval 短路 | ✅ |
| A8 单测 | EvalEngineTest + Evaluate + SetValues + ForceEarlyReturn + Helpers (6 个文件) | ✅ |

### 9.6 Phase B (JDWP 协议) — ✅ 全部完成 (历史已存在)

| 子任务 | 实现位置 | 状态 |
|------|------|------|
| B1 ClassPrepare | `SourceLocator.enableClassPrepare()` + `pending` 列表 | ✅ |
| B2 Exception | `SourceLocator.enableExceptionEvents()` (SuspendPolicy.ALL) | ✅ |
| B3 ArrayReference GetValues/SetValues | `EvalEngine.applyArith` + SetValues helpers | ✅ |
| B4 StringReference.Value | `EvalEngine` / `DebuggerStringValueTest` 覆盖 | ✅ |
| B5 ForceEarlyReturn | `EvalEngineForceEarlyReturnTest` 覆盖 | ✅ |
| B6 自动重连 | `JdwpClient` reconnect (initialDelayMs/maxDelayMs/executor) | ✅ |

### 9.7 单测覆盖率

| 模块 | 测试数 (估计) |
|------|--------------|
| ide-debugger | 27 个文件, 150+ 测例 (EvalEngine/JdwpClient/Debugger 等) |
| core/app 调试器 | 8 个文件, 80+ 测例 (EvalErrorMapper/LogStore/WatchStore/3 Adapter/CommandValidator) |
| **合计** | **35 个测试文件, 230+ 测例** |

### 9.8 仍待办 (Phase C / D / E / F / G / H)

参见 §3 推荐开发顺序:
- **Phase C** 目标应用端 (`utilities/logwire` + `ide-log-plugin` + `JdwpServer` 7 类 + `LogCaptureService` + `LogSocketServer`) — ⏳ 全部未做
- **Phase D** IDE 集成端到端 (Build + install + launch + shizuku + run-as + AutoAttach + 远程 adb + 应用就绪) — ⏳ 大部分未做
- **Phase E** UI 打磨 (E1-E5 已在 plan, 见 §10)
- **Phase F** 测试 / CI / 文档 (CI workflow / architecture diagram / JDWP 协议说明 / 贡献指南) — ⏳
- **Phase G** SourceLocator 升级 (JavaParser/ASM/inner class/Kotlin/lambda) — ⏳
- **Phase H** 性能 / 稳定性 (批量 GetValues / ANR 防护 / 长空闲断连) — ⏳

---

## 10. Phase E UI 打磨 — ✅ 全部完成

| 子任务 | 范围 | 状态 |
|------|------|------|
| E1 Adapter 完整 | CallStackAdapter ↑/↓ 键盘切帧 (PR-D7 已由 trae 完成, onListKey 全 6 个键) | ✅ |
| E2 条件断点对话框 | BreakpointConditionDialog 加 MaterialSwitch 启用 toggle + isBpEnabled + setEnabled | ✅ |
| E3 暗色主题 | values/colors_debugger.xml + values-night/colors_debugger.xml (历史已存在) | ✅ |
| E4 国际化 | values-ja/values-ko/values-pt-rBR 各 30+ 关键字符串 (本轮新增) | ✅ |
| E5 无障碍 | fragment_variables/watches/callstack/logpoint 关键节点加 contentDescription + 6 条新 string | ✅ |

---

*本节反映 PR 419 上 PR-D6 → PR-D9 全部工作的实际落地状态。*

