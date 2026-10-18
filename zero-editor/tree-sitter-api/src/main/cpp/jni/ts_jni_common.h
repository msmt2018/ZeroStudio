/**
 * @author android_zero
 *
 * 变更说明：
 * 1. 集中管理系统头文件、Tree-sitter 核心 API、异常抛出宏与 JNI 打包/解包工具函数。
 * 2. 定义 TSNode、TSPoint、TSRange 零开销扁平数组传递规则。
 *
 * 方法流程脑图：
 * TSNode Native Struct <== unpack_node / pack_node ==> JVM Context(IntArray) + Pointers(LongArray)
 */
#ifndef TS_JNI_COMMON_H
#define TS_JNI_COMMON_H

#include <jni.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#include "tree_sitter/api.h"
#include "highlight/highlight.h"
#include "tags/tags.h"

#define LOG_TAG "ZeroStudio-TS"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

#define TS_ENGINE_VERSION_STR "0.27.1-zerostudio-full"

#ifdef __cplusplus
extern "C" {
#endif

void ts_jni_throw_exception(JNIEnv *env, const char *message);

static inline TSNode ts_jni_unpack_node(const jint *context, const jlong *ptrs) {
    TSNode node;
    node.context[0] = (uint32_t)context[0];
    node.context[1] = (uint32_t)context[1];
    node.context[2] = (uint32_t)context[2];
    node.context[3] = (uint32_t)context[3];
    node.id = (const void *)(intptr_t)ptrs[0];
    node.tree = (const TSTree *)(intptr_t)ptrs[1];
    return node;
}

static inline void ts_jni_pack_node(JNIEnv *env, TSNode node, jintArray out_context, jlongArray out_ptrs) {
    jint context[4] = {
        (jint)node.context[0],
        (jint)node.context[1],
        (jint)node.context[2],
        (jint)node.context[3]
    };
    (*env)->SetIntArrayRegion(env, out_context, 0, 4, context);

    jlong ptrs[2] = {
        (jlong)(intptr_t)node.id,
        (jlong)(intptr_t)node.tree
    };
    (*env)->SetLongArrayRegion(env, out_ptrs, 0, 2, ptrs);
}

#ifdef __cplusplus
}
#endif

#endif // TS_JNI_COMMON_H