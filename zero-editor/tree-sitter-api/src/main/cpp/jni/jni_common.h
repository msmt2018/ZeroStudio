#ifndef __JNI_COMMON_H__
#define __JNI_COMMON_H__

#include <jni.h>
#include <string.h>

#ifdef __ANDROID__
#include <android/log.h>
#endif

#ifdef __cplusplus
extern "C" {
#endif

#define PACKAGE "com/zerostudio/idea/treesitter"

#define TREE_SITTER_VERSION "v0.27.1"

#define TAG "JNI_NATIVE_LOG"

#define JNI_VERSION JNI_VERSION_1_6

// get the base name of pathname
#define FILE_NAME(x) (strrchr(x, '/') ? strrchr(x, '/') + 1 : x)

#ifdef __ANDROID__
// android log print
// log.i
#define LOGI(fmt, ...)                                                         \
  __android_log_print(ANDROID_LOG_INFO, TAG, "[%s:%s:%u] " fmt,                \
                      FILE_NAME(__FILE__), __PRETTY_FUNCTION__, __LINE__,      \
                      ##__VA_ARGS__)

// log.e
#define LOGE(fmt, ...)                                                         \
  __android_log_print(ANDROID_LOG_ERROR, TAG, "[%s:%s:%u] " fmt,               \
                      FILE_NAME(__FILE__), __PRETTY_FUNCTION__, __LINE__,      \
                      ##__VA_ARGS__)
#else
// C-Style log print
#define LOGI(...) fprintf(stdout, __VA_ARGS__)
#define LOGE(...) fprintf(stderr, __VA_ARGS__)
#endif // end __ANDROID__

// the global JNIEnv
extern JNIEnv *getEnv();
// the global JavaVM
extern JavaVM *getJavaVM();

#ifdef __cplusplus
}
#endif // __cplusplus

#endif // __JNI_COMMON_H__
