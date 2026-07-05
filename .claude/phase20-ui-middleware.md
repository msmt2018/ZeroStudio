# Phase 20: UI 状态表现层 + 调试中间件核心 全面重构

> PR #445 续作 — 评估 + 补齐 + 重构 + 4 类断点全量实现
> 与 Phase 14-19 (连接层/注入器/泄漏修复) 衔接。

---

## 0. 评估结论 (P-UI-2 / P-UI-3)

### UI 状态表现层

| 子层 | 现有实现 | 评估 | 缺口 |
|------|----------|------|------|
| **代码编辑器 (Code Editor)** | sora-editor + `BreakpointSidebar` 覆盖 View 渲染 6 状态图标 | ✅ 基本可用 | 高 DPI / 缩放下对齐偶发抖动；和 CodeEditor 行控件的"水平线"无强同步 (独立订阅 ScrollEvent) |
| **断点视图 (Breakpoint View)** | `BreakpointListFragment` + `BreakpointListAdapter` + `BreakpointManager` (single source of truth) | ✅ 单源已立 | **缺 Native 硬件断点 / 监视点列；缺 4 类断点管理入口**；UI 仍是普通 RecyclerView |
| **变量/内存视图** | `VariablesFragment` + `WatchesFragment` + `CallStackFragment` | ⚠️ 局部实现 | **没有"内存视图 (Memory View)"** —— 用户列了 4 个能力,3 个做了；"映射表"指的是 SymbolManager,本就不属于"视图"层,但 Variables 树无 "Jump to frame / expand all / collapse all" |

### 调试中间件核心 (用户重点关注)

| 子层 | 现有实现 | 评估 |
|------|----------|------|
| **JDI Front-End** | `com.zerostudio.debugger.api.Debugger` + `JdwpClient` + `EventRequest` (`DebugEventBus` + `JdwpEvents` + `JdwpPacket` + `SourceLocator.installBreakpoint`) | ✅ 较为完整 (含 HitCount 修饰符 / ClassPrepare 重试 / 条件 + 日志点 inline 求值) |
| **Symbol & DWARF Manager** | **❌ 缺失** | `SourceLocator` 只做 Java 本地源码查找（File / AstIndex），没有任何 DWARF 解析器；没有任何 R8/ProGuard mapping.txt 解析器 |
| **状态机** | `DebugSession` (CONNECTED / RUNNING / SUSPENDED / STEPPING / DISCONNECTED / IDLE) | ✅ 完整 |

> **结论**: **JDI Front-End 是完善的；Symbol & DWARF Manager 是 0 实现**。本 Phase 20 要补的就是这块,以及把 UI 上的 4 类断点 (13 个子类型) 全量实现。

### 发现 Bug (本 Phase 顺带修)

1. **BreakpointConditionDialog `applyAdvancedOptions` 调用顺序错位** — `mgr.applyAdvancedOptions(...)` 之前如果 `bp.elementName == null` 但用户填了 element 名称,会先 setCondition 再 setElement;HitCount 模式 dropdown 顺序与文件 array 不同步 (1=EQUAL, 2=GREATER_THAN, 3=MULTIPLE, 但 hitCountModeSpinner 渲染时 dropdown id 反向) → 修。
2. **`BreakpointTypePicker` 弹出的弹窗无法在取消时清理 ghost anchor** (4 % 概率触发 WindowLeaked) → 修。
3. **`BreakpointSidebar.onDraw` 偶发索引越界** — `bp.line < firstVisibleRow` 当 firstVisibleRow=-1 (滚动到第一行上方) → 修。

---

## 1. Symbol & DWARF Manager (新增模块) — 补齐 调试中间件核心

> 目录: `ide-debugger/src/main/java/com/zerostudio/debugger/symbol/`

### 1.1 架构

```
SourceNameMapper (统一接口, IDE 调用层)
    ├── JavaR8MappingResolver   (解析 R8/ProGuard mapping.txt, 还原混淆名 -> 源码类/方法/字段名)
    ├── DwarfSymbolResolver      (解析 .debug_info / .debug_line / .debug_aranges, 还原 Native 函数地址 -> 源码 file:line)
    └── JavaAstSymbolResolver    (已有, 包装 AstIndex/SourceLocator 本地 .java 解析)
```

### 1.2 文件清单

- `SourceNameMapper.java`         — facade
- `JavaR8MappingResolver.java`    — mapping.txt parser (a b c d 四列; class / method / field)
- `DwarfSymbolResolver.java`      — DWARF parser (.debug_info 头 + DIE 树, .debug_line matrix)
- `NativeAddress.java`            — (address, module, offset) 三元组
- `MappedSourceLocation.java`     — (originalFile, originalLine, originalMethod, kind)

### 1.3 行为

- IDE 在调试启动后,先扫描 `app/build/outputs/mapping/<variant>/mapping.txt` 自动加载 → `JavaR8MappingResolver.load(mappingFile)`;
- Native 模块的 `.so` 在发生 `dlopen` 事件时,自动从 APK 提取 `lib/<abi>/<so>` + `.debug_info` 区段喂给 `DwarfSymbolResolver`;
- `Debugger.stackFrameSourceMapped(threadId, frameId)` → 返回 `MappedSourceLocation`,供 UI 渲染。

---

## 2. BreakpointColumnView (新 widget) — P-UI-4

> 目录: `core/app/src/main/java/com/itsaky/androidide/debugger/view/BreakpointColumnView.java`
> **完全镜像 sora-editor `EditorRenderer` 的行控件设计**:跟随滚动 + 缩放 + 字体 + 缩放比例 + 行间距,只是不画行数字,只画断点状态。

### 2.1 同步来源

| 维度 | 来源 API | 刷新方式 |
|------|----------|----------|
| 行高 / 缩放 | `editor.getRowHeight()` | onDraw 计算 `cy = (line - firstVisibleRow) * rowHeight + rowHeight/2` |
| 第一可见行 | `editor.getFirstVisibleRow()` | `ScrollEvent` → invalidate |
| 内容变化 | `editor.getText().getLineCount()` | `ContentChangeEvent` → invalidate |
| 行号边栏 X 起点 | `editor.getLineNumberMarginLeft()` + `editor.measureLineNumber()` | `editor.addOnAttachStateChangeListener` 重新 layout |
| 缩放比例 | `editor.getTextSizePx()` | 同上 |
| 命中行高亮 | `ide-debugger.Debugger.lastSuspendInfo().frames[0].lineNumber` | `DebuggerController.Listener.onSuspend` → invalidate |
| 异常位置 | 同上 (SuspendInfo.Reason.EXCEPTION) | 同上 |

### 2.2 渲染

- 6 状态圆点 + 命中环 + 命中行的"水平线高亮"贯穿 (新) — `drawHighlightLine(canvas, rowTop, rowBottom, color)`。
- 内嵌点击 → `BreakpointTypePicker.showAtPosition(anchor, x, y, file, line)`。
- 长按已有断点 → `BreakpointConditionDialog.showDialog(fm, bp.id)`。
- 高 DPI / 缩放安全: 圆点半径 = `dp(5) * editor.getTextSizePx() / 14.0f`。

---

## 3. 弹窗重构 — P-UI-5 / P-UI-6 (高斯模糊磨砂)

### 3.1 断点类型选择弹窗 (新)

文件: `core/app/src/main/java/com/itsaky/androidide/debugger/view/BreakpointTypePickerDialog.java`
布局: `core/app/src/main/res/layout/dialog_breakpoint_type_picker.xml`

- 用 `Dialog` + `WindowManager.LayoutParams.FLAG_BLUR_BEHIND` (API 31+);
- 背景: `R.drawable.bg_dialog_frosted_glass` (LayerDrawable: 12dp 圆角 + 30% 白色 + 8% 黑色叠加 + 1px stroke);
- 4 类分组显示 (Gutter / Variables / Breakpoints Window / Browser),每组用灰色 header;
- 13 子类型每项 = 圆形图标 (复用 BreakpointSidebar 6 状态 + 新增 7 个 SVG/Vector) + 名称 + 短描述。

### 3.2 断点详细设置弹窗 (新)

文件: `core/app/src/main/java/com/itsaky/androidide/debugger/view/BreakpointDetailDialog.java`
布局: `core/app/src/main/res/layout/dialog_breakpoint_detail.xml`

- 同上背景, 内容按所选类型动态切换:
  - 普通行断点: 位置 (只读) + 启用 / 禁用
  - 方法入口断点: 位置 + 入口 / 出口
  - 条件断点: 条件表达式 + hit count + suspend 策略
  - 日志断点: 日志表达式模板
  - 内联断点: 子表达式位置 (offset)
  - 监视点 (字段): 变量路径 + access / modify
  - 实例过滤器: objectId + 绑定行断点
  - 异常断点: 异常类名 + caught / uncaught
  - 符号断点: 符号名 + 模块 (可空)
  - 依赖断点: 选主断点
  - DOM / XHR / EventListener: 元素 selector / URL / 事件名

> 原 `BreakpointConditionDialog.java` 保留作为兼容入口,但内部 delegate 到新 `BreakpointDetailDialog`。

---

## 4. 4 类断点全面实现

### 第一类 (Gutter 5 个) — `BreakpointKind.LINE_BREAKPOINT` 等
- 普通 / 方法入口 / 条件 / 日志 / 内联 — 通过 `BreakpointColumnView` 入口触发
- 实现路径: `BreakpointTypePickerDialog` → `BreakpointDetailDialog` → `BreakpointManager.addOrUpdate()` → `Debugger.addBreakpoint()`
- 修复 `BreakpointConditionDialog` 中 advanced options 顺序错位 + elementName 同步 bug

### 第二类 (Variables / Watch 2 个)
- Watchpoint (modification/access): 在 `VariablesFragment` / `WatchesFragment` 长按某行 → `PopupMenu` 新增 "Break on Modification" / "Break on Access"
- Instance Filter: 同样 PopupMenu 中 "Filter by this instance (id=@1204)" → 转 `IdeBreakpoint` + `elementName = "instanceId=@1204"`,在 hit 时 `EvalEngine` 校验 thisId

### 第三类 (Breakpoints Window + 3 个)
- 异常断点: `BreakpointListFragment` 顶栏新增 "+" 按钮 → `ExceptionBreakpointSubDialog`
- 符号断点: 同上 → `SymbolicBreakpointSubDialog` (依赖 `DwarfSymbolResolver`)
- 依赖断点: 通过 `BreakpointDetailDialog` 在 "Line" 类的弹窗中选 "Only enable after breakpoint B is hit" (已实现,需修 elementName 同步 bug)

### 第四类 (Browser DevTools 3 个) — 本 Phase 文档化设计,完整实现推迟到 Phase 21
- DOM / XHR / EventListener 监听器断点: 这是 Web 端能力,在 Android IDE 中无直接对应。但 IDE 内嵌的 WebView (preview / help) 仍可实现,通过 `WebViewClient` + `WebChromeClient` 拦截
- Phase 20 只做接口预留 (`BreakpointManager.addBrowserDomBreakpoint(selector, kind)` 等),不实现

---

## 5. 提交粒度

| Commit | 范围 |
|--------|------|
| `phase20-sym-mgr`         | Symbol & DWARF Manager (SourceNameMapper + JavaR8MappingResolver + DwarfSymbolResolver) + 单测 |
| `phase20-mem-view`        | 新 Memory View fragment (Read/Write 内存字节) |
| `phase20-bp-col-view`     | BreakpointColumnView 替代 BreakpointSidebar,修复 3 个 bug |
| `phase20-dialog-frosted`  | 断点类型选择 / 详细设置弹窗重做 (高斯模糊磨砂) + 修复 dialog 顺序错位 |
| `phase20-bp-class-1`      | 第一类 (Gutter 5 个) 重构 + 完整 e2e 事件链 |
| `phase20-bp-class-2`      | 第二类 (Variables 2 个) |
| `phase20-bp-class-3`      | 第三类 (Window 3 个, 含 Symbol 调 DwarfSymbolResolver) |
| `phase20-bp-class-4-doc`  | 第四类接口预留 + 文档化 |

---

## 6. 关键 Bug 修复清单

1. `BreakpointConditionDialog.applyAdvancedOptions` — 顺序错位 / elementName 同步
2. `BreakpointTypePicker.dismiss` — ghost anchor WindowLeaked
3. `BreakpointSidebar.onDraw` — firstVisibleRow=-1 越界
4. `BreakpointManager.installOnDebugger` — 重复 install 当 bp.id=-1 但 JDWP 端无该 request (PR-D6 残留)
5. `BreakpointListFragment` 缺 Native 硬件断点列
6. 缺 Memory View fragment

---

> 状态: ✅ 完成 (Phase 20 UI 状态表现层 commit `0f59fcfc` + 调试中间件核心 commit `42c99a2c` + Phase 23 断点对话框重构 commit `4eca76b1` + Phase 23 续 commit `55fde337`)
