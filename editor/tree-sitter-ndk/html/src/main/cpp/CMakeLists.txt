cmake_minimum_required(VERSION 3.22.1)

project("tree-sitter-html")

# 从 src/main/cpp (当前目录) 向上跳 4 级到达 tree-sitter-ndk 目录
# 路径推演: cpp -> main -> src -> html -> tree-sitter-ndk
get_filename_component(NDK_COMMON_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)

# 引入通用配置 (设置 TS_DIR 和 include 路径)
include("${NDK_COMMON_DIR}/common-config.cmake")

# 确保编译器能找到手动创建的 "ts_html.h" 和 "ts__onload.h"
# 以及 scanner.c 依赖的 "tag.h"
# tree_sitter/ 目录下的头文件是混合版本兼容副本:
#   parser.h  — 基于 aidl 模块的旧版副本 (含 version 字段和 TSLexMode 类型,
#               匹配 html parser.c 的 LANGUAGE_VERSION 14 ABI), 并:
#               1. REDUCE 宏改为 4 参数版 (html parser.c 由 0.25+ 生成)
#               2. 新增 ADVANCE_MAP 宏 (从 0.27 源码移植, html parser.c 的
#                  lexer 用它做字符→状态映射查表)
#   alloc.h / array.h / ts_assert.h — 从 tree-sitter 0.27 源码复制 (独立工具头,
#               API 跨版本稳定, 被 tag.h 引用)
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# 定义源文件
# parser.c: Tree-sitter 生成的语法解析代码
# scanner.c: 手写的外部扫描器代码 (HTML 标签扫描)
# tag.h: scanner.c 依赖的标签类型定义 (HTML 语法独有, 其它语言无此文件)
# tree-sitter-html.cpp: JNI 绑定代码
# ${TS_DIR}/src/lib.c: Tree-sitter 核心运行时代码
add_library(${CMAKE_PROJECT_NAME} SHARED
        "${TS_DIR}/src/lib.c"
        parser.c
        scanner.c
        tag.h
        tree-sitter-html.cpp
)

#  链接 Android 日志库
if (${CMAKE_SYSTEM_NAME} STREQUAL Android)
    find_library(log-lib log)
    target_link_libraries(${CMAKE_PROJECT_NAME} ${log-lib})
endif()
