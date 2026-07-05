cmake_minimum_required(VERSION 3.22.1)

project("universal-preview")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# -O3 最高优化 / -fvisibility=hidden 只导出 JNI / 剔除死代码
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fvisibility=hidden -ffunction-sections -fdata-sections -fexceptions -frtti")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections")

set(CPP_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp)

# Dear ImGui + GLES3 渲染桥 (native_preview_bridge.cpp)
add_library(native_preview_engine SHARED
    ${CPP_ROOT}/native_preview_bridge.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv3)

target_link_libraries(native_preview_engine
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles-lib}
)
