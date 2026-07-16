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

# 默认架构 (ZeroStudio 只构建 3 个 ABI: arm64-v8a, armeabi-v7a, x86_64)
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
    echo "  sdkmanager \"ndk;$NDK_VERSION\""
    exit 1
fi

export ANDROID_NDK_ROOT="$NDK_DIR"
export ANDROID_TOOLCHAIN_ROOT="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64"
export PATH="$ANDROID_TOOLCHAIN_ROOT/bin:$PATH"
export prefix="$ANDROID_TOOLCHAIN_ROOT/sysroot/usr/local"

echo "NDK found at: $ANDROID_NDK_ROOT"

# 定位 CMake: 优先用 SDK 里的，其次用 pip 安装的
CMAKE_PATH=""
if [ -x "$ANDROID_HOME/cmake/$CMAKE_VERSION/bin/cmake" ]; then
    CMAKE_PATH="$ANDROID_HOME/cmake/$CMAKE_VERSION/bin/cmake"
elif command -v cmake &>/dev/null; then
    CMAKE_PATH="$(command -v cmake)"
else
    echo "ERROR: CMake not found. Install via pip: pip3 install cmake==$CMAKE_VERSION"
    exit 1
fi

echo "CMake found at: $CMAKE_PATH"
"$CMAKE_PATH" --version

# ---------- 下载源码 ----------

download_src() {
    echo "========== Downloading sources =========="

    OPENSSL_URL="https://github.com/openssl/openssl/releases/download/openssl-${OPENSSL_VERSION}/openssl-${OPENSSL_VERSION}.tar.gz"
    LIBGIT2_URL="https://github.com/libgit2/libgit2/archive/refs/tags/v${LIBGIT2_VERSION}.tar.gz"
    LIBSSH2_URL="https://github.com/libssh2/libssh2/releases/download/libssh2-${LIBSSH2_VERSION}/libssh2-${LIBSSH2_VERSION}.tar.gz"
    OPENSSL_CMAKE_URL="https://github.com/jimmy-park/openssl-cmake/archive/refs/tags/${OPENSSL_VERSION}.tar.gz"

    echo "Downloading libssh2..."
    curl -L -o "$build_src/libssh2-${LIBSSH2_VERSION}.tar.gz" "$LIBSSH2_URL"

    echo "Downloading openssl..."
    curl -L -o "$build_src/openssl-${OPENSSL_VERSION}.tar.gz" "$OPENSSL_URL"

    echo "Downloading libgit2..."
    curl -L -o "$build_src/libgit2-${LIBGIT2_VERSION}.tar.gz" "$LIBGIT2_URL"

    echo "Downloading openssl-cmake..."
    curl -L -o "$build_src/openssl-cmake-${OPENSSL_VERSION}.tar.gz" "$OPENSSL_CMAKE_URL"

    echo "Extracting libssh2..."
    tar -xzf "$build_src/libssh2-${LIBSSH2_VERSION}.tar.gz" -C "$build_src"
    rm -rf "$build_src/libssh2"
    mv "$build_src/libssh2-${LIBSSH2_VERSION}" "$build_src/libssh2"

    echo "Extracting openssl..."
    tar -xzf "$build_src/openssl-${OPENSSL_VERSION}.tar.gz" -C "$build_src"
    rm -rf "$build_src/openssl"
    mv "$build_src/openssl-${OPENSSL_VERSION}" "$build_src/openssl"

    echo "Extracting openssl-cmake..."
    tar -xzf "$build_src/openssl-cmake-${OPENSSL_VERSION}.tar.gz" -C "$build_src"
    rm -rf "$build_src/openssl-cmake"
    mv "$build_src/openssl-cmake-${OPENSSL_VERSION}" "$build_src/openssl-cmake"

    echo "Extracting libgit2..."
    tar -xzf "$build_src/libgit2-${LIBGIT2_VERSION}.tar.gz" -C "$build_src"
    rm -rf "$build_src/libgit2"
    mv "$build_src/libgit2-${LIBGIT2_VERSION}" "$build_src/libgit2"

    echo "Download and extraction complete."
}

# ---------- 编译单个架构 ----------

build_for_arch() {
    local arch="$1"

    echo ""
    echo "=========================================="
    echo " Building for arch: $arch"
    echo "=========================================="

    # 设置架构相关变量
    local liboutdir=""
    local toolchainfile=""
    local build_out_tmp=""
    local opensslarch=""
    local cur_android_abi=""

    if [ "$arch" == "x86" ]; then
        liboutdir="$build_out/x86"
        toolchainfile="$SCRIPT_DIR/libgit2-x86-toolchain.cmake"
        build_out_tmp=build_x86
        opensslarch=android-x86
        cur_android_abi=x86
    elif [ "$arch" == "x8664" ]; then
        liboutdir="$build_out/x86_64"
        toolchainfile="$SCRIPT_DIR/libgit2-x8664-toolchain.cmake"
        build_out_tmp=build_x8664
        opensslarch=android-x86_64
        cur_android_abi=x86_64
    elif [ "$arch" == "arm32" ]; then
        liboutdir="$build_out/armeabi-v7a"
        toolchainfile="$SCRIPT_DIR/libgit2-armv7-toolchain.cmake"
        build_out_tmp=build_arm32
        opensslarch=android-arm
        cur_android_abi=armeabi-v7a
    elif [ "$arch" == "arm64" ]; then
        liboutdir="$build_out/arm64-v8a"
        toolchainfile="$SCRIPT_DIR/libgit2-arm64-toolchain.cmake"
        build_out_tmp=build_arm64
        opensslarch=android-arm64
        cur_android_abi=arm64-v8a
    else
        echo "ERROR: Unknown arch '$arch'"
        return 1
    fi

    mkdir -p "$liboutdir"

    local opensslsrc="$build_src/openssl"
    local libssh2src="$build_src/libssh2"
    local libgit2src="$build_src/libgit2"
    local openssl_cmake="$build_src/openssl-cmake"

    # ---- 编译 openssl ----
    echo "--- Building openssl for $arch ---"
    cd "$openssl_cmake"
    mkdir -p "$build_out_tmp"
    cd "$build_out_tmp"

    # disable asm 以获得更好的兼容性 (与 PuppyGit 上游一致)
    "$CMAKE_PATH" .. \
        -DCMAKE_TOOLCHAIN_FILE="$toolchainfile" \
        -DANDROID_ABI="$cur_android_abi" \
        -DANDROID_PLATFORM="android-$android_target_abi" \
        -DCMAKE_INSTALL_PREFIX="$prefix" \
        -DOPENSSL_TARGET_PLATFORM="$opensslarch" \
        -DOPENSSL_CONFIGURE_OPTIONS="-D__ANDROID_API__=$android_target_abi;--openssldir=$prefix/ssl;--prefix=$prefix;no-asm" \
        -DOPENSSL_INSTALL=TRUE \
        -DOPENSSL_PATCH="$openssl_cmake/patch/android.patch" \
        -DBUILD_SHARED_LIBS=ON \
        -DOPENSSL_SOURCE="$opensslsrc"

    "$CMAKE_PATH" --build . --target install

    cp -f "$prefix/lib/libssl.so" "$liboutdir/libssl.so"
    cp -f "$prefix/lib/libcrypto.so" "$liboutdir/libcrypto.so"
    echo "--- openssl done for $arch ---"

    # ---- 编译 libssh2 ----
    echo "--- Building libssh2 for $arch ---"
    cd "$libssh2src"
    mkdir -p "$build_out_tmp"
    cd "$build_out_tmp"

    "$CMAKE_PATH" .. \
        -DCMAKE_TOOLCHAIN_FILE="$toolchainfile" \
        -DCMAKE_INSTALL_PREFIX="$prefix" \
        -DCMAKE_BUILD_TYPE=Release

    "$CMAKE_PATH" --build . --target install

    cp -f "$prefix/lib/libssh2.so" "$liboutdir/libssh2.so"
    echo "--- libssh2 done for $arch ---"

    # ---- 编译 libgit2 ----
    echo "--- Building libgit2 for $arch ---"
    cd "$libgit2src"
    mkdir -p "$build_out_tmp"
    cd "$build_out_tmp"

    "$CMAKE_PATH" .. \
        -DCMAKE_TOOLCHAIN_FILE="$toolchainfile" \
        -DCMAKE_INSTALL_PREFIX="$prefix" \
        -DCMAKE_BUILD_TYPE=Release \
        -DUSE_SSH=ON \
        -DBUILD_TESTS=OFF \
        -DBUILD_CLI=OFF \
        -DBUILD_EXAMPLES=OFF \
        -DBUILD_FUZZERS=OFF \
        -DBUILD_SHARED_LIBS=ON \
        -DCMAKE_C_STANDARD=99

    "$CMAKE_PATH" --build . --target install

    cp -f "$prefix/lib/libgit2.so" "$liboutdir/libgit2.so"
    echo "--- libgit2 done for $arch ---"

    # ---- 清理构建目录 (必须清理 openssl 源码目录才能编译其他架构) ----
    echo "--- Cleaning build dirs for $arch ---"
    rm -rf "$openssl_cmake/$build_out_tmp"
    rm -rf "$libssh2src/$build_out_tmp"
    rm -rf "$libgit2src/$build_out_tmp"

    echo "=========================================="
    echo " Build for '$arch' done"
    echo " Libs output: $liboutdir"
    echo "=========================================="
}

# ---------- 主流程 ----------

# 1. 下载源码 (如果还没下载)
if [ ! -d "$build_src/openssl" ] || [ ! -d "$build_src/libssh2" ] || [ ! -d "$build_src/libgit2" ] || [ ! -d "$build_src/openssl-cmake" ]; then
    download_src
fi

# 2. 设置可复现构建的时间戳 (openssl 依赖此变量)
export SOURCE_DATE_EPOCH=1779714574

# 3. 逐个架构编译
for arch in $ARCHS; do
    build_for_arch "$arch"
done

# 4. 复制结果到输出目录
echo ""
echo "========== Copying libs to output dir =========="
mkdir -p "$OUTPUT_DIR"
for arch_dir in "$build_out"/*/; do
    if [ -d "$arch_dir" ]; then
        abi_name="$(basename "$arch_dir")"
        mkdir -p "$OUTPUT_DIR/$abi_name"
        cp -f "$arch_dir"/*.so "$OUTPUT_DIR/$abi_name/" 2>/dev/null || true
        echo "Copied libs for $abi_name"
    fi
done

echo ""
echo "========== Build complete =========="
echo "Output directory: $OUTPUT_DIR"
echo "Contents:"
find "$OUTPUT_DIR" -name "*.so" -type f | sort
echo "===================================="
