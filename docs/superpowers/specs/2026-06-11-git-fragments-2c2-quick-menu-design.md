# 2c2 不常用功能整合到快捷菜单

| 字段 | 值 |
| --- | --- |
| 父 spec | `2026-06-11-git-fragments-refactor-roadmap.md` |
| 前置 | 2a1 (puppygit 基础设施) / 2c1 (popup 用户名/邮箱预填) |
| 后置 | (末端,无依赖) |
| 范围 | `core/app/.../fragments/git/menu/GitPopupManager.kt` |
| 状态 | draft, 实施中 |

## 1. 背景

`GitPopupManager` 当前把 git UI 相关的所有"低频"配置入口都塞进同一个
`PopupWindow`，导致：

1. **入口分散**：username/email 配置挂在 header 头像里（点击 header
   才弹 `AskGitUsernameAndEmailDialog`），init repository 在
   "Operations" 区段里。两者**视觉上不相邻**，用户找不到。
2. **占位死链**：底部 "Settings" 菜单项是 `git_settings_under_construction`
   toast 占位，没有任何实现。
3. **header 含义模糊**：把"配置入口"塞进"用户信息"头部，违反单一职责
   —— header 应当**只显示**当前用户，点击应该是"看详情"而不是"改东西"。

本 sub-spec 把 username/email 配置和 init repository **整合到一个
"快捷设置"区段**，header 改为纯只读。

## 2. 目标 / 非目标

### 2.1 目标

- popup 顶部 header 改为**只读**（仍显示 username/email + 眼睛切换，但
  不再绑点击事件 → 弹设置对话框）
- 新增 "快捷设置" 区段，含两个菜单项：
  - "设置用户名 / 邮箱" → 触发 `showSetUserInfoDialog`（2c1 修复后）
  - "初始化仓库" → 触发 `initRepositoryIfNeeded`
- 移除底部占位 "Settings" 死链 toast
- popup 区段结构变为（自上而下）：

  ```
  ┌───────────────────────────────┐
  │ [avatar]  username            │  ← header 只读
  │            email**** [eye]    │
  ├───────────────────────────────┤
  │ [icon] GitHub Token           │  ← 唯一保留在"凭据"区段的项
  │         凭据                  │
  ├───────────────────────────────┤
  │ ── 仓库操作 ──                │
  │ [icon] 初始化仓库             │  ← 从"快捷设置"移出,放在操作区
  ├───────────────────────────────┤
  │ ── 快捷设置 ──                │
  │ [icon] 设置用户名 / 邮箱       │  ← 新增,合并自原 header 点击
  └───────────────────────────────┘
  ```

### 2.2 非目标

- 不动 `AskGitUsernameAndEmailDialog` / 2c1 的预填逻辑
- 不动 token 凭据菜单（`showTokenCredentialDialog`）
- 不动 `initRepositoryIfNeeded` 实现
- 不动 popup 框架（保留 `PopupWindow` 实现）
- 不动 fragment / 工具栏

## 3. 架构 / 改动点

### 3.1 popup 重排

`GitPopupManager.show(anchor)` 当前结构：

```
1. setupHeader()        — 头像+用户+邮箱,header click 触发 setUserInfoDialog
2. addDivider()
3. addMenuItem(token)   — GitHub Token 凭据
4. addDivider()
5. addSectionTitle(Operations)
6. addMenuItem(initRepo)
7. addDivider()
8. addMenuItem(settings) — 死链 toast
```

新结构：

```
1. setupHeader()        — 头像+用户+邮箱,只读;点击不再绑任何动作
2. addDivider()
3. addMenuItem(token)   — GitHub Token 凭据
4. addDivider()
5. addSectionTitle(Operations)
6. addMenuItem(initRepo) — 仍在"仓库操作"区段
7. addDivider()
8. addSectionTitle(QuickSettings)  ← 新增
9. addMenuItem(setUserInfo)        ← 新增,触发 showSetUserInfoDialog
10.(去掉原占位 settings 死链)
```

init repository 保留在"仓库操作"区段（"操作"含义贴合）。新增"快捷设置"
区段只放"用户身份配置"一项（更聚焦）。

### 3.2 header 只读

`setupHeader()` 移除 `headerView.setOnClickListener { showSetUserInfoDialog() }`。
header 仍保留 `btnEye` 切换邮箱显示 / 隐藏（属于"显示"操作，不属于"配置"）。

### 3.3 关键改动

- **`GitPopupManager.kt::show`**：调整调用顺序，移除 settings 死链,
  新增 "快捷设置" 区段和 "设置用户名 / 邮箱" 菜单项
- **`GitPopupManager.kt::setupHeader`**：移除 `setOnClickListener`
- 新增字符串资源 (若需要)：
  - `git_quick_settings_section` = "快捷设置"
  - `git_set_user_info_title` = "设置用户名 / 邮箱"
  - `git_set_user_info_subtitle` = "设置全局 git 用户身份"

> 字符串资源走 `core/app/src/main/res/values/strings.xml` 添加;若已存在
> 复用之。

## 4. 不变量

- `AskGitUsernameAndEmailDialog` 行为不变（2c1 修复后已预填）
- `initRepositoryIfNeeded` 行为不变
- `showTokenCredentialDialog` 行为不变
- `refreshUserInfo` 行为不变（仍由 showSetUserInfoDialog 的 onOk 触发）
- 0 行 git24j / puppygit 内部 import 新增（仅复用现有的）
- popup 框架（PopupWindow + container LinearLayout）不变
- `btnGitMenu` 触发入口不变（`GitHostFragment` 仍调 `popupManager?.show(anchorView)`）

## 5. 验证

| 验证项 | 方式 |
| --- | --- |
| header 点击不再触发 `showSetUserInfoDialog` | code review（移除 setOnClickListener） |
| "快捷设置"区段标题为新增字符串 | code review |
| "设置用户名 / 邮箱" 菜单点击调 `showSetUserInfoDialog` | code review |
| "初始化仓库" 仍在"仓库操作"区段 | code review |
| 死链 "Settings" toast 被移除 | `git grep` `git_settings_under_construction` |
| 编译通过 | code review（沙箱无网络 gradle 编译跳过） |

## 6. 风险

| 风险 | 缓解 |
| --- | --- |
| 用户习惯了"点 header 改配置" | 显式添加新的"快捷设置"区段;后续可在 release notes / commit message 中说明迁移 |
| 字符串资源重复 | 优先复用 `core/app/src/main/res/values/strings.xml` 中已存在的 `git_*` key;不存在才新增 |

## 7. 关联

- 父 spec: `2026-06-11-git-fragments-refactor-roadmap.md`
- 前置: 2c1 (`showSetUserInfoDialog` 预填修复)
- 调用 puppygit API: `com.catpuppyapp.puppygit.utils.Libgit2Helper.saveGitUsernameAndEmailForGlobal`
