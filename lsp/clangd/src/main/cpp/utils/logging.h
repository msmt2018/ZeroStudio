// 日志工具头文件
// 提供统一的日志宏定义，用于 Android logcat 输出
//
// 使用方式：
//   #define LOG_TAG "YourModuleName"
//   #include "utils/logging.h"
//
// 如果不定义 LOG_TAG，默认使用 "TinaIDE"

#ifndef TINAIDE_LOGGING_H
#define TINAIDE_LOGGING_H

#include <android/log.h>

// 默认日志标签（如果文件没有定义 LOG_TAG）
#ifndef LOG_TAG
#define LOG_TAG "TinaIDE"
#endif

// 日志宏定义
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#endif // TINAIDE_LOGGING_H
