# ZeroStudio Editor Core

ZeroStudio 的代码编辑器核心采用一体化编辑器控件 + 少量独立能力框架的结构。编辑器主体由 `editor-core` 提供完整的 Android View；ZeroStudio Activity 负责 IDE 页面布局、工具栏、标签栏、侧栏、面板和其他应用层 UI。

## Modules

| Module | Responsibility |
|---|---|
| `editor-core` | 完整编辑器 View 与常规编辑能力，包括文本编辑、输入、布局、渲染、光标、选区、折叠、搜索替换、补全交互、格式化结果应用及代码片段。 |
| `editor-language` | 语言注册、Tree-sitter、Parser/Tree/Query、Grammar、JNI/Native、增量解析和语法高亮。 |
| `editor-lsp` | LSP、JSON-RPC、Transport、Language Server 生命周期、文档同步和语言服务适配。 |
| `editor-analysis` | 分析提供者、分析任务调度、诊断统一模型、多来源诊断整合和结果版本管理。 |
| `editor-dual` | 双编辑器实例组合、分屏布局、活动编辑器、焦点/命令协调和可选同步策略。 |

## Architecture boundaries

- `editor-core` 是独立可用的编辑器主体。文本存储、Undo/Redo、输入/IME、布局、渲染、搜索、补全交互、格式化应用和 Snippets 均为其内部能力，不拆成独立 Gradle 模块。
- Activity 负责 IDE 页面 UI；本项目不创建 `editor-ui` 模块。
- `editor-language` 负责语法解析，不负责编辑器 View 或文本编辑事务。
- `editor-lsp` 负责协议和服务器通信，不负责编辑器 UI 或 Tree-sitter 实现。
- `editor-analysis` 负责分析任务与诊断数据整合，不重复实现解析器或 LSP 传输。
- `editor-dual` 复用两个 `editor-core` 实例，不实现 Diff 算法，也不管理 Git 仓库或文件版本。
- 不为每个内部子系统创建 Gradle 模块；只有出现真实独立职责和依赖边界时才考虑新增模块。

## Current status

五个 Gradle library 模块已建立为空模块，仅包含构建配置、Android manifest 和职责说明。尚无业务实现、模块间依赖或已验证的集成能力。旧的 `:editor:impl` 及现有 Tree-sitter/LSP 模块保持不变，后续在源码审计和迁移计划明确后再逐步迁移。

## Documents

- [Architecture](ARCHITECTURE.md)
- [Modules and responsibilities](MODULES.md)
- [Roadmap](ROADMAP.md)
