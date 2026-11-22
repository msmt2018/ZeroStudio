# 文件引用与文件内容传输

## 1. 首选 FileReference，而不是序列化 java.io.File

`java.io.File` 本质上是路径封装，不是文件内容，也不是可跨进程共享的文件句柄。不要让 Gson/反射尝试序列化 JDK File 的内部字段；也不要将 File 当成大文件内容传输的代理对象。

父进程与 Tooling JVM 子进程通常运行在同一应用 UID / 沙箱内，且项目目录和 IDE 私有目录由同一应用访问。在该条件成立时，两端可直接访问相同路径，优先发送文件引用。

## 2. FileReference 字段

建议 schema：
- file_id：会话内/项目内稳定逻辑 ID；
- canonical_path 或受控 root_id + relative_path；
- size_bytes；
- last_modified_epoch_ms；
- file_generation / content_version；
- optional digest_algorithm + digest；
- access_mode：READ_ONLY / READ_WRITE；
- purpose：SOURCE / BUILD_FILE / ARTIFACT / TEMP / OTHER。

优先使用 root_id + relative_path，减少暴露任意绝对路径的机会。若传绝对路径，接收端必须 canonicalize 并校验其位于允许根目录下。

## 3. 文件引用校验

接收端打开文件前检查：
1. root_id 是否属于已授权根；
2. canonical path 是否仍位于该根目录；
3. 符号链接解析后是否越界；
4. 文件是否存在、类型是否符合预期；
5. size 与 generation/mtime 是否匹配；
6. 打开后再次读取属性，防止校验与打开之间发生替换；
7. 写操作使用临时文件 + 原子 rename（文件系统支持时）。

mtime + size 是快速变化检测，不是强完整性校验。对构建输入、缓存内容或跨信任边界数据，按需使用 digest；不要为每次只读引用都无条件计算大文件 SHA-256。

## 4. FileReference 性能

FileReference 只发送少量元数据。其成本是一次小消息的编码、管道往返（如果需要确认）和接收端路径校验/打开文件。它的吞吐应按 references/s 与 P95 open/resolve latency 衡量，不应写成 MiB/s。

如果双方共享文件系统，文件本身没有经过 IPC，因此：
- 传输 1 GiB 文件引用并不意味着走管道搬运 1 GiB；
- 读写速度由实际文件系统、闪存、缓存、文件大小和访问模式决定；
- 50–500 MiB/s 应当针对文件实际读写或内容传输测试，而不是针对 FileReference 消息。

## 5. 何时需要传输文件字节

只有下列情况才使用 FileTransferChunk：
- 接收端不能访问源文件所在目录；
- 文件来自不可共享的内容提供器/临时流；
- 文件需要被复制到另一侧专属工作区；
- 业务明确要求发送一份不可变内容快照。

即使需要复制，也优先评估源文件是否可先落到双方可访问的 IDE 私有临时目录，再发送引用。

## 6. 分块传输规范

- 默认 chunk 实验值：256 KiB；候选值 64 KiB、256 KiB、1 MiB。
- 单块硬上限：1 MiB。
- 每个 transfer 有 transfer_id、file_id、offset、chunk_index、chunk_length、optional digest。
- 发送端使用固定数量的复用 buffer；不得对整文件调用 readBytes()。
- 接收端顺序写入临时文件；校验 offset 连续性和长度。
- 使用 byte-window 限制未确认数据，例如初始 4 MiB；按实测在 1–16 MiB 范围调节。
- 队列满时暂停生产者读取，实现背压。
- 取消/异常时关闭流、释放 buffer、清理不完整临时文件。
- 完成后校验文件长度；有完整性要求时校验 digest，再原子移动到目标路径。

## 7. Buffer 与内存策略

初始实现应优先使用小型复用 byte[] / ByteBuffer，避免每块重新分配。DirectByteBuffer 并不自动更快，且会占用 native memory；只有基准证明其有益时再启用。

传输层内存上限必须显式计算：
`total_buffer_budget = reader_buffer + writer_buffer + in_flight_window + pending_batches`

建议首轮基准把传输层常驻/在途缓冲目标控制在 8–16 MiB，硬上限 32 MiB；这只是传输层预算，不包含 Gradle/AGP 本身的 heap。不得让模型批次、文件块队列无限增长。
