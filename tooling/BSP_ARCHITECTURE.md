# AndroidIDE × BSP 对接架构设计

> 目标：将 `tooling` / `core/projects` / `core/app` 的构建通信从自定义 LSP4J JSON-RPC 契约迁移为行业标准 BSP。

## 1. 架构分层

- **BSP Client（IDE 侧）**：`core/app` + `core/projects`
  - 负责启动 BSP server 进程。
  - 负责 `build/initialize`、`workspace/buildTargets`、`buildTarget/compile`、`buildTarget/test` 请求。
  - 负责将 BSP `task/start|progress|finish` 事件映射到现有 UI 与日志系统。
- **BSP Transport（协议层）**：`tooling/api`
  - 提供 BSP 连接、初始化、关闭生命周期封装。
  - 仅保留协议和类型适配，不承载业务。
- **Build Backend（构建后端）**：`tooling/impl`
  - 作为 BSP Server 实现层，桥接 Gradle Tooling API。
  - 把 BSP build target 映射到 Gradle modules/variants/tasks。

## 2. 核心协议映射

- 旧：`IToolingApiServer.initialize/getRootProject/executeTasks/cancelCurrentBuild`
- 新：
  - `build/initialize` + `build/initialized`
  - `workspace/buildTargets`
  - `buildTarget/compile`
  - `buildTarget/test`
  - `build/shutdown` + `build/exit`

## 3. 连接与发现

- 仓库根目录放置 `.bsp/androidide.json`，用于标准 BSP 发现。
- `core/app/assets/bsp/androidide.json` 作为移动端内置模板，首次导入项目时写入项目目录。
- server 启动命令标准化为：`./gradlew --quiet bsp`（后续在 `tooling/impl` 增加对应任务入口）。

## 4. 数据模型策略

- **优先使用中央仓库发布的 `bsp4j` 模型**，不再克隆 BSP 规范仓库。
- 保留现有内部 model 一段时间作为 compatibility 层，逐步迁移调用方。
- 禁止新增新的私有 RPC 注解接口（`@JsonRequest/@JsonNotification`）作为主通道。

## 5. 渐进迁移计划

1. `tooling/api` 提供 `BspServerConnection`（已落地）。
2. `core/projects` 新增 BSP session manager，先替代任务执行链路。
3. `core/app` 的 `ToolingServerRunner` 逐步切换到 BSP lifecycle。
4. `tooling/impl` 暴露 Gradle BSP server 入口 (`gradlew bsp`)。
5. 清理 `IToolingApiServer/IToolingApiClient` 的主流程依赖，仅保留兼容桥接。

## 6. 风险与回滚

- 风险：部分旧调用链依赖 `IProject` 动态代理。
- 缓解：保留 compatibility bridge，按功能模块分阶段切换（sync -> compile -> test）。
- 回滚：保留旧 runner 开关，通过 feature flag 在设置中切回 legacy transport。
