/**
 * Tree-sitter Node JNI Bindings
 */

#include <jni.h>
#include <string>
#include <cstring>
#include "../include/tree_sitter/api.h"

// Helper to reconstruct TSNode from Java data
static TSNode makeNode(JNIEnv* env, jintArray contextArr, jlong id, jlong treePtr) {
    TSNode node = {};
    if (contextArr != nullptr) {
        jint* context = env->GetIntArrayElements(contextArr, nullptr);
        if (context != nullptr) {
            node.context[0] = static_cast<uint32_t>(context[0]);
            node.context[1] = static_cast<uint32_t>(context[1]);
            node.context[2] = static_cast<uint32_t>(context[2]);
            node.context[3] = static_cast<uint32_t>(context[3]);
            env->ReleaseIntArrayElements(contextArr, context, JNI_ABORT);
        }
    }
    node.id = reinterpret_cast<const void*>(id);
    node.tree = reinterpret_cast<const TSTree*>(treePtr);
    return node;
}

// Helper to create node data array
static jlongArray makeNodeData(JNIEnv* env, TSNode node) {
    if (ts_node_is_null(node)) return nullptr;
    
    jlongArray result = env->NewLongArray(6);
    jlong data[6] = {
        static_cast<jlong>(node.context[0]),
        static_cast<jlong>(node.context[1]),
        static_cast<jlong>(node.context[2]),
        static_cast<jlong>(node.context[3]),
        reinterpret_cast<jlong>(node.id),
        reinterpret_cast<jlong>(node.tree)
    };
    env->SetLongArrayRegion(result, 0, 6, data);
    return result;
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeType(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    if (ts_node_is_null(node)) return nullptr;
    const char* type = ts_node_type(node);
    return type ? env->NewStringUTF(type) : nullptr;
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeSymbol(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    return static_cast<jint>(ts_node_symbol(node));
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeStartByte(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    return static_cast<jint>(ts_node_start_byte(node));
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeEndByte(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    return static_cast<jint>(ts_node_end_byte(node));
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeStartRow(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    return static_cast<jint>(ts_node_start_point(node).row);
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeStartColumn(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    return static_cast<jint>(ts_node_start_point(node).column);
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeEndRow(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    return static_cast<jint>(ts_node_end_point(node).row);
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeEndColumn(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    return static_cast<jint>(ts_node_end_point(node).column);
}

JNIEXPORT jboolean JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeIsNull(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    return ts_node_is_null(node) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeIsNamed(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    return ts_node_is_named(node) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeIsMissing(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    return ts_node_is_missing(node) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeHasError(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    return ts_node_has_error(node) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeChildCount(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    return static_cast<jint>(ts_node_child_count(node));
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeNamedChildCount(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    return static_cast<jint>(ts_node_named_child_count(node));
}

JNIEXPORT jlongArray JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeChild(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr, jint index) {
    TSNode node = makeNode(env, context, id, treePtr);
    TSNode child = ts_node_child(node, static_cast<uint32_t>(index));
    return makeNodeData(env, child);
}

JNIEXPORT jlongArray JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeNamedChild(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr, jint index) {
    TSNode node = makeNode(env, context, id, treePtr);
    TSNode child = ts_node_named_child(node, static_cast<uint32_t>(index));
    return makeNodeData(env, child);
}

JNIEXPORT jlongArray JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeParent(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    TSNode parent = ts_node_parent(node);
    return makeNodeData(env, parent);
}

JNIEXPORT jlongArray JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeNextSibling(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    TSNode sibling = ts_node_next_sibling(node);
    return makeNodeData(env, sibling);
}

JNIEXPORT jlongArray JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativePrevSibling(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    TSNode sibling = ts_node_prev_sibling(node);
    return makeNodeData(env, sibling);
}

JNIEXPORT jlongArray JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeNextNamedSibling(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    TSNode sibling = ts_node_next_named_sibling(node);
    return makeNodeData(env, sibling);
}

JNIEXPORT jlongArray JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativePrevNamedSibling(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    TSNode sibling = ts_node_prev_named_sibling(node);
    return makeNodeData(env, sibling);
}

JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSNode_nativeString(JNIEnv* env, jclass clazz, jintArray context, jlong id, jlong treePtr) {
    TSNode node = makeNode(env, context, id, treePtr);
    char* str = ts_node_string(node);
    if (str == nullptr) return nullptr;
    jstring result = env->NewStringUTF(str);
    free(str);
    return result;
}

} // extern "C"
