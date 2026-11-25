# 技术栈与平台基线

## 1. 配置事实来源

版本和构建配置以仓库实际配置为准，文档不作为版本定义源。当前已检查的关键位置：

- Android SDK/NDK/Java 基线：`composite-builds/build-logic/common/.../BuildConfig.kt`
- Android 模块统一配置：`composite-builds/build-logic/plugins/.../AndroidModuleConf.kt`
- 依赖版本目录：`gradle/libs.versions.toml`
- 工程模块和复合构建：`settings.gradle.kts`
- Application 构建特性：`core/app/build.gradle.kts`

## 2. 当前已核实基线

| 项目 | 当前配置 |
|---|---|
| compileSdk | 37 |
| compileSdkMinor | 1 |
| minSdk | 26 |
| targetSdk | 37 |
| Android NDK | 29.0.14206865 |
| Java/Kotlin JVM target（统一 Android 配置） | 17 |
| Application Compose build feature | 已启用 |

说明：版本值是本次读取 main 分支配置时的快照。升级时必须同步检查 AGP、Gradle Wrapper、Kotlin 插件、Compose Compiler 插件、AndroidX、NDK、CMake、Native 依赖及 CI runner。

## 3. Kotlin 与 Android

- Kotlin 是主要 Android 应用开发语言。
- Android 平台能力由独立适配层封装。
- 新增 Android UI 优先使用 Jetpack Compose Material 3。
- 传统 View 系统仅作为现存模块的兼容/迁移对象，不作为新页面默认技术选型。
- Android 生命周期仍然存在；“无传统 View / 声明式 UI”不等于取消 Activity、进程或平台生命周期。

## 4. Compose

Compose 目标方向：

- Material 3 作为基础设计系统。
- 使用单向数据流（UDF）：状态向下、事件向上。
- 页面由稳定状态模型驱动。
- Composable 不直接执行阻塞 I/O、构建、网络或 Native 长任务。
- 使用适当的 state holder、ViewModel 或生命周期无关状态持有者；选择依据是状态范围和持久化需求，而不是机械地禁止生命周期组件。
- UI 测试通过语义节点与用户行为验证，不依赖内部实现细节。

## 5. Gradle 与构建系统

- Kotlin DSL 为主要 Gradle 脚本形式。
- Version Catalog 管理常规依赖版本。
- Composite Build / convention plugins 管理共享构建逻辑。
- Android Application 与 Library 应使用统一 SDK/NDK/Java 约定配置。
- 模块只声明自身必要的构建参数；不得无理由覆盖中央 SDK/NDK 基线。
- Gradle Wrapper 是构建入口；CI 与本地开发应尽可能使用相同 Wrapper 和参数。

## 6. Native 技术

当前工程包含 NDK、CMake/JNI 等 Native 构建路径。C/C++/Rust 具体采用范围应由实际模块确认，不应把“可选技术”写成当前已全面使用。

Native 接口必须记录：

- ABI 和最低系统版本。
- 线程调用约束。
- 内存所有权与释放函数。
- 异常/错误码映射。
- JNI 引用管理和局部/全局引用边界。
- 并发访问和关闭顺序。
- 性能基准、内存预算与大文件行为。

## 7. 数据与持久化

持久化技术由各模块现状决定。新设计优先定义 repository 接口和数据模型，避免 UI 直接访问数据库、偏好设置或文件实现。敏感数据应使用平台安全存储能力，并限制日志输出。

## 8. 版本升级策略

升级平台或工具链时，先建立升级分支和兼容性矩阵，再逐步验证：

1. Gradle Wrapper 与 JDK。
2. AGP 与 Kotlin 插件。
3. compileSdk / targetSdk。
4. Compose 与 AndroidX 依赖。
5. NDK / CMake / Native ABI。
6. 所有独立 composite build。
7. 主 APK、模块单测、仪器测试和 Native 构建。
8. 真机安装、启动、关键功能冒烟测试。

禁止仅修改版本号而不验证构建插件加载和 Native 编译链。
