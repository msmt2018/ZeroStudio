# 获取当前文件（common-config.cmake）所在的目录路径
# 对应: root/ZeroStudio-ZeroStudio-devs/editor/tree-sitter-ndk/
set(NDK_ROOT_DIR "${CMAKE_CURRENT_LIST_DIR}")

# 定义 tree-sitter 引擎源码根目录
# 【升级 v14 → v15】从 tree-sitter-lib (0.22.1, LANGUAGE_VERSION 14) 切换到
# tree-sitter (0.27.0, LANGUAGE_VERSION 15)。v15 引擎向后兼容 v14 grammar
# (兼容窗口 [13, 15]), 现有 parser.c (v14) 仍可加载, 无需立即重新生成。
# 新增能力: reserved words / supertypes / metadata / 进度回调 / grammar_type 等。
if (NOT DEFINED TS_DIR)
    set(TS_DIR "${NDK_ROOT_DIR}/tree-sitter")
endif ()

# 打印日志帮助调试路径问题
message(STATUS "Tree-Sitter Lib Dir: ${TS_DIR}")

# 包含 tree-sitter 核心头文件
# lib/include 包含 <tree_sitter/api.h>
# lib/src 包含 parser.h 等内部实现头文件
set(TS_INCLUDES 
    "${TS_DIR}/lib/include" 
    "${TS_DIR}/lib/src"
)

# 将这些目录添加到编译器的搜索路径中
include_directories(${TS_INCLUDES})

# 针对非 Android 环境（如 Host 单元测试）查找 JNI
if (NOT ${CMAKE_SYSTEM_NAME} STREQUAL Android)
    find_package(JNI REQUIRED)
    include_directories(${JAVA_INCLUDE_PATH})
    include_directories(${JAVA_INCLUDE_PATH2})
endif ()