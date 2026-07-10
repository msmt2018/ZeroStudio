/*
 *  This file is part of android-tree-sitter.
 *  
 *  Manually generated for migration.
 *  Based on logic from GenerateNativeHeadersTask.kt
 */

/**
 * @author android_zero
 * @brief 跨平台日志宏定义，用于统一 Android NDK 和标准输出的日志调用。
 */

#ifndef ATS_TS_LOG_H
#define ATS_TS_LOG_H

// 定义日志 TAG，通常跟随项目名称
#define LOG_TAG "tree-sitter-java"

#ifdef __ANDROID__
#include <android/log.h>

// Android 平台的日志实现，调用 __android_log_print
#define LOGE(TAG, ...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(TAG, ...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGD(TAG, ...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(TAG, ...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGV(TAG, ...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)

#else
#include <cstdio>

// 非 Android 平台（如 Host 测试）的日志实现，映射到 printf
#define LOGE(TAG, ...) printf("[%s] ERROR: ", TAG); printf(__VA_ARGS__); printf("\n")
#define LOGW(TAG, ...) printf("[%s] WARNING: ", TAG); printf(__VA_ARGS__); printf("\n")
#define LOGD(TAG, ...) printf("[%s] DEBUG: ", TAG); printf(__VA_ARGS__); printf("\n")
#define LOGI(TAG, ...) printf("[%s] INFO: ", TAG); printf(__VA_ARGS__); printf("\n")
#define LOGV(TAG, ...) printf("[%s] VERBOSE: ", TAG); printf(__VA_ARGS__); printf("\n")

#endif // __ANDROID__

#endif //ATS_TS_LOG_H
