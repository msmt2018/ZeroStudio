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
