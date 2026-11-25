# 模块职责与边界

## 1. 目的

本文件定义 ZeroStudio 当前 Gradle 模块的领域归属和预期边界。模块名来自仓库 `settings.gradle.kts`；以下职责描述是架构分类，遇到与源码不一致时应以源码审计结果修订，不可仅凭模块名推断已实现能力。

## 2. 核心模块域

### Application 与核心业务

- `:core:app`：唯一 Android Application 装配入口。负责应用启动、依赖装配、顶层导航和系统级集成。业务能力应逐步委托给独立模块。
- `:core:common`：跨领域基础模型和通用设施。禁止成为无边界的“万能模块”。
- `:core:actions`：应用动作/命令契约与分发相关能力。动作定义应稳定、可测试。
- `:core:projects`：项目模型、项目状态和项目相关用例。
- `:core:resources`：资源访问与资源相关基础能力。
- `:core:indexing-api`、`:core:indexing-core`：索引接口与索引实现。
- `:core:lsp-api`、`:core:lsp-models`：语言服务接口与协议/数据模型。
- `:core:layout-editor`：布局编辑器领域功能。
- `:core:git`：Git 集成模块，外部来源及改造边界见第三方集成文档。
- `:core:ZeroStudio-Terminal`：ZeroStudio 终端集成入口。

### 编辑器与语言基础设施

- `:editor:api`：编辑器对外契约。
- `:editor:impl`：编辑器核心实现。
- `:editor:lexers`：词法分析器与相关语言支持。
- `:editor:treesitter`：Tree-sitter 编辑器侧集成。
- `:editor:editor-lsp`：编辑器与语言服务的适配层。
- `:editor:tree-sitter-ndk:android-tree-sitter`：Android Tree-sitter Native/JNI 核心适配。
- `:editor:tree-sitter-ndk:annotations`、`:annotation-processors`、`:tree-sitter-jnilibs`：注解、处理器和 Native 库分发支持。
- `:editor:tree-sitter-ndk:<language>`：语言语法模块，例如 toml、cmake、yaml、aidl、cpp。

约束：语言模块不应直接依赖具体编辑器页面；编辑器 UI 不应直接操作 Tree-sitter Native 指针或内部对象。

### 语言服务

- `:java:lsp`：Java 语言服务相关实现。
- `:java:javac-services`：Java 编译器服务支持。
- `:lsp:kotlin`、`:lsp:toml`、`:lsp:clangd`：对应语言服务模块。

语言服务应通过 `:core:lsp-api`、`:core:lsp-models` 或明确的适配 API 与编辑器交互。进程启动、协议读写、请求取消和资源关闭必须有明确所有权。

### 构建与开发工具

- `:tooling:api`：工具链接口。
- `:tooling:model`：工具链模型。
- `:tooling:events`：工具链事件。
- `:tooling:impl`：工具链实现。
- `:tooling:plugin`、`:tooling:plugin-config`：插件接入与配置。
- `:tooling:builder-model-impl`：构建模型实现。
- `:xml:aaptcompiler`：AAPT 资源编译相关能力。

构建逻辑应隔离 Gradle Tooling API、进程执行和 UI。UI 只消费构建状态与结果，不直接持有 Gradle 内部实现对象。

### 终端与环境

- `:termux:application`、`:termux:emulator`、`:termux:shared`、`:termux:view`、`:termux:shell`、`:termux:proot`：Termux 相关集成模块。
- 终端领域需要明确 Shell 会话、进程、PTY、文件系统、环境变量和终止信号的边界。

### AI 能力

- `:core:chatai:ai`
- `:core:chatai:app`
- `:core:chatai:common`
- `:core:chatai:document`
- `:core:chatai:highlight`
- `:core:chatai:search`
- `:core:chatai:speech`
- `:core:chatai:web`
- `:core:chatai:workspace`
- `:core:chatai:material3`

AI 功能应将模型提供商、会话/消息模型、工具调用、网络访问和 UI 分离。工具执行必须有权限边界、参数校验、取消机制和结果审计。

### 调试与设备连接

- `:debugger:adb-connection:adblib`
- `:debugger:adb-connection:libadb`
- `:debugger:adb-connection:fastbootlib`
- `:debugger:adb-connection:connection`
- `:debugger:adb-connection:settings-dsl`
- `:debugger:Breakpoint-debugger:library`
- `:debugger:Breakpoint-debugger:ide-debugger`
- `:debugger:Breakpoint-debugger:ide-debugger-host`
- `:debugger:Breakpoint-debugger:ide-log-plugin`
- `:debugger:Breakpoint-debugger:logwire`
- `:debugger:ide-decompiler`
- `:debugger:ide-language`

ADB、Fastboot、调试器和日志能力应各自保持协议实现、连接会话、领域状态和 UI 分离。连接凭据和配对密钥不得暴露给展示层。

### XML、事件、日志与工具模块

- `:xml:dom`、`:xml:lsp`、`:xml:resources-api`、`:xml:utils`、`:xml:vectormaster`：XML 与资源处理能力。
- `:event:eventbus`、`:event:eventbus-android`、`:event:eventbus-events`：事件基础设施。
- `:logging:idestats`、`:logging:logger`、`:logging:logsender`：日志和统计。
- `:utilities:*`：共享工具、偏好设置、模板、树结构和框架 Stub 等基础模块。

通用工具应有明确用途和依赖方向；领域逻辑不能因为“方便”而持续迁入 utilities/common。

### 扩展模块

当前 include 的示例包括：

- `:modules:compose-preview`
- `:modules:deviceCompat`
- `:modules:colorpicker`
- `:modules:kotlinc`
- `:modules:mt-data-files-provider`
- `:modules:soraLanguageMonarch`
- `:modules:soraLanguageTreesitter`
- `:modules:soraOnigurumaNative`
- `:modules:thinkmap-treeview`
- `:modules:zero-Symbol-input-view`
- `:modules:zero-mcp-server`
- `:modules:zero-regular-preview`
- `:modules:universal-preview`
- `:modules:web-preview`
- `:modules:zero-onboarding-guide`
- `:modules:shizuku:*`

扩展模块应尽可能通过稳定 API 接入主应用；不应反向污染核心领域模型。

## 3. 复合构建

当前工程使用 `composite-builds/build-logic` 集中管理构建逻辑，并在 `settings.gradle.kts` 中引入 `composite-builds/build-deps` 等复合构建。

- build-logic：约定插件、Android/Java 模块配置和共享构建逻辑。
- build-deps：维护以源码方式参与构建的第三方/共享依赖模块。
- 独立复合构建应明确自己的 settings、插件解析和 SDK/NDK 版本来源，避免与主工程配置分叉。

## 4. 新增模块准入条件

新增模块前必须回答：

1. 它属于哪个业务/技术域？
2. 为什么不能放入现有模块？
3. 它公开哪些 API？哪些实现必须保持 internal？
4. 它依赖哪些模块？是否违反依赖方向？
5. 是否引入第三方源码、许可证或 Native ABI？
6. 如何独立构建、测试和验证？
7. 生命周期、线程、资源和错误由谁负责？

## 5. 模块地图维护

每次模块新增、删除、拆分或合并时，同步更新本文件。模块路径应与 `settings.gradle.kts` 保持一致；若 Gradle include 与目录实际状态不同，记录差异并建立修复项。
