# core/actions 源码整体评审报告

> 评审仓库：`msmt2018/ZeroStudio`  
> 评审分支：`main`  
> 评审基准提交：`3deceb61f6ce6f8a113d76344630a3a68f332854`  
> 评审范围：`core/actions` 模块全部源码、Gradle/Manifest/规则文件，以及 `core/app`、编辑器和 LSP 中的关键调用点。  
> 评审方式：GitHub 源码静态审查。**本报告不代表已执行构建、单元测试、设备运行或性能测试。**

## 1. 总体结论

**总体工程评价：5.1/10（静态审查判断，不是测试分数）。**

`core/actions` 已经具备 Action 定义、注册、查找、菜单组织和协程执行等基础能力，抽象方向有价值；但当前实现仍明显依赖传统 Android UI 体系，且动作定义、运行状态、执行上下文和菜单渲染之间的边界不够清晰。

它目前更接近一个传统 Android IDE 的 Action/Menu 工具模块，而不是可独立复用、生命周期约束明确、UI 框架无关的动作执行内核。

主要结论：

1. Action 定义对象持有可变展示状态，存在跨编辑器、跨菜单实例状态互相污染的风险。
2. Registry 的并发安全不完整，单例初始化和监听器集合存在竞态风险。
3. Action 执行模型缺少明确的取消、异常、结果和生命周期策略。
4. 注册替换、注销和清理的资源所有权规则不完整。
5. Action Tree 的准备、可见性、启用状态与排序计算不够一致。
6. 核心 API 直接依赖 Android Menu、MenuItem、View、Drawable 和 Fragment 导航，不利于 Compose 全面迁移。
7. 模块自身缺少可见的测试源码，底层行为缺少自动化回归保障。

## 2. 架构现状

当前代码可概括为以下职责层：

- **Action 定义层**：`ActionItem`、`EditorActionItem`、`SidebarActionItem`、`ActionMenu`。
- **数据与参数层**：`ActionData`、`FillMenuParams`、`OnActionClickListener`。
- **注册与执行层**：`ActionsRegistry`、`DefaultActionsRegistry`。
- **Android UI 适配层**：`Menu`、`MenuItem`、`Drawable`、`View`、Fragment 导航。

优点：

- Action 声明与 Activity 中的具体点击代码有一定分离。
- `ActionData` 为操作提供统一上下文入口。
- `ActionMenu` 支持分层子菜单。
- Registry 提供统一注册、注销、查询和菜单填充入口。
- `execAction` 使用 suspend 函数，具备协程执行基础。
- `destroy()` 为资源释放提供了约定入口。

不足：虽然源码按类和文件划分了职责，但执行与 UI 适配仍集中在 Registry，且 Action 实例同时承载定义和可变运行状态，边界没有真正隔离。

## 3. 问题清单（按优先级）

### P1 — Registry 监听器集合不是线程安全的

文件：`core/actions/src/main/java/com/itsaky/androidide/actions/internal/DefaultActionsRegistry.kt`

当前：

```kotlin
private val actions =
    ConcurrentHashMap<String, ConcurrentHashMap<String, ActionItem>>()
private val listeners = HashSet<ActionExecListener>()
```

Action map 使用并发容器，但 listeners 使用普通 `HashSet`。注册、注销和执行通知可能发生在不同线程。通知时直接遍历集合，存在并发修改、迭代异常和竞态风险。

建议：
- 使用线程安全集合，或使用锁保护注册/注销。
- 通知时遍历不可变快照。
- 对单个 listener 的异常进行隔离，避免一个监听器阻断其他监听器的通知。
- 明确 listener 的注册范围和注销责任。

### P1 — Registry 单例初始化不安全

文件：`core/actions/src/main/java/com/itsaky/androidide/actions/ActionsRegistry.kt`

当前 `getInstance()` 使用普通 nullable 字段和非同步判空初始化。多线程首次调用可能重复创建实例。

建议采用线程安全 lazy 或同步初始化，并明确测试环境与 Android ServiceLoader 环境的实例策略。

### P1 — Action 定义与运行状态混合

文件：`ActionItem.kt`

Action 同时持有 `label`、`visible`、`enabled`、`icon`、`requiresUIThread`、`location` 等可变属性；`prepare(data)` 会修改可见和启用状态。

这使同一个 Action 实例无法安全地表达多个上下文下的状态。例如两个编辑器同时准备同一个 Action 时，后一次 `prepare` 可能覆盖前一个编辑器的状态。

`CodeActionsMenu` 等全局对象及其子操作尤其需要注意此问题。

建议将模型拆分为：
- 不可变 Action 定义：稳定 ID、标题、图标描述、位置、能力声明。
- 每次上下文计算出的 Action 状态：visible、enabled、排序和提示。
- 执行请求：上下文快照与参数。
- 渲染模型：供 Compose 或 Legacy UI 使用。

不要把一次菜单展示的状态写回全局 Action 定义对象。

### P1 — Action 替换时旧对象没有明确释放

文件：`DefaultActionsRegistry.kt`

当前 `registerAction` 对相同 location 和 ID 直接赋值覆盖旧对象，没有对旧对象调用 `destroy()`，也没有定义替换策略。旧对象若持有监听器、资源或 UI 引用，可能无法释放。

建议明确重复注册行为：拒绝、显式替换或更新。若替换，必须定义旧实例释放时机，并避免在仍有执行任务使用它时提前销毁。

### P1 — 按 ID 注销缺少 Location 约束

`unregisterAction(id)` 遍历所有 location，遇到第一个同 ID 操作就删除。由于不同 location 可以拥有相同 ID，该 API 的结果依赖遍历顺序。

建议以 `(location, actionId)` 作为明确身份，或提供明确的全局唯一 ID 规则。避免含糊的跨 location 删除。

### P1 — 清理操作没有递归覆盖 ActionMenu 子项

`clearActions(location)` 只对 Registry 中直接注册的 Action 调用 `destroy()`。若 `ActionMenu.children` 中的子 Action 也持有资源，当前实现没有显示递归释放协议。

建议定义 Action Tree 的所有权：子项由父菜单拥有还是独立注册；销毁必须按所有权规则递归或分别处理，避免重复销毁。

### P1 — Registry 抽象与实际执行入口不一致

`ActionsRegistry` 抽象了注册、注销、查询和填充菜单，但实际执行入口位于 `DefaultActionsRegistry.executeAction()`。部分调用方通过强制转换到具体实现来执行。

这使自定义 Registry、测试替身和替换执行引擎受到限制。

建议将执行能力抽象为正式的 `ActionExecutor` 或 Registry 的抽象执行 API，调用方不应强依赖 `DefaultActionsRegistry`。

### P1 — 协程 Scope 缺少完整的故障和取消策略

文件：`DefaultActionsRegistry.kt`

当前 Scope 基于 `Dispatchers.Default`，没有明确展示 `SupervisorJob`、执行任务所有者或关闭机制。

风险与不足：
- 子任务未处理异常可能影响同一父 Scope 的其他任务。
- CPU 密集与阻塞 IO 操作不应仅由一个 UI 线程 Boolean 决定调度。
- 编辑器关闭后，任务仍可能持有旧的 Editor、Activity 或 Context。
- 执行异常时不会进入正常的 `postExec` 和通知路径。
- 没有统一的任务取消、超时和关闭语义。

建议：
- 明确使用 Supervisor 语义并定义异常处理。
- 区分 CPU、IO、Main 等调度策略。
- 任务绑定明确的 owner/cancellation token，而不是让全局 Scope 无限持有 UI 上下文。
- 定义执行器关闭和任务取消流程。
- 将成功、失败、取消作为显式结果。

### P1 — 执行结果契约与实现不一致

文件：`ActionItem.kt`、`DefaultActionsRegistry.kt`

接口文档把返回值描述为 Boolean 成功/失败，并说明 `postExec` 只在成功时调用；但签名返回 `Any`，实际执行代码在 `execAction` 正常返回后无条件调用 `postExec`，没有检查结果是否为 true。

因此返回 false 也会进入 postExec。异常则直接跳过 postExec 和通知。

建议定义显式结果类型，例如：

```kotlin
sealed interface ActionResult {
    data object Success : ActionResult
    data class Failure(val cause: Throwable) : ActionResult
    data object Cancelled : ActionResult
}
```

如果需要返回业务数据，应通过类型化 payload 表达，而不是依赖无约束的 Any。成功、失败、取消都应有一致的事件语义。

### P1 — Menu item ID 由 String.hashCode 生成，存在冲突风险

文件：`ActionItem.kt`

当前 `itemId = id.hashCode()`。不同字符串可能产生相同 HashCode；而菜单查询又通过 Int itemId 反查 Action，冲突时可能找到错误操作。

建议字符串 ID 始终作为业务身份；整数 ID 由注册表分配并检测冲突，或在适配层维护可靠映射。

### P2 — ActionMenu 的 prepare 和状态计算存在重复与不一致

文件：`ActionMenu.kt`、`DefaultActionsRegistry.kt`

`ActionMenu.prepare()` 会调用子项的 `prepare()` 查找第一个可见子项；随后填充子菜单时又会再次调用子项的 `prepare()`。这可能造成重复计算或副作用重复。

子菜单启用状态计算只检查父菜单和子项的 enabled，没有同时检查子项 visible；不可见但 enabled 的子项可能影响父菜单启用状态。顶层操作有排序，而子项遍历没有统一排序逻辑。

建议先构建一次 Action Tree 状态快照，再由不同 Renderer 消费；准备过程应尽可能纯函数化，避免渲染过程中反复改变 Action 实例。

### P2 — Drawable 在菜单填充期间被直接修改

文件：`DefaultActionsRegistry.kt`

当前直接修改 Action 持有的 Drawable 的 colorFilter 和 alpha。若 Drawable 被多个菜单或上下文共享，状态可能互相污染。

建议图标定义与渲染状态分离，渲染时创建或取得独立 Drawable 实例；Compose 核心模型不应直接持有 Drawable。

### P2 — ActionData 是宽松的任意对象容器

文件：`ActionData.java`

内部使用 `Map<Class<?>, Object>` 保存任意对象。它缺少明确的不可变快照、生命周期所有权和线程访问约束，可能长期持有 Activity、Editor、CodeEditor、File 或语言服务对象。

建议逐步迁移为有类型的 ActionContext/ActionRequest，明确哪些数据是稳定值、哪些是短期引用，以及执行期间数据是否允许变化。异步任务不得默认假设 UI 对象仍然有效。

### P2 — ActionStyle 声明与实际渲染链路需要核验

文件：`ActionStyle.kt` 及编辑器 Action 调用方。

样式模型定义了 `textSizeSp`、`paddingHorizontalDp`、`minWidthDp`、`typefaceStyle`。部分编辑器操作设置了样式值，但当前菜单渲染链路需要进一步确认是否真正读取并应用这些字段。

建议：
- 为每个样式字段建立明确的 Renderer 消费位置和测试。
- Compose 样式使用 Compose 无关的语义模型，具体 Modifier/字体实现留在 Compose Renderer。
- 未被消费的字段应删除或明确标记为未实现，避免形成虚假的可用 API。

### P2 — actionUtils 的 Context 与 UI 类型依赖需要收敛

文件：`actionUtils.kt`

工具函数提供 Context、File、Editor 的强制获取，以及 Material Dialog Builder 等 Android UI 辅助能力。缺少上下文时通过异常失败是可理解的，但调用方需要明确校验契约。

建议将纯数据/路径工具与 Android UI 工具拆开；Dialog Builder 等 UI 辅助方法迁移到对应 UI Adapter，核心模块避免暴露不必要的 View/UI 实现类型。

## 4. 文件级评价

| 文件 | 评价 | 主要关注点 |
|---|---|---|
| `ActionData.java` | 需要重构 | 任意对象容器、线程与生命周期约束不足 |
| `ActionItem.kt` | 需要重构 | 可变状态、Drawable/View 耦合、执行结果契约 |
| `ActionMenu.kt` | 需要重构 | 子项重复 prepare、可见性和启用状态计算 |
| `ActionStyle.kt` | 需要完善 | 字段是否被 Renderer 消费需要验证 |
| `ActionsRegistry.kt` | 需要重构 | 单例初始化、执行抽象边界 |
| `EditorActionItem.kt` | 基本合理 | 需随基础 Action 契约迁移 |
| `FillMenuParams.kt` | 需要重构 | 直接依赖 Android Menu，执行回调耦合具体实现 |
| `OnActionClickListener.kt` | 需要重构 | 回调携带 MenuItem，绑定传统菜单 |
| `SidebarActionItem.kt` | 需要重构 | Fragment 和 Fragment Navigator 依赖 |
| `actionUtils.kt` | 需要整理 | Context/UI 辅助 API 与核心工具混合 |
| `DefaultActionsRegistry.kt` | 优先重构 | 并发、执行、资源释放、菜单构建耦合 |
| `CodeActionsMenu.kt` | 需要重构 | 全局可变菜单树及子项所有权 |

## 5. Gradle 与模块边界

文件：`core/actions/build.gradle.kts`

当前依赖包含 Navigation Fragment/UI、Material、编辑器以及项目公共模块等。

需要核验的边界：

1. 公共 API 暴露的类型与 Gradle `api`/`implementation` 依赖范围应一致。若公开签名使用某依赖类型，应确认消费者能否正确编译；若不希望该类型成为公共契约，应收窄可见性或拆分 API。
2. `nav.fragment` 与 SidebarActionItem 的 Fragment API 有直接关系；`nav.ui` 是否必须作为公共依赖，应根据实际公开签名和消费者使用情况进一步确认。
3. 模块目录中未发现对应的 `test`/`androidTest` 源码目录；这意味着本次审查没有看到模块自身的自动化测试覆盖，建议补充测试而不是假定其他模块测试已覆盖所有行为。
4. `consumer-rules.pro` 当前为空。是否需要 ServiceLoader/AutoService 相关混淆规则，应以 Release 混淆构建和运行验证为依据，不应直接添加宽泛 keep 规则。

## 6. Jetpack Compose 全面迁移评价

目前核心 API 中存在以下传统 Android UI 依赖：

| 旧依赖 | 影响 |
|---|---|
| `Drawable` | 图标模型绑定 Android Drawable |
| `View` | 自定义 Action View 绑定传统 View |
| `Menu` | Registry 负责填充 Android Menu |
| `MenuItem` | 点击事件依赖 MenuItem |
| `Fragment` | 侧边栏操作依赖 Fragment |
| Fragment Navigation Builder | 导航描述与 Fragment Navigation 绑定 |

**不能仅将 Menu/View 替换成 Compose 控件，就认为底层迁移完成。**

建议目标架构：

1. **Compose 无关的 Action Core**：Action 定义、稳定 ID、上下文、执行请求、结果、注册表、调度器、Action Tree。
2. **Compose Renderer**：工具栏、菜单、弹出层、侧边栏、操作按钮。
3. **Legacy Adapter（迁移过渡期）**：在调用方逐步迁移期间兼容旧 Menu/View；完成迁移后删除。
4. **Navigation Adapter**：核心只表达导航意图或目标标识，具体路由由 Compose 页面层执行。

“无生命周期”不应理解为 Android 应用不再有生命周期。应消除核心 Action 对 Activity、Fragment、View 的直接持有，并为异步任务提供明确的 owner、取消和资源释放规则。

## 7. 推荐重构顺序（从底层向上）

### 阶段 1：基础 Action 契约
涉及：`ActionItem`、`EditorActionItem`、`ActionData`、`ActionStyle`。

先统一稳定 ID、不可变定义、执行上下文、执行结果、状态模型和资源所有权。去除核心契约对 Drawable、View 的直接依赖。

### 阶段 2：Registry
涉及：`ActionsRegistry`、`DefaultActionsRegistry`。

实现线程安全注册、ID 唯一性、明确替换/注销策略、监听器快照、递归/所有权清理规则和执行器抽象。

### 阶段 3：Execution Engine
建立调度策略、Supervisor 语义、异常隔离、取消、任务 owner、结果通知和执行器关闭机制。

### 阶段 4：Action Tree
涉及：`ActionMenu`、`FillMenuParams`、`OnActionClickListener`。

建立独立于 Android Menu 的树模型，一次计算状态快照，统一排序、可见性和启用状态。

### 阶段 5：导航抽象
涉及：`SidebarActionItem`。

把 Fragment 类和 Fragment Navigator Builder 替换为 UI 无关的导航描述，由 Compose 路由层负责执行。

### 阶段 6：Compose Renderer
实现工具栏、编辑器操作菜单、子菜单、侧边栏和自定义操作内容的 Compose 渲染。

### 阶段 7：调用方迁移与旧实现删除
迁移 `core/app`、编辑器、LSP 和其他 Action 调用方。通过源码搜索和构建验证确认不再依赖旧 View/Menu/Fragment Action API 后，删除 Legacy Adapter 和不再需要的依赖。

## 8. 必须补充的测试

| 测试项 | 验证目标 |
|---|---|
| ID 唯一性 | 不同 Action 不因 HashCode 冲突而误识别 |
| 并发注册/注销 | 多线程操作不会损坏 Registry |
| 监听器并发 | 通知期间增删监听器不崩溃 |
| 重复注册 | 替换时旧对象资源按约定释放 |
| 子菜单 | 可见、不可见、禁用、嵌套和排序正确 |
| 执行结果 | 成功、失败、异常、取消语义一致 |
| 生命周期 | 编辑器关闭后不访问失效 UI 对象 |
| 资源释放 | 注销、清空和父菜单销毁符合所有权规则 |
| 多编辑器 | 同一 Action 在不同上下文状态互不干扰 |
| Compose Renderer | UI 状态与 Action Core 快照一致 |

## 9. 评分明细

| 维度 | 评分 |
|---|---:|
| 架构抽象与职责划分 | 7.0/10 |
| API 设计与扩展能力 | 5.5/10 |
| 执行正确性 | 4.5/10 |
| 并发与线程安全 | 3.5/10 |
| 生命周期与资源管理 | 4.0/10 |
| 菜单行为正确性 | 5.0/10 |
| 可测试性 | 3.5/10 |
| Jetpack Compose 适配程度 | 2.0/10 |
| **综合评价** | **5.1/10** |

## 10. 最终建议

将 `core/actions` 定位为 ZeroStudio 的**通用动作执行基础设施**，而不是 Android 菜单工具库。

优先解决三个根本问题：

1. 动作定义与展示状态耦合。
2. 动作执行与 UI 框架耦合。
3. 资源所有权和任务生命周期不明确。

**推荐优先级：`ActionItem → ActionData → ActionsRegistry → DefaultActionsRegistry → Execution Engine → Action Tree → Compose Renderer`。**

不要先重做菜单控件，也不要在旧 Action API 上继续堆叠 Compose 包装层。先把底层契约稳定下来，再逐层迁移上层调用方。

---

## 评审限制

本报告基于指定 main 分支提交的源码静态审查。未执行 Gradle 构建、单元测试、设备运行、并发压力测试或性能基准。涉及“风险”的内容表示从代码结构推导出的潜在问题，不应误读为已在运行时复现的缺陷。对样式字段的消费情况、混淆规则和依赖暴露问题，仍需结合完整调用图及 Release 构建进一步验证。
