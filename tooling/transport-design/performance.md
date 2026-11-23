# 性能模型、吞吐口径与基准计划

## 1. 先回答“每秒多少”

当前没有在目标 Android 设备上运行过同等负载的 JSON-RPC 与 Protobuf A/B 基准，因此**不能给出已验证的 MiB/s 数值**。任何把 50、200 或 500 MiB/s 写成当前实现实测值的说法都是不成立的。

必须分开回答：

| 场景 | 正确指标 | 是否计入 IPC 文件字节 |
|---|---|---|
| FileReference 元数据 | references/s、P50/P95 resolve/open latency、bytes/reference | 否 |
| 小型模型 DTO | requests/s、items/s、P95 latency、有效 payload MiB/s | 是，只有 DTO payload |
| 大型模型批次 | payload MiB/s、完整快照耗时、峰值内存、GC | 是 |
| FileTransferChunk | 实际 payload MiB/s、CPU、内存、温升、错误率 | 是 |
| 双方共享路径的文件读写 | 文件系统读/写 MiB/s | 否，不是 IPC 传输 |

### 吞吐时间换算

| 有效吞吐 | 传输 100 MiB | 传输 1 GiB |
|---:|---:|---:|
| 50 MiB/s | 2.00 s | 20.48 s |
| 100 MiB/s | 1.00 s | 10.24 s |
| 200 MiB/s | 0.50 s | 5.12 s |
| 500 MiB/s | 0.20 s | 2.05 s |

这是算术换算，不是对 ZeroStudio 设备表现的预测。

## 2. 不同路径的性能解释

### A. FileReference

消息本身很小，成本主要来自编码/解码、管道调度、路径 canonicalize、权限/存在性校验和 open。它不应按 MiB/s 宣称性能。对同一项目目录的共享引用，文件内容完全不经过 IPC。

### B. Protobuf 模型 DTO

Protobuf 通常比 JSON 文本减少字段名重复、文本数字转换和通用反射开销，但端到端结果取决于 DTO 投影、字段数量、字符串占比、批次大小、分配和管道写入。不能从 Protobuf 格式本身推导设备上的吞吐。

模型查询通常应优先减少不必要的字段与对象数量；“少传数据”往往比“换更快编码器”收益更大。

### C. 文件字节经进程管道传输

实际速度受 Android/Linux pipe 缓冲、内核拷贝、用户态 buffer、Protobuf framing、线程调度、CPU/内存带宽、文件读写设备和热降频共同限制。Protobuf chunk 的吞吐不是闪存顺序读写的理论速度。

50 MiB/s 适合作为首轮最低目标候选；500 MiB/s 应作为高性能设备上的 stretch goal，而非跨设备保证。若实测低于目标，应通过分阶段耗时定位瓶颈，不得盲目叠加 IPC 机制。

## 3. A/B 测试组

必须比较：
1. 当前 JSON-RPC/LSP4J + Gson 基线；
2. 同样 DTO、同样业务路径的 Protobuf + 4-byte length frame；
3. FileReference 路径（不传内容）；
4. FileTransferChunk（真实传字节）；
5. 文件系统直接读取/写入（作为存储路径参照，不作为 IPC 结果）。

保持数据内容、模型对象数量、设备、热状态、线程数和文件缓存状态一致。分别报告冷缓存与热缓存，不要混合。

## 4. 测试数据集

### 模型负载
- 小：1 KiB、10 KiB、64 KiB；
- 中：256 KiB、1 MiB、4 MiB；
- 大：16 MiB、64 MiB；
- 真实工程：从实际 Android/Gradle 项目提取代表性项目/模块/variant/dependency DTO，记录 item 数和字段分布。

### 文件负载
- 64 KiB、256 KiB、1 MiB、4 MiB、16 MiB、64 MiB、100 MiB、256 MiB、512 MiB、1 GiB。
- 对每个大小测 FileReference、pipe transfer、shared-path direct read/write。
- 100 MiB 与 512 MiB 额外验证峰值内存和内存压力降级。

## 5. 测试指标

每个 case 至少记录：
- payload_bytes、wire_bytes、compression（如无压缩明确写 none）；
- elapsed_ms、throughput_mib_s；
- P50/P95/P99 latency；
- encode/decode/projector/read/write 时间；
- CPU time、线程数、context switch（可获得时）；
- Java heap、native memory、RSS、分配速率；
- GC 次数、总暂停、最长暂停；
- 错误/重试/取消次数；
- 电池温度或设备温度、持续负载前后频率/降频状态。

吞吐计算：
`throughput_mib_s = payload_bytes / 1048576 / elapsed_seconds`

文件引用测试不得把被引用文件大小计入 payload_bytes。

## 6. 测试运行方法

- 固定设备型号、Android 版本、电源状态和环境温度。
- 每组先 warm-up，再运行至少 10 次；报告 median、P95 和范围。
- 另做 5–10 分钟持续传输测试，观察温升和热降频。
- 单独测试单向与双向并发；双向负载不应导致控制消息被文件数据饿死。
- 使用真实构建服务启动方式和真实子进程，而不只做同 JVM ByteArray benchmark。
- 每个测试保留原始 CSV/JSON、设备信息、commit SHA 和测试脚本版本。

## 7. 初始验收门槛（提案，需设备验证）

- 控制消息：无明显 UI 阻塞；P95 延迟单独记录。
- 大模型快照：与 JSON 基线比较完整端到端耗时、峰值内存和 GC，不能只比较编码速度。
- FileReference：不传输文件内容；验证路径安全、替换检测和权限错误。
- 文件内容：首轮以持续 50 MiB/s 为目标候选；高端设备尝试 200–500 MiB/s，最终按设备分档报告。
- 内存：传输层在途 buffer 目标 8–16 MiB，硬上限 32 MiB；任何队列都必须有上限。
- 热稳定性：持续测试不得出现因传输线程/缓冲造成的明显 UI 卡顿或异常温升；报告实际测量，不用主观判断代替数据。

未达到门槛时先定位是 Protobuf encode/decode、pipe write/read、文件 I/O、buffer copy 还是调度瓶颈，再决定是否调整 chunk 和队列。
