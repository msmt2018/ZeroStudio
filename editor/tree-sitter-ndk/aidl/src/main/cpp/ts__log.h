/*
 * This file is part of ZeroStudio.
 *
 * Copyright (C) 2026 android_zero
 *
 * SPDX-License-Identifier: AGPL-3.0-only AND GPL-3.0-only AND Apache-2.0
 *
 * License Information:
 *
 * This file is distributed under the following licenses, which apply
 * cumulatively to the same work:
 *
 * 1. GNU Affero General Public License v3.0 only (AGPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    When a modified version is used to provide network services to users,
 *    the corresponding source code must be made available under the
 *    applicable AGPL terms.
 *
 * 2. GNU General Public License v3.0 only (GPL-3.0-only):
 *    Grants the rights to use, study, modify, and redistribute this work.
 *    Distribution of covered binaries requires compliance with the source
 *    code, license notice, attribution, and other applicable GPL terms.
 *
 * 3. Apache License 2.0 (Apache-2.0):
 *    Grants copyright and applicable patent rights to use, modify, and
 *    distribute this work, subject to its license conditions, including
 *    preservation of notices, inclusion of the license, and marking changes.
 *
 * The SPDX operator "AND" means that all listed license terms apply
 * cumulatively. It does not mean that recipients may choose only one
 * of the listed licenses.
 *
 * You must comply with the applicable terms of all three licenses.
 * This notice is a summary and does not replace the full license texts.
 *
 * Full license texts:
 * - GNU AGPL v3.0: https://www.gnu.org/licenses/agpl-3.0.html
 * - GNU GPL v3.0:  https://www.gnu.org/licenses/gpl-3.0.html
 * - Apache 2.0:    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Author: android_zero
 * GitHub: https://github.com/msmt2018
 * WeChat: android_zero
 */

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
#define LOG_TAG "tree-sitter-aidl"

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