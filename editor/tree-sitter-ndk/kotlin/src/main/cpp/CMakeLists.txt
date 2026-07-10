cmake_minimum_required(VERSION 3.22.1)

project("tree-sitter-kotlin")

# 从 src/main/cpp (当前目录) 向上跳 4 级到达 tree-sitter-ndk 目录
# 路径推演: cpp -> main -> src -> kotlin -> tree-sitter-ndk
get_filename_component(NDK_COMMON_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)

# 引入通用配置 (设置 TS_DIR 和 include 路径)
include("${NDK_COMMON_DIR}/common-config.cmake")

# 确保编译器能找到手动创建的 "ts_kotlin.h" 和 "ts__onload.h"
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# 定义源文件
# parser.c: Tree-sitter 生成的语法解析代码
# scanner.c: 手写的外部扫描器代码
# tree-sitter-kotlin.cpp: JNI 绑定代码
# ${TS_DIR}/lib/src/lib.c: Tree-sitter 核心运行时代码
add_library(${CMAKE_PROJECT_NAME} SHARED
        "${TS_DIR}/lib/src/lib.c"
        parser.c
        scanner.c
        tree-sitter-kotlin.cpp
)

#  链接 Android 日志库
if (${CMAKE_SYSTEM_NAME} STREQUAL Android)
    find_library(log-lib log)
    target_link_libraries(${CMAKE_PROJECT_NAME} ${log-lib})
endif()
