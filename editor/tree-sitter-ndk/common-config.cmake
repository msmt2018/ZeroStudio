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

# 注意: 各语言子模块的 parser.c / scanner.c 通过 #include <tree_sitter/parser.h>
# 引用内部头文件。tree-sitter 0.27 将这些头文件从 include/tree_sitter/ 移到了 src/
# (不带 tree_sitter/ 前缀)。但 aidl/toml/cmake/cpp/yaml 等旧语法模块的 parser.c
# 是用旧版 tree-sitter 生成的, 与 0.27 的 parser.h 不兼容 (REDUCE 宏参数数量不同、
# TSFieldMapSlice 类型被移除等)。因此这些模块在各自 src/main/cpp/tree_sitter/ 下
# 维护了兼容的旧版 parser.h 副本, 编译时通过 -I<module>/src/main/cpp 优先解析到
# 本地副本。不要在 src/ 下创建 tree_sitter/ 符号链接指向 0.27 版本, 那会覆盖
# 各模块的本地副本导致编译失败。
# 新语法模块 (如 html, parser.c 由 tree-sitter 0.27 生成) 同样在本地维护
# tree_sitter/ 头文件副本, 只需从 0.27 源码复制即可。

# tree-sitter amalgamation 源码 (供各语言子模块编译引擎核心用)
set(TS_LIB_SRC "${TS_DIR}/src/lib.c")

# 针对非 Android 环境（如 Host 单元测试）查找 JNI
if (NOT ${CMAKE_SYSTEM_NAME} STREQUAL Android)
    find_package(JNI REQUIRED)
    include_directories(${JAVA_INCLUDE_PATH})
    include_directories(${JAVA_INCLUDE_PATH2})
endif ()