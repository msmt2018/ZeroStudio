# Phase 7 - BuildTimeInjector 端到端验证 (TODO)

## 状态

**未完成** - 沙箱无 gradle / 无 Android SDK / 无设备, 跑不了完整 build。

## 背景

`BuildTimeInjector` 在 build.gradle 里 transform 时机注入 `DebugAttachProvider`
+ `ContentProvider`, 跟 `ide-debugger-host` 一起打包到 host app。

Phase 7 目标: CI 跑 `gradle :app:assembleDebug` + `:ide-debugger-host:assembleDebug`,
验证 BuildTimeInjector:
1. manifest merger 不冲突 (新加的 ContentProvider 不跟 host app 现有 manifest 冲突)
2. 字节码 transform 成功 (host app MainApplication 不被破坏)
3. 生成的 APK install 到设备能正常 attach

## 沙箱限制

- 无 `gradle` 可执行
- 无 `android-sdk` 目录
- 无 `aapt2` / `d8` / `kotlinc` 等工具
- 沙箱 Linux 不支持 Android build (缺 platform jars)

## 端到端验证步骤 (留给有环境的开发者)

### 本地开发机 (msmt2018 仓库)

```bash
# 1. BuildTimeInjector 不破坏 host app build
./gradlew :app:assembleDebug
./gradlew :ide-debugger-host:assembleDebug
# 期望: BUILD SUCCESSFUL, 0 编译错误, 0 manifest 冲突

# 2. BuildTimeInjector 注入 ContentProvider
./gradlew :ide-debugger-host:assembleDebug --info 2>&1 | grep -i "BuildTimeInjector"
# 期望: 看到 BuildTimeInjector transform 走了, 注入到 host app manifest

# 3. APK 装载到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 期望: install 成功, 没 "INSTALL_FAILED" 错误

# 4. ContentProvider 触发 HELLO
adb shell am start -n com.itsaky.androidide.zerostudio/.MainActivity
# ContentProvider onCreate 自动跑, ContentProvider attach 到 host app
# 看 logcat: "HELLO pkg=... pid=... process=... sdk=... buildVersion=..."
```

### CI (GitHub Actions)

```yaml
- name: Build with BuildTimeInjector
  run: |
    ./gradlew :app:assembleDebug
    ./gradlew :ide-debugger-host:assembleDebug
- name: Verify BuildTimeInjector transform
  run: |
    # 检查 transform 输出
    test -f app/build/intermediates/transforms/BuildTimeInjector/*/injector.log
```

## 已知风险

| 风险 | 应对 |
| ---- | ---- |
| BuildTimeInjector 跟 host app 现有 manifest 冲突 (例如 user 已配 ContentProvider) | merge strategy 设 `merge`, 冲突时 transform 失败抛出明确错误 |
| AGP 升级 (8.x → 9.x) 改 transform API | 留 `// TODO Phase 7.2: 适配 AGP 9 transform API` |
| 字节码 transform 跟 R8/ProGuard 冲突 | R8 默认 keep ContentProvider, 应该 OK, 但需真机验证 |
| multi-process host app 场景 (Phase 13j 已加 process= 字段) | manifest merger 注入 ContentProvider 时强制 default process |

## 限制

- 沙箱无法验证
- 任何 host app 集成 BuildTimeInjector 都要先跑端到端
- BuildTimeInjector 当前实装在 `build-logic/injector/`, 集成在 `:ide-debugger-host:assemble`
  任务里

## 相关文件

- `build-logic/injector/src/main/kotlin/.../BuildTimeInjector.kt` (主实装)
- `build-logic/injector/src/main/kotlin/.../ManifestMerger.kt` (manifest 合并)
- `ide-debugger-host/build.gradle` (apply plugin + classpath)
- `docs/superpowers/specs/2026-07-02-debugger-injection-generator.md` (spec)
- `docs/superpowers/specs/2026-07-02-subproject-11-deployment-checklist.md` (部署检查表)

## 后续 (待 Phase 10/13l 一起收尾)

- Phase 10: 真机 e2e 测 BuildTimeInjector
- Phase 13l: SubPathCapability 测试 (跟 BuildTimeInjector 无关, 独立)
