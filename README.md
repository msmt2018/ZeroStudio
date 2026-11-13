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

### 1. License model

Original ZeroStudio source files that are owned by the relevant copyright holder or are otherwise properly authorized may be released under these three licenses together:

- **GNU Affero General Public License v3.0 only** — `AGPL-3.0-only`
- **GNU General Public License v3.0 only** — `GPL-3.0-only`
- **Apache License 2.0** — `Apache-2.0`

The combined file-level identifier is `AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0`. The operator **AND** means the terms of all three licenses apply cumulatively to a file carrying that exact identifier. It is not an `OR` choice and does not offer three alternative licenses.

The following sections summarize the principal permissions, conditions, and limitations of each license. They are explanatory summaries, not verbatim reproductions or substitutes for the complete legal texts.

### 2. GNU Affero General Public License v3.0 only (AGPL-3.0-only)

**Purpose:** AGPLv3 is a strong copyleft license designed to address modified software made available for remote network interaction.

```text
CORE TERMS — GNU AGPL v3.0

PERMISSIONS
- Run the covered program for any purpose.
- Study how the program works and modify it.
- Copy and redistribute the original program.
- Distribute modified versions and works based on the program.
- Charge a price for copies, distribution, support, or warranty services.

CONDITIONS WHEN YOU MODIFY OR DISTRIBUTE
- Provide recipients with a copy of the AGPLv3 license.
- Preserve applicable copyright, license, and warranty-disclaimer notices.
- Mark modified versions with prominent notices and relevant modification dates.
- When conveying covered object code, provide or offer the Corresponding Source as required by the license.
- License covered modified works under AGPLv3, subject to the license's permitted additional terms.
- Do not impose further restrictions that prevent recipients from exercising the rights granted by the license.
- For covered object code in a User Product, provide required Installation Information when the applicable conditions are met.

REMOTE NETWORK INTERACTION — SECTION 13
- If you modify the program and your modified version supports remote interaction
  over a computer network, users interacting with that version remotely must be
  prominently offered access to its Corresponding Source.
- The source access must be provided without charge through a standard or
  customary means of facilitating software copying.

WARRANTY AND LIABILITY
- The program is generally provided without warranty, unless applicable law or
  a separate written agreement requires otherwise.
- The license limits contributors' liability to the extent permitted by law.
```

Official text: [GNU AGPL v3.0](https://www.gnu.org/licenses/agpl-3.0.html) · [Project license text](./LICENSE-AGPL-3.0-only.txt)

### 3. GNU General Public License v3.0 only (GPL-3.0-only)

**Purpose:** GPLv3 protects users' freedom to run, study, modify, and share covered software through copyleft conditions on distribution.

```text
CORE TERMS — GNU GPL v3.0

PERMISSIONS
- Run the covered program for any purpose.
- Study the program and modify it for your needs.
- Make and distribute copies.
- Distribute modified versions and works based on the program.
- Charge any price for copies and offer paid support or warranty services.

CONDITIONS WHEN YOU DISTRIBUTE
- Provide recipients with a copy of the GPLv3 license.
- Preserve applicable copyright notices and notices stating that the license
  and relevant additional terms apply.
- Preserve notices of the absence of warranty.
- Provide the Corresponding Source, or satisfy an applicable source-offer
  method, when conveying covered object code.
- Mark modified versions with prominent notices and relevant modification dates.
- License a covered modified work as a whole under GPLv3, subject to the
  license's permitted additional terms.
- Ensure recipients receive the same freedoms to run, modify, and redistribute.
- Do not impose further restrictions on the rights granted by the license.
- For covered object code in a User Product, provide required Installation
  Information when the applicable conditions are met.

WARRANTY AND LIABILITY
- The program is generally provided without warranty, unless applicable law
  or a separate written agreement requires otherwise.
- The license limits contributors' liability to the extent permitted by law.
```

Official text: [GNU GPL v3.0](https://www.gnu.org/licenses/gpl-3.0.html) · [Project license text](./LICENSE-GPL-3.0-only.txt)

### 4. Apache License 2.0 (Apache-2.0)

**Purpose:** Apache-2.0 is a permissive license that grants copyright and contributor patent permissions, subject to its redistribution and notice conditions.

```text
CORE TERMS — APACHE LICENSE 2.0

PERMISSIONS
- Use the licensed work for any purpose.
- Reproduce the work and prepare derivative works.
- Modify, publicly display, publicly perform, sublicense, and distribute
  the work in source or object form.
- Exercise the express patent license granted by contributors for patent
  claims necessarily infringed by their contributions or the combined work.

REDISTRIBUTION CONDITIONS
- Give recipients a copy of the Apache License 2.0.
- Mark modified files with prominent notices stating that they were changed.
- Retain applicable copyright, patent, trademark, and attribution notices.
- If the distribution includes a NOTICE file, reproduce the relevant
  attribution notices in an allowed location in the redistributed work.
- You may add your own copyright notices to your modifications.
- You may provide different terms for your own modifications or derivative
  works as a whole, provided the original license conditions are respected.

PATENTS AND TRADEMARKS
- The contributor patent license is subject to the license conditions.
- If you initiate patent litigation alleging that the work or a contribution
  constitutes patent infringement, the patent licenses granted for that
  work terminate as described in the license.
- The license does not grant general permission to use the licensor's
  trademarks, trade names, or product names.

WARRANTY AND LIABILITY
- The work is generally provided on an "AS IS" basis, without warranties,
  unless applicable law or a written agreement provides otherwise.
- The license limits contributors' liability to the extent permitted by law.
```

Official text: [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) · [Project license text](./LICENSE-Apache-2.0.txt)

> **Important:** The three code blocks above are plain-language summaries of major license provisions. They are not the complete legal texts. The original license texts linked above control and must be consulted for exact definitions, conditions, exceptions, and procedures.

### 5. Where this license combination applies

The three-license combination is **not a blanket license for every file in this repository**. It applies only where the relevant rights holder has the authority to grant it and the file is expressly marked with the combined identifier `AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0`.

| Source / file type | Applicable licensing boundary |
| --- | --- |
| **Original ZeroStudio code** | May use the three-license combination when the rights holder owns or has obtained the necessary rights and the file carries the exact identifier. |
| **AndroidIDE-derived code** | Retains its applicable AndroidIDE upstream license and copyright notices. It is not automatically relicensed by being included in ZeroStudio. |
| **Third-party components** | RikkaHub, PuppyGit, Termux, Tree-sitter, libgit2, Sora Editor, Eclipse, and other external code retain their respective licenses, notices, and NOTICE requirements. |
| **Mixed-origin files** | Preserve upstream notices and terms. Do not apply the combined identifier to the entire file unless all necessary rights have been obtained. Identify ZeroStudio modifications separately where appropriate. |
| **Files without a clear license notice** | Missing headers do not establish ownership or licensing. Verify the file's origin and authorization before adding or changing a license notice. |

### 6. Commercial use, modification, and distribution

ZeroStudio does not add a blanket non-commercial restriction to works validly covered by the three-license combination.

Commercial use, commercial modification, and commercial distribution are **not prohibited merely because the code belongs to ZeroStudio**. They remain subject to all applicable terms of AGPL-3.0-only, GPL-3.0-only, and Apache-2.0, as well as the separate licenses of third-party components.

In particular:

- Preserve applicable copyright, license, attribution, modification, and NOTICE information.
- When distributing covered binaries or modified works, satisfy the source-code and other obligations triggered by the applicable license.
- Comply with the AGPL network-interaction provisions whenever their conditions apply.
- Do not assume this README grants rights to third-party code beyond the permissions granted by its own license.

### 7. Copyright, author attribution, and contributions

Project attribution does not automatically determine file copyright ownership.

- Existing files: preserve original authors, copyright statements, SPDX identifiers, and upstream license notices. Contributors may record their changes in comments where appropriate, commit messages, change logs, or Pull Requests.
- New files: contributors may provide author and copyright notices reflecting actual authorship. They may identify the project with **Project: ZeroStudio**.
- Contributor rights: merging a contribution does not, by itself, transfer the contributor's copyright to the project author or project.
- New-file licensing: a contributor may apply the three-license identifier only when they have the necessary rights and expressly authorize that licensing.

See [协议.md](./协议.md) for the detailed attribution, authorization, modification, and third-party notice policy.

### 8. Independent works created with ZeroStudio

ZeroStudio is a general-purpose development environment. Applications, libraries, source code, and other works independently created, built, or generated by users remain the responsibility of their respective authors and distributors.

Users must determine and comply with the licenses and legal requirements applicable to their own projects, dependencies, and distribution outputs.

### 9. Legal reference files

| File | Purpose |
| --- | --- |
| [`LICENSE`](./LICENSE) | Repository-level license notice and file-level license index. |
| [`协议.md`](./协议.md) | Detailed license scope, third-party boundaries, copyright ownership, and contributor policy. |
| [`COPYRIGHT_HEADER.txt`](./COPYRIGHT_HEADER.txt) | Source-file copyright header template. |
| [AGPL-3.0-only](./LICENSE-AGPL-3.0-only.txt) · [GPL-3.0-only](./LICENSE-GPL-3.0-only.txt) · [Apache-2.0](./LICENSE-Apache-2.0.txt) | Complete license texts. |

### 10. Interpretation and withdrawn license

This README is an explanatory project notice. It is not a separate custom license and does not add permissions or restrictions beyond those validly granted by the relevant rights holders.

The former **ZeroStudio Community License (ZSCL)** and its custom terms have been withdrawn. They are not part of the current three-license combination and must not be treated as additional conditions on files covered by the current license identifier.

For each file, consult its valid file-level license notice, the rights held by the relevant copyright holder, applicable upstream notices, and the complete license text. Nothing in this README relicenses third-party works or overrides their original terms.

---

**[📄 Repository LICENSE notice](./LICENSE)** · **[📜 Detailed license and copyright policy](./协议.md)**

</details>

## Contact the author of ZeroStudio

- Email: 2049219004@qq.com
- WeChat: android_zero
