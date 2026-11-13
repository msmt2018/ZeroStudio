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
under the licenses applicable to each individual source file and component.
This repository contains original ZeroStudio work, AndroidIDE-derived work,
and other third-party components; it is not a single uniformly licensed work.

Users are responsible for the software they create, build, modify, or distribute
with ZeroStudio, including compliance with the licenses and laws applicable to
that software and to any third-party components they use.

---

## License

<p align="center">
  <a href="https://www.gnu.org/licenses/agpl-3.0.html"><img src="https://img.shields.io/badge/License-AGPL%20v3-blue.svg" alt="GNU AGPL v3"></a>
  <a href="https://www.gnu.org/licenses/gpl-3.0.html"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="GNU GPL v3"></a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="Apache License 2.0"></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Commercial_Use-PERMITTED_UNDER_LICENSE_TERMS-success.svg?style=flat-square" alt="Commercial use permitted under license terms">
  <img src="https://img.shields.io/badge/License_Scope-FILE--LEVEL-informational.svg?style=flat-square" alt="File-level license scope">
</p>

<details open>
<summary><strong>📜 ZeroStudio — License Overview & Legal Terms</strong></summary>

### 1. Three-license combination

Original ZeroStudio source files that are owned by the relevant copyright holder or are otherwise properly authorized may be released under the following three licenses **together**:

- **GNU Affero General Public License v3.0 only** — `AGPL-3.0-only`
- **GNU General Public License v3.0 only** — `GPL-3.0-only`
- **Apache License 2.0** — `Apache-2.0`

The combined SPDX expression is:

```text
SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
```

> **Important:** `AND` means the three licenses apply cumulatively to a file carrying this exact SPDX expression. It is not an `OR` choice, and recipients cannot select only one of the three licenses as an alternative.

### 2. Main license terms

The following is a high-level guide to the principal subjects of the three licenses. It is a summary, not a replacement for the complete legal texts.

| License | Main rights and responsibilities |
| --- | --- |
| **AGPL-3.0-only** | Run, study, modify, and share covered software. Comply with applicable copyleft, license-notice, and source-code requirements. When the AGPL network-interaction conditions apply to a modified version, provide users the corresponding opportunity to obtain its source code. |
| **GPL-3.0-only** | Run, study, modify, and redistribute covered software. When distributing covered works or modified versions, comply with applicable source-code, copyright, license-notice, and copyleft requirements. |
| **Apache-2.0** | Use, modify, and distribute covered software, including under its express patent license. Preserve required copyright and license notices, mark significant modifications as required, and comply with applicable patent and NOTICE provisions. |

**Complete license texts**

- [GNU AGPL v3.0 only](./LICENSE-AGPL-3.0-only.txt)
- [GNU GPL v3.0 only](./LICENSE-GPL-3.0-only.txt)
- [Apache License 2.0](./LICENSE-Apache-2.0.txt)

### 3. Where this license combination applies

The three-license combination is **not a blanket license for every file in this repository**. It applies only where the relevant rights holder has the authority to grant it and the file is expressly marked with the combined SPDX expression shown above.

| Source / file type | Applicable licensing boundary |
| --- | --- |
| **Original ZeroStudio code** | May use the three-license combination when the rights holder owns or has obtained the necessary rights and the file carries the exact SPDX expression. |
| **AndroidIDE-derived code** | Retains its applicable AndroidIDE upstream license and copyright notices. It is not automatically relicensed by being included in ZeroStudio. |
| **Third-party components** | RikkaHub, PuppyGit, Termux, Tree-sitter, libgit2, Sora Editor, Eclipse, and other external code retain their respective licenses, notices, and NOTICE requirements. |
| **Mixed-origin files** | Preserve upstream notices and terms. Do not apply the combined expression to the entire file unless all necessary rights have been obtained. Identify ZeroStudio modifications separately where appropriate. |
| **Files without a clear license notice** | Missing headers do not establish ownership or licensing. Verify the file's origin and authorization before adding or changing a license notice. |

### 4. Commercial use, modification, and distribution

ZeroStudio does not add a blanket non-commercial restriction to works validly covered by the three-license combination.

Commercial use, commercial modification, and commercial distribution are **not prohibited merely because the code belongs to ZeroStudio**. They remain subject to all applicable terms of AGPL-3.0-only, GPL-3.0-only, and Apache-2.0, as well as the separate licenses of third-party components.

In particular:

- Preserve applicable copyright, license, attribution, modification, and NOTICE information.
- When distributing covered binaries or modified works, satisfy the source-code and other obligations triggered by the applicable license.
- Comply with the AGPL network-interaction provisions whenever their conditions apply.
- Do not assume this README grants rights to third-party code beyond the permissions granted by its own license.

### 5. Copyright, author attribution, and contributions

Project attribution does not automatically determine file copyright ownership.

- Existing files: preserve original authors, copyright statements, SPDX identifiers, and upstream license notices. Contributors may record their changes in comments where appropriate, commit messages, change logs, or Pull Requests.
- New files: contributors may provide author and copyright notices reflecting actual authorship. They may identify the project with **Project: ZeroStudio**.
- Contributor rights: merging a contribution does not, by itself, transfer the contributor's copyright to the project author or project.
- New-file licensing: a contributor may apply the three-license SPDX expression only when they have the necessary rights and expressly authorize that licensing.

See [协议.md](./协议.md) for the detailed attribution, authorization, modification, and third-party notice policy.

### 6. Independent works created with ZeroStudio

ZeroStudio is a general-purpose development environment. Applications, libraries, source code, and other works independently created, built, or generated by users remain the responsibility of their respective authors and distributors.

Users must determine and comply with the licenses and legal requirements applicable to their own projects, dependencies, and distribution outputs.

### 7. Legal reference files

| File | Purpose |
| --- | --- |
| [`LICENSE`](./LICENSE) | Repository-level license notice and file-level license index. |
| [`协议.md`](./协议.md) | Detailed license scope, third-party boundaries, copyright ownership, and contributor policy. |
| [`COPYRIGHT_HEADER.txt`](./COPYRIGHT_HEADER.txt) | Source-file copyright header template. |
| [AGPL-3.0-only](./LICENSE-AGPL-3.0-only.txt) · [GPL-3.0-only](./LICENSE-GPL-3.0-only.txt) · [Apache-2.0](./LICENSE-Apache-2.0.txt) | Complete license texts. |

### 8. Interpretation and withdrawn license

This README is an explanatory project notice. It is not a separate custom license and does not add permissions or restrictions beyond those validly granted by the relevant rights holders.

The former **ZeroStudio Community License (ZSCL)** and its custom terms have been withdrawn. They are not part of the current three-license combination and must not be treated as additional conditions on files covered by the current SPDX expression.

For each file, consult its valid file-level license notice, the rights held by the relevant copyright holder, applicable upstream notices, and the complete license text. Nothing in this README relicenses third-party works or overrides their original terms.

---

**[📄 Repository LICENSE notice](./LICENSE)** · **[📜 Detailed license and copyright policy](./协议.md)**

</details>

## Contact the author of ZeroStudio

- Email: 2049219004@qq.com
- WeChat: android_zero
