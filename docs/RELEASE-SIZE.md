# Compose Preview Release 体积报告 (v2.4 P0)

> 本文件由 `scripts/report-release-size.py` 自动生成. 不要手动编辑.
> 上次更新: 2026-06-14 (模板)

## R8 优化策略

### 启用范围

- **Release** preview 启用 R8 minify + resource shrink
- **Debug** preview 不动 (与 Android Studio 行为一致)

### ProGuard 规则覆盖

`proguard-preview-rules.pro` 包含 10 大类规则:

1. **保留 Compose 注解** (`@Composable` / `@Preview` / `@PreviewParameter`) — 反射扫描需要
2. **保留 LiveLiterals 静态 int 字段** — Compose Compiler 1.5+ 生成, 反射写值需要
3. **保留 `@Preview` 标记的 Composable 函数签名** — ComposableRenderer 反射调用
4. **保留 PreviewParameterProvider** — v2.3 P1 反射 `getValues()` 需要
5. **保留 ViewModel 构造** — Hilt 兼容
6. **保留 Coroutines internal 符号** — compose-runtime 引用
7. **保留 Class.getDeclaredConstructor** — 反射 newInstance
8. **关闭 R8 fullMode 警告** (compose-compiler 生成代码)
9. **保留 ASM 反射** (P2-BLD-01 AsmComposeBinder 用)
10. **保留 throw/catch stack 信息** (调试可读)

## 体积基线

(运行 `./gradlew :modules:compose-preview:assembleRelease` 后, 执行
`python3 scripts/report-release-size.py --out docs/RELEASE-SIZE.md` 自动填充)

| 产物 | 大小 | 备注 |
| --- | --- | --- |
| (待生成) |  |  |

## R8 Mapping

(同上, 自动生成 mapping.txt 路径)

## 历史

| 日期 | R8 状态 | 总体积 | 备注 |
| --- | --- | --- | --- |
| 2026-06-14 | 启用 | (待测) | v2.4 P0 首次集成 |
