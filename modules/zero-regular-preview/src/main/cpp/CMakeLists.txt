cmake_minimum_required(VERSION 3.31.1)

project("zero-regular-preview")

set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build shared libraries" FORCE)
set(INSTALL_DOCUMENTATION OFF CACHE BOOL "Install documentation" FORCE)
set(INSTALL_EXAMPLES OFF CACHE BOOL "Install examples" FORCE)
set(BUILD_TEST OFF CACHE BOOL "Build tests" FORCE)

add_subdirectory(oniguruma)

add_library(
        zero-regular-preview
        SHARED
        zero_preview_jni.c
)

target_include_directories(zero-regular-preview PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/oniguruma/src"
        "${CMAKE_CURRENT_BINARY_DIR}/oniguruma"
)

# 4. 链接
find_library(log-lib log)
target_link_libraries(zero-regular-preview onig ${log-lib})