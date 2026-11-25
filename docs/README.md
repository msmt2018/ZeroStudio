# ZeroStudio 文档中心

ZeroStudio 是面向 Android 设备端真实 Android 项目开发的集成开发环境（IDE）。本文档体系用于描述产品方向、软件架构、模块职责、工程约束、开发流程和质量标准。

## 文档状态

- 本文档描述基于仓库当前可见源码与 Gradle 配置建立的架构基线。
- “现状”表示可从当前工程配置或源码确认；“目标”表示后续开发要求，不代表已经实现。
- 模块清单会随 Gradle include、实际源码和模块拆分持续校正。
- 任何架构迁移均应同步更新对应文档，避免文档与代码长期漂移。

## 阅读顺序

1. [总体架构](architecture/overview.md)
2. [模块职责与边界](architecture/modules.md)
3. [技术栈与平台基线](architecture/technology-stack.md)
4. [架构决策记录](architecture/architecture-decisions.md)
5. [断点调试器目标架构](../debugger/DEBUGGER_TARGET_ARCHITECTURE.md)
6. [断点调试器 SDK、依赖与上游源码总目录](../debugger/DEBUGGER_SDK_AND_SOURCE_CATALOG.md)
7. [现有断点调试器源码审计](../debugger/DEBUGGER_ARCHITECTURE_AUDIT.md)
8. [产品方向与开发路线](development/roadmap.md)
9. [开发与交付流程](development/workflow.md)
10. [编码规范](standards/coding-standards.md)
11. [Compose 迁移规范](standards/compose-migration.md)
12. [测试与质量门禁](standards/testing-and-quality.md)
13. [第三方项目集成](architecture/third-party-integrations.md)

## 文档目录

- `architecture/`：系统分层、模块边界、技术选型、架构决策和外部集成。
- `development/`：产品方向、路线图、开发协作与交付流程。
- `standards/`：代码风格、UI 迁移、测试和质量要求。
- `../debugger/`：断点调试器源码审计、目标架构、SDK/依赖和上游源码登记。

## 文档维护规则

- 架构边界或技术选型发生变化时，必须更新 ADR。
- 新增 Gradle 模块时，必须在模块地图中登记其职责、公开 API、依赖方向和验证方式。
- 新增外部源码或 SDK 时，必须登记来源、版本/提交、许可证、修改范围和升级策略。
- 调试器新增 SDK 时，必须记录 Android host ABI/运行时兼容性、目标设备组件、依赖树和测试模块归属。
- 完成迁移时，必须提供源码扫描、构建和测试证据；不得仅凭目录命名或 UI 截图宣称完成。
- 版本号、SDK/NDK 等可变值应以仓库中央配置为准，文档注明文件路径，不重复维护容易过期的副本。

## 范围与非目标

本文档是工程开发规范，不替代各模块 API 文档、用户使用手册、隐私政策或法律许可文本。具体模块行为以当前实现和对应 API 契约为准。
