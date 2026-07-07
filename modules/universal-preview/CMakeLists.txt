cmake_minimum_required(VERSION 3.22.1)

project("universal-preview")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── ImGui 集成选项 ──────────────────────────────────────────
# 启用后需要将 Dear ImGui 源码放在 src/main/cpp/imgui/ 目录下:
#   git clone https://github.com/ocornut/imgui src/main/cpp/imgui
# CMake 会自动编译 imgui core + OpenGL ES3 backend + Android backend
option(WITH_IMGUI "Enable Dear ImGui integration" OFF)
if(WITH_IMGUI)
  add_definitions(-DWITH_IMGUI)
  set(IMGUI_DIR ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/imgui)
endif()

# -O3 最高优化 / -fvisibility=hidden 只导出 JNI / 剔除死代码
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fvisibility=hidden -ffunction-sections -fdata-sections -fexceptions -frtti")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections")

set(CPP_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp)

# Dear ImGui + GLES3 渲染桥 (native_preview_bridge.cpp)
set(SOURCES ${CPP_ROOT}/native_preview_bridge.cpp)

if(WITH_IMGUI)
  list(APPEND SOURCES
    ${IMGUI_DIR}/imgui.cpp
    ${IMGUI_DIR}/imgui_draw.cpp
    ${IMGUI_DIR}/imgui_tables.cpp
    ${IMGUI_DIR}/imgui_widgets.cpp
    ${IMGUI_DIR}/backends/imgui_impl_opengl3.cpp
  )
  include_directories(${IMGUI_DIR} ${IMGUI_DIR}/backends)
endif()

add_library(native_preview_engine SHARED ${SOURCES})

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
