# 设备连接管理重构 设计文档

- **日期**: 2026-08-03
- **状态**: 已批准（待实现规划）
- **关联**: 替换 `DeviceConnectionBottomSheet` 的 JDWP-only 实现；复用 `:debugger:adb-connection:connection` 模块连接层

---

## 1. 背景与目标

### 1.1 现状

`core/app/src/main/java/com/itsaky/androidide/fragments/debugger/DeviceConnectionBottomSheet.kt`
当前是 JDWP-only 的 BottomSheet：仅做 JDWP 端点探测 + 一个禁止 adb 命令的本地控制台。所有
ADB / Root / Shizuku / OTG / 无线调试入口已被移除。

该 Sheet 被 2 处调用：
- `actions/etc/OpenDeviceConnectionAction.kt:78`（编辑器工具栏 Action）
- `preferences/debuggerPrefExts.kt:62`（偏好设置入口）

### 1.2 参考工程

`debugger/adb-connection/connection` 模块（由 `debugger/android-adb-shell` 迁移而来）拥有完整的
ADB 连接生态：
- LocalAdb 三模式（BASIC/SHIZUKU/ROOT）、OTG USB ADB、WiFi ADB 自配对前台 Service、Fastboot
- CommandExamples Room 库（200+ 预置命令）
- 单例 StateFlow 状态（OtgConnection / WifiAdbConnection / ShizukuPermissionHandler）
- Hilt DI（`@InstallIn(SingletonComponent::class)`），`@HiltViewModel`

### 1.3 目标

1. 移除 DeviceConnectionBottomSheet 的 JDWP 链接页面全部源码与操作事件
2. 参照 connection 模块的 MainActivity UI 能力，重做为：
   - **adb 命令执行页面**（本地命令执行 + 命令示范列表 + 关键词匹配）
   - **设备连接页面**（无线 ADB / Root / OTG 三类连接卡片 + 入口）
3. **Root 权限**完整开发：标准 su + KernelSU + Magisk + APatch；授权后驱动 ADB 连接设备
   （本机 / 无线局域网 / USB 有线）
4. **状态通道系统**：Shizuku / Root / OTG 在各自卡片右上角显示红黄绿状态点 + 刷新按钮
5. **全新 UI 设计语言**：深色科技感 + 毛玻璃层次，不复用 connection 模块的 palette

### 1.4 关键决策（已确认）

| 决策点 | 选择 |
|---|---|
| 复用方式 | core:app 新增 `implementation(project(":debugger:adb-connection:connection"))`，复用其连接层 |
| 承载形式 | BottomSheet 做设备连接页入口 + 全屏 Fragment 子页面 |
| Root 范围 | 标准 su + 真正对接 KernelSU/Magisk/APatch SDK，完整开发 |
| 命令后端 | BASIC/SHIZUKU/ROOT/OTG 按工作模式切换；必须先有连接才能用 adb 命令执行 |

---

## 2. 架构总览

### 2.1 模块依赖

core:app 新增：
```kotlin
implementation(project(":debugger:adb-connection:connection"))
```

DI 整合零成本：
- connection 模块 Hilt `@Module` 均为 `@InstallIn(SingletonComponent::class)`
- core:app 的 `IDEApplication` 已是 `@HiltAndroidApp`
- 依赖后 `ShellRepository` / `OtgRepository` / `WifiAdbRepository` / `CommandDatabase` 等自动并入
  全局组件，可在 core:app 直接 `@Inject` 或 `hiltViewModel()` 取用

### 2.2 复用层（connection 模块，不改动）

- **连接逻辑**：`WifiAdbRepositoryImpl`、`OtgRepositoryImpl`、`ShellRepositoryImpl`、`AdbConnectionManager`
- **前台服务**：`SelfPairingService`（自配对通知 + RemoteInput 配对码）、`AdbConnectionService`（连接保活）
- **状态单例**：`OtgConnection.state`、`WifiAdbConnection.state/currentDevice`、`ShizukuPermissionHandler.permissionGranted`
- **命令库**：`CommandDatabase` / `CommandDao` / `PreloadedCommands`（200+ 预置命令）/ `CommandRepository`
- **执行器**：`ShellCommandExecutor`（runBasic / runRoot / runShizuku）

### 2.3 新写层（core:app，全新 UI）

- `DeviceConnectionBottomSheet` 改造为「设备连接页」入口（3 卡片 + 状态通道 + 刷新）
- 全屏 Fragment：`PairingOwnDeviceFragment`、`PairingOtherDeviceFragment`、`AdbConsoleFragment`
- `RootManager`（新）：统一探测 标准 su / KernelSU / Magisk / APatch
- `RootAdbBridge`（新）：root 后驱动 ADB 连接设备（adb 二进制 / adblib 双后端）
- 自定义 Compose 主题 `DeviceConnectionTheme`（专属配色 / 控件 / 弹窗）

### 2.4 不复用 connection 的 UI Screen

`PairingOwnDeviceScreen` / `HomeScreen` / `BaseShellScreen` 等 Screen 不复用，因为：
1. 它们依赖 connection 的 `LocalNavController` / `LocalDialogManager` 等 CompositionLocal
2. 用户要求全新 UI 设计

仅复用底层逻辑（repository / service / 数据库 / 状态）。

---

## 3. 移除 JDWP + 设备连接页入口

### 3.1 移除范围

`DeviceConnectionBottomSheet.kt` 整个文件重写，删除：
- `JdwpConnectionScreen`、`ProtocolCard`、`EndpointCard`、`CommandConsole`、`InfoLine`
- `probeJdwp()`、`isForbiddenCommand()`、`runLocalCommand()`、`copyAttachHint()`
- JDWP 相关 import（Socket / InetSocketAddress / BufferedReader 等）

保留 `DeviceConnectionBottomSheet` 类壳与 `BaseBottomSheetFragment` 继承，内部 `setContent`
改为新的 `DeviceConnectionSheetContent`。

### 3.2 调用方保持不变

- `OpenDeviceConnectionAction.kt:78`
- `debuggerPrefExts.kt:62`

### 3.3 BottomSheet 新内容结构

```
┌─ DeviceConnectionSheetContent ─────────────────────┐
│ Header: 「设备连接」+ 关闭按钮                        │
│                                                      │
│ 状态通道总览条 (右上角)                                │
│  [●绿] Shizuku  [●黄] OTG  [●红] Root  [↻刷新]      │
│                                                      │
│ ┌─ 无线 ADB 卡片 ─────────────────────────────────┐ │
│ │ 图标 标题/副标题        状态点●(右上)            │ │
│ │ [指南] [配对设备▾] [启动]   ← 三按钮             │ │
│ └────────────────────────────────────────────────┘ │
│ ┌─ Root 权限卡片 ─────────────────────────────────┐ │
│ │ 图标 标题/当前管理器    状态点●(右上)            │ │
│ │ [申请权限] [管理器选择▾] [ADB 设备▾]            │ │
│ └────────────────────────────────────────────────┘ │
│ ┌─ OTG 卡片 ──────────────────────────────────────┐ │
│ │ 图标 标题/设备名        状态点●(右上)            │ │
│ │ [等待设备] [管理设备]                           │ │
│ └────────────────────────────────────────────────┘ │
│                                                      │
│ [ADB 命令执行 →]  (入口按钮，跳全屏)                  │
└──────────────────────────────────────────────────────┘
```

### 3.4 配对设备按钮

点击 → 底部弹 `PairModeChooseSheet`（自定义弹窗，非系统 Dialog），两个入口：
- 「配对此设备」→ `PairingOwnDeviceFragment`（全屏）
- 「配对其它设备」→ `PairingOtherDeviceFragment`（全屏）

---

## 4. 三张连接卡片 + 状态点

### 4.1 统一状态点组件 `StatusDot`

新写，放 core:app。三色语义：
- `GREEN` = 已连接 / 已授权
- `YELLOW` = 连接中 / 等待 / 未授权
- `RED` = 未连接 / 断开 / 不可用

颜色映射由各卡片自身的 `ConnectionStatus` 枚举驱动，非硬编码。YELLOW 时带脉冲呼吸动画 + 外圈光晕。

### 4.2 无线 ADB 卡片

- **状态来源**：`WifiAdbConnection.state`（StateFlow）+ `WifiAdbConnection.currentDevice`
- **三按钮**（对应 connection 模块 HomeScreen 的同款）：
  - **指南**：打开无线调试开启指引（自定义 `GuideSheet`，内容为图文步骤，非外链）
  - **配对设备**：弹 `PairModeChooseSheet` → 配对此设备 / 配对其它设备
  - **启动**：已配对设备则 `WifiAdbRepository.connect()`，无设备则提示先配对
- **配对实际调用**：`SelfPairingService`（复用，通知栏输入配对码）+ `WifiAdbRepositoryImpl.pair()`
- **卡片右上状态点**：Connected→绿、Searching/Connecting→黄、Idle/Error→红

### 4.3 Root 权限卡片（本次完整开发）

#### 4.3.1 RootManager（新）

统一探测：
- **标准 su**：`ShellRepositoryImpl.hasRootAccess()`（复用，`Runtime.exec("su")` 探测）
- **KernelSU**：检测 `/system/bin/ksud` 或 `KernelSU` App 存在 + 调用其 API
- **Magisk**：检测 `libsu`（topjohnwu，core:app 已依赖 `libs.libsu.core`）的 `Shell.getShell()` 是否 root shell + Magisk App
- **APatch**：检测 APatch App 存在

执行：
- 标准 su 走 `ShellRepositoryImpl.executeRootCommand()`
- KSU / Magisk 走 libsu `Shell.cmd(...).exec()`

状态来源：`RootManager.rootState: StateFlow<RootState>`（新建）

#### 4.3.2 RootAdbBridge（新）— root 后驱动 ADB 连接设备

两种后端并存：
- **adb 二进制后端**：app 内置静态编译的 `adb` 二进制（放 `assets/`，运行时释放到 `filesDir` 并 `chmod +x`），root 后执行 `adb start-server` / `adb connect` / `adb devices -l` / `adb -s <serial> shell`
- **adblib 后端**：复用 connection 模块的 `AdbConnectionManager`（纯 Java ADB 协议），root 权限下可访问 `/dev/bus/usb` 做 USB 连接、TCP 做 WiFi 连接

#### 4.3.3 连接方式全覆盖

| 连接目标 | 方式 | 实现 |
|---|---|---|
| 连接此设备（本机） | root 已授权即等于连上本机；或 `adb root` 让 adbd 以 root 重启后连本机 `127.0.0.1` | `RootManager` + `ShellRepositoryImpl.executeRootCommand` |
| 连接其它设备 - 无线/局域网 | `adb connect <ip:port>`（adb 二进制）或 `AdbConnectionManager.connect`（adblib） | `RootAdbBridge.connectWifi(ip, port)` |
| 连接其它设备 - USB 有线 | root 后 adb server 自动接管 USB 设备；或 adblib + `UsbChannel` | `RootAdbBridge.connectUsb()` |

#### 4.3.4 Root 卡片 UI

```
┌─ Root 权限卡片 ───────────────────────────────┐
│ 图标 标题/当前管理器        状态点●(右上)       │
│ [申请权限] [管理器选择▾] [ADB 设备▾]            │
└──────────────────────────────────────────────┘
```

按钮：
- **申请权限**：触发对应管理器授权弹窗（标准 su 直接 `su` 探测；KSU/Magisk 走其授权流程）
- **管理器选择**：弹 `RootManagerPickerSheet`，列出「标准 su / KernelSU / Magisk / APatch」，显示各自可用性
- **ADB 设备**：弹 `RootAdbDeviceSheet`（自定义抽屉式弹窗）

#### 4.3.5 RootAdbDeviceSheet

```
┌─ Root ADB 设备管理 ──────────────────────────┐
│  当前活动设备: (无) / <serial>                 │
│                                                │
│  ┌─ 本机 ─────────────────────────────────┐  │
│  │ 📱 this-device  已root  [设为活动]      │  │
│  └────────────────────────────────────────┘  │
│  ┌─ 无线设备 (2) ─────────────────────────┐  │
│  │ 📡 192.168.1.50:5555  device  [断开]    │  │
│  │    [设为活动]                          │  │
│  └────────────────────────────────────────┘  │
│  ┌─ USB 设备 (1) ─────────────────────────┐  │
│  │ 🔌 serial-abc  device  [设为活动]       │  │
│  └────────────────────────────────────────┘  │
│                                                │
│  [+ 连接无线设备]  → 输入 ip:port             │
│  [↻ 扫描 USB]  [⟲ adb devices 刷新]          │
└────────────────────────────────────────────────┘
```

#### 4.3.6 Root ADB 工作流程

1. 用户在 Root 卡片「申请权限」→ 管理器授权成功 → 状态点变绿
2. 「ADB 设备」按钮变为可用 → 弹 `RootAdbDeviceSheet`
3. Sheet 内 `adb start-server`（首次）→ `adb devices -l` 列出设备
4. 「+ 连接无线设备」→ 输入 `ip:port` → `adb connect` → 设备进入列表
5. USB 设备插入 → root 下 adb server 自动识别 → 列表刷新
6. 点击某设备「设为活动」→ 后续 adb 命令执行页的命令自动带 `-s <serial>`
7. 断开：无线 `adb disconnect`，USB 拔出自动移除

状态来源：`RootAdbBridge.deviceList: StateFlow<List<RootAdbDevice>>`（新建），含 `type`（LOCAL/WIFI/USB）、`serial`、`state`、`isActive`。

#### 4.3.7 Root 卡片状态点

已授权→绿、探测中/待授权→黄、无 root→红

### 4.4 OTG 卡片

- **状态来源**：`OtgConnection.state`（StateFlow，复用）
- **按钮**：
  - **等待设备**：`OtgViewModel.startScan()` + 弹 `OtgWaitingSheet`（等待 USB 插入，复用 OtgRepositoryImpl 的 BroadcastReceiver）
  - **管理设备**：已连接时显示设备名 + 断开按钮
- **连接实际走**：`OtgRepositoryImpl.connectToDevice()`（USB host + adblib，复用）
- **卡片右上状态点**：Connected→绿、Searching/DeviceFound→黄、Idle/Detached/Error→红

---

## 5. 配对页面（单独全屏）

### 5.1 PairingOwnDeviceFragment（配对此设备）

本机 Android 11+ 无线调试自配对。

- **复用**：`SelfPairingService` + `AdbMdnsDiscovery` + `WifiAdbRepositoryImpl.pair()`
- **UI 全新设计**：
  - 顶部：说明「本机 Android 11+ 无线调试自配对」
  - 中部：6 位配对码输入框（也可在通知栏输入，页面提供手动入口）
  - 状态：发现 pairing 服务中 / 等待输入码 / 配对中 / 成功 / 失败
  - 操作：[开始发现] [配对] [复制本机地址]

### 5.2 PairingOtherDeviceFragment（配对其它设备）

手动输入 host:port:code。

- **复用**：`WifiAdbRepositoryImpl.pair(ip, port, code, ...)`
- **UI**：
  - 三个输入框：IP 地址 / 端口 / 6 位配对码
  - [配对] 按钮 + 状态反馈
  - 配对成功后保存到 `WifiAdbDeviceDatabase`（复用）

两个 Fragment 用全屏 `Fragment` 承载（或 `DialogFragment` 全屏），内部 Compose 全新 UI，不引入 connection 的 `LocalNavController`。

---

## 6. adb 命令执行页

### 6.1 AdbConsoleFragment（全屏）

**复用 connection 模块**：`CommandDatabase` / `CommandDao` / `PreloadedCommands`（200+ 预置命令）/ `CommandRepository`，以及 `ShellCommandExecutor`（runBasic / runRoot / runShizuku）。

但 Screen 与 ViewModel 全新编写，不引入 connection 的 `BaseShellScreen` / `ShellViewModel` / `LocalNavController`。

### 6.2 页面结构

```
┌─ AdbConsoleScreen ────────────────────────────────────┐
│ TopBar: [←返回] 「ADB 命令」  [活动通道: Shizuku ▾] [↻]│  ← 通道选择器+刷新
│                                                         │
│ ┌─ 活动连接条 ────────────────────────────────────────┐│
│ │ ●绿 通道: Shizuku  设备: this-device  模式: SHIZUKU ││  ← 无连接时显示红条+禁用
│ └────────────────────────────────────────────────────┘│
│                                                         │
│ ┌─ 命令示范列表 (可折叠) ──────────────────────────────┐│
│ │ [🔍 搜索] [筛选 labels] [排序]                      ││
│ │ ▸ adb shell pm list packages    [labels: pm]  [★]  ││
│ │ ▸ adb shell dumpsys activity    [labels: dumpsys]  ││
│ │ ▸ adb push <local> <remote>     [labels: file]     ││
│ │ ... (来自 CommandDatabase, 关键词高亮匹配)          ││
│ └────────────────────────────────────────────────────┘│
│                                                         │
│ ┌─ 输出控制台 ────────────────────────────────────────┐│
│ │ $ adb shell pm list packages                       ││
│ │ com.android.settings                               ││  ← 关键词高亮: 命令绿/路径蓝/错误红
│ │ com.android.chrome                                 ││
│ │ exit=0                                             ││
│ └────────────────────────────────────────────────────┘│
│                                                         │
│ [命令输入框________________] [▶ 运行] [⏹停止] [⎘历史]  │
└─────────────────────────────────────────────────────────┘
```

### 6.3 活动通道选择器

顶部右上下拉列出当前可用的执行后端：
- `BASIC`（本机 sh）：始终可用，但仅本机命令
- `SHIZUKU`：仅当 `ShizukuPermissionHandler.permissionGranted == true` 时可选
- `ROOT`：仅当 `RootManager.rootState == GRANTED` 且 Root ADB 有活动设备时可选
- `OTG`：仅当 `OtgConnection.state is Connected` 时可选

### 6.4 约束落实

"shizuku 和 root 等成功连接到设备后才能使用 adb 命令执行"：
- 无任何可用通道时：命令输入框禁用 + 输出区显示红条「请先在设备连接页建立连接」+ 跳转按钮
- 选了通道但通道掉线：自动回退禁用 + 提示

### 6.5 关键词匹配/高亮

- **命令示范搜索**：复用 `CommandRepository.searchCommands(query)` 逻辑（匹配 command/description/labels）
- **输出高亮**：新写 `ConsoleHighlighter`，按正则匹配 adb 关键词（`adb` / `shell` / `push` / `pull` / `install` / `exit=` / 路径 / 错误行），用不同颜色渲染。复刻 connection 模块 `HighlightQueryText` 的思路但全新实现。

### 6.6 命令执行

- `BASIC` → `ShellCommandExecutor.runBasic(cmd)`
- `SHIZUKU` → `ShellCommandExecutor.runShizuku(cmd)`（需 `Shizuku.newProcess`）
- `ROOT` → `RootAdbBridge.execOnActiveDevice(cmd)`（`adb -s <serial> shell <cmd>`，或 root shell）
- `OTG` → `OtgRepositoryImpl.runOtgCommand(cmd)`（复用，`AdbConnection.open("shell:$cmd")`）

输出按行流式 emit（复刻 `ShellViewModel` 的 250ms / 100 行批量策略，新写 VM）。

### 6.7 FAB 菜单

复刻 connection 思路：[加载预置命令] / [添加自定义命令] / [书签]

---

## 7. 状态通道系统

### 7.1 统一状态模型

core:app 新建 `ConnectionStatusAggregator`。每个通道归一化为 `ChannelStatus`：

```kotlin
enum class ChannelLevel { GREEN, YELLOW, RED }

data class ChannelStatus(
    val channel: Channel,        // SHIZUKU / ROOT_ADB / OTG / WIFI_ADB
    val level: ChannelLevel,
    val label: String,           // "已连接"/"连接中"/"未连接"
    val deviceName: String? = null,
)
```

### 7.2 状态来源映射

| 通道 | 来源 StateFlow | → ChannelStatus |
|---|---|---|
| Shizuku | `ShizukuPermissionHandler.permissionGranted` | true→GREEN「已授权」/ false→RED「未授权」 |
| Root | `RootManager.rootState` | GRANTED→GREEN「已root」/ PROBING→YELLOW / DENIED→RED |
| Root ADB 设备 | `RootAdbBridge.deviceList` | 有活动设备→GREEN+设备名 / 无→YELLOW「无活动设备」 |
| OTG | `OtgConnection.state` | Connected→GREEN+设备名 / Searching/Found→YELLOW / Idle→RED |
| WiFi ADB | `WifiAdbConnection.state`+`currentDevice` | Connected→GREEN / Connecting→YELLOW / Idle→RED |

### 7.3 显示位置

落实"状态显示在各自卡片上"：
- 每张连接卡片右上角：该通道的 `StatusDot` + 文字 label
- BottomSheet 顶部总览条：三/四个 `StatusDot` 横排 + 统一「↻刷新」按钮
- adb 命令执行页顶部活动连接条：当前通道的 `StatusDot`

### 7.4 刷新按钮

点击 → 并行触发：
- `ShizukuPermissionHandler.refreshPermissionState()`
- `RootManager.probe()`
- `OtgRepositoryImpl.searchDevices()`
- `WifiAdbRepositoryImpl.refresh()`

刷新中所有 YELLOW 闪烁，完成后归位。

---

## 8. UI 设计语言

### 8.1 设计基调

深色科技感 + 毛玻璃层次。core:app 已依赖 Haze（毛玻璃库），充分利用。

### 8.2 配色 DeviceConnectionTheme

新写，独立于 connection 的 `SeedColors`：
- 背景：`#0D1117`（深炭黑）
- 卡片层 1：`#161B22`（面板）
- 卡片层 2：`#1F2630`（高亮卡片）
- 描边：`#30363D`
- 主色：`#3B82F6`（电光蓝，连接/操作）
- 状态色：绿 `#22C55E` / 黄 `#EAB308` / 红 `#EF4444`
- 文字：主 `#F0F6FC` / 次 `#8B949E`
- 强调渐变：蓝→紫 `#3B82F6`→`#8B5CF6`（用于主操作按钮）

### 8.3 控件样式

全部新写：
- **卡片**：`RoundedCornerShape(20.dp)` + 1dp 描边 + 内部左侧色条（按通道色：Shizuku 蓝 / Root 紫 / OTG 青）
- **按钮**：主操作填色渐变 + 圆角 14dp；次操作描边 + 半透明背景
- **状态点**：12dp 圆 + YELLOW 脉冲动画 + 外圈光晕
- **弹窗/Sheet**：`RoundedCornerShape(28.dp)` 顶部圆角 + Haze 毛玻璃背景 + 顶部小拖拽条
- **输入框**：圆角 12dp + 半透明填充 + 聚焦时主色描边
- **控制台**：等宽字体 + 深黑底 `#0D1117` + 行级高亮

### 8.4 弹窗统一规范

- 所有 Sheet 用 `ModalBottomSheet`（Material3）+ 自定义 Haze 背景
- 所有 Dialog 用全屏 `DialogFragment` 或 Compose `Dialog` + 圆角 24dp
- 配对 / 设备选择 / 管理器选择均用同款 `OptionSheet` 模板（图标 + 标题 + 副标题 + 操作）

### 8.5 布局样式

- BottomSheet 内：竖向 `LazyColumn` + 12dp 间距 + 16dp 内边距
- 卡片内：`Row`（左图标 + 中标题 + 右状态点）+ 底部按钮行
- 全屏 Fragment：`Scaffold` + `TopAppBar` + 内容区，TopAppBar 用 Haze 半透明

---

## 9. 新建文件清单

### 9.1 core:app 新建

```
core/app/src/main/java/com/itsaky/androidide/
├── fragments/debugger/
│   ├── DeviceConnectionBottomSheet.kt          # 重写（入口）
│   ├── PairingOwnDeviceFragment.kt             # 新建（全屏）
│   └── PairingOtherDeviceFragment.kt           # 新建（全屏）
├── fragments/debugger/console/
│   ├── AdbConsoleFragment.kt                   # 新建（全屏）
│   ├── AdbConsoleScreen.kt                     # 新建（Compose UI）
│   └── AdbConsoleViewModel.kt                  # 新建（@HiltViewModel）
├── fragments/debugger/connection/
│   ├── DeviceConnectionSheetContent.kt         # 新建（Sheet 内容）
│   ├── WirelessAdbCard.kt                      # 新建
│   ├── RootCard.kt                             # 新建
│   ├── OtgCard.kt                              # 新建
│   ├── StatusDot.kt                            # 新建
│   ├── StatusOverviewBar.kt                    # 新建
│   ├── PairModeChooseSheet.kt                  # 新建
│   ├── GuideSheet.kt                           # 新建
│   ├── OtgWaitingSheet.kt                      # 新建
│   ├── RootManagerPickerSheet.kt               # 新建
│   └── RootAdbDeviceSheet.kt                   # 新建
├── debugger/root/                              # 新建包
│   ├── RootManager.kt                          # 新建
│   ├── RootAdbBridge.kt                        # 新建
│   ├── RootState.kt                            # 新建
│   └── RootAdbDevice.kt                        # 新建
├── debugger/connection/status/                 # 新建包
│   ├── ConnectionStatusAggregator.kt           # 新建
│   ├── ChannelStatus.kt                        # 新建
│   └── Channel.kt                              # 新建
└── ui/theme/deviceconnection/                  # 新建包
    ├── DeviceConnectionTheme.kt                # 新建
    ├── DeviceConnectionColors.kt               # 新建
    └── DeviceConnectionComponents.kt           # 新建
```

### 9.2 core:app 修改

- `core/app/build.gradle.kts`：新增 `implementation(project(":debugger:adb-connection:connection"))`
- `DeviceConnectionBottomSheet.kt`：重写

### 9.3 不改动

- connection 模块所有文件
- `OpenDeviceConnectionAction.kt`、`debuggerPrefExts.kt`（调用方）

---

## 10. 风险与注意事项

1. **adb 二进制体积**：内置静态编译 adb 二进制会增大 APK 体积，需评估是否按 ABI 分包或仅保留 arm64
2. **Hilt 组件冲突**：connection 模块已用 kapt 处理 Hilt + Room，core:app 也用 kapt，依赖后需验证 kapt 处理器不冲突
3. **Manifest 合并**：connection 模块声明了 ShizukuProvider / SelfPairingService / AdbConnectionService / USB 权限，需验证 core:app 的 Manifest 合并无冲突
4. **Root ADB 二进制兼容性**：静态编译的 adb 在不同 Android 版本（尤其 SELinux 策略）下可能无法 start-server，需降级到 adblib 后端
5. **KernelSU/Magisk API 对接**：需引入其 SDK 或通过 shell 命令交互，需评估依赖体积与稳定性

---

## 11. 验收标准

1. `DeviceConnectionBottomSheet` 不再包含任何 JDWP 相关代码
2. 打开 Sheet 显示三张连接卡片 + 状态通道总览条 + 刷新按钮
3. 每张卡片右上角显示对应通道的红黄绿状态点
4. 无线 ADB 卡片三按钮（指南/配对/启动）功能可用，配对走 SelfPairingService
5. Root 卡片支持标准 su / KernelSU / Magisk / APatch 探测与授权
6. Root 授权后「ADB 设备」按钮可用，能连接本机/无线/USB 设备并设为活动
7. OTG 卡片能等待 USB 设备插入并连接
8. adb 命令执行页在无连接时禁用，有连接时按所选通道执行命令
9. 命令示范列表来自 CommandDatabase，支持搜索/筛选/排序/高亮
10. 所有 UI 使用 DeviceConnectionTheme 全新设计语言