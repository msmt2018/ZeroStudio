# 获取当前文件（common-config.cmake）所在的目录路径
# 对应: root/ZeroStudio-ZeroStudio-devs/editor/tree-sitter-ndk/
set(NDK_ROOT_DIR "${CMAKE_CURRENT_LIST_DIR}")

# 定义 tree-sitter-lib 的根目录
if (NOT DEFINED TS_DIR)
    set(TS_DIR "${NDK_ROOT_DIR}/tree-sitter-lib")
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

# 语法生成的 parser.c / scanner.c 引用 #include "tree_sitter/parser.h" 和
# #include "tree_sitter/array.h"，但 tree-sitter-lib 的这两个头文件直接放在
# lib/src/ 下（不带 tree_sitter/ 前缀）。这里创建符号链接使路径可解析。
set(_ts_compat_dir "${TS_DIR}/lib/src/tree_sitter")
if (NOT EXISTS "${_ts_compat_dir}/parser.h")
    file(MAKE_DIRECTORY "${_ts_compat_dir}")
    if (EXISTS "${TS_DIR}/lib/src/parser.h")
        file(CREATE_LINK "${TS_DIR}/lib/src/parser.h" "${_ts_compat_dir}/parser.h" SYMBOLIC)
    endif()
    if (EXISTS "${TS_DIR}/lib/src/array.h")
        file(CREATE_LINK "${TS_DIR}/lib/src/array.h" "${_ts_compat_dir}/array.h" SYMBOLIC)
    endif()
    message(STATUS "Created tree_sitter/ compat symlinks in ${_ts_compat_dir}")
endif()

# 针对非 Android 环境（如 Host 单元测试）查找 JNI
if (NOT ${CMAKE_SYSTEM_NAME} STREQUAL Android)
    find_package(JNI REQUIRED)
    include_directories(${JAVA_INCLUDE_PATH})
    include_directories(${JAVA_INCLUDE_PATH2})
endif ()