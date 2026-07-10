# debugger:library

ZeroStudio 调试器运行时产物的打包聚合模块。它本身不含源码,而是把多个
调试器 / 日志相关模块的构建产物 (AAR / JAR) 打包成一个可分发的 `zip`,
供 IDE 在运行时解压到 `~/.androidide/plugin/logger/` 供 Gradle 构建用户项目时使用。

## 产物

构建后输出 `build/distributions/debugger-library.zip`,内含以下 6 个产物:

### 注入宿主 app 的运行时产物

| 打包文件名 | 来源模块 | 类型 | 用途 |
| --- | --- | --- | --- |
| `ide-debugger-host.aar` | `:debugger:Breakpoint-debugger:ide-debugger-host` | AAR | **宿主端** 反连桥 (HostAttachAgent + ContentProvider),注入宿主 app |
| `ide-log-plugin-1.0.0.aar` | `:debugger:Breakpoint-debugger:ide-log-plugin` | AAR | **宿主端** JdwpServer + LogCaptureService,注入宿主 app |
| `logsender.aar` | `:logging:logsender` | AAR | 日志接收端,编辑器底部抽屉接收宿主 app 全部日志 |

### Gradle 构建工具链产物 (不进宿主 app)

| 打包文件名 | 来源模块 | 类型 | 用途 |
| --- | --- | --- | --- |
| `androidide-plugin.jar` | `:tooling:plugin` | JAR | Gradle init-script 插件本体 (`IdeLogInitScriptPlugin` / `IdeDebuggerInitScriptPlugin`),构建用户项目时加载 |
| `plugin-config.jar` | `:tooling:plugin-config` | JAR | Gradle 插件配置,init script classpath 依赖 |
| `logger.jar` | `:logging:logger` | JAR | 日志门面,init script classpath 依赖 |

> **注意:** `androidide-plugin.jar` / `plugin-config.jar` / `logger.jar` 虽然不进宿主 app APK,
> 但它们是 Gradle daemon 构建用户项目时的 init script classpath 依赖
> (见 `GenerateInitScriptTask`),必须随 zip 分发到文件系统,否则 Gradle 构建会崩溃。

## 不打包的产物

`ide-debugger.aar` (IDE 端 JDWP 客户端引擎) **不打包** — 它是 IDE 进程内运行的代码,
已通过 IDE APK 自身编译包含,不需要从 zip 解压。`IdeDebuggerInitScriptPlugin` 和
`IdeLogInitScriptPlugin` 也不再将它注入宿主 app。

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
