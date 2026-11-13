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

<details close>
<summary>⚖️ <strong>🍀 Core projects referenced in project integration</strong></summary>

## Thank you to the developers for their outstanding contributions to open source.
-> The order of arrangement is not important.

 - [x] (sora editor)[https://github.com/Rosemoe/sora-editor]
 - [x] (PuppyGit)[https://github.com/catpuppyapp/PuppyGit]
 - [x] (rikkahub)[https://github.com/rikkahub/rikkahub] 
 - [x] (termux)[https://github.com/termux/termux-app]

## Thanks to

- **[AndroidIDE](https://github.com/AndroidIDEOfficial/AndroidIDE)** by **[itsaky](https://github.com/itsaky)** and **[AndroidIDEOfficial](https://github.com/AndroidIDEOfficial)** for the original project foundation.
- [Rosemoe](https://github.com/Rosemoe) for the awesome [CodeEditor](https://github.com/Rosemoe/sora-editor)
- [Termux](https://github.com/termux) for [Terminal Emulator](https://github.com/termux/termux-app)
- [Bogdan Melnychuk](https://github.com/bmelnychuk) for [AndroidTreeView](https://github.com/bmelnychuk/AndroidTreeView)
- [George Fraser](https://github.com/georgewfraser) for the [Java Language Server](https://github.com/georgewfraser/java-language-server)
- [Vivek](https://github.com/itsvks19) for [LayoutEditor](https://github.com/itsvks19/LayoutEditor)
- [catpuppyapp](https://github.com/catpuppyapp) for [PuppyGit](https://github.com/catpuppyapp/PuppyGit)
- [rikkahub](https://github.com/rikkahub) for [Rikkahub](https://github.com/rikkahub/rikkahub)

Thanks to all the developers who have contributed to this project.
</details>


## Contact Us
- [Telegram channel](https://t.me/android_zero_studio)
- [Tencent QQ group](https://qm.qq.com/q/FjC6t6XSsU)

<p align="center">
  <a href="https://github.com/msmt2018/ZeroStudio/issues/new?labels=bug&template=BUG.yml&title=%5BBug%5D%3A+">Report a bug</a> &nbsp; &#8226; &nbsp;
  <a href="https://github.com/msmt2018/ZeroStudio/issues/new?labels=feature&template=FEATURE.yml&title=%5BFeature%5D%3A+">Request a feature</a> &nbsp; &#8226; &nbsp;
</p>


<details close>
<summary>⚖️ <strong>IMPORTANT DISCLAIMER / 重要免责声明 (CLICK TO READ)</strong></summary>

### 🛡️ Legal Disclaimer & Liability Waiver (免责声明与责任豁免)

> [!CAUTION]
> **CRITICAL NOTICE: PLEASE READ CAREFULLY BEFORE USE**
> **重要通告：请在使用前仔细阅读本条款，使用本软件即代表您完全接受以下内容**

#### 1. Neutrality of Tool (工具中立性与责任切割)
*   **CN:** `ZeroStudio` 仅作为一款通用集成开发环境（IDE）提供，属于技术生产力工具。**任何使用本 IDE 构建、编译、生成的软件项目、代码或应用程序，均属于用户的个人行为，与 `ZeroStudio` 开源项目、维护团队及贡献者（下称“我们”）完全无关。** 我们不对用户开发的内容、功能、安全性或合法性承担任何明示或暗示的担保或连带责任。
*   **EN:** `ZeroStudio` is provided solely as a general-purpose Integrated Development Environment (IDE) and is a technical productivity tool. **Any software projects, code, or applications built, compiled, or generated using this IDE are the sole acts of the user and are completely independent of the `ZeroStudio` open-source project, its maintenance team, and contributors (hereinafter "We").** We assume no express or implied warranty or joint liability for the content, functionality, security, or legality of what users develop.

#### 2. Strict Prohibition of Malicious Use (严禁恶意用途)
*   **CN:** 严禁使用 `ZeroStudio` 编写、构建或传播任何**恶意代码（Malware）**或用于**非法用途**。
    *   **禁止范围包括但不限于**：勒索软件（Ransomware）、特洛伊木马（Trojans）、计算机病毒、网络蠕虫、间谍软件、僵尸网络控制端、DDOS 攻击工具、非法渗透/入侵工具、流量劫持软件、以及任何旨在窃取数据、破坏计算机系统、损坏硬件设备或干扰网络正常运行的程序。
    *   **后果**：任何利用本工具进行上述违规开发的行为，其后果完全由使用者自行承担，与本项目及开发者无关。
*   **EN:** It is strictly prohibited to use `ZeroStudio` to write, build, or distribute any **Malicious Code** or for any **Illegal Purposes**.
    *   **Prohibited acts include but are not limited to**: Ransomware, Trojans, Computer Viruses, Worms, Spyware, Botnet controllers, DDoS attack tools, Illegal Penetration/Hacking tools, Traffic Hijacking software, and any program intended to steal data, damage computer systems, brick hardware devices, or disrupt network operations.
    *   **Consequences**: The consequences of any such non-compliant development using this tool shall be borne solely by the user This has nothing to do with this project or its developers.。

#### 3. Legal Compliance & Indemnification (法律合规与赔偿)
*   **CN:** 用户必须承诺在**遵守当地国家/地区法律法规**（包括但不限于《中华人民共和国网络安全法》、《计算机信息系统安全保护条例》等）的前提下使用本工具。
    *   **独担责任**：因用户使用本 IDE 开发的软件导致任何第三方损失、法律纠纷或行政处罚，**全部法律责任与赔偿责任由用户独立承担**。
    *   **无关联声明**：项目作者 `github/msmt2018(android_zero）` 及所有贡献者不对用户的任何违法行为背书，亦不承担任何法律责任。
*   **EN:** Users must commit to using this tool in full **compliance with local/national laws and regulations** (e.g., Cybersecurity Laws, Computer Fraud and Abuse Acts).
    *   **Sole Liability**: The user assumes **full legal and indemnification liability** for any third-party losses, legal disputes, or administrative penalties caused by software developed using this IDE.
    *   **Non-Affiliation**: The project author `github/msmt2018(android_zero）` and all contributors do not endorse any illegal acts by users and assume no legal liability whatsoever.

> [!NOTE]
> **Transparency Statement / 透明化声明**
> This project is 100% open-source and transparent. The code is available for public audit to prove it contains no built-in malice. Users are responsible for their own creations.
> 本项目保持 100% 开源透明，代码接受公众审计以证明不包含内置恶意功能。用户需对其创造的内容全权负责。


</details>

## License

ZeroStudio uses file-level licensing because the repository contains original
ZeroStudio code as well as code inherited from AndroidIDE and other third-party
projects.

Original ZeroStudio files that carry the following SPDX identifier are
licensed under all three licenses cumulatively:

```text
SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
```

The `AND` operator means all three licenses apply simultaneously; it is not
a choice between licenses. See the complete license texts:

- [GNU AGPL v3.0 only](./LICENSE-AGPL-3.0-only.txt)
- [GNU GPL v3.0 only](./LICENSE-GPL-3.0-only.txt)
- [Apache License 2.0](./LICENSE-Apache-2.0.txt)

Read [协议.md](./协议.md) for the licensing scope and third-party code policy,
[LICENSE](./LICENSE) for the repository license index, and
[COPYRIGHT_HEADER.txt](./COPYRIGHT_HEADER.txt) for the source-file header.

AndroidIDE-derived and other third-party files retain their original licenses
and copyright notices. The former ZeroStudio Community License (ZSCL) and its
custom non-commercial, organization-size, anti-obfuscation, and other extra
restrictions are withdrawn and no longer apply as ZeroStudio licensing terms.

## Contact the author of ZeroStudio

- Email: 2049219004@qq.com
- WeChat: android_zero
