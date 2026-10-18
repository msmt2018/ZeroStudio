/**
 * @author android_zero
 *
 * 变更说明：
 * 1. 映射 TSQuery 与 TSQueryCursor 全部模式匹配机制。
 * 2. 支持按字节/行列区间剪裁视口范围，大幅度提升光标查询效率。
 */
#include "ts_jni_common.h"

JNIEXPORT jlong JNICALL
Java_com_zerostudio_ide_treesitter_TSQuery_nativeNew(
        JNIEnv *env, jclass clazz, jlong lang_ptr, jstring source, jintArray out_error) {
    (void)clazz;
    const TSLanguage *lang = (const TSLanguage *)(intptr_t)lang_ptr;
    if (!lang || !source) return 0;

    const char *src = (*env)->GetStringUTFChars(env, source, NULL);
    uint32_t len = (uint32_t)(*env)->GetStringUTFLength(env, source);

    uint32_t error_offset = 0;
    TSQueryError error_type = TSQueryErrorNone;
    TSQuery *query = ts_query_new(lang, src, len, &error_offset, &error_type);

    (*env)->ReleaseStringUTFChars(env, source, src);

    jint err_data[2] = {(jint)error_offset, (jint)error_type};
    (*env)->SetIntArrayRegion(env, out_error, 0, 2, err_data);
    return (jlong)(intptr_t)query;
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSQuery_nativeDelete(JNIEnv *env, jclass clazz, jlong query_ptr) {
    (void)clazz;
    (void)env;
    TSQuery *query = (TSQuery *)(intptr_t)query_ptr;
    if (query) {
        ts_query_delete(query);
    }
}

JNIEXPORT jstring JNICALL
Java_com_zerostudio_ide_treesitter_TSQuery_nativeGetCaptureNameForId(
        JNIEnv *env, jclass clazz, jlong query_ptr, jint index) {
    (void)clazz;
    const TSQuery *query = (const TSQuery *)(intptr_t)query_ptr;
    if (!query) return NULL;
    uint32_t len = 0;
    const char *name = ts_query_capture_name_for_id(query, (uint32_t)index, &len);
    return name ? (*env)->NewStringUTF(env, name) : NULL;
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TSQuery_nativeGetCaptureCount(JNIEnv *env, jclass clazz, jlong query_ptr) {
    (void)clazz;
    (void)env;
    const TSQuery *query = (const TSQuery *)(intptr_t)query_ptr;
    return query ? (jint)ts_query_capture_count(query) : 0;
}

JNIEXPORT jlong JNICALL
Java_com_zerostudio_ide_treesitter_TSQueryCursor_nativeNew(JNIEnv *env, jclass clazz) {
    (void)clazz;
    (void)env;
    return (jlong)(intptr_t)ts_query_cursor_new();
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSQueryCursor_nativeDelete(JNIEnv *env, jclass clazz, jlong cursor_ptr) {
    (void)clazz;
    (void)env;
    TSQueryCursor *cursor = (TSQueryCursor *)(intptr_t)cursor_ptr;
    if (cursor) {
        ts_query_cursor_delete(cursor);
    }
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSQueryCursor_nativeExec(
        JNIEnv *env, jclass clazz, jlong cursor_ptr, jlong query_ptr,
        jintArray in_context, jlongArray in_ptrs) {
    (void)clazz;
    TSQueryCursor *cursor = (TSQueryCursor *)(intptr_t)cursor_ptr;
    const TSQuery *query = (const TSQuery *)(intptr_t)query_ptr;
    if (!cursor || !query) return;

    jint context[4];
    jlong ptrs[2];
    (*env)->GetIntArrayRegion(env, in_context, 0, 4, context);
    (*env)->GetLongArrayRegion(env, in_ptrs, 0, 2, ptrs);
    TSNode node = ts_jni_unpack_node(context, ptrs);

    ts_query_cursor_exec(cursor, query, node);
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSQueryCursor_nativeSetPointRange(
        JNIEnv *env, jclass clazz, jlong cursor_ptr,
        jint start_row, jint start_col, jint end_row, jint end_col) {
    (void)clazz;
    (void)env;
    TSQueryCursor *cursor = (TSQueryCursor *)(intptr_t)cursor_ptr;
    if (!cursor) return;
    TSPoint start_pt = {.row = (uint32_t)start_row, .column = (uint32_t)start_col};
    TSPoint end_pt = {.row = (uint32_t)end_row, .column = (uint32_t)end_col};
    ts_query_cursor_set_point_range(cursor, start_pt, end_pt);
}

JNIEXPORT jboolean JNICALL
Java_com_zerostudio_ide_treesitter_TSQueryCursor_nativeNextCapture(
        JNIEnv *env, jclass clazz, jlong cursor_ptr,
        jintArray out_meta, jintArray out_node_context, jlongArray out_node_ptrs) {
    (void)clazz;
    TSQueryCursor *cursor = (TSQueryCursor *)(intptr_t)cursor_ptr;
    if (!cursor) return JNI_FALSE;

    TSQueryMatch match;
    uint32_t capture_index = 0;
    if (ts_query_cursor_next_capture(cursor, &match, &capture_index)) {
        const TSQueryCapture *cap = &match.captures[capture_index];

        jint meta[3] = {
            (jint)match.id,
            (jint)match.pattern_index,
            (jint)cap->index
        };
        (*env)->SetIntArrayRegion(env, out_meta, 0, 3, meta);
        ts_jni_pack_node(env, cap->node, out_node_context, out_node_ptrs);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}