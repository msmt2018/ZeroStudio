#
#  This file is part of android-tree-sitter.
#
#  android-tree-sitter library is free software; you can redistribute it and/or
#  modify it under the terms of the GNU Lesser General Public
#  License as published by the Free Software Foundation; either
#  version 2.1 of the License, or (at your option) any later version.
#
#  android-tree-sitter library is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
#  Lesser General Public License for more details.
#
#   You should have received a copy of the GNU General Public License
#  along with android-tree-sitter.  If not, see <https://www.gnu.org/licenses/>.
#

cmake_minimum_required(VERSION 3.22.1)

# Project name
project("android-tree-sitter")

set(PROJECT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../..)

# 【完整重做】tree-sitter 最新完整 C 源码已内嵌到 src/main/cpp/tree-sitter/
# 不再依赖外部 tree-sitter-lib / tree-sitter 目录, 模块自包含。
# common-config.cmake 仍提供 TS_INCLUDES 给各语言子模块用 (指向本模块内嵌源码)。
set(TS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/tree-sitter")

# tree-sitter 核心头文件
# include/tree_sitter/api.h — 公共 API
# src/parser.h — 内部实现头 (JNI 层 ts_node.cc 用到 subtree.h)
include_directories("${TS_DIR}/include")
include_directories("${TS_DIR}/src")

# 确保编译器能找到手动维护的 JNI 绑定层头文件 (ts__log.h, ts__onload.h, ts_*.h 等)
include_directories("${CMAKE_CURRENT_SOURCE_DIR}")

# WebAssembly 功能开关（与上游 tree-sitter CMake 一致，默认 OFF）
# 启用后需要提供 wasmtime 库（WASMTIME_INCLUDE_DIR + WASMTIME_LIBRARY）
# 未启用时，wasm_store.c 的 #else 部分提供 dummy 实现，编译通过但 wasm API 返回空值
option(TREE_SITTER_FEATURE_WASM "Enable the WebAssembly feature" OFF)

# Set visibility to hidden by default
SET(CMAKE_CXX_FLAGS "-fvisibility=hidden")

# add android-tree-sitter library
add_library(android-tree-sitter SHARED

        # tree-sitter 最新完整核心源码 (amalgamation 单文件编译，含 wasm_store.c)
        "${TS_DIR}/src/lib.c"

        # android-tree-sitter JNI 绑定层
        main.cc
        ts.cc
        ts_cursor.cc
        ts_language.cc
        ts_lookahead_iterator.cc
        ts_node.cc
        ts_parser.cc
        ts_query.cc
        ts_query_cursor.cc
        ts_tree.cc
        ts_utils.cc
        ts_wasm_store.cc
        utf16str/JavaUTF16String.cpp
        utf16str/JavaUTF16StringFactory.cpp
        utf16str/UTF16String.cpp
        utils/jni_string.cpp
        utils/ts_exceptions.cpp
        utils/ts_preconditions.cpp
        utils/ts_obj_utils.cpp
        )

if (${CMAKE_SYSTEM_NAME} STREQUAL Android)
    # Find the log library and link it to android-tree-sitter
    find_library(log log)
    target_link_libraries(android-tree-sitter ${log})
endif()

# 启用 wasm 功能时，定义宏并链接 wasmtime 库
if(TREE_SITTER_FEATURE_WASM)
    target_compile_definitions(android-tree-sitter PUBLIC TREE_SITTER_FEATURE_WASM)

    if(NOT DEFINED WASMTIME_INCLUDE_DIR)
        message(FATAL_ERROR "TREE_SITTER_FEATURE_WASM=ON 但未设置 WASMTIME_INCLUDE_DIR")
    endif()
    if(NOT DEFINED WASMTIME_LIBRARY)
        message(FATAL_ERROR "TREE_SITTER_FEATURE_WASM=ON 但未设置 WASMTIME_LIBRARY")
    endif()

    target_include_directories(android-tree-sitter SYSTEM PRIVATE "${WASMTIME_INCLUDE_DIR}")
    target_link_libraries(android-tree-sitter PUBLIC "${WASMTIME_LIBRARY}")
    message(STATUS "Tree-sitter Wasm feature enabled (wasmtime: ${WASMTIME_LIBRARY})")
endif()
