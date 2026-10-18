/**
 * @author android_zero
 *
 * 变更说明：
 * 1. 负责 TSTree 生命周期释放、克隆操作。
 * 2. 映射关键增量编辑 `ts_tree_edit`，以极速通知旧 AST 语法范围位移。
 * 3. 映射 `ts_tree_get_changed_ranges` 导出精确变更区间数组。
 *
 * 流程脑图：
 * TSTree* ──> ts_tree_edit(&edit) ──> ts_tree_get_changed_ranges() ──> int[]
 */
#include "ts_jni_common.h"

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSTree_nativeDelete(JNIEnv *env, jclass clazz, jlong tree_ptr) {
    (void)clazz;
    (void)env;
    TSTree *tree = (TSTree *)(intptr_t)tree_ptr;
    if (tree) {
        ts_tree_delete(tree);
    }
}

JNIEXPORT jlong JNICALL
Java_com_zerostudio_ide_treesitter_TSTree_nativeCopy(JNIEnv *env, jclass clazz, jlong tree_ptr) {
    (void)clazz;
    (void)env;
    const TSTree *tree = (const TSTree *)(intptr_t)tree_ptr;
    return tree ? (jlong)(intptr_t)ts_tree_copy(tree) : 0;
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSTree_nativeGetRootNode(
        JNIEnv *env, jclass clazz, jlong tree_ptr, jintArray out_context, jlongArray out_ptrs) {
    (void)clazz;
    const TSTree *tree = (const TSTree *)(intptr_t)tree_ptr;
    if (!tree) return;
    TSNode node = ts_tree_root_node(tree);
    ts_jni_pack_node(env, node, out_context, out_ptrs);
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSTree_nativeEdit(
        JNIEnv *env, jclass clazz, jlong tree_ptr,
        jint start_byte, jint old_end_byte, jint new_end_byte,
        jint start_row, jint start_col,
        jint old_end_row, jint old_end_col,
        jint new_end_row, jint new_end_col) {
    (void)clazz;
    (void)env;
    TSTree *tree = (TSTree *)(intptr_t)tree_ptr;
    if (!tree) return;

    TSInputEdit edit = {
        .start_byte = (uint32_t)start_byte,
        .old_end_byte = (uint32_t)old_end_byte,
        .new_end_byte = (uint32_t)new_end_byte,
        .start_point = {.row = (uint32_t)start_row, .column = (uint32_t)start_col},
        .old_end_point = {.row = (uint32_t)old_end_row, .column = (uint32_t)old_end_col},
        .new_end_point = {.row = (uint32_t)new_end_row, .column = (uint32_t)new_end_col}
    };
    ts_tree_edit(tree, &edit);
}

JNIEXPORT jintArray JNICALL
Java_com_zerostudio_ide_treesitter_TSTree_nativeGetChangedRanges(
        JNIEnv *env, jclass clazz, jlong old_tree_ptr, jlong new_tree_ptr) {
    (void)clazz;
    const TSTree *old_tree = (const TSTree *)(intptr_t)old_tree_ptr;
    const TSTree *new_tree = (const TSTree *)(intptr_t)new_tree_ptr;
    if (!old_tree || !new_tree) return NULL;

    uint32_t length = 0;
    TSRange *ranges = ts_tree_get_changed_ranges(old_tree, new_tree, &length);
    if (!ranges || length == 0) return NULL;

    // 每 6 个整型表示一个 TSRange: [start_byte, end_byte, start_row, start_col, end_row, end_col]
    jsize element_count = (jsize)(length * 6);
    jintArray result = (*env)->NewIntArray(env, element_count);
    jint *buf = (jint *)malloc(sizeof(jint) * element_count);

    for (uint32_t i = 0; i < length; ++i) {
        uint32_t idx = i * 6;
        buf[idx + 0] = (jint)ranges[i].start_byte;
        buf[idx + 1] = (jint)ranges[i].end_byte;
        buf[idx + 2] = (jint)ranges[i].start_point.row;
        buf[idx + 3] = (jint)ranges[i].start_point.column;
        buf[idx + 4] = (jint)ranges[i].end_point.row;
        buf[idx + 5] = (jint)ranges[i].end_point.column;
    }
    (*env)->SetIntArrayRegion(env, result, 0, element_count, buf);
    free(buf);
    free(ranges);
    return result;
}