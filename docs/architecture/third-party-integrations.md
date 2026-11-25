# 第三方项目与外部能力集成

## 1. 目的

本文件记录已知外部项目来源及其在 ZeroStudio 中的集成边界。引用外部仓库不代表已完整集成其全部功能，也不代表外部项目由 ZeroStudio 维护。

## 2. 已知来源登记

| 项目 | 上游仓库 | 当前已知用途/关联 | 集成注意事项 |
|---|---|---|---|
| AndroidIDE | https://github.com/AndroidIDEOfficial/AndroidIDE | ZeroStudio 的历史工程基础 | 保留上游许可证与版权声明；区分历史继承代码与 ZeroStudio 改动 |
| PuppyGit | https://github.com/catpuppyapp/PuppyGit | Git 功能来源；工程含 `:core:git` | 记录导入范围、二次修改、Native 依赖和许可证 |
| RikkaHub | https://github.com/rikkahub/rikkahub | Chat AI 能力参考/来源；工程含 `:core:chatai:*` | 核对实际复制/依赖的代码范围、模型/工具协议和许可证 |
| Sora Editor | https://github.com/Rosemoe/sora-editor | 代码编辑器相关能力 | 明确上游版本、补丁和 API 兼容层 |
| Termux | https://github.com/termux/termux-app | 终端与 Shell 相关模块 | 核对各子项目许可证、构建脚本和 Android 平台差异 |
| zero-Symbol-input-view | https://github.com/msmt2018/zero-Symbol-input-view | 编辑器符号输入工具栏相关模块 | 记录本项目维护的 API、版本和迁移状态 |

上述列表来源于仓库 README、Gradle 模块命名及项目提供的来源登记。具体代码是否直接复制、作为依赖或仅参考，必须逐模块核实后补充，不能仅凭来源链接推断。

## 3. 每项集成必须登记

- 上游仓库 URL。
- 上游 release/tag/commit。
- 实际引入路径：源码复制、Git submodule、Maven/Gradle 依赖或设计参考。
- 修改文件范围及补丁说明。
- SPDX/许可证标识与版权声明。
- 上游更新与安全公告跟踪方式。
- 与 ZeroStudio API 的适配层。
- 删除/替换该依赖的可行性。

## 4. 集成原则

1. 尽可能通过 adapter/facade 隔离外部 API。
2. 避免让外部项目内部类型扩散到核心领域模型。
3. 对外部代码的修改保留可追踪补丁或提交记录。
4. 不删除、覆盖或误改上游许可证和版权声明。
5. 升级前比较 API、行为、许可证和 Native ABI 变化。
6. 外部工具执行、网络请求和模型工具调用应遵循 ZeroStudio 自身的权限、安全和取消机制。

## 5. 许可证审计

README 中的项目许可说明与实际仓库 LICENSE 文件必须一致。对于混合许可证仓库，应按文件/模块确认适用许可证；不得以主仓库许可证覆盖第三方代码许可证。发布前应生成并审查依赖许可证清单。
