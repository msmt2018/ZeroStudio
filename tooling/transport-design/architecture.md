# 架构与组件边界

## 1. 进程与数据流

主进程与 Tooling JVM 是两个独立进程，不是同一 JVM 内的对象调用。Android 侧通过进程管道与子进程通信；因此“进程内端到端二进制传输”在这里应准确理解为**同一应用沙箱内的父子进程 IPC**，不是 Java/Kotlin 对象引用直传。

父进程：
- `GradleBuildService` 管理构建服务与 Tooling 服务。
- `ToolingServerRunner` 启动 Tooling JVM，保留 Process 对象。
- `Process.outputStream` 是写给子进程 stdin 的通道。
- `Process.inputStream` 是读取子进程 stdout 的通道。
- `Process.errorStream` 单独读取日志。

子进程：
- `Main.main` 初始化 Tooling 服务。
- stdin/stdout 交给传输层。
- Gradle Tooling API 对象与 AGP 模型对象留在子进程内。
- 所有日志输出到 stderr，不得写入 stdout。

## 2. 分层

| 层 | 组件 | 责任 | 不负责 |
|---|---|---|---|
| Service | GradleBuildService / ToolingServerRunner | 进程生命周期、启动、退出、取消、stderr 日志 | DTO 编码 |
| Pipe I/O | PipeReader / PipeWriter | 从流读取完整帧、串行写帧、关闭处理 | 业务逻辑 |
| Frame | LengthPrefixedFrameCodec | 长度前缀、最大帧限制、截断检测 | 请求路由 |
| Codec | ProtobufCodec | Protobuf 消息解析/序列化 | 进程管理 |
| Dispatch | MessageDispatcher | 按消息 oneof/type 路由；请求 ID 关联 | 远程对象代理 |
| Flow control | BoundedQueue / ByteWindow | 队列上限、背压、优先级、取消 | 文件存储 |
| Domain | ToolingCommandHandler / ModelProjector | 构建命令、项目查询、模型投影 | 管道读写 |
| File | FileReferenceResolver / ChunkTransfer | 文件引用校验、必要时分块传输 | 全量文件缓存 |

## 3. 单通道设计

使用现有父子进程管道，不另开端口或 socket：

- 父 -> 子：父进程写 `Process.outputStream`，子进程读取 `System.in`。
- 子 -> 父：子进程写 `System.out`，父进程读取 `Process.inputStream`。
- 子进程日志：`System.err` -> 父进程 `Process.errorStream`。

每个方向各有一个 reader 和一个 writer。多个业务线程只能向有界发送队列提交消息，不能直接并发写 OutputStream。writer 负责串行化帧，避免字节交错。

## 4. 模型边界

AGP/Gradle 的对象图可能包含大量关联、延迟计算对象、内部实现类型和文件句柄。禁止尝试把这些对象直接序列化为 Protobuf 或通过任何通用序列化器搬运。

在 Tooling JVM 内建立显式投影层：
- Gradle/AGP 对象 -> 只读 DTO；
- DTO -> Protobuf message；
- Protobuf message -> Android 侧 DTO/cache；
- DTO 不保留 AGP/Gradle 类引用，也不暴露可变内部对象。

对于大项目，继续以“项目选择 + 按需查询”为默认路径。需要一次性展示项目结构时，采用分页/批次快照，并允许按需取消。

## 5. 不把 Gradle Daemon 混入本设计

该架构只覆盖：

`Android App Process <-> Tooling JVM Child Process`

Gradle Tooling API 仍由 Tooling JVM 调用 `GradleConnector`、`ProjectConnection`、`BuildLauncher` 等 API 与 Gradle Daemon 交互。此处换成 Protobuf 不意味着替换 Gradle 自己的 daemon protocol。

## 6. 失败与恢复边界

- 管道 EOF：标记 peer disconnected，完成/失败所有 pending request，关闭 writer/reader。
- 帧长度非法或超过限制：立即终止会话，记录协议错误；不可尝试跳过未知字节继续解析。
- Protobuf 无法解析：记录 frame sequence 与错误类型，按协议错误关闭会话。
- 子进程异常退出：由 ToolingServerRunner 处理退出码、stderr、pending 请求失败。
- 单个业务请求失败：返回明确的错误响应，不应导致整个传输会话退出。
- 文件传输取消：停止读取、释放 buffer、清理临时文件；不得影响其他控制消息。
