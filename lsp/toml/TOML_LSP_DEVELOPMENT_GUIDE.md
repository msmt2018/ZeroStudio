# TOML LSP 源码能力评估与开发指导

> 评估对象：`lsp/toml` 模块及其直接关联的 TOML 编辑器解析、词法和 Tree-sitter 实现  
> 评估基线：`main` 分支，评估时仓库 HEAD 为 `71eb2da9c03f0e10485da89cd428ef9ffd6f965b`  
> 文档性质：源码现状评估 + 后续开发规范。本文严格区分现有实现、部分实现和规划能力，不代表规划功能已经交付。

## 1. 总体结论

当前 TOML 支持已经有可工作的 LSP 接入骨架，但距离完整 TOML 语言服务仍有明显差距。现状可概括为：

- **接入层已建立**：`TomlServer` 实现项目内部 `ILanguageServer`，通过 server ID `toml` 被编辑器侧 `TomlLanguage` 发现。
- **基础功能已有实现**：文档缓存、基础补全、基础诊断、符号大纲、全文格式化、悬停、定义定位、重命名、折叠、全文语义 Token 和 URL 文档链接均有代码入口。
- **解析与语义能力不足**：多数功能由正则和逐行字符串处理驱动，不是由统一、完整的 TOML AST 驱动；复杂语法和错误恢复能力有限。
- **解析体系存在分散**：仓库同时存在 Tree-sitter TOML、ANTLR TOML Lexer/Parser、IntelliJ 风格 Flex/BNF 及 `toml.jar`，以及独立的编辑器词法补全实现。需要明确职责，避免各套解析结果互相矛盾。
- **Schema 与生态能力基本缺失**：没有看到 JSON Schema 关联、校验、Schema 驱动补全，也没有 Cargo/Python 包注册表数据源实现。
- **协议与工程能力尚不完整**：增量同步、版本一致性、范围格式化、增量语义 Token、引用查找、重命名安全性和自动化测试都需要补齐。

建议将该模块定位为：**以 TOML 语法树为基础、以 LSP 功能引擎为中层、以 JSON Schema 和生态适配器为上层的进程内 TOML 语言服务**。不建议继续在当前正则逻辑上不断叠加特殊规则。

## 2. 当前源码结构与职责

### 2.1 LSP 模块

| 文件 | 当前职责 | 评估 |
|---|---|---|
| `TomlServer.kt` | LSP 服务器入口、客户端连接、文档事件及功能分发 | 已有入口；多个功能委托给 FeatureEngine/Diagnostics |
| `server/TomlTextDocumentService.kt` | open/change/close 文档事件 | 仅写入/移除全文缓存；没有真正的增量编辑应用 |
| `server/TomlDocumentCache.kt` | 以 Path 为键的并发文档缓存 | 线程安全容器，但无文档版本、URI 规范化和生命周期隔离 |
| `server/TomlFeatureEngine.kt` | 补全、定义、重命名、符号、折叠、链接、格式化、悬停 | 功能集中，但核心算法以正则和行扫描为主 |
| `server/TomlDiagnostics.kt` | 基础语法诊断 | 逐行启发式检查，不是 TOML 解析器诊断 |
| `server/TomlSemanticTokens.kt` | 将词法 Token 编码为 LSP SemanticTokens | 有全文 Token 编码；定位和 Token 分类仍需验证 |
| `server/TokenMapping.kt` | IntelliJ ElementType 到语义 Token 类型的映射 | 依赖 `toml.jar` 内部 Token 类型 |
| `server/SemanticHighlighting.kt` | 另一套语义高亮计算 | 与 TomlSemanticTokens 存在职责重叠 |
| `server/TomlDocumentation.kt` | 通用 TOML 和 Gradle Version Catalog 的硬编码悬停说明 | 仅覆盖少量符号/表名，不是字段 Schema 文档 |
| `server/TomlWorkspaceService.kt` | 工作区变更入口 | 当前只记录日志，没有配置发现或索引 |
| `server/Logger.kt` | Android Log 包装 | 基础设施 |

### 2.2 解析与编辑器侧关联实现

| 路径 | 当前用途 | 评估 |
|---|---|---|
| `lsp/toml/TomlLexer.flex` | IntelliJ 风格词法规则源文件 | 规则覆盖基础 Token；需要和实际打包的 `libs/toml.jar` 生成物核对 |
| `lsp/toml/TomlParser.bnf` | IntelliJ 风格语法规则源文件 | 定义 Table、Array、InlineTable、KeyValue 等语法；需要验证生成解析器与该文件同步 |
| `lsp/toml/libs/toml.jar` | LSP 使用的解析/词法类依赖 | 当前 TokenMapping 和 Lexer 直接依赖它；需记录来源、版本、许可证和可复现构建方式 |
| `editor/tree-sitter-ndk/toml` | Tree-sitter TOML Native parser/scanner 与 JNI 包装 | 编辑器 TreeSitterLanguage 使用的解析基础 |
| `editor/impl/.../treesitter/TomlLanguage.kt` | 将 Tree-sitter TOML 接入编辑器并关联 TomlServer | LSP 与编辑器语言类型通过 `toml` ID 关联 |
| `editor/lexers/src/main/antlr/toml` | ANTLR TOML Lexer/Parser 语法 | 与 Tree-sitter、Flex/BNF 并存，必须明确用途，避免作为第四套业务语义来源 |
| `language-lexer/toml/.../TomlAnalyzer.kt` | Sora 增量词法高亮 | 旧/独立编辑器路径的词法高亮实现 |
| `language-lexer/toml/.../TomlAutoComplete.kt` | 基础值和常见 Cargo 表补全 | 静态列表补全，不具备上下文语义 |
| `language-lexer/toml/.../TomlLanguageImpl.kt` | IDELanguage 驱动类 | 与 TreeSitterLanguage 路径并存，需要确认实际注册和使用范围 |

**重要架构要求：** 编辑器高亮、LSP 诊断、补全、符号、格式化和重构应尽可能消费同一套语法模型。可以保留不同前端（例如 Tree-sitter 用于增量编辑解析），但不能让每个功能各自用正则重新猜测 TOML 结构。

## 3. 当前能力盘点

状态定义：
- **已有**：源码中存在明确实现入口，但不代表所有边界行为已通过测试。
- **部分**：仅覆盖简单场景，存在明显功能或正确性限制。
- **未实现**：接口返回空值、没有业务逻辑，或没有对应能力实现。

| 能力 | 当前状态 | 源码依据与限制 |
|---|---|---|
| LSP 服务器接入 | 已有 | `TomlServer` 实现 `ILanguageServer`，ID 为 `toml` |
| 文档打开/关闭缓存 | 已有 | `TomlTextDocumentService` + `TomlDocumentCache` |
| 文档变更同步 | 部分 | 只取最后一个 change 的 text 并整体覆盖缓存；不应用 range/rangeLength 增量编辑，也没有版本校验 |
| 基础补全 | 部分 | 补 true/false 和文档已有键；不理解当前 Table、Key 路径、值类型、枚举或 Schema |
| Cargo 常见表补全 | 部分 | 编辑器侧 `TomlAutoComplete` 使用固定表名列表；不是注册表或项目语义补全 |
| 语法诊断 | 部分 | 检查等号数量、空 Key/Value、未闭合表头、简单重复 Key；不验证完整 TOML 语法 |
| 重复 Key 诊断 | 部分 | 以字符串拼接表前缀；无法可靠处理 dotted key、quoted key、inline table、array-of-tables 和作用域规则 |
| 文档符号 | 部分 | 扫描表头和 KeyValue；只识别简单表头正则，层级 Key/数组表结构表达不完整 |
| 全文格式化 | 部分 | 简单键值正则并固定添加四空格；不保留/处理所有 TOML 结构语义，可能破坏根级布局或原缩进 |
| 范围格式化 | 未实现 | 服务器只实现全文 `formatCode` |
| Folding Ranges | 部分 | 主要按简单 Table 表头分段；不支持数组、多行字符串、嵌套数组/内联表等完整折叠模型 |
| Semantic Tokens Full | 部分 | 有全文编码；Token 类型映射、字符单位、跨行 Token 和错误恢复需要测试 |
| Semantic Tokens Range/Delta | 未实现 | `TomlServer` 返回空结果 |
| Hover | 部分 | 硬编码 TOML 符号及少量 `libs.versions.toml` 表文档；不提供字段级 Schema 文档 |
| Definition | 部分 | 仅按当前单词查找简单 `key =` 行；没有表路径、作用域和跨文件解析 |
| References | 未实现 | 返回空列表 |
| Rename | 部分且不安全 | 直接替换文档中所有相同单词 Token，可能误改注释、字符串、值或不同 Table 下同名 Key |
| Document Links | 部分 | 仅匹配 URL；不解析字段语义，不识别本地路径，不验证目标 |
| Workspace Symbols | 未实现 | 返回空结果 |
| Code Actions | 未实现 | 没有修复/重构动作实现 |
| Signature Help | 未实现/不适用 | 返回空签名列表 |
| Inlay Hints | 未实现 | 返回空列表 |
| Code Lens | 未实现 | 返回空列表 |
| Call/Type Hierarchy | 未实现/不适用 | 返回空列表 |
| JSON Schema 关联 | 未实现 | 未发现 Schema 加载、缓存、匹配或配置实现 |
| Schema Validation | 未实现 | 未发现 required/type/enum/range/pattern 等验证 |
| Cargo/PyProject 深度支持 | 很有限 | 有静态 Cargo 表补全和 Gradle Version Catalog 悬停说明；无生态模型、包索引或外部数据源 |

## 4. 已发现的关键问题

### 4.1 正则文本扫描不能作为 TOML 语义基础

TOML 的 Key 可以是裸键、基本字符串键、字面字符串键和 dotted key；值可包含数组、内联表、多行字符串、日期时间等。当前 `TomlFeatureEngine` 的主要表达式：

- `wordRegex` 只覆盖 ASCII 裸单词字符及点、连字符、下划线。
- `kvRegex` 只覆盖简单裸键形式。
- `tableRegex` 不能正确表达完整的数组表结构和所有合法 Key 写法。
- `getWordAt` 无法准确定位引号 Key、转义内容和复杂值中的语义 Token。

后果是补全、定义、符号、重命名和格式化之间没有稳定的语义身份，后续修补正则无法可靠解决。

### 4.2 诊断逻辑会误判合法文本，也会漏掉真实错误

`TomlDiagnostics` 对整行直接统计 `=`。但等号也可能出现在字符串值中，例如：

```toml
endpoint = "https://example.test/a?x=y"
```

因此“多个等号”并不等于语法错误。当前诊断也没有解析数组、内联表、字符串转义、日期时间范围和 TOML 版本规则。重复 Key 规则同样没有按 TOML 的完整表和数组表语义建立作用域。

**指导：** 语法错误必须来自 Parser/Tree-sitter 的错误节点或错误恢复结果；重复定义、键路径冲突等结构错误应在 AST/语义模型层实现，不得以字符计数代替解析。

### 4.3 重命名和定义跳转缺少符号身份

当前定义查找会将同名简单键的所有声明都作为定义候选；重命名会把全文相同词串都当成同一个符号。这不满足安全重构的要求。

**指导：** 先构建带文档 URI、Table 路径、Key 路径、节点类型和 SourceRange 的符号索引，再实现 Definition/References/Rename。无法确认语义身份时，宁可不提供重命名，也不能扩大替换范围。

### 4.4 文档同步模型不完整

`didChange` 只使用 `contentChanges.lastOrNull()?.text`。如果上游发送的是 LSP 增量变更（带 range），该逻辑不能把变更应用到旧文档；多个变更也不能简单只取最后一个。缓存没有 document version，异步诊断可能覆盖新版本结果。

**指导：**
1. 先核对项目内部 `DidChangeTextDocumentParams` 和 `ContentChange` 的真实字段与协议约定。
2. 明确当前客户端发送的是全文同步还是增量同步。
3. 若使用 Incremental，按协议顺序把每个 change 应用到当前文本。
4. 为缓存保存 version，异步任务发布结果前验证版本仍然匹配。
5. 文档关闭、服务器关闭和项目切换时按文档/服务器实例清理，不要让并行工作区互相清空状态。

### 4.5 语义 Token 有重复实现与坐标风险

`SemanticHighlighting` 与 `TomlSemanticTokens` 都独立扫描 Lexer 并生成 Token 数据。前者的位置查找是线性遍历，后者使用二分查找；两者存在维护重复。还需要确认 LSP Position/Token length 使用 UTF-16 code unit，而 Lexer offset 是否使用 Kotlin/JVM UTF-16 索引，并覆盖 CRLF、补充平面字符和多行字符串测试。

**指导：** 只保留一个 Semantic Token 计算管线；Token 类型 legend 与客户端声明保持一致；按 LSP 编码规则计算 delta；后续再实现 range/delta。

### 4.6 解析依赖来源不透明

`lsp/toml/libs/toml.jar` 是 LSP 当前依赖，但仓库内同时存在 Flex/BNF 规则和 Tree-sitter/ANTLR 语法。必须确认 JAR 的准确上游来源、版本、许可证、生成方式及规则同步关系。不能只修改某一份语法文件就假定运行时解析器已同步更新。

## 5. 推荐目标架构

建议采用分层结构，避免把解析、协议、生态逻辑继续堆入 `TomlServer` 或单个 FeatureEngine。

```text
TomlServer (ILanguageServer adapter)
  ├── DocumentManager
  │     ├── VersionedDocumentStore
  │     └── TextChangeApplier
  ├── Parser Layer
  │     ├── TomlParserFacade
  │     ├── Incremental Parse Tree
  │     └── SourceRange / Error Nodes
  ├── Semantic Model
  │     ├── Table & Array-Of-Tables Model
  │     ├── Key Path / Value Type
  │     └── Symbol Index
  ├── LSP Feature Providers
  │     ├── Diagnostics
  │     ├── Completion / Hover
  │     ├── Definition / References / Rename
  │     ├── Document Symbols / Folding
  │     ├── Formatting / Code Actions
  │     ├── Semantic Tokens
  │     └── Document Links
  ├── Schema Layer
  │     ├── Schema Association
  │     ├── Schema Store / Cache
  │     └── JSON Schema Validator
  └── Ecosystem Providers
        ├── Cargo.toml
        ├── pyproject.toml
        └── Gradle Version Catalog
```

### 5.1 Parser 选型原则

仓库已经有 Tree-sitter TOML Native parser，并且编辑器的 `TomlLanguage` 继承 `TreeSitterLanguage`。因此优先评估复用现有 Tree-sitter 解析树的可行性：

- 确认 JNI API 是否能暴露节点类型、子节点、字段名、错误节点和 Byte/Point 范围。
- 确认 parser/tree 生命周期和并发安全；每个打开文档应有独立解析状态。
- 把 LSP UTF-16 Position 与 Tree-sitter Byte offset/row-column 做严格双向映射。
- 增量变更时使用 Tree-sitter edit API 更新旧树，再重新解析；若 JNI 封装目前不支持，应先补齐底层 API，而不是先开发依赖 AST 的上层功能。
- 对 Tree-sitter grammar 的 TOML 版本、上游提交和测试语料建立可追溯记录。

若 Tree-sitter JNI 的节点 API 暂时不足，可评估 `toml.jar` 中解析器作为过渡实现，但必须建立统一的 `TomlParserFacade`，使上层功能不直接依赖具体解析器。不要同时让 Tree-sitter、ANTLR、Flex/BNF 四套解析器分别驱动语义功能。

### 5.2 文档与坐标模型

每个打开文档至少维护：

- URI/规范化路径、当前文本、document version。
- 当前解析树及上次解析版本。
- 行起始偏移索引（UTF-16 与 UTF-8/Byte 映射）。
- 解析错误节点和语义诊断。
- Table/Key 符号索引。
- 关联 Schema 和生态 Provider 上下文。

所有 LSP Range 都从统一的 SourceRange 转换，不允许每个 Provider 自己用 `String.indexOf` 或重新扫描行文本。

## 6. 分阶段功能开发路线

开发顺序必须遵循“底层解析与文档模型优先，上层 LSP 功能随后”的依赖关系。

### Phase 0：基线核验与测试框架

1. 核验 `toml.jar` 来源、许可证、版本和生成流程。
2. 核验 Tree-sitter TOML grammar 的来源/版本，确认 JNI 节点查询和增量编辑 API。
3. 检查 `ILanguageServer` 的同步模型、Position 编码和客户端能力声明。
4. 建立 TOML 1.0/1.1（按项目目标版本选定）语法样例、错误样例和回归测试。
5. 记录现有功能行为，避免重构时静默回归。

**验收：** 能稳定解析合法 TOML；错误输入不会崩溃；每个节点的 Range 可与编辑器位置互相转换。

### Phase 1：解析 API 与文档状态（最高优先级）

1. 建立 `TomlParserFacade`，屏蔽底层 parser 实现。
2. 建立统一 AST/语义节点访问接口，覆盖 Table、ArrayTable、KeyValue、DottedKey、Array、InlineTable、String、Number、Boolean、DateTime、Comment。
3. 建立错误恢复和错误节点模型。
4. 实现 versioned document store 与 change applier。
5. 解析树按文档隔离；支持全量初始化和增量更新。

**验收：** 多次连续编辑、多个变更事件、CRLF、Unicode、未闭合字符串、半截 KeyValue 均能保持正确文档状态和有效解析结果。

### Phase 2：正确性优先的核心 LSP 能力

建议按以下顺序开发：

1. **Diagnostics**：Parser 错误、重复 Key、Key/Table 冲突、非法结构；诊断范围精确。
2. **Document Symbols**：正确构建嵌套 Table、ArrayTable 和 Key 层级。
3. **Folding Ranges**：Table/ArrayTable、多行数组、多行字符串。
4. **Semantic Tokens**：统一 Token 分类、UTF-16 Range、注释和字符串边界。
5. **Completion**：基于当前 Table/Key 路径提供语法上下文补全；支持 Key、Table、数组/内联表值位置。
6. **Hover**：当前 AST 节点信息、值类型和基础 TOML 文档。
7. **Formatting**：基于 AST/保留注释的 CST 或可靠格式化器；全文与范围格式化均需保持语义不变。

**验收：** 每项功能都有针对嵌套结构、引号 Key、Dotted Key、ArrayTable、InlineTable、多行字符串、注释和不完整输入的测试。

### Phase 3：安全的语义导航和代码操作

1. 构建 Key Path 与符号索引。
2. 实现 Definition 和 References。
3. 实现 PrepareRename 与 Rename；仅对语义上确定的 Key 引用生成编辑。
4. 实现 Code Actions：语法错误可自动修复项、排序 Key、格式化局部区域等。
5. Document Links 支持明确语义的 URL/本地路径字段，检查目标存在性和安全边界。

**验收：** 不同 Table 下同名 Key 不会互相误跳转或误重命名；字符串、注释和普通值不会被错误替换。

### Phase 4：JSON Schema 通用智能

1. Schema 关联优先级：显式文件注释指令、项目配置、文件名/路径规则。
2. 支持本地 Schema、工作区 Schema 和可选远程 Schema；远程加载需超时、大小限制、缓存、取消和失败降级。
3. 实现 Schema Draft 版本识别与明确的支持范围。
4. 校验 type、required、enum/const、数值边界、字符串长度/pattern、数组约束、对象属性及 additionalProperties。
5. Completion 根据当前 JSON Pointer/Key Path 生成属性、枚举和类型建议。
6. Hover 展示 description、type、default、deprecated、examples。
7. 诊断区分 TOML 语法错误和 Schema 语义错误，给出稳定 code/source。

**重要：** TOML 不是 JSON。必须先将 TOML AST 转换为保留来源位置的语义值模型，再执行 Schema 校验；不能简单把文本当 JSON 解析。Schema 错误需要映射回原始 TOML 节点 Range。

### Phase 5：生态 Provider

按优先级建议：

**A. Gradle Version Catalog**
- `[versions]`、`[libraries]`、`[bundles]`、`[plugins]` 上下文补全和 Hover。
- 识别 `version.ref`、`module`、`group`、`name` 等字段。
- 检查引用是否存在、别名冲突及格式错误。

**B. Cargo.toml**
- package、workspace、dependencies/dev-dependencies/build-dependencies、features、target、profile 等上下文。
- 依赖声明格式与 workspace inheritance 校验。
- 可选 crates.io 搜索/版本补全：必须异步、可取消、缓存、速率限制，并允许离线工作。
- 依赖版本与 Feature 建议不能阻塞编辑线程。

**C. pyproject.toml**
- 根据 `[build-system]`、`[project]`、`[tool.*]` 区分 PEP 规范和工具自定义配置。
- 结合所选 Schema/PEP 规范提供补全与验证。
- PyPI 查询作为可选 Provider，不应成为基础 TOML LSP 的硬依赖。

## 7. 非功能性要求

### 7.1 性能与内存

- 输入事件不能在 UI 线程执行全文件解析、Schema 网络请求或注册表搜索。
- 对高频变更做 debounce/coalescing，并支持取消过期任务。
- 优先增量解析；无法增量时使用受控后台全量解析。
- 大文件应有内存预算、缓存淘汰和降级策略；不能无限保留关闭文档的 AST。
- Schema 与包注册表缓存需要 TTL、容量上限和离线策略。

### 7.2 错误容忍

- 未闭合引号、未完成数组、`key =`、未闭合 Table Header 等输入状态不得导致崩溃。
- 解析失败时仍应保留可恢复的局部节点，使补全、诊断和高亮继续工作。
- 解析器异常应转化为受控错误日志/诊断，不得静默吞掉异常后把后续 Token 全部丢弃。

### 7.3 协议与正确性

- 明确全文/增量同步模式，严格应用多个 contentChanges。
- 异步结果必须检查文档版本，防止旧诊断覆盖新内容。
- 统一 UTF-16 Position 与 parser offset 的转换。
- Semantic Token legend、Token index、modifier 和 delta 编码必须一致。
- 未支持的 LSP 方法应明确返回空结果或不声明能力，不应对客户端伪装成已支持。

### 7.4 测试体系

至少建立以下测试组：

1. **Parser**：TOML 合法/非法语料、嵌套数组、内联表、数组表、Dotted Key、quoted Key、日期时间。
2. **Recovery**：半截字符串、半截数组、缺失等号、缺失值、未闭合表头。
3. **Position**：LF/CRLF、Tab、中文、Emoji、组合字符、补充平面字符。
4. **LSP**：open/change/close/save、多个增量 change、版本乱序、取消请求。
5. **Features**：Completion/Hover/Definition/References/Rename/Symbols/Folding/Formatting/SemanticTokens。
6. **Schema**：Schema 关联、必填项、类型、枚举、范围、错误位置映射、远程加载失败。
7. **生态**：真实 Cargo.toml、pyproject.toml、libs.versions.toml fixtures。
8. **性能**：小型常规配置、大型依赖清单、连续快速输入和多文档并行。

## 8. 开发优先级清单

| 优先级 | 工作项 | 依赖 | 目标 |
|---|---|---|---|
| P0 | Parser/JAR/Grammar 来源与版本核验 | 无 | 建立可信解析基线 |
| P0 | Parser Facade、AST/CST、Range 映射 | P0 基线 | 所有功能共享语法结构 |
| P0 | Versioned Document Store、增量变更 | 文档协议核验 | 文档状态正确 |
| P1 | AST 驱动 Diagnostics | Parser Facade | 语法/结构诊断准确 |
| P1 | Symbols、Folding、Semantic Tokens | AST + Range | 编辑器结构能力完整 |
| P1 | Completion、Hover | AST + 语义路径 | 上下文智能 |
| P1 | 正确的 Formatting | AST/CST | 格式化不破坏语义 |
| P2 | Definition、References、Rename | 符号索引 | 安全导航和重构 |
| P2 | Code Actions、Document Links | AST + 文件系统边界 | 快速修复和跳转 |
| P3 | JSON Schema association/validation | 语义值模型 | 通用配置语义智能 |
| P4 | Cargo/PyProject/Gradle Providers | Schema/Provider API | 生态深度支持 |
| P5 | 注册表数据源、缓存与高级重构 | Provider 基础设施 | 可选增强能力 |

## 9. 当前代码应优先处理的风险项

1. 不要继续扩展 `TomlFeatureEngine` 中的正则解析；先落地 Parser Facade。
2. 修复/重构 `TomlDiagnostics` 对 `=` 字符计数的判断，避免合法字符串被误报。
3. 在语义索引建立前，限制或暂时关闭不安全 Rename；当前全文词串替换有误改风险。
4. 明确 `didChange` 的同步模式，不能默认最后一个 change 一定是全文文本。
5. 合并 `SemanticHighlighting` 和 `TomlSemanticTokens` 的重复实现。
6. 明确 Tree-sitter、ANTLR、Flex/BNF、`toml.jar` 的职责与来源；不要继续引入第五套解析逻辑。
7. 为每项已有功能补充行为测试，不能仅以“方法已实现”作为完成标准。

## 10. 完成标准

TOML LSP 不以接口数量作为完成度，而以以下条件作为验收：

- 同一文档只存在一个权威解析/语义模型，各功能共享节点和 Range。
- 合法 TOML 不产生误报；非法 TOML 能准确定位；不完整输入可恢复。
- Completion/Hover/Symbols/Definition/References/Rename 的语义范围一致。
- Rename 不修改注释、字符串值或其他作用域的同名 Key。
- Formatting 前后解析语义一致，并保留注释和用户数据。
- 增量同步、版本控制、取消和多文档隔离正确。
- Schema 错误能映射到 TOML 原文位置，远程依赖失败时仍可编辑。
- 有自动化回归测试、性能测试和明确的解析器/Schema 版本来源。

---

## 11. 主要源码索引

- `lsp/toml/src/main/java/com/itsaky/androidide/lsp/servers/toml/TomlServer.kt`
- `lsp/toml/src/main/java/com/itsaky/androidide/lsp/servers/toml/server/TomlFeatureEngine.kt`
- `lsp/toml/src/main/java/com/itsaky/androidide/lsp/servers/toml/server/TomlDiagnostics.kt`
- `lsp/toml/src/main/java/com/itsaky/androidide/lsp/servers/toml/server/TomlDocumentCache.kt`
- `lsp/toml/src/main/java/com/itsaky/androidide/lsp/servers/toml/server/TomlTextDocumentService.kt`
- `lsp/toml/src/main/java/com/itsaky/androidide/lsp/servers/toml/server/TomlSemanticTokens.kt`
- `lsp/toml/src/main/java/com/itsaky/androidide/lsp/servers/toml/server/TokenMapping.kt`
- `lsp/toml/src/main/java/com/itsaky/androidide/lsp/servers/toml/server/TomlDocumentation.kt`
- `lsp/toml/TomlLexer.flex`
- `lsp/toml/TomlParser.bnf`
- `lsp/toml/libs/toml.jar`
- `editor/tree-sitter-ndk/toml/src/main/cpp/parser.c`
- `editor/tree-sitter-ndk/toml/src/main/cpp/scanner.c`
- `editor/impl/src/main/java/com/itsaky/androidide/editor/language/treesitter/TomlLanguage.kt`
- `editor/lexers/src/main/antlr/toml/TomlLexer.g4`
- `editor/lexers/src/main/antlr/toml/TomlParser.g4`
- `language-lexer/toml/src/main/java/android/zero/studio/editor/language/toml/TomlAnalyzer.kt`
- `language-lexer/toml/src/main/java/android/zero/studio/editor/language/toml/TomlAutoComplete.kt`
- `language-lexer/toml/src/main/java/android/zero/studio/editor/language/toml/TomlLanguageImpl.kt`

**本评估结论：** 当前模块适合作为 TOML LSP 的初始骨架，不应直接视为成熟语言服务器。后续开发必须从解析器与文档同步底层开始，再向诊断、语义模型、补全/导航、Schema 和生态 Provider 逐层推进。
