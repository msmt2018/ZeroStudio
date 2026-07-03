# Phase 13l - SubPathCapability 测试验证 (TODO)

## 状态

**未完成** - 沙箱无 test runner (无 JUnit / Mockito / Truth 依赖, gradle 跑不了)。

用户明确指令 "不用浪费时间去写测试单元源码。直接按计划进行开发", 所以 Phase 13l
没写测试代码, 留 TODO 文档化, 等有环境的开发者补测试。

## 背景

`ShizukuAutoSubPathProbe` 走 4 个 `ShizukuSubPathCapability` (WifiAdb / Binder /
InHostPlugin / Socks) 按顺序探测, 选第一个 `isAvailable() == true` 的。

Phase 13l 目标: 给 4 个 capability 各加 5 connection 集成测试 (共 20 个),
验证探测顺序 / 优先级 / fallback 行为。

## 沙箱限制

- 无 `gradle` 跑 `:app:testDebugUnitTest`
- 无 `junit` / `mockk` / `truth` 依赖 (写测试也跑不了)
- Shizuku 13+ Binder 死链 / Socks 失败 mock 写不了

## 测试矩阵 (留给有环境的开发者)

### Unit 测试 (`:app:testDebugUnitTest`)

```kotlin
// ShizukuSubPathCapabilityTest.kt
class ShizukuSubPathCapabilityTest {
    @Test fun wifiAdb_isAvailable_returnsTrue_when_adbInstallPath_in_PATH() { ... }
    @Test fun wifiAdb_isAvailable_returnsFalse_when_adbNotFound() { ... }
    @Test fun binder_isAvailable_returnsTrue_when_shizukuBinder_alive() { ... }
    @Test fun binder_isAvailable_returnsFalse_when_shizukuBinder_dead() { ... }
    @Test fun inHostPlugin_isAvailable_returnsTrue_when_hostAppInstalled() { ... }
    @Test fun inHostPlugin_isAvailable_returnsFalse_when_hostAppMissing() { ... }
    @Test fun socks_isAvailable_returnsTrue_when_userServiceBindable() { ... }
    @Test fun socks_isAvailable_returnsFalse_when_userServiceBindFails() { ... }
}

// ShizukuAutoSubPathProbeTest.kt
class ShizukuAutoSubPathProbeTest {
    @Test fun probe_returnsFirstAvailable_in_priorityOrder() { ... }
    @Test fun probe_returnsNull_whenAllCapabilitiesUnavailable() { ... }
    @Test fun probe_skipsDeadBinder_andTriesNextCapability() { ... }
    @Test fun probe_cachesResult_within_same_session() { ... }
    @Test fun probe_clearsCache_when_sessionEnded() { ... }
}
```

### 集成测试 (`:app:connectedAndroidTest`)

```kotlin
@RunWith(AndroidJUnit4::class)
class ShizukuSubPathIntegrationTest {
    @Test fun shizukuWifiAdb_attach_realDevice_works_endToEnd() { ... }
    @Test fun shizukuBinder_attach_realDevice_works_endToEnd() { ... }
    @Test fun shizukuInHostPlugin_attach_realDevice_works_endToEnd() { ... }
    @Test fun shizukuSocks_attach_realDevice_works_endToEnd() { ... }
    @Test fun shizukuAutoSubPath_fallback_works_when_wifiAdb_dead() { ... }
}
```

### 5 connection 集成测试 (跟 4 capability 配对)

| Connection | Capability 组合 | 期望 fallback |
| ---------- | --------------- | ------------- |
| AidlSocket | (N/A, 走 AIDL) | N/A |
| Shizuku | WifiAdb → Binder → InHostPlugin → Socks | Socks 是终极 fallback |
| Root | (N/A, 走 su) | N/A |
| InnetVmSocks | (N/A, 走 SOCKS5) | N/A |
| InnetVmAdb | (N/A, 走 adb connect) | N/A |
| UsbLan | (N/A, 走 adb devices) | N/A |

## 已知风险

| 风险 | 应对 |
| ---- | ---- |
| `Shizuku.pingBinder()` 行为不可靠 (Shizuku 13 已知 bug) | 加 retry, 最多 3 次, 每次 100ms 间隔 |
| Socks 探测需要 user service bind, 慢 (1-2s) | 加 timeout 5s, 超时返 false |
| Binder 探测跟 IDE settings 联动 (Settings.shizuku.binderDead) | mock Settings, 测边界 |
| multi-process host app (Phase 13j) | 测 ContentProvider 在 :debug 场景的探测 |

## 限制

- 沙箱无法验证
- Phase 13l 测全 4 capability × 5 connection = 20 测试 case 留给有环境的人
- 用户明确说不写测试, 所以只留 TODO 文档化

## 相关文件

- `core/app/.../shizuku/capability/ShizukuSubPathCapability.kt` (interface)
- `core/app/.../shizuku/capability/WifiAdbCapability.kt`
- `core/app/.../shizuku/capability/BinderCapability.kt`
- `core/app/.../shizuku/capability/InHostPluginCapability.kt`
- `core/app/.../shizuku/capability/SocksCapability.kt`
- `core/app/.../shizuku/ShizukuAutoSubPathProbe.kt` (工厂 + 探测顺序)
- `core/app/src/test/.../shizuku/capability/` (TODO 测试目录)
- `docs/superpowers/specs/2026-07-02-debugger-connection-layer-subproject-9.md` (sub-project 9 spec)

## 后续

- 等有环境的开发者补 20 个测试 case
- Phase 7 / Phase 10 验证完后再回头补 Phase 13l 测试
