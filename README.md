<p align="center">
  <img src="./docs/images/icons.png" alt="ZeroStudio" width="80" height="80"/>
</p>

<h2 align="center"><b>ZeroStudio</b></h2>
<p align="center">
  An IDE to develop real, Gradle-based Android applications on Android devices.
</p><br>

<p align="center">
<!-- Latest release -->
<img src="https://img.shields.io/github/v/release/ZeroStudioOfficial/ZeroStudio?include_prereleases&amp;label=latest%20release" alt="Latest release">
<!-- Build and test -->
<img src="https://github.com/ZeroStudioOfficial/ZeroStudio/actions/workflows/build.yml/badge.svg" alt="Builds and tests">
<!-- CodeFactor -->
<img src="https://www.codefactor.io/repository/github/ZeroStudioofficial/ZeroStudio/badge/main" alt="CodeFactor">
<!-- Crowdin -->
<a href="https://crowdin.com/project/ZeroStudio"><img src="https://badges.crowdin.net/ZeroStudio/localized.svg" alt="Crowdin"></a>
<!-- License -->
<img src="https://img.shields.io/badge/License-AGPL--3.0--only%20AND%20GPL--3.0--only%20AND%20Apache--2.0-blue.svg" alt="SPDX: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0"></p>

<p align="center">
  <a href="https://docs.ZeroStudio.com/">Explore the docs »</a> &nbsp; &nbsp;
</p>


![page preview](docs/About/pages.png)


## Features

- [x] Gradle support.
- [x] `JDK 11` and `JDK 17` available for use.
- [x] Terminal with necessary packages.
- [x] Custom environment variables (for Build & Terminal).
- [x] SDK Manager (Available via terminal).
- [x] API information for classes and their members (since, removed, deprecated).
- [x] Log reader (shows your app's logs in real-time)
- [ ] Language servers
    - [x] Java
    - [x] XML
    - [x] Kotlin
    - [ ] lua 
    - [ ] cmake 
    - [ ] c/c++/obj-c/obj-c++
    - [ ] web （html，css...)
    - [x] toml
    - [ ] python
    - [ ] shell
    - [ ] more.....
- [ ] UI Designer
    - [x] Layout inflater
    - [x] Resolve resource references
    - [x] Auto-complete resource values when user edits attributes using the attribute editor
    - [x] Drag & Drop
    - [x] Visual attribute editor
    - [x] Android Widgets
    - [ ] Implement a gripper constraint layout similar to Android studio
- [ ] String Translator
- [ ] Asset Studio (Drawable & Icon Maker)
- [x] (Git)[https://github.com/catpuppyapp/PuppyGit] 
- [ ] git CD/CI/PR/workflow container
- [x] (Chat ai)[https://github.com/rikkahub/rikkahub] Support assistant skills, tts ， mcp， Numerous common provider models etc...
- [ ] Preview function
    - [x] AndroidX Jetpack Compose Preview 
    - [x] images Preview
    - [x] Markdown preview
    - [ ] web(html，vue，nodejs，js，etc ...) preview
    - [ ] C/C++ drawing preview
    - [ ] flutter preview
    - [ ] More waiting ...
- [x] Built in Kotlin+JSON written MCP server, tools can be freely defined
- [x] Regular expression preview

To make your Markdown document look more professional and user-friendly (perfect for a GitHub README), I have added formatting elements like tables, blockquotes, and code blocks.

Here is the decorated version:

---
## ⚡️Offline SDK build tool installation package download channel(离线sdk构建工具安装包下载渠道)
[github Release page](https://github.com/msmt2018/SDK-tool-for-Android-platform/releases/tag/ZeroStudio_OfflineResourceInstallationPackage)

[gitee Release page](https://gitee.com/android-zero/ZeroStudio-sdkbuildtool/releases/tag/ZeroStudio-sdk%E6%9E%84%E5%BB%BA%E5%B7%A5%E5%85%B7%E7%A6%BB%E7%BA%BF%E5%AE%89%E8%A3%85%E5%8C%85)

[Google Drive](https://drive.google.com/file/d/1jiFzQXD1HXwzr3zDrYVlGqYxOjua0QMH/view?usp=drive_link)

---

## Installation
[<img src="https://github.com/Kunzisoft/Github-badge/raw/main/get-it-on-github.svg"
    alt="Get it on F-Droid"
    height="80">](https://github.com/msmt2018/ZeroStudio/releases)

> _Please install ZeroStudio from trusted sources only i.e._
> - [_GitHub Releases_](https://github.com/msmt2018/ZeroStudio/releases)
> - [_GitHub Actions_](https://github.com/msmt2018/ZeroStudio/actions?query=branch%3Adev+event%3Apush)
<!-- > - [_F-Droid_](https://f-droid.org/packages/com.itsaky.ZeroStudio/) -->

- Download the ZeroStudio APK from the mentioned trusted sources.
- Follow the
  instructions [here](https://docs.ZeroStudio.com/tutorials/get-started.html) to
  install the build tools.

## Limitations

- For working with projects in ZeroStudio, your project must use Android Gradle Plugin v7.2.0 or
  newer. Projects with older AGP must be migrated to newer versions.
- SDK Manager is already included in Android SDK and is accessible in ZeroStudio via its Terminal.
  But, you cannot use it to install some tools (like NDK) because those tools are not built for
  Android.
- No official NDK support because we haven't built the NDK for Android.

The app is still being developed actively. It's in beta stage and may not be stable. if you have any
issues using the app, please let us know.

## Contributing

See the [contributing guide](./CONTRIBUTING.md).

For translations, visit the [Crowdin project page](https://crowdin.com/project/zerosrudio).

## Disclaimer

ZeroStudio is a general-purpose Android development environment. It is provided
under the terms of the licenses that apply to each individual source file and
component in this repository. The repository contains original ZeroStudio work,
upstream AndroidIDE-derived work, and other third-party components; it is not
a single uniformly licensed work.

Users are responsible for the software they create, build, modify, or distribute
with ZeroStudio, including compliance with the licenses and laws applicable to
that software and to any third-party components they use.

## License

### ZeroStudio 三协议组合许可

ZeroStudio 的原创项目代码采用以下三份开源许可证组成的**组合许可协议组**：

<blockquote>
<strong>GNU Affero General Public License v3.0 only（AGPL-3.0-only）</strong><br/>
<strong>GNU General Public License v3.0 only（GPL-3.0-only）</strong><br/>
<strong>Apache License 2.0（Apache-2.0）</strong>
</blockquote>

本项目使用的 SPDX 许可证表达式为：

```text
SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
```

这三份许可证共同构成 ZeroStudio 原创代码采用的许可协议组。这里的
`AND` 是 SPDX 许可证表达式中的逻辑“与”，表示列出的三份许可证条件
**同时适用、需要一并遵守**，并不是让使用者从 AGPL、GPL 或 Apache
中任选一份，也不是三种互相独立的可选授权方案。不能只挑选其中条件较宽松
的一份来代替整个组合许可。

### 三份许可证分别包含什么

| 许可证 | 主要许可内容与要求 |
| --- | --- |
| **GNU AGPL v3.0 only** | 以 GNU GPL v3.0 为基础，并针对通过网络向用户提供修改版程序交互服务的情形规定相应的源代码提供义务。具体触发条件、对应源代码范围和履行方式以 AGPL-3.0 正文为准。 |
| **GNU GPL v3.0 only** | 允许使用、研究、修改和再分发程序；在分发受其约束的作品或修改版本时，须遵守相应的版权声明、许可证文本、源代码提供及同等许可等条件。具体义务以 GPL-3.0 正文为准。 |
| **Apache License 2.0** | 提供较宽松的使用、修改和分发许可，并包含明确的专利授权条款；再分发时仍须遵守许可证要求的版权、专利、修改声明、许可证副本及 NOTICE 文件等条件。具体义务以 Apache-2.0 正文为准。 |

**请注意：** 上表仅用于帮助理解每份许可证的主要特点，并不意味着三份许可证可以
拆开选择或分别适用于同一文件的不同使用场景。对于明确标注上述 SPDX 表达式的
作品，应将三份许可证作为一个整体阅读和遵守；发生具体法律问题时，应以对应的
完整许可证文本为准。

### 完整许可证文本

以下文件包含三份许可证的完整正文：

1. [GNU Affero General Public License v3.0 only（AGPL-3.0-only）](./LICENSE-AGPL-3.0-only.txt)
2. [GNU General Public License v3.0 only（GPL-3.0-only）](./LICENSE-GPL-3.0-only.txt)
3. [Apache License 2.0（Apache-2.0）](./LICENSE-Apache-2.0.txt)

### 哪些文件适用这组三协议

这组三协议**不是对整个 Git 仓库所有文件的一揽子重新许可**。它仅适用于
由相应版权持有人原创、拥有必要版权或再许可权利，并且文件中明确标注以下
SPDX 表达式的作品：

```text
SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
```

判断文件许可证时，应按以下原则处理：

- **ZeroStudio 原创文件：** 由项目作者原创并依法持有相应权利，且明确标注上述 SPDX 表达式的文件，适用这组三协议。
- **AndroidIDE 上游及其衍生文件：** 继承、复制或修改自 AndroidIDE 的代码，继续遵循其适用的上游许可证和版权声明。不能仅因文件被放入 ZeroStudio 仓库，就自动改为这组三协议。
- **其他第三方代码：** RikkaHub、PuppyGit、Termux、Tree-sitter、libgit2、Sora Editor、Eclipse 及其他第三方项目的代码，继续遵循各自适用的许可证、版权声明和 NOTICE 要求。
- **混合来源文件：** 应保留上游作者、版权和许可证信息，并准确标记 ZeroStudio 自主修改部分。没有取得全部必要权利人授权时，不得把整个文件统一标注为这组三协议。
- **未标明许可证的文件：** 不应仅凭文件位于本仓库或缺少许可证头，就推断其自动适用这组三协议；应先核实来源、版权归属和授权情况。

### 版权、作者与贡献者署名

ZeroStudio 的项目归属标识与具体文件的作者、版权持有人是不同概念：

- ZeroStudio 原创主源码可以标注 `Project: ZeroStudio`，并保留其实际作者和版权声明。
- 贡献者修复 Bug 或修改现有文件时，应保留原文件已有的作者、版权、许可证和 SPDX 声明；可以在修改位置、提交记录、Pull Request 或变更日志中注明自己的署名及实际修改内容。
- 贡献者独立新建源码文件时，可以按实际创作情况自行署名，并明确该文件的版权归属和许可证；如同时属于 ZeroStudio 项目，可以并列标注 `Project: ZeroStudio`。
- 代码被合并到 ZeroStudio，并不当然构成贡献者著作权向项目作者或项目本身的转让。贡献者授权、署名和修改记录的详细规则见 [协议.md](./协议.md)。

### 许可证与项目文件索引

| 文件 | 说明 |
| --- | --- |
| [LICENSE](./LICENSE) | 仓库根目录的许可证说明和文件级许可证索引。 |
| [协议.md](./协议.md) | ZeroStudio 许可证适用范围、第三方代码边界、版权归属及贡献者署名规则。 |
| [LICENSE-AGPL-3.0-only.txt](./LICENSE-AGPL-3.0-only.txt) | GNU AGPL v3.0 only 完整许可证文本。 |
| [LICENSE-GPL-3.0-only.txt](./LICENSE-GPL-3.0-only.txt) | GNU GPL v3.0 only 完整许可证文本。 |
| [LICENSE-Apache-2.0.txt](./LICENSE-Apache-2.0.txt) | Apache License 2.0 完整许可证文本。 |
| [COPYRIGHT_HEADER.txt](./COPYRIGHT_HEADER.txt) | ZeroStudio 原创源码版权头模板。 |

### 旧版自定义许可说明

此前使用的 ZeroStudio Community License（ZSCL）及其自定义条款已经撤销，
不再作为 ZeroStudio 当前原创代码的许可协议，也不构成上述三协议组合许可的
附加条件。当前适用的许可条件以具体文件的有效 SPDX 声明、对应完整许可证
正文及合法授权范围为准。本 README 和仓库说明文件不另行增加非商业使用、
组织规模、额外商业许可门槛或其他自定义限制。

## Contact the author of ZeroStudio

- Email: 2049219004@qq.com
- WeChat: android_zero
