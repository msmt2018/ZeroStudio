# ZeroStudio Debugger 真实开发状况审计报告

> **生成日期**: 2026-06-23
> **PR-D4 增量更新**: 2026-06-24
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

## 7. PR-D4 增量修复(2026-06-24)

> 对照"完整断点调试器"的架构 / 业务 / 行为 / 操作 / 交互规范,系统性修复 15 项问题。

### 7.1 业务/操作端修复

| 修复点 | 改动文件 | 关键改动 |
|--------|---------|---------|
| 缺 LogpointFragment 注册 | `EditorBottomSheetTabAdapter.java` + `core/resources/strings.xml` | 把 `LogpointFragment` 加进 TabAdapter 末尾;新增 `editor_tab_watches` + `editor_tab_logpoint` |
| `BreakpointTypePicker` 弹窗位置错乱 | `BreakpointTypePicker.java` | 新增 `showAtPosition(activity, parent, anchorX, anchorY, file, line)` 重载,放 1x1 ghost View 实现任意坐标 anchor |
| `BreakpointSidebar` 错误的长按检测 | `BreakpointSidebar.java` | 用 `GestureDetector.SimpleOnGestureListener` 替代 `event.getEventTime() - event.getDownTime() > 500L` 的错误检测 |
| `WatchesAdapter` items/values 错位 | `WatchesAdapter.java` | 合并 `List<String> items + List<String> values` 为单一 `WatchEntry(expression, value)` 数据源;`setValues(String[])` 原子更新 |
| 缺 set-value 入口 | `VariablesAdapter.java` + `VariablesFragment.java` | `Listener` 加 `onItemClick(VariableInfo)` 默认方法;`VariablesFragment` 弹 AlertDialog + 调 `EvalEngine.setLocal` 真正实现 set-value |
| 缺 watch 表达式编辑入口 | `WatchesFragment.java` + `WatchStore.java` | 接 `onItemClick` → `showEditDialog()`;`WatchStore.set(int, String)` 替换并去重 |
| `DebuggerController.stop()` 未实现 | `DebuggerController.java` | 真正实现:`resume → disconnect → 异步 am force-stop → a11y 公告 → flash`;加 `targetPackage` 字段 + `bg` ExecutorService |
| `DebugSessionLauncher` 无法取消 | `DebugSessionLauncher.java` | 加 `cancelled AtomicBoolean` + `stop()` 5 个 step 边界检查 + `fireCancelled` 回调 |
| 缺异常跳转菜单项 | `DebuggerActionMenuProvider.java` + `ids_debugger.xml` + `strings_debugger.xml` | 加 `dbg_action_goto_exception` → `ctl.gotoException()` |

### 7.2 交互端修复

| 修复点 | 改动文件 | 关键改动 |
|--------|---------|---------|
| 缺 a11y 公告 | `DebuggerAccessibility.java` + `DebuggerController.java` | 加 `Activity` 重载;`onSuspend/Resumed/Connected/Disconnected` 全部挂上 `announceForAccessibility` 公告 |
| `BreakpointManager` 落盘阻塞 | `BreakpointManager.java` | `schedulePersist` 300ms 防抖 + `ScheduledExecutorService` + 单线程 `persistExecutor` 异步落盘 |
| `RemoteDeviceScanner` 慢 | `RemoteDeviceScanner.java` | 16 路 `FixedThreadPool` + `CountDownLatch` 并发;每探针 250ms 超时(原 1000ms);`ConcurrentLinkedQueue` 收集 |
| `BreakpointGutterManager` 事件订阅泄漏 | `BreakpointGutterManager.java` | 加 `List<SubscriptionReceipt<?>> subscriptions`;`unbind()` 调 `unsubscribeEditorEvents` |
| `closeFile` 未清理 BreakpointGutterManager | `EditorHandlerActivity.kt` | 关闭前 `getEditorAtIndex(index)` + `BreakpointGutterManager.detach` |
| `onSuspend` 不切底部抽屉 | `DebuggerController.java` + `EditorBottomSheet.kt` + `BaseEditorActivity.kt` | 加 `selectTabByFragmentClass` + `openDebuggerTab`;`onSuspend` 自动切到 `VariablesFragment` tab |

### 7.3 业务端补全

| 补全项 | 改动文件 | 关键改动 |
|--------|---------|---------|
| Watch 表达式编辑 | `WatchStore.java` | `set(int index, String expr)` 去重 + 替换 + save |
| 字符串/ID 资源补全 | `strings_debugger.xml` + `values-en/strings_debugger.xml` | 加 `debugger_msg_logpoint_added` / `debugger_set_value_*` / `debugger_a11y_exception_prefix` / `debugger_action_goto_exception` |
| 通用编辑器 tab 字符串 | `core/resources/strings.xml` | `editor_tab_watches` / `editor_tab_logpoint` |

### 7.4 PR-D4 后的剩余扩展点(留作 PR-D5+)

- `View → 源码位置` 反向映射(R3)
- `Find References` / `Peek Definition`(R4)
- 多语言 AST 解析(R6: Kotlin / C / C++)
- NDK lldb/gdbserver 集成(R7)
- `BreakpointTypePicker.showAtPosition` 在 EditorHandlerActivity 接入真实行的屏幕坐标

---

*本报告由代码审计生成,不夸大已实现功能,明确标记未实现项。*
