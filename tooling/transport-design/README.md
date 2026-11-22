# Tooling 进程间传输架构方案

> 状态：设计基线 / 待基准验证。本文档只描述 ZeroStudio Android 进程与独立 Tooling JVM 子进程之间的通信，不改变 Gradle Tooling API 与 Gradle Daemon 自身的通信协议。

## 1. 目标与边界

本方案解决的是 AndroidIDE/ZeroStudio 主进程中的构建服务，与 `ToolingServerRunner` 启动的独立 JVM 进程之间，如何高效传递命令、构建事件、AGP/Gradle 派生数据模型，以及必要时的文件内容。

当前仓库代码路径已经使用 `ProcessBuilder` 风格的子进程启动方式，父子进程通过 `Process.inputStream`、`Process.outputStream` 形成双向管道，`Process.errorStream` 独立承载子进程日志。Tooling 端在 `Main.kt` 中把 `System.in` / `System.out` 交给 LSP4J launcher。当前 JSON-RPC/LSP4J + Gson 是需要评估替换的应用层协议；Gradle Tooling API 与 Gradle Daemon 的内部通信不在替换范围内。

### 目标

- 消除主进程与 Tooling JVM 之间 JSON 文本协议、通用 JSON-RPC 代理和反射式 Gson 对大模型数据的额外开销。
- 使用紧凑、可演进、可分帧的 Protobuf 二进制消息表达控制命令、结果、事件和模型 DTO。
- 对文件优先传递受校验的文件引用，不把 `java.io.File` 对象图序列化过管道。
- 仅在文件无法被另一端直接访问时传输文件字节；传输必须分块、有界、可取消、可校验。
- 保持单一 IPC 机制、单一消息协议和清晰故障边界，避免叠加多个 IPC 框架。
- 用可重复的设备基准测试回答吞吐、延迟、CPU、内存、GC 和持续负载温升问题。

### 非目标

- 不替换 Gradle Tooling API 到 Gradle Daemon 的协议。
- 不把 AGP/Gradle 内部对象、ProjectConnection、BuildLauncher 或任意 JVM 对象直接跨进程传输。
- 不承诺未经测量的 50–500 MiB/s。
- 不在本阶段移除旧协议；只有新旧路径功能等价且测试通过后才迁移。
- 不引入 gRPC、AIDL 作为数据面、FlatBuffers、独立 LocalSocket 服务、mmap/SharedMemory 传输栈或多个并行 IPC 通道。

## 2. 推荐结论

**保留 ProcessBuilder stdin/stdout 双向管道；将 LSP4J JSON-RPC + Gson 替换为长度前缀分帧的 Protobuf 消息协议。**

Protobuf 是结构化消息编码层，不是新的 IPC 机制。底层仍是现有操作系统进程管道。协议按消息类型表达请求、响应、事件和批次；request_id 只用于关联异步响应，不构建通用远程对象代理。

文件采用双路径策略：

1. 两个进程都能访问同一文件时，发送 `FileReference`（路径/逻辑 ID、长度、版本信息和访问权限），接收端直接访问文件。
2. 只有文件不可共享访问时，才通过同一 Protobuf 管道发送分块字节。

## 3. 文档导航

- [架构与组件边界](architecture.md)
- [消息协议与分帧规范](protocol.md)
- [数据模型与模型快照](data-models.md)
- [文件引用与内容传输](file-transfer.md)
- [性能模型、吞吐口径与基准计划](performance.md)
- [迁移阶段、验收与回滚](migration.md)
- [架构图与时序图](diagrams.md)

## 4. 当前实现基线（仓库代码事实）

| 位置 | 当前职责 |
|---|---|
| `core/app/.../GradleBuildService.kt` | Android 侧构建服务、Tooling server 生命周期与构建请求协调 |
| `core/app/.../ToolingServerRunner.kt` | 启动独立 Java 子进程；取出 stdin/stdout/stderr；创建客户端 launcher |
| `tooling/impl/.../Main.kt` | Tooling JVM 入口；将 System.in/System.out 接入服务端 launcher |
| `tooling/api/.../IToolingApiClient.kt` | Tooling JVM 回调 Android 端的日志、构建状态、进度和参数查询接口 |
| `tooling/api/.../IToolingApiServer.kt` | Android 端调用 Tooling JVM 的初始化、项目查询、任务执行、取消和关闭接口 |
| `tooling/model/.../IProjectQueries.kt` | 项目选择后按需查询项目数据；当前注释明确避免整体搬运大型项目模型 |

上述基线来自当前 main 分支源码检查。迁移实施前仍需对所有 API、消息 DTO、结果 DTO、事件类型和调用点做逐项清单，不可仅凭本表视为接口盘点完成。

## 5. 核心原则

1. **对象不跨进程，数据契约跨进程。** AGP/Gradle 原始对象留在 Tooling JVM 内，投影为稳定 DTO。
2. **按需查询优先于全量快照。** 先保持现有选中项目/按需读取的内存控制思想；确有 UI 或索引需求时再批量发送摘要。
3. **小消息低延迟，大数据分块。** 控制消息与文件块共享同一传输层，但使用不同大小限制和调度优先级。
4. **引用优先，复制兜底。** 同一沙箱文件可共享时不复制文件内容。
5. **单读者、单写者、有界队列。** 不允许无界缓存、无界线程或无限制并发。
6. **stdout 只承载协议。** Tooling JVM 的日志必须进入 stderr；任何 println 或库输出写入 stdout 都会破坏帧流。
7. **基准数据优先于估算。** 文档中的目标值是验收门槛，不是现有性能声明。
