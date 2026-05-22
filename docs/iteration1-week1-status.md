# 迭代1-第1周执行状态（截至 2026-05-22）

> 基线计划来源：`docs/gradle-tooling-api-9.5.1-upgrade-plan.md` 中“迭代 1：基建与协议（1~2 周）”及其“你可以立刻执行的下一步”条目。

---

## 1) 原计划目标拆解（迭代1-第1周）

本周核心目标可归并为以下 5 个可交付项：

1. `ExecutionRequest/ExecutionResult` 统一请求与结果协议落地。  
2. Client/Server capability 协商链路落地（尤其 operation types、事件速率）。  
3. 统一执行入口初步打通（保留旧路径并可切换）。  
4. 错误模型/诊断信息增强（失败分类 + diagnostics）。  
5. 可观测性基础（请求关联标识、关键日志可关联）。

---

## 2) 已完成项（Done）

### 2.1 协议与模型
- 已新增 `ExecutionRequest`（tasks/arguments/jvmArguments/operationTypes）并用于 tooling execute 路径。  
- 已新增 `ExecutionResult`，用于统一执行返回。  
- 已新增 `ToolingClientCapabilities`，并在初始化参数/结果上完成协商字段扩展。  

### 2.2 执行链路
- `IToolingApiServer` 已提供 `execute(request)`。  
- `ToolingApiServerImpl` 已完成 execute 请求校验、参数清洗、operation type 协商与执行落地。  
- `BuildService`/`GradleBuildService` 已支持通用 `execute(request)`。  
- 业务调用侧（QuickRun、RunTasksDialog、ProjectManager）已支持在开关控制下走新 execute 路径，并保留 `executeTasks` 回退路径。

### 2.3 事件与协商
- 已完成 operation types 能力协商与动态使用。  
- 已完成 progress 事件节流基础能力（速率限制）。

### 2.4 错误与诊断
- execute 失败分支已经统一映射到 `TaskExecutionResult.Failure` 并携带 diagnostics。  

### 2.5 可观测性（本次继续推进）
- `ExecutionRequest` 已携带 `requestId`（UUID）。  
- `ExecutionResult` 已回传 `requestId`。  
- `ToolingApiServerImpl` 已在 execute 成功/失败分支传播 `requestId`。  
- 本次补充：QuickRun/RunTasksDialog 发起 execute 时记录 requestId，形成“请求发起 -> 服务端执行 -> 客户端结果”的关联日志闭环。

---

## 3) 未完成 / 未开发项（Open）

下列项目仍未在代码库中形成完整交付（按优先级）：

### P0（建议本周继续）
1. **Capability Matrix v1 文档**（计划中明确“2天内完成”）：已产出 `docs/tooling-951-capability-matrix.md`，待评审确认细节粒度。  
2. **Gap Register 文档**（P0 缺口登记）：已产出 `docs/tooling-951-gap-register.md`，待转 issue 并绑定负责人与里程碑。  
3. **协议兼容测试（新旧 client/server 交叉）**：未见自动化或脚本化回归覆盖。  

### P1
4. **统一 trace 贯穿所有调用链**：当前 requestId 主要覆盖 execute 路径，尚未覆盖同步/模型构建等其他关键请求。  
5. **失败分类进一步细化**：已具备基础 failure type，但未形成更细粒度“脚本错误/模型错误/连接错误”等稳定枚举映射策略文档。  

### P2
6. **交付物清单 1~6 的系统化文档**：除升级总规划外，其余专项文档尚未全部落地。

---

## 4) 当前执行进度评估（迭代1-第1周）

- **总体完成度（估算）**：约 **75% ~ 82%**。  
- **“协议与执行链路”维度**：完成度高（已具备可运行主干）。  
- **“治理与工程化文档/验证”维度**：完成度中低（矩阵、gap、兼容测试闭环仍不足）。  

---

## 5) 下一步建议（第1周后半段）

1. 将 `docs/tooling-951-gap-register.md` 中 P0 条目转为 issue，并补充 owner / due date。  
2. 增加最小兼容测试矩阵（新server+旧client、旧server+新client）脚本化检查。  
3. 在 execute 之外继续铺设 requestId（尤其同步入口），统一日志字段格式。  
4. 设计 capability 扩展字段（phased action / model snapshot / query support）并完成 API 草案。  

---

## 6) 周结束判断（是否完结）

- **结论：第1周“主线开发基本完成，但周目标未完全完结”。**  
- 已完成：
  - 协议与执行主链路；
  - requestId 基础观测；
  - capability matrix 与 gap register 文档。  
- 未完成（阻断“完结”）：
  - 兼容测试自动化矩阵（C1/C2）尚未实跑闭环；
  - 事件正确性自动验收尚未建立。  
- 建议：将第1周收口延长 1~2 个工作日，优先完成兼容测试最小闭环（T1/T2）。  

---

## 7) 本次追加推进（2026-05-22）

- 已将 capability 扩展字段 API 草案落地到协议模型（以默认值保持兼容）：
  - `ToolingClientCapabilities` 新增：
    - `requestModelSnapshotSupport`
    - `requestQueryServiceSupport`
    - `requestPhasedActionSupport`
  - `InitializeResult` 新增：
    - `supportsModelSnapshot`
    - `supportsQueryService`
    - `supportsPhasedAction`
  - `ToolingServerMetadata` 新增：
    - `supportsModelSnapshot`
    - `supportsQueryService`
- 当前阶段定位：**完成了“能力扩展字段设计/API 草案”这一步，但尚未完成服务端协商逻辑与兼容测试实跑。**  


## 8) 第1周剩余任务拆分（可直接执行）

### 8.1 D1-D2（收口 P0）
1. 完成兼容矩阵 smoke：`new-server+old-client` / `old-server+new-client`。
2. 事件闭合校验脚本：至少覆盖 TASK、PROJECT_CONFIGURATION。
3. Capability 协商字段在 client UI 侧显示（只读 debug 面板）。

### 8.2 D3（协议改造预备）
1. 建立 `lsp4j-rpc` 使用点清单（client/server/serialization）。
2. 新建 AIDL 控制面接口草案（connect/execute/cancel/subscribe/unsubscribe）。
3. 新建 proto 服务草案（EventStream、ModelChunkStream、ExecutionSummary）。

### 8.3 D4-D5（风险前置验证）
1. 在大型项目样本上运行 2 组对照：旧协议 vs 新协议（若新协议未全量完成，先跑 mock stream）。
2. 记录：峰值 RSS、GC 次数、UI 掉帧、构建总时长。
3. 输出迭代1周报补充页：性能基线与差异结论。

### 8.4 本周 Definition of Done（更新）
- 不仅完成 execute 主链路；
- 还需具备：兼容矩阵实跑结果 + 事件闭合验收 + 协议替换预备文档三件套。
