# 消息协议与分帧规范

## 1. 协议形态

采用 Protobuf 定义稳定消息契约，使用现有 stdin/stdout 管道承载。它不是 gRPC，也不引入 gRPC service/runtime。

消息类别：
- Command：初始化、项目选择、查询、执行任务、取消、关闭。
- Response：命令结果或结构化错误。
- Event：日志、构建输出、进度、构建成功/失败。
- ModelBatch：项目/模块/变体/依赖等 DTO 批次。
- FileControl：文件引用、文件块开始/块数据/完成/取消。

request_id 用于将 Response 与 Command 关联；event 不要求 request_id。sequence_id 用于诊断、顺序检查和丢帧检测。session_id 用于区分每次子进程会话。

## 2. 帧格式

每个帧由 4 字节无符号大端长度 + Protobuf Envelope payload 构成：

`[uint32_be payload_length][payload bytes]`

长度只表示 Protobuf payload 字节数，不包含 4 字节头。接收端必须：
1. 精确读取 4 字节头；EOF 且尚未读到任何头字节表示正常关闭。
2. 校验长度大于 0 且不超过对应帧类别的硬限制。
3. 精确读取 payload_length 字节；中途 EOF 视为截断帧。
4. 解析 Envelope，验证 protocol_version、session_id、sequence_id 和 oneof。
5. 将消息交给 dispatcher，不在 reader 线程执行 Gradle/文件 I/O。

建议初始限制（需在设备测试后调整）：
- 控制/响应/事件帧：最大 4 MiB。
- 模型批次帧：最大 4 MiB；更大的模型结果拆成多批。
- 文件块帧：单块目标 256 KiB，允许实验 64 KiB / 256 KiB / 1 MiB；硬限制 1 MiB。
- 任意帧不得触发不受限分配；长度检查必须先于 payload buffer 分配。

## 3. Envelope 概念结构

以下是逻辑 schema 草图，不是已经加入仓库或生成代码的 proto 文件：

```proto
syntax = "proto3";
package zerostudio.tooling.transport.v1;

message Envelope {
  uint32 protocol_version = 1;
  bytes session_id = 2;
  uint64 sequence_id = 3;
  uint64 request_id = 4;

  oneof body {
    Hello hello = 10;
    InitializeProject initialize_project = 11;
    SelectProject select_project = 12;
    ExecuteBuild execute_build = 13;
    CancelBuild cancel_build = 14;
    Shutdown shutdown = 15;

    CommandResult command_result = 20;
    TransportError transport_error = 21;

    BuildLog build_log = 30;
    BuildProgress build_progress = 31;
    BuildFinished build_finished = 32;

    ModelBatch model_batch = 40;
    FileReference file_reference = 50;
    FileTransferStart file_transfer_start = 51;
    FileTransferChunk file_transfer_chunk = 52;
    FileTransferFinish file_transfer_finish = 53;
    FileTransferCancel file_transfer_cancel = 54;
  }
}
```

真实 schema 必须为每个消息字段定义编号、类型、是否可选、默认值语义、兼容策略和敏感信息规则。字段编号发布后不得复用；删除字段使用 `reserved` 保留编号和名称。

## 4. 握手

子进程启动后先发送 Hello：
- protocol_version；
- 支持的 feature flags；
- 最大控制帧大小；
- 最大文件块大小；
- session_id；
- build/tooling 版本信息。

父进程返回 HelloAck：
- 选定的协议版本；
- 协商后的帧大小；
- 文件引用能力；
- 是否允许文件块传输。

版本不兼容必须显式失败，不得静默按未知字段继续执行危险操作。

## 5. 写入与并发

- 每个方向一个专用 writer。
- writer 从有界队列取消息，先编码，再写 length + payload，最后 flush 策略按消息类型合并；不要求每条小消息都强制 flush。
- BuildProgress / BuildLog 可批量合并或限频，不能无限堆积。
- Cancel、Shutdown、Error 等控制消息高于大文件块的发送优先级，但不得破坏同一文件流的块序。
- 每个请求都有超时/取消状态；超时后清理 pending map。
- writer 队列按字节数限额，不只按消息数量限额。

## 6. 协议安全与健壮性

- 所有字符串、列表、嵌套消息都受总帧大小约束。
- 文件路径必须通过文件访问策略校验，不接受任意路径写入。
- 任何异常消息不能导致无界递归、无界批次或无限重试。
- 处理未知 oneof/字段时按版本和消息类型规则决定忽略或拒绝；未知命令不得执行。
- stdout 禁止日志。所有诊断写入 stderr，并附 session_id、sequence_id、request_id。
