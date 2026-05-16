# ZeroStudio Build Transport V2 (BSP + Binder + Binary Data Plane)

## 目标（对标 IDEA/AS 的体验）

- 大项目构建时，UI 不被构建事件洪峰拖死。
- 构建协议分层：语义层稳定（BSP），传输层高吞吐（Binder + 二进制数据面）。
- 让 `initialize/sync/compile/test/cancel/shutdown` 全链路可观测、可限流、可恢复。

---

## 一、总体架构（Control Plane / Data Plane 分离）

### 1) Control Plane：AIDL/Binder（小消息 + 命令）

用于：
- 会话控制：`initialize/sync/compile/test/cancel/shutdown`
- 订阅/取消订阅事件
- 心跳与 backpressure 协议

要求：
- 单次 Binder 消息只传轻量 envelope（taskId、targetId、token、状态码、统计字段）
- 不传源码快照/大型对象图/长日志全文

### 2) Semantic Plane：BSP（构建语义标准）

用于：
- build server 标准接口：`workspace/buildTargets`、`buildTarget/compile`、`buildTarget/test` ...
- 统一 IDE 与构建后端契约，避免私有协议绑死

### 3) Data Plane：二进制大对象通道（Proto + Chunked）

用于：
- 大体积 metadata（依赖图、source roots、索引快照、诊断批次）
- 大日志与构建产物描述

建议：
- protobuf 定义 envelope + 分片协议
- 分片存储到 mmap/file cache（或 content provider）
- Binder 只传 `blobToken` + `offset/length/checksum`

---

## 二、进程与线程模型

### 进程划分
- UI/Editor 进程：仅消费事件与发控制命令
- Build Session Service 进程（可独立 `:build_session`）：调度 BSP 会话
- Tooling/Gradle 进程：实际构建执行

### 线程与队列
- Q0（高优先）：用户交互命令（cancel、status、quick diagnostics）
- Q1（中优先）：compile/test 任务
- Q2（低优先）：全量 sync、大元数据抓取

每个队列单独线程池 + 令牌桶限流，防止 Q2 饿死 Q0。

---

## 三、关键性能策略（系统化落地）

1. **事件节流 + 合并**
- progress 事件按时间窗（如 50~100ms）合并
- 同 target/task 的状态仅保留最新增量

2. **目标缓存与快照增量**
- `workspace/buildTargets` 结果本地缓存
- sync 改为 diff 模式（hash/version stamp）

3. **对象图瘦身**
- 运行期模型转为轻量 DTO（避免将全量 Gradle model 常驻内存）
- 大字段延迟加载（on-demand fetch）

4. **统一取消语义**
- cancel 必须贯通：UI -> Binder -> BSP `build/cancel` -> Gradle daemon
- cancel 后自动触发清理策略（队列、缓存、临时文件句柄）

5. **生命周期复用**
- BSP 会话池化（按 project root）
- 避免每次构建重启全链路进程

6. **背压协议（Backpressure）**
- callback 消费慢时，服务端降频/采样
- 高水位触发事件降载策略（只推关键事件）

---

## 四、推荐模块职责重构

### core/app
- `BspSessionBinderService`：控制面 RPC + 订阅总线 + 背压
- `GradleBuildService`：保留为兼容入口，逐步下沉到 Session Service

### core/projects
- 只持有轻量工作区快照与查询 API
- 深层元数据通过 token 按需拉取

### tooling/api
- BSP facade + proto codec + token store 抽象
- 不再暴露 legacy lsp4j-rpc 风格接口给新链路

### tooling/impl
- 专注 build backend 适配（BSP server 语义实现）
- 不承担 UI 事件直连职责

---

## 五、分阶段实施路线

### Phase A（1~2 周）
- 完成 Binder control plane 正式接线（替换 stub）
- 打通 compile/test/cancel/shutdown 的 Binder->BSP 真实调用

### Phase B（2~4 周）
- 引入 protobuf chunk data plane
- 落地 metadata/log 大对象 token 化

### Phase C（2~4 周）
- 队列优先级 + 背压 + 事件合并
- 增加性能观测指标（P50/P95 延迟、队列深度、GC 抖动）

### Phase D（持续）
- 下线 legacy RPC 与兼容桥
- 完成全链路稳定性压测（大项目 20~60GB 缓存场景）

---

## 六、验收指标（必须量化）

- 构建期间 UI 主线程 dropped frames 下降 > 60%
- 大项目首次 sync 时间下降 > 30%
- 事件吞吐峰值下，内存峰值下降 > 25%
- cancel 生效时间（用户点击到任务停止）P95 < 1.5s
- 与终端 `gradlew` 相比，总耗时差距收敛到 1.1~1.5x

