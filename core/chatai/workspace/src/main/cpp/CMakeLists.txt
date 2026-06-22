
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("workspace")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        workspace.cpp)

# 注意: 之前这里还 add_library(termux SHARED termux_pty.cpp) 提供
# com.termux.terminal.JNI 的 JNI 实现。但 termux/emulator 模块已经通过
# ndk-build (src/main/jni/Android.mk) 输出了 libtermux.so, 二者同名冲突,
# 会在 :core:app:mergeDebugNativeLibs 阶段直接 FAILED:
#   "2 files found with path 'lib/arm64-v8a/libtermux.so' from inputs:
#    - termux/emulator/.../libtermux.so
#    - core/chatai/workspace/.../libtermux.so"
# termux/emulator 那一份覆盖范围更全 (多出 setPtyUTF8Mode 等), 并且
# core/chatai/workspace 自己的 Java/Kotlin 代码没有调用 com.termux.terminal.JNI,
# 所以把这里的 termux target 移除, JNI 完全交给 termux/emulator 负责。
# 保留 termux_pty.cpp 源文件但不再编译, 方便以后真要替换实现时参考。

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        android
        log)
