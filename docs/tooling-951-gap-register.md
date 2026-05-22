# Tooling 9.5.1 Gap Register（迭代1-第1周）

> 更新时间：2026-05-22  
> 范围：`core/projects`、`core/app`、`tooling/api`、`tooling/impl`、`tooling/events`、`tooling/model`

## P0（立即推进）

### GAP-P0-01：缺少协议兼容自动化矩阵
- **能力名**：新旧 client/server 协议兼容测试。  
- **当前状态**：未建立自动化验证。  
- **影响范围**：`tooling/api`、`tooling/impl`、`core/app`。  
- **风险**：新增字段导致旧端隐式行为变化不可见。  
- **最小实现方案**：
  1. 增加兼容性 smoke 测试任务：`new-server + old-client`、`old-server + new-client`；
  2. 核验新增字段忽略/默认值行为（ExecutionRequest/Result/InitializeResult）。  
- **验收标准**：
  - 两组组合均可完成一次任务执行并回收标准结果；
  - 无序列化/反序列化崩溃。  

### GAP-P0-02：Capability 协商覆盖面不足
- **能力名**：server feature 协商（operation types 之外）。  
- **当前状态**：仅 operationTypes + maxEventRate 形成稳定链路。  
- **影响范围**：`tooling/api`、`tooling/model`、`core/projects`。  
- **风险**：客户端难以做按能力降级，造成误请求。  
- **最小实现方案**：
  1. 在 metadata/result 中增加显式能力标记（如 phasedAction/modelSnapshot/querySupport）；
  2. client 端按 capability 决定功能可用性。  
- **验收标准**：
  - 客户端在 capability 缺失时自动 fallback；
  - capability 字段新增不破坏旧版本解析。  

### GAP-P0-03：事件正确性无自动验收
- **能力名**：事件闭合与顺序校验。  
- **当前状态**：有节流能力，无事件一致性自动验证。  
- **影响范围**：`tooling/events`、`tooling/impl/progress`、`core/app`。  
- **风险**：UI 卡顿/状态机异常难复现。  
- **最小实现方案**：
  1. 新增测试工具统计 start/progress/finish 对齐；
  2. 验证事件 parent-child 链完整性。  
- **验收标准**：
  - 每个 start 必有 finish；
  - 关键 operationType（TASK/PROJECT_CONFIGURATION）通过校验。  

## P1（第2阶段跟进）

### GAP-P1-01：requestId 未覆盖非 execute 主链路
- **能力名**：端到端 trace id 全链路。  
- **当前状态**：execute 路径已覆盖，sync/model 查询未统一。  
- **影响范围**：`tooling/impl`、`core/projects`、`core/app`。  
- **最小实现方案**：同步入口、模型查询入口引入 requestId 并统一日志键。  
- **验收标准**：任一同步请求可从客户端日志串联至服务端执行日志。  

### GAP-P1-02：失败分类粒度不统一
- **能力名**：失败模型标准化（脚本/连接/配置/取消/未知）。  
- **当前状态**：有基础分类，细粒度边界不清晰。  
- **影响范围**：`tooling/api`、`tooling/impl`、`core/app`。  
- **最小实现方案**：扩展 failure 分类映射表与测试样例。  
- **验收标准**：典型失败场景映射稳定且 UI 提示可区分。  

## P2（文档与工程化）

### GAP-P2-01：专项文档未补齐
- **能力名**：交付物文档体系。  
- **当前状态**：已有升级总规划、周状态文档；其余专项文档不完整。  
- **影响范围**：研发协作、回归与灰度管理。  
- **最小实现方案**：补齐 contract/test-plan/rollout 文档。  
- **验收标准**：文档覆盖迭代2所需输入，评审可直接执行。  

## 本周落地顺序建议

1. 先做 **GAP-P0-01**（兼容矩阵）与 **GAP-P0-03**（事件正确性校验）。  
2. 并行推进 **GAP-P0-02** capability 扩展字段设计。  
3. 下周初收敛 **GAP-P1-01** requestId 全链路。  



## P0（协议替换专项：lsp4j-rpc -> AIDL/gRPC/REAPI）

### GAP-P0-04：lsp4j-rpc 依赖与调用链未移除
- **能力名**：构建服务通信协议迁移。
- **当前状态**：仍存在 lsp4j-rpc 主链路。
- **影响范围**：`core/app`、`tooling/api`、`tooling/impl`、`tooling/events`。
- **风险**：序列化/反序列化高开销导致高内存、高延迟、UI 卡顿与 OOM。
- **最小实现方案**：
  1. 盘点并冻结新增 lsp4j-rpc 接口；
  2. 设计 AIDL 控制面 + gRPC 数据面；
  3. 加入双栈灰度开关与回退策略；
  4. 完成旧链路删除计划。
- **验收标准**：
  - 构建主流程默认不再经过 lsp4j-rpc；
  - 保留可控回退开关（发布前可临时启用）。

### GAP-P0-05：缺少 REAPI 远程执行/缓存接入层
- **能力名**：远程执行与缓存标准化。
- **当前状态**：未形成 REAPI bridge。
- **影响范围**：`tooling/impl`、`tooling/model`、`core/projects`。
- **风险**：大型项目缓存复用不足，构建耗时无法显著下降。
- **最小实现方案**：
  1. 新增 `ReapiExecutionBridge` 抽象；
  2. 打通 Action Cache / CAS 查询与上传下载；
  3. 增加 capability 协商：是否支持 remote execution。
- **验收标准**：
  - 远程缓存命中率可观测；
  - 失败可自动 fallback 到本地执行。

### GAP-P1-03：大模型/大事件缺少分片传输协议
- **能力名**：流式传输与分片重组。
- **当前状态**：协议默认单包模型，不利于超大结果。
- **影响范围**：`tooling/events`、`tooling/model`、`core/app`。
- **最小实现方案**：事件与模型新增 `chunkId/sequence/total/hash` 字段，并提供重组校验器。
- **验收标准**：100MB 级事件/模型流可稳定传输且可校验一致性。
