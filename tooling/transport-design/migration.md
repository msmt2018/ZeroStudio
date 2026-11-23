# 迁移阶段、验收与回滚

## 阶段 0：接口与数据清单

在修改现有协议前，盘点：
- IToolingApiClient / IToolingApiServer 的全部方法；
- IProject、IProjectQueries、IGradleProject、IAndroidProject、IJavaProject 等 delegate 方法；
- messages、params、result、events 下的所有传输 DTO；
- CompletableFuture 请求、notification/event 回调、取消与 shutdown 路径；
- File、Path、URI、数组、集合、嵌套对象等字段；
- Android 端与 Tooling JVM 端各自的实现类和调用点。

每个接口记录：调用方向、同步/异步、请求/响应类型、最大预期 payload、是否可取消、是否幂等、错误语义、是否涉及文件。

## 阶段 1：现状基线

在不改现有协议的情况下，增加或准备基准 harness，测 JSON-RPC/Gson：
- 启动与握手时间；
- 小消息延迟；
- 模型快照 encode/decode 与总耗时；
- 文件引用往返；
- 大文件实际传输（若当前系统有此用例）；
- CPU、heap、RSS、GC 和温升。

## 阶段 2：协议契约

- 在独立目录定义 Protobuf schema。
- 固定字段编号、保留策略、协议版本和错误码。
- 为所有现有 API 定义明确消息映射。
- 明确最大帧长度、模型 batch 上限、chunk 上限和字节窗口。
- 生成代码版本必须与仓库 Kotlin/Gradle/AGP/JDK 工具链兼容；先核对现有插件管理方式再选插件版本。

## 阶段 3：独立传输原型

只实现最小双向 pipe prototype：
- parent writer -> child stdin；
- child writer -> parent stdout；
- reader/writer 分离；
- length-prefixed frame；
- Protobuf encode/decode；
- Hello 握手；
- 一个 request/response；
- 一个 event；
- EOF、截断帧、超长帧、取消和子进程退出测试。

这一步不得同时改造所有 Gradle/AGP 模型业务逻辑。

## 阶段 4：双协议影子验证

保留旧协议可运行路径，对相同查询执行新旧协议对比：
- DTO 字段值一致；
- 事件顺序和构建结果一致；
- 取消/关闭语义一致；
- 文件引用解析结果一致；
- 性能与内存可重复。

若影子验证无法做到同一请求双跑，则建立固定录制数据集进行 codec 与 framing 对照，并另做真实集成测试。

## 阶段 5：按业务域迁移

建议顺序：
1. 握手、metadata、初始化、shutdown；
2. 低频控制命令与错误响应；
3. build arguments、task execution、cancel；
4. build logs/progress/success/failure events；
5. project selection 与小型 project DTO；
6. Android/Gradle/Java 查询 DTO；
7. 批量模型快照；
8. FileReference；
9. 可选 FileTransferChunk。

每个阶段都必须有单元测试、进程集成测试和回归结果。不要先删除旧接口再补齐新协议。

## 阶段 6：切换与删除旧协议

仅当以下条件全部满足才切换默认：
- 接口映射 100%；
- 新旧结果一致；
- 启动、异常退出、取消、shutdown、超长帧和截断帧测试通过；
- 大模型压力测试不出现无界内存增长；
- 文件引用路径安全测试通过；
- 目标设备 A/B 数据证明新方案端到端收益；
- 构建工作流通过。

切换后保留一个版本周期的回滚开关/兼容分支。稳定后再删除 LSP4J JSON-RPC transport 与 Gson transport-only 依赖；若其他模块仍使用它们，不得误删共享依赖。

## 回滚

遇到协议不兼容、构建失败、数据不一致、进程退出无法恢复、内存增长或温升异常时：
- 默认切回旧 transport；
- 保留诊断日志与失败样本；
- 不删除旧实现；
- 不通过跳过测试、降低校验或隐藏异常来“修复”。
