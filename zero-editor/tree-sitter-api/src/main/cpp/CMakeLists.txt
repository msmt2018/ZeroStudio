# 
# This file is part of ZeroStudio.
# 
# Copyright (C) 2026 android_zero
# 
# SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
# 
# License Information:
# 
# This file is distributed under the following licenses, which apply
# cumulatively to the same work:
# 
# 1. GNU Affero General Public License v3.0 only (AGPL-3.0-only):
#    Grants the rights to use, study, modify, and redistribute this work.
#    When a modified version is used to provide network services to users,
#    the corresponding source code must be made available under the
#    applicable AGPL terms.
# 
# 2. GNU General Public License v3.0 only (GPL-3.0-only):
#    Grants the rights to use, study, modify, and redistribute this work.
#    Distribution of covered binaries requires compliance with the source
#    code, license notice, attribution, and other applicable GPL terms.
# 
# 3. Apache License 2.0 (Apache-2.0):
#    Grants copyright and applicable patent rights to use, modify, and
#    distribute this work, subject to its license conditions, including
#    preservation of notices, inclusion of the license, and marking changes.
# 
# The SPDX operator "AND" means that all listed license terms apply
# cumulatively. It does not mean that recipients may choose only one
# of the listed licenses.
# 
# You must comply with the applicable terms of all three licenses.
# This notice is a summary and does not replace the full license texts.
# 
# Full license texts:
# - GNU AGPL v3.0: https://www.gnu.org/licenses/agpl-3.0.html
# - GNU GPL v3.0:  https://www.gnu.org/licenses/gpl-3.0.html
# - Apache 2.0:    https://www.apache.org/licenses/LICENSE-2.0
# 
# Author: android_zero
# GitHub: https://github.com/msmt2018
# WeChat: android_zero
# 
#
# ===============================
# @author android_zero
# ===============================
cmake_minimum_required(VERSION 4.1.2)

project(zerostudio_treesitter C CXX)

#全局编译选项与标准配置
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 隐藏符号默认可见性，缩减 so 体积
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_C_VISIBILITY_PRESET hidden)

add_compile_options(
    -fcolor-diagnostics
    -Wall 
    -Wextra
    -Wno-backslash-newline-escape
    -Wno-unused-parameter
    -Wno-cast-function-type
    -Werror=incompatible-pointer-types
    -Werror=implicit-function-declaration
)

#编译 Tree-sitter C 核心静态库
add_library(tree-sitter-core STATIC
    tree_sitter/src/lib.c
)

# 仅对核心库生效的宏定义
target_compile_definitions(tree-sitter-core PRIVATE
    _GNU_SOURCE
    UTF8PROC_STATIC
)

# 核心库所需的头文件路径
target_include_directories(tree-sitter-core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/tree_sitter
    ${CMAKE_CURRENT_SOURCE_DIR}/tree_sitter/src
    ${CMAKE_CURRENT_SOURCE_DIR}/tree_sitter/src/unicode
)

# 编译完整 JNI 适配层共享库
set(JNI_SOURCES
    jni/jni_common.cpp
    jni/ts_language.cpp
    jni/ts_lookahead_iterator.cpp
    jni/ts_node.cpp
    jni/ts_parser.cpp
    jni/ts_query.cpp
    jni/ts_query_cursor.cpp
    jni/ts_query_filter.cpp
    jni/ts_tree.cpp
    jni/ts_tree_cursor.cpp
)

add_library(zerostudio_treesitter SHARED
    ${JNI_SOURCES}
)

# JNI 层所需的头文件路径（使用 PRIVATE 隔离）
target_include_directories(zerostudio_treesitter PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/tree_sitter/src
    ${CMAKE_CURRENT_SOURCE_DIR}/highlight
    ${CMAKE_CURRENT_SOURCE_DIR}/tags
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
)

#链接依赖项
find_library(log-lib log)

target_link_libraries(zerostudio_treesitter
    PRIVATE
    tree-sitter-core   # 链接上面编译的静态核心
    ${log-lib}         # 链接 Android NDK 日志库
)
