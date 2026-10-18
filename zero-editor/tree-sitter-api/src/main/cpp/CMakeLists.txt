# ==============================================================================
# @author android_zero
# 变更说明:
# 1. 采用 CMake 3.31.1 构建配置。
# 2. 静态编译 tree_sitter/src/lib.c 核心。
# 3. 编译 JNI 适配层模块：language, parser, tree, node, cursor, query, lookahead, tags, highlight。
# ==============================================================================
cmake_minimum_required(VERSION 3.31.1)

project(zerostudio_treesitter C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/tree_sitter
    ${CMAKE_CURRENT_SOURCE_DIR}/tree_sitter/src
    ${CMAKE_CURRENT_SOURCE_DIR}/tree_sitter/src/unicode
    ${CMAKE_CURRENT_SOURCE_DIR}/highlight
    ${CMAKE_CURRENT_SOURCE_DIR}/tags
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
)

# 编译 Tree-sitter C 核心静态库
add_library(tree-sitter-core STATIC
    tree_sitter/src/lib.c
)
target_compile_definitions(tree-sitter-core PRIVATE
    -D_GNU_SOURCE
    -DUTF8PROC_STATIC
)

# 编译完整 JNI 拆分源文件
set(JNI_SOURCES
    jni/ts_jni_common.c
    jni/ts_jni_language.c
    jni/ts_jni_parser.c
    jni/ts_jni_tree.c
    jni/ts_jni_node.c
    jni/ts_jni_cursor.c
    jni/ts_jni_query.c
    jni/ts_jni_lookahead.c
    jni/ts_jni_tags.c
    jni/ts_jni_highlight.c
)

add_library(zerostudio_treesitter SHARED
    ${JNI_SOURCES}
)

find_library(log-lib log)

target_link_libraries(zerostudio_treesitter
    PRIVATE
    tree-sitter-core
    ${log-lib}
)