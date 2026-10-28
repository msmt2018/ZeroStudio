#include "ts_utils.h"

#ifdef __cplusplus
extern "C" {
#endif

jlong JNICALL tree_cursor_init(JNIEnv *env, jclass clazz, jobject node) {
    return reinterpret_cast<jlong>(
        new TSTreeCursor(
            ts_tree_cursor_new(unmarshal_node(env, node))
        )
    );
}

jlong JNICALL tree_cursor_copy(jlong cursor) {
    TSTreeCursor copy = ts_tree_cursor_copy(
        reinterpret_cast<TSTreeCursor*>(cursor)
    );
    // return the copied TSTreeCursor
    return reinterpret_cast<jlong>(new TSTreeCursor(copy));
}

void JNICALL tree_cursor_delete(jlong cursor) {
    ts_tree_cursor_delete(reinterpret_cast<TSTreeCursor*>(cursor));
    delete reinterpret_cast<TSTreeCursor*>(cursor);
}

jobject JNICALL tree_cursor_get_current_node(JNIEnv *env, jobject thiz) {
    TSTreeCursor *self = GET_POINTER(TSTreeCursor, thiz);
    TSNode node = ts_tree_cursor_current_node(self);
    jobject tree = GET_FIELD(Object, thiz, TSTreeCursor_tree);
    return marshal_node(env, &node, tree);
}

jint JNICALL tree_cursor_get_current_depth(JNIEnv *env, jobject thiz) {
    TSTreeCursor *self = GET_POINTER(TSTreeCursor, thiz);
    return ts_tree_cursor_current_depth(self);
}

jshort JNICALL tree_cursor_get_current_field_id(JNIEnv *env, jobject thiz) {
    TSTreeCursor *self = GET_POINTER(TSTreeCursor, thiz);
    return ts_tree_cursor_current_field_id(self);
}

jstring JNICALL tree_cursor_get_current_field_name(JNIEnv *env, jobject thiz) {
    TSTreeCursor *self = GET_POINTER(TSTreeCursor, thiz);
    const char *name = ts_tree_cursor_current_field_name(self);
    return name ? env->NewStringUTF(name) : nullptr;
}

jint JNICALL tree_cursor_get_current_descendant_index(JNIEnv *env, jobject thiz) {
    TSTreeCursor *self = GET_POINTER(TSTreeCursor, thiz);
    return ts_tree_cursor_current_descendant_index(self);
}

void JNICALL tree_cursor_reset__node(JNIEnv *env, jobject thiz, jobject node) {
    TSTreeCursor *self = GET_POINTER(TSTreeCursor, thiz);
    ts_tree_cursor_reset(self, unmarshal_node(env, node));
}

void JNICALL tree_cursor_reset__cursor(JNIEnv *env, jobject thiz, jobject cursor) {
    auto self = GET_POINTER(TSTreeCursor, thiz);
    auto other = GET_POINTER(TSTreeCursor, cursor);
    ts_tree_cursor_reset_to(self, other);
}

jboolean JNICALL tree_cursor_goto_first_child(JNIEnv *env, jobject thiz) {
    TSTreeCursor *self = GET_POINTER(TSTreeCursor, thiz);
    return ts_tree_cursor_goto_first_child(self);
}

jboolean JNICALL tree_cursor_goto_last_child(JNIEnv *env, jobject thiz) {
    TSTreeCursor *self = GET_POINTER(TSTreeCursor, thiz);
    return ts_tree_cursor_goto_last_child(self);
}

jboolean JNICALL tree_cursor_goto_parent(JNIEnv *env, jobject thiz) {
    TSTreeCursor *self = GET_POINTER(TSTreeCursor, thiz);
    return ts_tree_cursor_goto_parent(self);
}

jboolean JNICALL tree_cursor_goto_next_sibling(JNIEnv *env, jobject thiz) {
    TSTreeCursor *self = GET_POINTER(TSTreeCursor, thiz);
    return ts_tree_cursor_goto_next_sibling(self);
}

jboolean JNICALL tree_cursor_goto_previous_sibling(JNIEnv *env, jobject thiz) {
    TSTreeCursor *self = GET_POINTER(TSTreeCursor, thiz);
    return ts_tree_cursor_goto_previous_sibling(self);
}

void JNICALL tree_cursor_goto_descendant(JNIEnv *env, jobject thiz, jint index) {
    TSTreeCursor *self = GET_POINTER(TSTreeCursor, thiz);
    ts_tree_cursor_goto_descendant(self, static_cast<uint32_t>(index));
}

jlong JNICALL tree_cursor_goto_first_child_for_byte(JNIEnv *env, jobject thiz, jint byte) {
    TSTreeCursor *self = GET_POINTER(TSTreeCursor, thiz);
    return ts_tree_cursor_goto_first_child_for_byte(self, static_cast<uint32_t>(byte));
}

jlong JNICALL tree_cursor_goto_first_child_for_point(JNIEnv *env, jobject thiz, jobject point) {
    TSTreeCursor *self = GET_POINTER(TSTreeCursor, thiz);
    return ts_tree_cursor_goto_first_child_for_point(self, unmarshal_point(env, point));
}

extern const JNINativeMethod TSTreeCursor_methods[] = {
    {"init", "(L" PACKAGE "TSNode;)J", (void *)&tree_cursor_init},
    {"copy", "(J)J", (void *)&tree_cursor_copy},
    {"delete", "(J)V", (void *)&tree_cursor_delete},
    {"getCurrentNode", "()L" PACKAGE "TSNode;", (void *)&tree_cursor_get_current_node},
    {"getCurrentDepth", "()I", (void *)&tree_cursor_get_current_depth},
    {"getCurrentFieldId", "()S", (void *)&tree_cursor_get_current_field_id},
    {"getCurrentFieldName", "()Ljava/lang/String;", (void *)&tree_cursor_get_current_field_name},
    {"getCurrentDescendantIndex", "()I", (void *)&tree_cursor_get_current_descendant_index},
    {"reset", "(L" PACKAGE "TSNode;)V", (void *)&tree_cursor_reset__node},
    {"reset", "(L" PACKAGE "TSTreeCursor;)V", (void *)&tree_cursor_reset__cursor},
    {"gotoFirstChild", "()Z", (void *)&tree_cursor_goto_first_child},
    {"gotoLastChild", "()Z", (void *)&tree_cursor_goto_last_child},
    {"gotoParent", "()Z", (void *)&tree_cursor_goto_parent},
    {"gotoNextSibling", "()Z", (void *)&tree_cursor_goto_next_sibling},
    {"gotoPreviousSibling", "()Z", (void *)&tree_cursor_goto_previous_sibling},
    {"gotoDescendant", "(I)V", (void *)&tree_cursor_goto_descendant},
    {"gotoFirstChildForByte", "(I)J", (void *)&tree_cursor_goto_first_child_for_byte},
    {"gotoFirstChildForPoint", "(L" PACKAGE "TSPoint;)J",
     (void *)&tree_cursor_goto_first_child_for_point},
};

extern const size_t TSTreeCursor_methods_size = sizeof TSTreeCursor_methods / sizeof(JNINativeMethod);

#ifdef __cplusplus
}
#endif // __cplusplus

