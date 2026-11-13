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

/**
 * @author android_zero
 *
 * 变更说明：
 * 提供极简高效的单次穿透接口：设置纯净 SCM 查询与直出 RGBA 高亮流。
 */

#include <jni.h>
#include <dlfcn.h>
#include <vector>
#include "ts_highlighter_engine.hpp"

using namespace zerostudio;

static void throwJavaException(JNIEnv *env, const char *msg) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls) env->ThrowNew(cls, msg);
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_zerostudio_idea_treesitter_NativeHighlightEngine_nativeGetEngineVersion(JNIEnv *env, jclass) {
    return env->NewStringUTF("0.25.3-zerostudio-full-tsh");
}

JNIEXPORT jlong JNICALL
Java_com_zerostudio_idea_treesitter_NativeHighlightEngine_nativeCreate(JNIEnv *, jclass) {
    auto *engine = new TSHighlighterEngine();
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_zerostudio_idea_treesitter_NativeHighlightEngine_nativeDestroy(JNIEnv *, jclass, jlong engine_ptr) {
    auto *engine = reinterpret_cast<TSHighlighterEngine *>(engine_ptr);
    delete engine;
}

JNIEXPORT jboolean JNICALL
Java_com_zerostudio_idea_treesitter_NativeHighlightEngine_nativeSetLanguageFromSharedLib(
    JNIEnv *env, jclass, jlong engine_ptr, jstring lib_path, jstring func_name) {
    auto *engine = reinterpret_cast<TSHighlighterEngine *>(engine_ptr);
    if (!engine) return JNI_FALSE;

    const char *path = env->GetStringUTFChars(lib_path, nullptr);
    const char *func = env->GetStringUTFChars(func_name, nullptr);

    void *handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        throwJavaException(env, dlerror());
        env->ReleaseStringUTFChars(lib_path, path);
        env->ReleaseStringUTFChars(func_name, func);
        return JNI_FALSE;
    }

    typedef const TSLanguage *(*LanguageConstructor)(void);
    auto constructor = reinterpret_cast<LanguageConstructor>(dlsym(handle, func));
    if (!constructor) {
        throwJavaException(env, dlerror());
        env->ReleaseStringUTFChars(lib_path, path);
        env->ReleaseStringUTFChars(func_name, func);
        return JNI_FALSE;
    }

    const TSLanguage *language = constructor();
    env->ReleaseStringUTFChars(lib_path, path);
    env->ReleaseStringUTFChars(func_name, func);

    return engine->setLanguage(language) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_zerostudio_idea_treesitter_NativeHighlightEngine_nativeSetQuery(
    JNIEnv *env, jclass, jlong engine_ptr, jstring query_str, jintArray out_error) {
    auto *engine = reinterpret_cast<TSHighlighterEngine *>(engine_ptr);
    if (!engine || !query_str) return JNI_FALSE;

    const char *src = env->GetStringUTFChars(query_str, nullptr);
    uint32_t len = static_cast<uint32_t>(env->GetStringUTFLength(query_str));

    uint32_t err_offset = 0;
    uint32_t err_type = 0;
    bool success = engine->setQuery(src, len, &err_offset, &err_type);

    env->ReleaseStringUTFChars(query_str, src);

    if (out_error) {
        jint err[2] = {static_cast<jint>(err_offset), static_cast<jint>(err_type)};
        env->SetIntArrayRegion(out_error, 0, 2, err);
    }

    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_zerostudio_idea_treesitter_NativeHighlightEngine_nativeSetCaptureStyleTable(
    JNIEnv *env, jclass, jlong engine_ptr, jintArray style_table, jint entry_count) {
    auto *engine = reinterpret_cast<TSHighlighterEngine *>(engine_ptr);
    if (!engine || !style_table) return;

    jint *raw_table = env->GetIntArrayElements(style_table, nullptr);
    engine->setCompiledStyles(raw_table, static_cast<size_t>(entry_count));
    env->ReleaseIntArrayElements(style_table, raw_table, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_zerostudio_idea_treesitter_NativeHighlightEngine_nativeSetText(
    JNIEnv *env, jclass, jlong engine_ptr, jstring text_str) {
    auto *engine = reinterpret_cast<TSHighlighterEngine *>(engine_ptr);
    if (!engine || !text_str) return;

    const char *text = env->GetStringUTFChars(text_str, nullptr);
    size_t len = static_cast<size_t>(env->GetStringUTFLength(text_str));

    engine->setText(text, len);

    env->ReleaseStringUTFChars(text_str, text);
}

JNIEXPORT void JNICALL
Java_com_zerostudio_idea_treesitter_NativeHighlightEngine_nativeApplyEdit(
    JNIEnv *env, jclass, jlong engine_ptr,
    jint start_byte, jint old_end_byte, jint new_end_byte,
    jint start_row, jint start_col,
    jint old_end_row, jint old_end_col,
    jint new_end_row, jint new_end_col,
    jstring inserted_text) {
    auto *engine = reinterpret_cast<TSHighlighterEngine *>(engine_ptr);
    if (!engine) return;

    const char *new_txt = "";
    uint32_t new_txt_len = 0;
    if (inserted_text) {
        new_txt = env->GetStringUTFChars(inserted_text, nullptr);
        new_txt_len = static_cast<uint32_t>(env->GetStringUTFLength(inserted_text));
    }

    TSPoint start_pt = {static_cast<uint32_t>(start_row), static_cast<uint32_t>(start_col)};
    TSPoint old_end_pt = {static_cast<uint32_t>(old_end_row), static_cast<uint32_t>(old_end_col)};
    TSPoint new_end_pt = {static_cast<uint32_t>(new_end_row), static_cast<uint32_t>(new_end_col)};

    engine->applyIncrementalEdit(
        static_cast<uint32_t>(start_byte),
        static_cast<uint32_t>(old_end_byte),
        static_cast<uint32_t>(new_end_byte),
        start_pt, old_end_pt, new_end_pt,
        new_txt, new_txt_len
    );

    if (inserted_text) {
        env->ReleaseStringUTFChars(inserted_text, new_txt);
    }
}

JNIEXPORT jintArray JNICALL
Java_com_zerostudio_idea_treesitter_NativeHighlightEngine_nativeComputeViewportHighlight(
    JNIEnv *env, jclass, jlong engine_ptr, jint start_line, jint end_line) {
    auto *engine = reinterpret_cast<TSHighlighterEngine *>(engine_ptr);
    if (!engine) return nullptr;

    std::vector<int32_t> spans = engine->computeViewportHighlight(
        static_cast<uint32_t>(start_line),
        static_cast<uint32_t>(end_line)
    );

    jsize size = static_cast<jsize>(spans.size());
    jintArray result = env->NewIntArray(size);
    if (result && size > 0) {
        env->SetIntArrayRegion(result, 0, size, spans.data());
    }
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_zerostudio_idea_treesitter_NativeHighlightEngine_nativeHasSyntaxError(
    JNIEnv *, jclass, jlong engine_ptr) {
    auto *engine = reinterpret_cast<TSHighlighterEngine *>(engine_ptr);
    return (engine && engine->hasSyntaxError()) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"