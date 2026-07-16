#!/bin#!/bin/bash
# ============================================================================
# ZeroStudio 专用:#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       -#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 C#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/j#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCH#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home 下，避免污染源码树)
export build#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home 下，避免污染源码树)
export build_root="$HOME/puppylibsbuild"
export build_out="$build_root/out"
export build_src="$#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home 下，避免污染源码树)
export build_root="$HOME/puppylibsbuild"
export build_out="$build_root/out"
export build_src="$build_root/src"
mkdir -p "$build_out" "$build_src"

echo "=========================================="
#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home 下，避免污染源码树)
export build_root="$HOME/puppylibsbuild"
export build_out="$build_root/out"
export build_src="$build_root/src"
mkdir -p "$build_out" "$build_src"

echo "=========================================="
echo " ZeroStudio Native Libs Builder"
echo#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home 下，避免污染源码树)
export build_root="$HOME/puppylibsbuild"
export build_out="$build_root/out"
export build_src="$build_root/src"
mkdir -p "$build_out" "$build_src"

echo "=========================================="
echo " ZeroStudio Native Libs Builder"
echo "=========================================="
echo " NDK version:     $NDK_VERSION"
echo " CMake version:   $CMAKE_VERSION"
echo " Target ABIs:     $ARCHS"
echo#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home 下，避免污染源码树)
export build_root="$HOME/puppylibsbuild"
export build_out="$build_root/out"
export build_src="$build_root/src"
mkdir -p "$build_out" "$build_src"

echo "=========================================="
echo " ZeroStudio Native Libs Builder"
echo "=========================================="
echo " NDK version:     $NDK_VERSION"
echo " CMake version:   $CMAKE_VERSION"
echo " Target ABIs:     $ARCHS"
echo " Output dir:      $OUTPUT_DIR"
echo " Build root:      $build_root"
echo#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home 下，避免污染源码树)
export build_root="$HOME/puppylibsbuild"
export build_out="$build_root/out"
export build_src="$build_root/src"
mkdir -p "$build_out" "$build_src"

echo "=========================================="
echo " ZeroStudio Native Libs Builder"
echo "=========================================="
echo " NDK version:     $NDK_VERSION"
echo " CMake version:   $CMAKE_VERSION"
echo " Target ABIs:     $ARCHS"
echo " Output dir:      $OUTPUT_DIR"
echo " Build root:      $build_root"
echo "=========================================="

# ---------- 定位 NDK 和 CMake ----------

# CI 环境#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home 下，避免污染源码树)
export build_root="$HOME/puppylibsbuild"
export build_out="$build_root/out"
export build_src="$build_root/src"
mkdir -p "$build_out" "$build_src"

echo "=========================================="
echo " ZeroStudio Native Libs Builder"
echo "=========================================="
echo " NDK version:     $NDK_VERSION"
echo " CMake version:   $CMAKE_VERSION"
echo " Target ABIs:     $ARCHS"
echo " Output dir:      $OUTPUT_DIR"
echo " Build root:      $build_root"
echo "=========================================="

# ---------- 定位 NDK 和 CMake ----------

# CI 环境通常通过 ANDROID_HOME 指定 SDK 路径
if [ -n "$ANDROID_HOME" ]; then
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

##!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home 下，避免污染源码树)
export build_root="$HOME/puppylibsbuild"
export build_out="$build_root/out"
export build_src="$build_root/src"
mkdir -p "$build_out" "$build_src"

echo "=========================================="
echo " ZeroStudio Native Libs Builder"
echo "=========================================="
echo " NDK version:     $NDK_VERSION"
echo " CMake version:   $CMAKE_VERSION"
echo " Target ABIs:     $ARCHS"
echo " Output dir:      $OUTPUT_DIR"
echo " Build root:      $build_root"
echo "=========================================="

# ---------- 定位 NDK 和 CMake ----------

# CI 环境通常通过 ANDROID_HOME 指定 SDK 路径
if [ -n "$ANDROID_HOME" ]; then
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

# 尝试找到 NDK 路径
ND#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home 下，避免污染源码树)
export build_root="$HOME/puppylibsbuild"
export build_out="$build_root/out"
export build_src="$build_root/src"
mkdir -p "$build_out" "$build_src"

echo "=========================================="
echo " ZeroStudio Native Libs Builder"
echo "=========================================="
echo " NDK version:     $NDK_VERSION"
echo " CMake version:   $CMAKE_VERSION"
echo " Target ABIs:     $ARCHS"
echo " Output dir:      $OUTPUT_DIR"
echo " Build root:      $build_root"
echo "=========================================="

# ---------- 定位 NDK 和 CMake ----------

# CI 环境通常通过 ANDROID_HOME 指定 SDK 路径
if [ -n "$ANDROID_HOME" ]; then
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

# 尝试找到 NDK 路径
NDK_DIR=""
if [ -n "$ANDROID_HOME#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home 下，避免污染源码树)
export build_root="$HOME/puppylibsbuild"
export build_out="$build_root/out"
export build_src="$build_root/src"
mkdir -p "$build_out" "$build_src"

echo "=========================================="
echo " ZeroStudio Native Libs Builder"
echo "=========================================="
echo " NDK version:     $NDK_VERSION"
echo " CMake version:   $CMAKE_VERSION"
echo " Target ABIs:     $ARCHS"
echo " Output dir:      $OUTPUT_DIR"
echo " Build root:      $build_root"
echo "=========================================="

# ---------- 定位 NDK 和 CMake ----------

# CI 环境通常通过 ANDROID_HOME 指定 SDK 路径
if [ -n "$ANDROID_HOME" ]; then
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

# 尝试找到 NDK 路径
NDK_DIR=""
if [ -n "$ANDROID_HOME" ]; then
    NDK_DIR="$ANDROID_HOME/ndk/$NDK_VERSION"
fi#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home 下，避免污染源码树)
export build_root="$HOME/puppylibsbuild"
export build_out="$build_root/out"
export build_src="$build_root/src"
mkdir -p "$build_out" "$build_src"

echo "=========================================="
echo " ZeroStudio Native Libs Builder"
echo "=========================================="
echo " NDK version:     $NDK_VERSION"
echo " CMake version:   $CMAKE_VERSION"
echo " Target ABIs:     $ARCHS"
echo " Output dir:      $OUTPUT_DIR"
echo " Build root:      $build_root"
echo "=========================================="

# ---------- 定位 NDK 和 CMake ----------

# CI 环境通常通过 ANDROID_HOME 指定 SDK 路径
if [ -n "$ANDROID_HOME" ]; then
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

# 尝试找到 NDK 路径
NDK_DIR=""
if [ -n "$ANDROID_HOME" ]; then
    NDK_DIR="$ANDROID_HOME/ndk/$NDK_VERSION"
fi

if [ ! -d "$NDK_DIR" ]; then
    echo "ERROR: N#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home 下，避免污染源码树)
export build_root="$HOME/puppylibsbuild"
export build_out="$build_root/out"
export build_src="$build_root/src"
mkdir -p "$build_out" "$build_src"

echo "=========================================="
echo " ZeroStudio Native Libs Builder"
echo "=========================================="
echo " NDK version:     $NDK_VERSION"
echo " CMake version:   $CMAKE_VERSION"
echo " Target ABIs:     $ARCHS"
echo " Output dir:      $OUTPUT_DIR"
echo " Build root:      $build_root"
echo "=========================================="

# ---------- 定位 NDK 和 CMake ----------

# CI 环境通常通过 ANDROID_HOME 指定 SDK 路径
if [ -n "$ANDROID_HOME" ]; then
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

# 尝试找到 NDK 路径
NDK_DIR=""
if [ -n "$ANDROID_HOME" ]; then
    NDK_DIR="$ANDROID_HOME/ndk/$NDK_VERSION"
fi

if [ ! -d "$NDK_DIR" ]; then
    echo "ERROR: NDK not found at $NDK_DIR"
    echo "Please install NDK $NDK_VERSION#!/bin/bash
# ============================================================================
# ZeroStudio 专用: 编译 puppygit 所需的 native 库 (libcrypto, libssl, libssh2,
# libgit2) 到 core/git/src/main/jniLibs/<abi>/ 目录。
#
# 本脚本改编自 PuppyGit 上游的 lib_build_scripts/1_installrequire.sh +
# 2_downloadsrc.sh + 3_buildlibs.sh，适配 ZeroStudio CI 环境已安装的
# NDK 27.1.12297006 和 CMake 3.31.1。
#
# 用法:
#   bash build_native_libs.sh [output_dir] [archs]
#
#   output_dir  - .so 文件输出目录，默认为 $CORE_GIT_DIR/src/main/jniLibs
#   archs       - 要编译的架构，空格分隔，默认为 "arm64 arm32 x8664"
#
# 在 CI 中调用:
#   bash core/git/lib_build_scripts/build_native_libs.sh
# ============================================================================
set -e

# ---------- 可配置变量 ----------

# NDK 和 CMake 版本 (与 ZeroStudio BuildConfig.kt 保持一致)
NDK_VERSION="${NDK_VERSION:-27.1.12297006}"
CMAKE_VERSION="${CMAKE_VERSION:-3.31.1}"

# Android API level (与 PuppyGit 上游一致)
export android_target_abi=21

# 源码版本
LIBSSH2_VERSION="1.11.1"
OPENSSL_VERSION="4.0.0"
LIBGIT2_VERSION="1.9.4"

# ---------- 路径推导 ----------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# core/git 目录 (lib_build_scripts 的上一级)
CORE_GIT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 默认输出目录
DEFAULT_OUT="$CORE_GIT_DIR/src/main/jniLibs"
OUTPUT_DIR="${1:-$DEFAULT_OUT}"

# 默认架构
DEFAULT_ARCHS="arm64 arm32 x8664"
ARCHS="${2:-$DEFAULT_ARCHS}"

# 构建工作区 (放在 home 下，避免污染源码树)
export build_root="$HOME/puppylibsbuild"
export build_out="$build_root/out"
export build_src="$build_root/src"
mkdir -p "$build_out" "$build_src"

echo "=========================================="
echo " ZeroStudio Native Libs Builder"
echo "=========================================="
echo " NDK version:     $NDK_VERSION"
echo " CMake version:   $CMAKE_VERSION"
echo " Target ABIs:     $ARCHS"
echo " Output dir:      $OUTPUT_DIR"
echo " Build root:      $build_root"
echo "=========================================="

# ---------- 定位 NDK 和 CMake ----------

# CI 环境通常通过 ANDROID_HOME 指定 SDK 路径
if [ -n "$ANDROID_HOME" ]; then
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

# 尝试找到 NDK 路径
NDK_DIR=""
if [ -n "$ANDROID_HOME" ]; then
    NDK_DIR="$ANDROID_HOME/ndk/$NDK_VERSION"
fi

if [ ! -d "$NDK_DIR" ]; then
    echo "ERROR: NDK not found at $NDK_DIR"
    echo "Please install NDK $NDK_VERSION via sdkmanager:"
    echo "  sdkmanager