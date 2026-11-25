# ZeroStudio 总体架构

## 1. 产品定位

ZeroStudio 是运行于 Android 设备上的集成开发环境，目标是在 Android 设备本机完成真实 Gradle Android 工程的导入、编辑、构建、调试和项目管理。它不是单纯的代码编辑器，也不是远程桌面式 IDE。

核心能力域：

- 工程与文件：项目导入、目录树、资源和工程元数据。
- 代码编辑：编辑器内核、语法高亮、语言服务、代码导航和编辑辅助。
- 构建工具链：Gradle Tooling、JDK、Android SDK/Build Tools、NDK 与 Native 构建。
- 运行与调试：设备连接、ADB/Fastboot、日志、断点调试和应用部署。
- 终端与环境：终端会话、Shell、环境变量、工具链安装与执行。
- 扩展能力：预览、MCP、AI 助手、Git、正则表达式等。

## 2. 架构分层

目标逻辑分层如下。它描述依赖边界，不要求每一层必须对应一个 Gradle module。

```text
Presentation
  Compose Screens / Navigation / UI State / Design System
        |
Application
  Use Cases / Coordinators / Session orchestration
        |
Domain
  Project / Editor / Build / Debug / Device domain contracts
        |
Platform & Infrastructure
  Android APIs / Storage / Process / Gradle Tooling / ADB / Network
        |
Native Runtime
  JNI boundary / C-C++ libraries / Rust libraries / platform binaries
```

### Presentation

- 新页面和新组件优先使用 Jetpack Compose 与 Material 3。
- UI 负责展示状态、收集用户意图，不直接持有构建、Git、ADB 等复杂业务流程。
- 页面状态应由明确的状态持有者或状态流提供；Composable 保持可组合、可预览、可测试。
- 导航参数传递稳定标识或轻量参数，不传递大型可变对象。

### Application

- 编排跨模块用例，例如打开项目、启动构建、连接设备、安装 APK。
- 负责协调多个领域接口，但不实现底层协议细节。
- 长任务应有明确的取消、进度、错误和重试语义。

### Domain

- 定义项目、编辑器、构建、调试、设备等核心概念和接口。
- 尽可能不依赖 Android View、Activity、Fragment 或具体 UI 框架。
- 不允许领域层依赖具体页面或应用入口模块。

### Platform & Infrastructure

- 封装 Android 平台 API、文件系统、进程、网络、Gradle、ADB、持久化和第三方 SDK。
- 对上层暴露可测试的接口，隔离平台差异和外部实现。
- 不将外部库类型无边界地泄漏到核心领域 API。

### Native Runtime

- C/C++/Rust 负责适合 Native 的性能敏感或平台底层能力。
- JNI/FFI 是显式边界，必须定义线程、内存所有权、错误映射、生命周期和 ABI 契约。
- Native 模块不依赖 UI；Kotlin 层负责调用编排和 Android 平台集成。

## 3. 当前 Gradle 工程映射

当前 `settings.gradle.kts` 显示工程由多个功能域模块和复合构建组成。主要映射如下：

| 逻辑层/领域 | 当前模块示例 | 职责说明 |
|---|---|---|
| 应用入口 | `:core:app` | Android Application、应用装配与启动 |
| 核心基础 | `:core:common`、`:core:actions`、`:core:resources` | 通用能力、动作契约和资源能力 |
| 项目系统 | `:core:projects` | 项目模型与项目相关功能 |
| 编辑器 | `:editor:api`、`:editor:impl`、`:editor:lexers`、`:editor:treesitter`、`:editor:editor-lsp` | 编辑器接口、实现、语法与语言服务集成 |
| Tree-sitter Native | `:editor:tree-sitter-ndk:*` | Tree-sitter JNI/Native 及语言模块 |
| 语言服务 | `:java:lsp`、`:lsp:kotlin`、`:lsp:toml`、`:lsp:clangd` | 各语言服务适配 |
| 构建与工具链 | `:tooling:*`、`:java:javac-services`、`:xml:aaptcompiler` | Gradle/构建工具模型与执行支持 |
| 终端 | `:core:ZeroStudio-Terminal`、`:termux:*` | 终端、Shell 与环境集成 |
| Git | `:core:git` | Git 能力集成，当前来源项目为 PuppyGit |
| AI | `:core:chatai:*` | AI 领域功能、应用、文档、搜索、语音和工作区等 |
| 调试与设备 | `:debugger:*`、`:modules:shizuku:*` | 调试、日志、ADB/设备能力 |
| XML 与资源 | `:xml:*` | XML、资源解析与编辑辅助 |
| 扩展模块 | `:modules:*` | Preview、MCP、正则、符号输入等可选功能 |
| 基础设施 | `:utilities:*`、`:event:*`、`:logging:*` | 工具类、事件和日志基础设施 |

该表是逻辑地图，不是完整模块清单。精确模块列表以 `settings.gradle.kts` 为准；模块的实际职责以其源码和公开 API 为准。

## 4. 依赖规则

1. UI 依赖 Application/Domain 暴露的接口，不反向依赖 UI。
2. Domain 不依赖具体 Android UI、Activity、Fragment 或 View。
3. 核心 API 不依赖某个外部项目的内部实现包。
4. Native 模块不得依赖上层 Kotlin UI 模块。
5. 跨模块调用优先使用公开 API、接口或事件契约，不直接访问对方内部类。
6. 依赖注入只负责组装依赖，不承担业务逻辑。
7. 模块不得通过全局可变单例绕过依赖关系。

## 5. 状态与并发

- UI 状态应可观察、可重建，并明确区分短暂事件与持久状态。
- 长任务使用结构化并发，定义所属 CoroutineScope、取消边界和异常传播策略。
- 不允许在主线程执行文件扫描、完整语法解析、构建进程等待或大规模数据转换。
- 对编辑器、索引、语言服务和 Native 调用，应定义线程安全和资源释放契约。
- 进度、取消和错误应通过稳定状态模型传递，而不是依赖日志文本解析。

## 6. 安全与数据边界

- 项目文件、终端命令、外部仓库和 AI 输入均视为不可信数据。
- 凭据、签名密钥、访问令牌不得写入源码、日志或普通配置文件。
- 外部进程执行必须明确参数边界、工作目录、环境变量和取消机制。
- Git、ADB、MCP、AI 工具等具有副作用的操作应提供可审计的调用路径和必要的用户确认。

## 7. 架构演进

新功能按“契约先行、底层先行、逐层接入”的顺序实施：

1. 明确领域模型与公开 API。
2. 建立平台/基础设施实现。
3. 实现应用层用例和状态流。
4. 实现 Compose UI 与导航。
5. 添加测试、性能基线和文档。
6. 完成构建、运行与回归验证。

不得先堆叠页面，再让底层 API 迁就页面实现。
