# 获取当前文件（common-config.cmake）所在的目录路径
# 对应: root/ZeroStudio-ZeroStudio-devs/editor/tree-sitter-ndk/
set(NDK_ROOT_DIR "${CMAKE_CURRENT_LIST_DIR}")

# 【v15 重做】tree-sitter 0.27.0 完整 C 源码已内嵌到 android-tree-sitter 模块内
# src/main/cpp/tree-sitter/。各语言子模块 (cpp/toml/yaml/aidl/cmake) 通过
# TS_INCLUDES 引用这些头文件, 通过 TS_LIB_SRC 引用 lib.c 编译引擎核心。
#
# 历史: 之前 TS_DIR 指向 tree-sitter-lib (0.22.1, v14) 或 tree-sitter (0.27.0, v15)
# 的外部目录, 现在统一指向 android-tree-sitter 内嵌源码, 模块自包含。
set(TS_DIR "${NDK_ROOT_DIR}/android-tree-sitter/src/main/cpp/tree-sitter")

# 打印日志帮助调试路径问题
message(STATUS "Tree-Sitter Source Dir: ${TS_DIR}")

# 包含 tree-sitter 核心头文件
# include/tree_sitter/api.h — 公共 API
# src/parser.h / subtree.h — 内部实现头 (JNI 层用到)
set(TS_INCLUDES
    "${TS_DIR}/include"
    "${TS_DIR}/src"
)

# 将这些目录添加到编译器的搜索路径中
include_directories(${TS_INCLUDES})

# tree-sitter 0.27 将 parser.h / alloc.h / array.h 等内部头文件从 include/tree_sitter/
# 移到了 src/ 目录（不再有 tree_sitter/ 前缀）。但各语言的 parser.c / scanner.c 仍通过
# #include <tree_sitter/parser.h> 引用这些头文件。
# 在 src/ 下创建 tree_sitter/ 子目录并放置符号链接，使 <tree_sitter/parser.h>
# 能解析到 src/parser.h，避免每个语言子模块各自维护一份头文件副本。
set(TS_TREE_SITTER_DIR "${TS_DIR}/src/tree_sitter")
if (NOT EXISTS "${TS_TREE_SITTER_DIR}")
    file(MAKE_DIRECTORY "${TS_TREE_SITTER_DIR}")
    foreach(_hdr parser.h alloc.h array.h)
        if (EXISTS "${TS_DIR}/src/${_hdr}" AND NOT EXISTS "${TS_TREE_SITTER_DIR}/${_hdr}")
            file(CREATE_LINK "${TS_DIR}/src/${_hdr}" "${TS_TREE_SITTER_DIR}/${_hdr}" SYMBOLIC)
        endif()
    endforeach()
endif()

# tree-sitter amalgamation 源码 (供各语言子模块编译引擎核心用)
set(TS_LIB_SRC "${TS_DIR}/src/lib.c")

# 针对非 Android 环境（如 Host 单元测试）查找 JNI
if (NOT ${CMAKE_SYSTEM_NAME} STREQUAL Android)
    find_package(JNI REQUIRED)
    include_directories(${JAVA_INCLUDE_PATH})
    include_directories(${JAVA_INCLUDE_PATH2})
endif ()