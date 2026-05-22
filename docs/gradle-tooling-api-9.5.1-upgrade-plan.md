# Gradle Tooling API 9.5.1 升级迭代总规划（面向 `core/projects` + `tooling/*`）

> 范围：`core/projects`、`tooling/api`、`tooling/builder-model-impl`、`tooling/events`、`tooling/impl`、`tooling/model`。  
> 目标：把现有客户端/服务端的 Tooling 能力从“已接入 9.5.1 依赖”升级到“全链路 9.5.1 能力可用 + 缺口补齐 + 可演进架构”。

---

## 0. 现状快照（基于仓库结构与依赖）

### 0.1 版本现状
- 版本目录与版本声明已经指向 `9.5.1`：
  - `gradle/libs.versions.toml` 中 `gradle-tooling = "9.5.1"`。
  - 本地 Maven 仓中已有 `gradle-tooling-api-9.5.1` 的 `jar/pom/sources`。
- `tooling/impl` 通过 `libs.tooling.gradleApi` 使用 Tooling API。

### 0.2 模块职责现状（高层）
- `tooling/api`：客户端-服务端通信接口、参数与消息模型。
- `tooling/impl`：服务端主实现（Connector、同步构建、事件转发、模型构建）。
- `tooling/events`：事件抽象与 DTO（Task/Transform/WorkItem/Download/Configuration）。
- `tooling/model`：IDE 内部项目模型与图结构。
- `tooling/builder-model-impl`：Android Builder model 侧落地实现。
- `core/projects`：工作区模型聚合、项目管理与上层消费。

### 0.3 判断
- **你们已经完成“依赖版本升级”，但尚未完成“能力面升级”**。  
- 下一步重点不是改 `version`，而是：
  1) 覆盖 9.5.1 新增/扩展能力；
  2) 把能力打通到 API/事件/模型/查询；
  3) 保证可回退与兼容。

---

## 1. 目标能力地图（9.5.1 全面对接）

把升级目标拆成 7 大能力域，每个域都要有“服务端采集 + 传输协议 + 客户端消费 + 数据落盘/缓存 + 查询接口”。

## 1.1 连接与分发能力（Connector / Distribution）
- Gradle 分发选择策略：wrapper、固定版本、指定 distribution URI、指定 user home。
- 下载/安装进度（含文件级别）可观察。
- Daemon 生命周期可控（超时、重连、隔离目录）。

## 1.2 执行能力（BuildLauncher / TestLauncher / BuildAction）
- 统一支持：`tasks`、`arguments`、`jvm args`、`env`、`stdin`、取消 token。
- BuildAction 与 Phased BuildAction（projectsLoaded/buildFinished）能力通路。
- 失败分类（脚本错误、模型错误、连接错误、取消）标准化。

## 1.3 事件总线能力（Progress Event 全类型）
- `OperationType` 订阅颗粒度可配置。
- 覆盖：Task、Test、WorkItem、ProjectConfiguration、Transform、FileDownload、BuildPhase、Problems（若可映射）。
- 事件具备上下文链：operation id / parent id / plugin id / project id / timestamps。

## 1.4 模型能力（Gradle/Android/Project Graph）
- 基础模型：`GradleProject`、`BuildEnvironment`、`IdeaProject`（按需）。
- Android v2 模型快照增强：变体、依赖图、artifact、测试套件、标志位。
- 多模块关系：buildSrc、included builds、composite build、version catalog 关联。

## 1.5 查询与检索能力（Client Query API）
- 提供可分页/可过滤查询：任务、变体、依赖、事件、执行历史。
- 增量刷新（按模块、按变体、按事件类型）。
- 支持按“项目物料”检索（manifest、source set、artifact 输出）。

## 1.6 兼容与降级能力
- 同一协议同时兼容 8.x/9.x 构建环境（最少错误中断）。
- 新字段用 `capability` 协商，不让旧客户端崩溃。
- 关键 API 反射/探测式调用，避免直接 hard fail。

## 1.7 可观测与可靠性
- 端到端 trace id（请求 -> Gradle operation -> 客户端展示）。
- 结构化日志 + 指标（耗时、成功率、取消率、模型体积）。
- 大项目保护：超时、分片同步、内存阈值、事件采样。

---

## 2. 现有模块“对齐改造清单”（按你指定模块）

## 2.1 `tooling/api`（协议层）

### 必做
1. 为所有执行入口补齐统一 `ExecutionRequest`（任务、参数、JVM、环境、取消、operationTypes）。
2. 增加 `Capabilities` 协商模型：
   - server->client: 支持的 operation types / model types / phased action。
   - client->server: 期望事件等级、最大事件速率、是否拉取大模型。
3. 扩展结果模型：
   - `ExecutionResult`（success/cancel/failure + classified failure + diagnostics）。
   - `ModelSnapshotMeta`（版本、来源、构建时间、hash、schemaVersion）。

### 建议
- 把 `IToolingApiServer` 拆为 `ConnectionService` / `ExecutionService` / `ModelQueryService`，降低后续扩展风险。

## 2.2 `tooling/impl`（服务端执行与桥接）

### 必做
1. `ToolingApiServerImpl` 中构建执行路径做“统一执行管线”：
   - preflight -> connect -> execute -> collect events -> build models -> persist -> publish result。
2. 事件采集升级：
   - `addProgressListener(listener, operationTypes)` 支持动态订阅。
   - 为每个事件补唯一 operation key（用于 UI 合并同一任务的 start/progress/finish）。
3. 引入 `BuildAction` / `PhasedBuildAction` 实现层（目前看主要是模型 + task 执行）。
4. 失败转换器：Gradle 异常树 -> 你们内部 `ModelBuilderException`/`SyncIssue`/`UserFacingError`。

### 建议
- 新增 `ToolingConnectionManager`（缓存 connector、connection 池、自动回收）。

## 2.3 `tooling/events`（事件模型层）

### 必做
1. 事件基类统一字段：`eventId`, `parentEventId`, `operationType`, `displayName`, `startTime`, `endTime`, `plugin`, `projectPath`。
2. 新增/补齐事件域：
   - test 执行事件、build phase 事件、problem/report 类事件（可先映射为通用 diagnostics）。
3. 明确序列化兼容策略：新增字段必须可选，旧客户端可忽略。

### 建议
- 增加 `EventSchemaVersion` 与 migration adapter。

## 2.4 `tooling/model`（IDE 内部模型）

### 必做
1. 增加“能力型模型”而非一次性大对象：
   - `BuildExecutionSummaryModel`
   - `TaskGraphSummaryModel`
   - `CompositeBuildTopologyModel`
2. 统一图结构（project/module/variant/dependency）中的 identity 规则。
3. 变体上下文（`VariantContextModel`）增加“来源任务、产物路径、问题列表关联”。

### 建议
- 模型拆层：`raw snapshot`（近 Gradle 原始） + `normalized view`（给 IDE）。

## 2.5 `tooling/builder-model-impl`（Android Builder 模型映射）

### 必做
1. 针对 AGP/Builder v2 的字段漂移做映射清单（新增字段、废弃字段、语义变更）。
2. 所有 `Default*` model 增加 schema 版本标记与空值保护。
3. 同步问题（sync issue）分类对齐 9.5.1 语义：warning/error/fatal + remediation hint。

### 建议
- 构建“模型契约测试”：输入固定 AGP 输出，断言你们模型 JSON snapshot 不回退。

## 2.6 `core/projects`（业务消费层）

### 必做
1. `WorkspaceModelBuilder`/`ProjectManagerImpl` 接入 capability 协商结果，避免盲目请求重模型。
2. 构建“增量刷新策略”：
   - 文件变化触发局部刷新；
   - 变体切换触发最小必要模型重建。
3. 上层查询 API 增加状态型结果（running/cancelled/failed + 最新事件摘要）。

### 建议
- 加入“项目物料索引器”（source sets、artifacts、manifest merge、依赖图）用于快速检索。

---

## 3. 从 9.5.1 sources（5k+ 文件）落地的“工程化导入策略”

你不应逐文件硬搬；应采用“能力清单驱动 + 自动抽取 + 人工审阅”模式。

## 3.1 建立参考索引（一次性）
- 解析 `org/gradle/tooling/**`、`org/gradle/tooling/events/**`、`org/gradle/tooling/model/**` 包：
  - 导出 public type 列表、方法签名、since 信息。
- 产出 CSV/JSON 索引（用于 gap diff）。

## 3.2 建立“现有实现映射表”
- 每个你们接口，映射到 Gradle 原生 API：
  - 是否已支持、部分支持、未支持。
- 输出覆盖率：接口覆盖率 / 事件覆盖率 / 模型字段覆盖率。

## 3.3 制定优先级
- P0：影响同步可用性/稳定性（连接、取消、失败处理、基础模型）。
- P1：影响 IDE 体验（事件细节、任务图、变体上下文）。
- P2：增强项（高级统计、深层诊断、扩展模型）。

---

## 4. 差距（Gap）模板：你可以直接按此推进实现

每个未支持项都记录：
1. **能力名**（例如 TestOperation events）
2. **Gradle 9.5.1 对应 API**（类/方法）
3. **当前状态**（none/partial/full）
4. **影响范围**（server/client/model/UI）
5. **实现方案**（最小变更 + 理想架构）
6. **风险**（兼容、性能、内存）
7. **验收标准**（可自动化验证）

---

## 5. 分阶段实施计划（建议 6 个迭代）

## 迭代 1：基建与协议（1~2 周）
- 完成 capability 协商 + 统一执行请求/结果。
- 统一错误模型和日志 trace。
- 验收：旧客户端不崩，新客户端可读 capability。

## 迭代 2：事件总线升级（1~2 周）
- OperationType 动态订阅。
- 补齐关键事件域与关联字段。
- 验收：一次构建可输出完整 start/progress/finish 链。

## 迭代 3：模型快照升级（2~3 周）
- Android/Gradle 模型扩充，拆 raw/normalized。
- 引入模型缓存与 hash。
- 验收：大项目同步耗时可对比基线下降或持平。

## 迭代 4：执行与动作体系（1~2 周）
- BuildAction/PhasedBuildAction 支持。
- 统一 task/test/build action 执行入口。
- 验收：至少 3 类执行路径可共用管线。

## 迭代 5：`core/projects` 消费增强（1~2 周）
- 增量刷新、检索查询、状态面板数据源。
- 验收：变体切换与小改动刷新具备局部化效果。

## 迭代 6：稳定性与回归（1~2 周）
- 契约测试、兼容测试、压力测试。
- 验收：关键用例通过率 >= 95%，无 P0 回归。

---

## 6. 测试矩阵（必须同时覆盖客户端与服务端）

## 6.1 协议兼容测试
- 新 server + 旧 client。
- 旧 server + 新 client。
- 新增字段忽略/降级逻辑。

## 6.2 功能测试
- sync 全量、sync 增量、cancel、retry、offline。
- 多模块 + composite build + version catalog。
- AGP 不同版本样本（至少 2~3 个）。

## 6.3 事件正确性测试
- 事件顺序、父子关系、完成态闭合（每个 start 都有 finish）。
- 大量事件吞吐（避免 UI 阻塞）。

## 6.4 性能与资源
- 同步耗时、峰值内存、事件数量、模型序列化体积。
- 10w+ 文件项目场景压测。

---

## 7. 风险与规避

- **风险 1：一次性引入过多模型导致内存暴涨**  
  规避：分层模型 + lazy query + 大字段按需加载。

- **风险 2：事件洪泛导致客户端卡顿**  
  规避：服务器侧采样/节流 + 客户端聚合渲染。

- **风险 3：兼容旧协议失败**  
  规避：capability 协商 + optional 字段 + fallback 行为。

- **风险 4：AGP/Gradle 组合差异导致同步不稳定**  
  规避：样本矩阵 + 契约快照 + 失败分类可观测。

---

## 8. 交付物清单（你要求的“超详细报告”可拆成这些文档）

1. `tooling-951-capability-matrix.md`：能力覆盖矩阵。  
2. `tooling-951-gap-register.md`：未支持能力登记册（按模块）。  
3. `tooling-951-api-contract.md`：协议变更（请求/响应/事件）。  
4. `tooling-951-model-schema.md`：模型 schema 与兼容策略。  
5. `tooling-951-test-plan.md`：测试矩阵与验收门禁。  
6. `tooling-951-rollout-plan.md`：灰度/回滚/监控策略。

---

## 9. 你可以立刻执行的下一步（按优先顺序）

1. 先产出 **Capability Matrix v1**（2 天内完成）。
2. 锁定 P0 缺口并建立 issue（连接、取消、失败、基础事件）。
3. 完成协议扩展（capabilities + ExecutionRequest/Result）。
4. 落地统一执行管线（`tooling/impl`）。
5. 补齐事件链与最小模型快照。
6. 再推进高级模型与检索能力。

---

## 10. 结论

你当前仓库已经具备升级到 9.5.1 的基础条件（依赖与模块结构都在），但离“全方面功能支持”还差一层**体系化补齐**。建议按“协议 -> 执行 -> 事件 -> 模型 -> 消费 -> 验证”顺序推进，避免先堆模型导致维护成本失控。


## 10. 构建服务通信协议重构（强制）：移除 lsp4j-rpc，切换 AIDL + gRPC + REAPI + Proto

> 目标：彻底移除客户端与服务端构建通信中的 lsp4j-rpc 依赖与调用链，构建“设备内 IPC + 远端执行/缓存 + 高效序列化”的双通道架构。

### 10.1 目标架构（分层）
1. **进程内/同机跨进程控制面**：AIDL（Binder）
   - 用于 App(UI 进程) <-> 本机构建服务进程的控制命令、状态订阅、生命周期管理。
2. **数据面/远程执行面**：gRPC + Proto
   - 用于大体量构建事件、模型快照分片、日志流与远程执行请求。
3. **远程构建与缓存协议面**：REAPI
   - 对接 CAS/Action Cache/Execution，支持大项目缓存复用与并发执行。

### 10.2 模块落点（与当前仓库对应）
- `tooling/api`：
  - 新增 AIDL 对应 DTO（轻量控制命令）。
  - 新增 proto DTO 映射层（大对象流式传输，不经 Binder 直接搬运）。
- `tooling/impl`：
  - 新增 `TransportFacade`：`AidlControlChannel` + `GrpcDataChannel`。
  - 新增 `ReapiExecutionBridge`：封装 Action/CAS/Execution 调用与重试。
- `tooling/events`：
  - 事件模型新增 chunk/sequence 字段，支持流式分片和断点续传。
- `tooling/model`：
  - 模型快照支持 `snapshotId/chunkCount/chunkHash` 元信息。
- `core/app`：
  - UI 仅通过 AIDL 控制通道发起请求；大数据流改为 gRPC 订阅。
- `core/projects`：
  - 同步流程改为 capability 驱动：优先 REAPI，失败自动 fallback 本地 Gradle。

### 10.3 分阶段迁移（避免一次性切换风险）
- **阶段A（并行接入）**：保留 lsp4j-rpc，新增 AIDL/gRPC 双栈，默认仍走旧链路。
- **阶段B（灰度切流）**：按项目大小/开关切 5% -> 25% -> 50% 流量到新协议。
- **阶段C（全面替换）**：删除 lsp4j-rpc server/client adapter 与序列化模型。
- **阶段D（清理收口）**：删除遗留配置、文档、依赖与兼容分支。

### 10.4 性能与稳定性验收门槛（必须量化）
- P95 UI 主线程阻塞时间下降 >= 40%。
- 构建服务进程峰值内存下降 >= 30%。
- 超大型项目（`./build` 缓存 5GB~100GB+）连续构建 20 次无 OOM。
- 同项目构建时长与 Termux 直跑 Gradle 的差距缩小到 <= 1.5x。

### 10.5 与 9.5.1 升级的耦合关系
- 9.5.1 Tooling API 升级优先解决“能力覆盖”；
- 协议重构优先解决“传输与性能瓶颈”；
- 两条线并行，但以 capability 协商统一收敛，确保客户端按能力选择本地/远程执行路径。
