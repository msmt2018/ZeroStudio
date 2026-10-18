/**
 * @author android_zero
 *
 * 变更说明：
 * 1. 负责 TSNode 全部属性访问与导航（类型、符号、行列、字段、命名子节点、兄弟节点）。
 * 2. 导出 S-expression 调试文本 `ts_node_string`。
 */
#include "ts_jni_common.h"

JNIEXPORT jstring JNICALL
Java_com_zerostudio_ide_treesitter_TSNode_nativeGetType(
        JNIEnv *env, jclass clazz, jintArray in_context, jlongArray in_ptrs) {
    (void)clazz;
    jint context[4];
    jlong ptrs[2];
    (*env)->GetIntArrayRegion(env, in_context, 0, 4, context);
    (*env)->GetLongArrayRegion(env, in_ptrs, 0, 2, ptrs);
    TSNode node = ts_jni_unpack_node(context, ptrs);
    if (ts_node_is_null(node)) return NULL;
    const char *type = ts_node_type(node);
    return type ? (*env)->NewStringUTF(env, type) : NULL;
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TSNode_nativeGetSymbol(
        JNIEnv *env, jclass clazz, jintArray in_context, jlongArray in_ptrs) {
    (void)clazz;
    jint context[4];
    jlong ptrs[2];
    (*env)->GetIntArrayRegion(env, in_context, 0, 4, context);
    (*env)->GetLongArrayRegion(env, in_ptrs, 0, 2, ptrs);
    TSNode node = ts_jni_unpack_node(context, ptrs);
    return (jint)ts_node_symbol(node);
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TSNode_nativeGetStartByte(
        JNIEnv *env, jclass clazz, jintArray in_context, jlongArray in_ptrs) {
    (void)clazz;
    jint context[4];
    jlong ptrs[2];
    (*env)->GetIntArrayRegion(env, in_context, 0, 4, context);
    (*env)->GetLongArrayRegion(env, in_ptrs, 0, 2, ptrs);
    TSNode node = ts_jni_unpack_node(context, ptrs);
    return (jint)ts_node_start_byte(node);
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TSNode_nativeGetEndByte(
        JNIEnv *env, jclass clazz, jintArray in_context, jlongArray in_ptrs) {
    (void)clazz;
    jint context[4];
    jlong ptrs[2];
    (*env)->GetIntArrayRegion(env, in_context, 0, 4, context);
    (*env)->GetLongArrayRegion(env, in_ptrs, 0, 2, ptrs);
    TSNode node = ts_jni_unpack_node(context, ptrs);
    return (jint)ts_node_end_byte(node);
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSNode_nativeGetStartPoint(
        JNIEnv *env, jclass clazz, jintArray in_context, jlongArray in_ptrs, jintArray out_point) {
    (void)clazz;
    jint context[4];
    jlong ptrs[2];
    (*env)->GetIntArrayRegion(env, in_context, 0, 4, context);
    (*env)->GetLongArrayRegion(env, in_ptrs, 0, 2, ptrs);
    TSNode node = ts_jni_unpack_node(context, ptrs);
    TSPoint pt = ts_node_start_point(node);
    jint p[2] = {(jint)pt.row, (jint)pt.column};
    (*env)->SetIntArrayRegion(env, out_point, 0, 2, p);
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSNode_nativeGetEndPoint(
        JNIEnv *env, jclass clazz, jintArray in_context, jlongArray in_ptrs, jintArray out_point) {
    (void)clazz;
    jint context[4];
    jlong ptrs[2];
    (*env)->GetIntArrayRegion(env, in_context, 0, 4, context);
    (*env)->GetLongArrayRegion(env, in_ptrs, 0, 2, ptrs);
    TSNode node = ts_jni_unpack_node(context, ptrs);
    TSPoint pt = ts_node_end_point(node);
    jint p[2] = {(jint)pt.row, (jint)pt.column};
    (*env)->SetIntArrayRegion(env, out_point, 0, 2, p);
}

JNIEXPORT jboolean JNICALL
Java_com_zerostudio_ide_treesitter_TSNode_nativeIsNamed(
        JNIEnv *env, jclass clazz, jintArray in_context, jlongArray in_ptrs) {
    (void)clazz;
    jint context[4];
    jlong ptrs[2];
    (*env)->GetIntArrayRegion(env, in_context, 0, 4, context);
    (*env)->GetLongArrayRegion(env, in_ptrs, 0, 2, ptrs);
    TSNode node = ts_jni_unpack_node(context, ptrs);
    return ts_node_is_named(node) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_zerostudio_ide_treesitter_TSNode_nativeHasError(
        JNIEnv *env, jclass clazz, jintArray in_context, jlongArray in_ptrs) {
    (void)clazz;
    jint context[4];
    jlong ptrs[2];
    (*env)->GetIntArrayRegion(env, in_context, 0, 4, context);
    (*env)->GetLongArrayRegion(env, in_ptrs, 0, 2, ptrs);
    TSNode node = ts_jni_unpack_node(context, ptrs);
    return ts_node_has_error(node) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TSNode_nativeGetChildCount(
        JNIEnv *env, jclass clazz, jintArray in_context, jlongArray in_ptrs) {
    (void)clazz;
    jint context[4];
    jlong ptrs[2];
    (*env)->GetIntArrayRegion(env, in_context, 0, 4, context);
    (*env)->GetLongArrayRegion(env, in_ptrs, 0, 2, ptrs);
    TSNode node = ts_jni_unpack_node(context, ptrs);
    return (jint)ts_node_child_count(node);
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSNode_nativeGetChild(
        JNIEnv *env, jclass clazz, jintArray in_context, jlongArray in_ptrs, jint index,
        jintArray out_context, jlongArray out_ptrs) {
    (void)clazz;
    jint context[4];
    jlong ptrs[2];
    (*env)->GetIntArrayRegion(env, in_context, 0, 4, context);
    (*env)->GetLongArrayRegion(env, in_ptrs, 0, 2, ptrs);
    TSNode node = ts_jni_unpack_node(context, ptrs);
    TSNode child = ts_node_child(node, (uint32_t)index);
    ts_jni_pack_node(env, child, out_context, out_ptrs);
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSNode_nativeGetChildByFieldName(
        JNIEnv *env, jclass clazz, jintArray in_context, jlongArray in_ptrs, jstring name,
        jintArray out_context, jlongArray out_ptrs) {
    (void)clazz;
    jint context[4];
    jlong ptrs[2];
    (*env)->GetIntArrayRegion(env, in_context, 0, 4, context);
    (*env)->GetLongArrayRegion(env, in_ptrs, 0, 2, ptrs);
    TSNode node = ts_jni_unpack_node(context, ptrs);

    const char *str = (*env)->GetStringUTFChars(env, name, NULL);
    uint32_t len = (uint32_t)(*env)->GetStringUTFLength(env, name);
    TSNode child = ts_node_child_by_field_name(node, str, len);
    (*env)->ReleaseStringUTFChars(env, name, str);

    ts_jni_pack_node(env, child, out_context, out_ptrs);
}

JNIEXPORT jstring JNICALL
Java_com_zerostudio_ide_treesitter_TSNode_nativeToString(
        JNIEnv *env, jclass clazz, jintArray in_context, jlongArray in_ptrs) {
    (void)clazz;
    jint context[4];
    jlong ptrs[2];
    (*env)->GetIntArrayRegion(env, in_context, 0, 4, context);
    (*env)->GetLongArrayRegion(env, in_ptrs, 0, 2, ptrs);
    TSNode node = ts_jni_unpack_node(context, ptrs);
    char *s = ts_node_string(node);
    if (!s) return NULL;
    jstring res = (*env)->NewStringUTF(env, s);
    free(s);
    return res;
}