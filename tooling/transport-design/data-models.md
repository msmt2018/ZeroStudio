# AGP / Gradle 数据模型传输方案

## 1. 不序列化原始模型对象

AGP 与 Gradle 模型对象不是稳定的跨进程 DTO：
- 它们可能引用大量其他对象，形成高扇出对象图；
- 可能包含 Gradle/AGP 内部实现类型、懒加载对象或不适合序列化的实例；
- 对象图序列化会增加遍历、反射、分配、GC 和版本耦合成本；
- 反序列化后也无法得到原始 Gradle 对象的有效行为语义。

因此，原始对象始终留在 Tooling JVM。只传输经过筛选的值对象（DTO）。

## 2. 建议的数据域

按实际调用点逐项映射，不代表当前仓库已经全部具备这些字段：

| DTO 域 | 代表字段 | 默认策略 |
|---|---|---|
| ProjectMetadata | project_id、path、name、project_dir_ref、project_type | 项目选择后按需查询 |
| ModuleMetadata | module_id、project_id、path、name、build_file_ref | 分批 |
| AndroidVariant | variant_id、module_id、name、build_type、flavors、application_id | 按需/批量 |
| SourceSetMetadata | source_set_id、variant_id、source_dirs、resource_dirs、manifest_ref | 分批；目录列表可分页 |
| DependencyMetadata | dependency_id、configuration、group、artifact、version、variant_id | 分页/分批 |
| ArtifactMetadata | artifact_id、kind、file_ref、size、digest | 文件优先引用 |
| CompilerOptions | language、source/target compatibility、flags | 按需 |
| TaskMetadata | task_path、group、description、outcome | 按需；进度实时事件单独发送 |

DTO 使用稳定 ID 关联，不要在每个子对象重复嵌入完整父对象。重复字符串可在同一快照中使用 string table，但只有基准证明收益明显时再增加复杂度。

## 3. 快照和增量

默认查询：
1. 父进程请求项目选择/查询。
2. 子进程只读取对应项目当前需要的数据。
3. 小结果一次响应；大结果按 ModelBatch 发送。
4. 每个快照包含 snapshot_id、batch_index、is_last_batch、item_count。
5. 接收端完整接收后再原子替换对应缓存，避免 UI 读到半套模型。
6. 请求取消或子进程退出时，丢弃未完成快照。

增量更新只有在现有调用场景明确需要时才设计；不为了“实时”而建立全项目对象镜像。

## 4. 批次尺寸

初始实验范围：
- 目标 payload：128–512 KiB；
- 控制帧最大 4 MiB；
- 模型列表超过批次目标时拆分；
- 每个批次按字节数控制，而不是只按 item 数；
- 单项特别大时拆分字段或拒绝不合理的模型记录。

128–512 KiB 是待测起点，不是最终常量。模型字段分布、Android 设备 CPU、管道吞吐和 GC 情况决定最优值。

## 5. 模型传输的性能口径

模型传输至少记录：
- DTO 投影时间；
- Protobuf encode 时间；
- 写管道时间；
- 读管道时间；
- Protobuf decode 时间；
- DTO/cache 应用时间；
- 总字节数、item 数、批次数；
- Java heap 峰值、分配速率、GC 次数/暂停；
- CPU 时间与 P50/P95/P99 延迟。

只记录“protobuf encode 很快”不能代表端到端快；同样，单纯统计管道字节吞吐也不能代表模型查询体验。

## 6. 当前设计与现有代码的连续性

当前 `IProjectQueries.kt` 的注释已经明确指出：整体传输大型 project model 会造成性能和内存问题，因此先选择项目，再按需查询选中项目。新协议应保留这个设计原则，而不是改成全量 AGP/Gradle 模型 dump。新方案的改进点是将每次请求/响应和必要的 DTO 批次从 JSON 文本切换为紧凑二进制消息。
