# Jetpack Compose 迁移规范

## 1. 目标

将 ZeroStudio 新 UI 与逐步迁移的历史 UI 建立在 Jetpack Compose Material 3 上。迁移目标是声明式、状态驱动、可测试的 UI 架构，减少传统 View/XML/ViewBinding/Fragment UI 依赖。

“零传统 View”描述 UI 实现目标；“零生命周期”不表示 Android Activity、进程或平台生命周期不存在。正确目标是 Composable 不依赖手工 View 生命周期管理，状态和任务拥有明确且可验证的所有权。

## 2. 禁止的迁移顺序

不得采用以下顺序：

1. 先将旧控件外观替换为 Compose。
2. 保留或临时包装旧 API、旧 View 状态和旧事件系统。
3. 最后才尝试重写底层框架。

这会导致 Compose 页面仍依赖传统 View 生命周期、旧状态容器和不可测试的回调链。

## 3. 正确的自底向上顺序

### Stage A：底层 API 与领域契约

- 梳理现有 View/Fragment/Adapter 暴露的行为，而不是直接照搬类层级。
- 抽取纯 Kotlin 模型、接口、状态和事件。
- 明确数据源、线程、并发、取消、错误和资源释放。
- 将 UI 状态与 Android View 类型解耦。

### Stage B：状态与应用层

- 建立 StateFlow/Flow 或适合该模块的状态流。
- 定义用户意图和单向状态转换。
- 把数据库、文件、网络、Native 操作放到 use case/repository/adapter。
- 使核心逻辑可以在不创建 Activity/View 的情况下测试。

### Stage C：基础 Compose 组件

- 建立主题、颜色、排版、形状、间距和尺寸系统。
- 实现基础输入、按钮、列表项、弹窗、工具栏和状态组件。
- 组件只接收状态和回调，不直接访问全局业务对象。

### Stage D：复合组件和交互容器

- 迁移编辑器工具栏、面板、导航、弹层、拖拽、手势和多窗口布局。
- 复杂手势与布局逻辑独立为可测试状态/算法。
- 使用 Compose 原生布局、Lazy 容器和 Modifier，不以 AndroidView 作为默认桥接。

### Stage E：页面与导航

- 页面只负责组合组件、绑定状态和发送事件。
- 导航参数使用稳定 ID/轻量值。
- 页面离开时任务是否取消由任务所有权决定，不依赖随意的全局作用域。
- 适配手机、平板、折叠屏、横竖屏和窗口尺寸变化。

### Stage F：清理与验证

- 删除已无调用者的旧 View、XML、Binding、Adapter 和旧 UI 状态桥接。
- 搜索并审查 View、ViewGroup、RecyclerView、Fragment、LayoutInflater、XML layout、ViewBinding、DataBinding、AndroidView 等引用。
- 区分纯平台 API（如 Window、InputMethod、Accessibility、Text layout）与实际传统 UI 控件，不以关键词命中直接删除。
- 运行模块构建、测试、截图/语义测试和真机交互验证。

## 4. 迁移约束

- 不允许用 AndroidView/ComposeView 包装旧页面来宣称迁移完成。
- 如短期必须互操作，必须记录具体文件、原因、隔离边界和删除计划。
- 不得为了 Compose 迁移破坏现有公开 API、持久化格式或用户数据兼容性。
- 页面状态必须能在配置变化或进程重建的适当场景下恢复；状态持久化策略按数据性质选择。
- 不要将所有状态都提升到全局，也不要把所有状态都塞进 ViewModel。
- 复杂控件先定义行为模型与 API，再实现 Compose 表现层。

## 5. “无 View”验收标准

迁移模块需满足：

1. 不存在用于呈现页面的传统自定义 View/ViewGroup。
2. 不依赖 XML layout、LayoutInflater、ViewBinding 或 DataBinding 构建该模块 UI。
3. 不以 RecyclerView/传统 Adapter 承担 Compose 页面列表。
4. 不通过 AndroidView/ComposeView 隐藏旧页面。
5. UI 状态和交互可由 Compose 状态模型表达。
6. 通过源码扫描和人工审查确认残留引用性质。
7. 构建、测试及目标设备交互通过。

平台服务可能仍需要 Context、Window、InputMethodManager 等 Android API；这不等于传统 View UI 残留。必须按用途审查，而不是追求简单的零关键词命中。

## 6. 进度记录

每个迁移模块记录：
- API/模型迁移：未开始 / 进行中 / 完成
- 状态层迁移：未开始 / 进行中 / 完成
- 组件迁移：未开始 / 进行中 / 完成
- 页面迁移：未开始 / 进行中 / 完成
- 旧 UI 清理：未开始 / 进行中 / 完成
- 构建/测试/设备验证：分别记录
- 遗留 View 及保留原因：文件路径与删除计划

不得用“页面已经能显示”代替完整迁移验收。
