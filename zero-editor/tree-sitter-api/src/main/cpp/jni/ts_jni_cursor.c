/**
 * @author android_zero
 *
 * 变更说明：
 * 实现了 TSTreeCursor 遍历 AST（gotoFirstChild, gotoNextSibling, gotoParent, currentNode）。
 */
#include "ts_jni_common.h"

JNIEXPORT jlong JNICALL
Java_com_zerostudio_ide_treesitter_TSTreeCursor_nativeNew(
        JNIEnv *env, jclass clazz, jintArray in_context, jlongArray in_ptrs) {
    (void)clazz;
    jint context[4];
    jlong ptrs[2];
    (*env)->GetIntArrayRegion(env, in_context, 0, 4, context);
    (*env)->GetLongArrayRegion(env, in_ptrs, 0, 2, ptrs);

    TSNode node = ts_jni_unpack_node(context, ptrs);
    TSTreeCursor *cursor = (TSTreeCursor *)malloc(sizeof(TSTreeCursor));
    *cursor = ts_tree_cursor_new(node);
    return (jlong)(intptr_t)cursor;
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSTreeCursor_nativeDelete(
        JNIEnv *env, jclass clazz, jlong cursor_ptr) {
    (void)clazz;
    (void)env;
    TSTreeCursor *cursor = (TSTreeCursor *)(intptr_t)cursor_ptr;
    if (cursor) {
        ts_tree_cursor_delete(cursor);
        free(cursor);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_zerostudio_ide_treesitter_TSTreeCursor_nativeGotoFirstChild(
        JNIEnv *env, jclass clazz, jlong cursor_ptr) {
    (void)clazz;
    (void)env;
    TSTreeCursor *cursor = (TSTreeCursor *)(intptr_t)cursor_ptr;
    return cursor && ts_tree_cursor_goto_first_child(cursor) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_zerostudio_ide_treesitter_TSTreeCursor_nativeGotoNextSibling(
        JNIEnv *env, jclass clazz, jlong cursor_ptr) {
    (void)clazz;
    (void)env;
    TSTreeCursor *cursor = (TSTreeCursor *)(intptr_t)cursor_ptr;
    return cursor && ts_tree_cursor_goto_next_sibling(cursor) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_zerostudio_ide_treesitter_TSTreeCursor_nativeGotoParent(
        JNIEnv *env, jclass clazz, jlong cursor_ptr) {
    (void)clazz;
    (void)env;
    TSTreeCursor *cursor = (TSTreeCursor *)(intptr_t)cursor_ptr;
    return cursor && ts_tree_cursor_goto_parent(cursor) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSTreeCursor_nativeCurrentNode(
        JNIEnv *env, jclass clazz, jlong cursor_ptr, jintArray out_context, jlongArray out_ptrs) {
    (void)clazz;
    TSTreeCursor *cursor = (TSTreeCursor *)(intptr_t)cursor_ptr;
    if (!cursor) return;
    TSNode node = ts_tree_cursor_current_node(cursor);
    ts_jni_pack_node(env, node, out_context, out_ptrs);
}