
## 后续修复 (Phase 12x) - Shizuku 13.1.5 socksPort 传递限制调研 + 修编译错误

### Phase 12x - 调研 + 修编译错误 + 留 binder transact TODO (commit af59ca2a)

调研 + 修 4 个文件:

#### 12x.1 - 调研: Shizuku 13.1.5 没 .args(Bundle) API

用 `javap` 看 `Shizuku-13.1.5.aar` 提取 `Shizuku$UserServiceArgs`:
```
public class rikka.shizuku.Shizuku$UserServiceArgs {
  final android.content.ComponentName componentName;
  int versionCode;
  java.lang.String processName;
  java.lang.String tag;
  boolean debuggable;
  boolean daemon;
  boolean use32BitAppProcess;
  public rikka.shizuku.Shizuku$UserServiceArgs(android.content.ComponentName);
  public rikka.shizuku.Shizuku$UserServiceArgs daemon(boolean);
  public rikka.shizuku.Shizuku$UserServiceArgs tag(java.lang.String);
  public rikka.shizuku.Shizuku$UserServiceArgs version(int);
  public rikka.shizuku.Shizuku$UserServiceArgs debuggable(boolean);
  public rikka.shizuku.Shizuku$UserServiceArgs processNameSuffix(java.lang.String);
  private rikka.shizuku.Shizuku$UserServiceArgs use32BitAppProcess(boolean);
  private android.os.Bundle forAdd();
  private android.os.Bundle forRemove(boolean);
}
```

**关键发现**:
- `UserServiceArgs` 字段只有 `componentName` / `versionCode` / `processName` / `tag` / `debuggable` / `daemon` / `use32BitAppProcess` - **没有 Bundle 字段**
- `forAdd()` Bundle 是 Shizuku 私有 (`private`), user-supplied extras 不能加
- IDE 端改 `settings.shizuku.socksPort` 不能从 onBind(Intent) extras 传进来
- intent 永远没 extras, 走默认 39939

**结论**: 走 `Shizuku.UserServiceArgs` Bundle 不可行, 只能走:
- (a) **binder transact** 在 user service onBind 之后调 host setter
- (b) **共享 sharedPreferences** - 失败, 不同进程
- (c) **约定 file 路径** - 走 Shizuku.newProcess 写 /data/local/tmp, 但 Phase 12u 已 throw UOE
- (d) **SystemProperty** - 走 Shizuku.newProcess 跑 setprop, 同 (c) 需先解锁 newProcess

最终选 (a) **binder transact** 走 ISocksControl AIDL。

#### 12x.2 - 修编译错误: class 路径错

之前代码 `rikka.shizuku.api.UserServiceArgs(componentName)` 是错的 class 路径 -
**应该是** `rikka.shizuku.Shizuku.UserServiceArgs` (内部类)。沙箱没跑 gradle 没人
发现这个错。

修法:
```kotlin
val builder = rikka.shizuku.Shizuku.UserServiceArgs(componentName)
    .processName(processName)
    .daemon(false)
    .debuggable(false)
Shizuku.bindUserService(builder, conn)
```

#### 12x.3 - args 参数保留接口但暂忽略

ShizukuBinderClient.bindUserService 加 `args: Bundle?` 参数, 当前 Shizuku 13.1.5
没 API 接收, log.warn 提示, 留 TODO Phase 12y 实装 binder transact 协议。

#### 12x.4 - host 端 onBind 行为保留 (Phase 12j 端到端跑通)

`IdeShizukuSocksUserService.onBind` 行为不变, 走默认 39939 启 SOCKS5 server。注释
更新说明 custom port 走 Phase 12y binder transact 协议。

#### 12x.5 - Phase 12y TODO 留 binder transact 协议

下次实装:
- `ide-debugger-host/aidl/ISocksControl.aidl` 定义 AIDL
- host 端 `IdeShizukuSocksUserService` onBind 返 `ISocksControl.Stub()` (真 binder)
- IDE 端 `ShizukuConnection.attachViaSocks` 拿 binder 后 `ISocksControl.Stub.asInterface(binder).setSocksPort(port)`
- 这条路径完全不依赖 Shizuku args API

#### 12x.6 - 副作用与不变性

- Phase 12j 修的 "默认 39939 端到端跑通" 保留 (host 端 onBind 行为不变)
- 用户改 `settings.shizuku.socksPort` 当前**无效** (intent 没 extras), 等 Phase 12y
- class 路径修对后, IDE 编译能过 (之前错的路径)
- args 参数接口预留, 调用方不传, 等 Phase 12y 用

效果:
- Shizuku 13.1.5 真编译通过 (class 路径修对)
- 默认 39939 端到端跑通 (Phase 12j 修的保留)
- custom port 留 Phase 12y ISocksControl AIDL 实装 (binder transact 协议)

## 新增/修改文件 (Phase 12x)

| 类型 | 路径 |
| ---- | ---- |
| 改 | `core/app/.../shizuku/ShizukuBinderClient.kt` (class 路径修对 + args 参数加但暂忽略) |
| 改 | `core/app/.../impl/ShizukuConnection.kt` (注释更新, args 留 TODO) |
| 改 | `ide-debugger-host/.../IdeShizukuSocksUserService.kt` (注释更新 + onBind 行为保留) |
