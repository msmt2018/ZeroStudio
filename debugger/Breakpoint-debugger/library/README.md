# debugger:library

ZeroStudio 调试器运行时产物的打包聚合模块。它本身不含源码,而是把多个
调试器 / 日志相关模块的构建产物 (AAR / JAR) 打包成一个可分发的 `zip`,
供 IDE 在运行时下载并注入到用户项目的 debug variant 中。

## 产物

构建后输出 `build/distributions/debugger-library.zip`,内含以下 7 个产物:

| 打包文件名 | 来源模块 | 类型 | 用途 |
| --- | --- | --- | --- |
| `androidide-plugin.jar` | `:tooling:plugin` | JAR | Gradle init-script 插件,注入 AAR + manifest placeholder 到用户项目 |
| `ide-debugger.aar` | `:debugger:Breakpoint-debugger:ide-debugger` | AAR | **IDE 端** JDWP 客户端引擎 (断点/求值/堆栈),仅 IDE 进程使用 |
| `ide-debugger-host.aar` | `:debugger:Breakpoint-debugger:ide-debugger-host` | AAR | **宿主端** 反连桥 (HostAttachAgent + ContentProvider),注入宿主 app |
| `ide-log-plugin-1.0.0.aar` | `:debugger:Breakpoint-debugger:ide-log-plugin` | AAR | **宿主端** JdwpServer + LogCaptureService,注入宿主 app |
| `logger.jar` | `:logging:logger` | JAR | 日志门面,IDE 与宿主端共用 |
| `logsender.aar` | `:logging:logsender` | AAR | 日志发送端 |
| `plugin-config.jar` | `:tooling:plugin-config` | JAR | 插件配置 |

## 分层说明

`ide-debugger.aar` 是 **IDE 端** 引擎,不会注入宿主 app (由 `IdeDebuggerInitScriptPlugin`
和 `IdeLogInitScriptPlugin` 控制只注入 `ide-log-plugin` + `ide-debugger-host`)。
它被打包进 zip 是因为 IDE 自身需要从 zip 中取用它。

## 构建

```bash
gradle :debugger:Breakpoint-debugger:library:assemble
```

产物路径: `debugger/Breakpoint-debugger/library/build/distributions/debugger-library.zip`

## 实现细节

- `RuntimeArtifact` data class 描述每个产物的来源 (`projectPath`)、构建任务
  (`producerTaskName`)、构建输出相对路径 (`buildOutput`) 和打包后的文件名
  (`packagedName`)。
- `packageDebuggerLibrary` (Zip task) 对每个产物 `dependsOn` 对应模块的构建任务,
  再用 `from { rename }` 把输出文件重命名后打入 zip。
- `DuplicatesStrategy.FAIL` 保证产物名冲突时构建失败而不是静默覆盖。
