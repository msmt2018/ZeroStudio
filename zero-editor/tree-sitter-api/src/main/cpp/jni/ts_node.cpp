#include <stdio.h>

#include "ts_utils.h"

#ifdef __cplusplus
extern "C" {
#endif

jobject JNICALL node_string(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    const char *string = ts_node_string(self);
    jstring node_text = env->NewStringUTF(string);
    // here requires free the string
    free((void*)string);
    return node_text;
}

jshort JNICALL node_symbol(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jshort>(ts_node_symbol(self));
}

jshort JNICALL node_grammar_symbol(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jshort>(ts_node_grammar_symbol(self));
}

jstring JNICALL node_type(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    const char *type = ts_node_type(self);
    return env->NewStringUTF(type);
}

jstring JNICALL node_grammar_type(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    const char *type = ts_node_grammar_type(self);
    return env->NewStringUTF(type);
}

jboolean JNICALL node_is_named(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jboolean>(ts_node_is_named(self));
}

jboolean JNICALL node_is_extra(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jboolean>(ts_node_is_extra(self));
}

jboolean JNICALL node_is_error(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jboolean>(ts_node_is_error(self));
}

jboolean JNICALL node_is_missing(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jboolean>(ts_node_is_missing(self));
}

jboolean JNICALL node_has_error(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jboolean>(ts_node_has_error(self));
}

jboolean JNICALL node_has_changes(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jboolean>(ts_node_has_changes(self));
}

jshort JNICALL node_get_parse_state(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jshort>(ts_node_parse_state(self));
}

jshort JNICALL node_get_next_parse_state(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jshort>(ts_node_next_parse_state(self));
}

jint JNICALL node_get_start_byte(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jint>(ts_node_start_byte(self));
}

jint JNICALL node_get_end_byte(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jint>(ts_node_end_byte(self));
}

jobject JNICALL node_get_start_point(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    TSPoint point = ts_node_start_point(self);
    return marshal_point(env, &point);
}

jobject JNICALL node_get_end_point(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    TSPoint point = ts_node_end_point(self);
    return marshal_point(env, &point);
}

jint JNICALL node_get_child_count(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jint>(ts_node_child_count(self));
}

jint JNICALL node_get_named_child_count(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jint>(ts_node_named_child_count(self));
}

jint JNICALL node_get_descendant_count(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    return static_cast<jint>(ts_node_descendant_count(self));
}


jobject JNICALL node_get_parent(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    auto parent = ts_node_parent(self);
    if (ts_node_is_null(parent))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &parent, tree);
}

jobject JNICALL node_get_next_sibling(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    TSNode result = ts_node_next_sibling(self);
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

jobject JNICALL node_get_prev_sibling(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    TSNode result = ts_node_prev_sibling(self);
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

jobject JNICALL node_get_prev_named_sibling(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    TSNode result = ts_node_prev_named_sibling(self);
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

jobject JNICALL node_get_next_named_sibling(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    TSNode result = ts_node_next_named_sibling(self);
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

jobject JNICALL node_get_children(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    uint32_t count = ts_node_child_count(self);
    
    jobject array_list = NEW_OBJECT(ArrayList, (jint)count);
    if (count == 0) return array_list;

    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    TSTreeCursor cursor = ts_tree_cursor_new(self);
    ts_tree_cursor_goto_first_child(&cursor);
    for (uint32_t i = 0; i < count; ++i) {
        TSNode child_node = ts_tree_cursor_current_node(&cursor);
        jobject node_object = marshal_node(env, &child_node, tree);
        CALL_METHOD(Boolean, array_list, ArrayList_add, node_object);       
        env->DeleteLocalRef(node_object);
        ts_tree_cursor_goto_next_sibling(&cursor);
    }
    
    ts_tree_cursor_delete(&cursor);    
    return array_list;
}


jobject JNICALL node_child(JNIEnv *env, jobject thiz, jint index) {
    TSNode self = unmarshal_node(env, thiz);
    if (ts_node_child_count(self) <= static_cast<uint32_t>(index)) {
        const char *fmt = "Child index %u is out of bounds";
        char buffer[40] = {0};
        sprintf(buffer, fmt, static_cast<uint32_t>(index));
        THROW(IndexOutOfBoundsException, static_cast<const char*>(buffer));
        return nullptr;
    }

    TSNode result = ts_node_child(self, static_cast<uint32_t>(index));
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

jobject JNICALL node_named_child(JNIEnv *env, jobject thiz, jint index) {
    TSNode self = unmarshal_node(env, thiz);
    if (ts_node_child_count(self) <= static_cast<uint32_t>(index)) {
        const char *fmt = "Child index %u is out of bounds";
        char buffer[40] = {0};
        sprintf(buffer, fmt, static_cast<uint32_t>(index));
        THROW(IndexOutOfBoundsException, static_cast<const char*>(buffer));
        return nullptr;
    }

    TSNode result = ts_node_named_child(self, static_cast<uint32_t>(index));
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

jobject JNICALL node_first_child_for_byte(JNIEnv *env, jobject thiz, jint byte) {
    TSNode self = unmarshal_node(env, thiz);
    TSNode result = ts_node_first_child_for_byte(self, byte);
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

jobject JNICALL node_first_named_child_for_byte(JNIEnv *env, jobject thiz, jint byte) {
    TSNode self = unmarshal_node(env, thiz);
    TSNode result = ts_node_first_named_child_for_byte(self, byte);
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

jobject JNICALL node_child_by_field_id(JNIEnv *env, jobject thiz, jshort id) {
    TSNode self = unmarshal_node(env, thiz);
    TSNode result = ts_node_child_by_field_id(self, static_cast<uint16_t>(id));
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

jobject JNICALL node_child_by_field_name(JNIEnv *env, jobject thiz, jstring name) {
    TSNode self = unmarshal_node(env, thiz);
    const char *field_name = env->GetStringUTFChars(name, nullptr);
    uint32_t length = static_cast<uint32_t>(env->GetStringUTFLength(name));
    TSNode result = ts_node_child_by_field_name(self, field_name, length);
    env->ReleaseStringUTFChars(name, field_name);
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

jobject JNICALL node_children_by_field_id(JNIEnv *env, jobject thiz, jshort id) {
    // nothing to do
    if (id == 0) return NEW_OBJECT(ArrayList, 0);
    // ...
    TSNode self = unmarshal_node(env, thiz);
    uint32_t count = ts_node_child_count(self);
    jobject array_list = NEW_OBJECT(ArrayList, static_cast<jint>(count));
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    TSTreeCursor cursor = ts_tree_cursor_new(self);
    bool ok = ts_tree_cursor_goto_first_child(&cursor);
    while (ok) {
        uint16_t field_id = ts_tree_cursor_current_field_id(&cursor);
        if (field_id == static_cast<uint16_t>(id)) {
            TSNode child_node = ts_tree_cursor_current_node(&cursor);
            jobject node_object = marshal_node(env, &child_node, tree);
            CALL_METHOD(Boolean, array_list, ArrayList_add, node_object);
            env->DeleteLocalRef(node_object);
        }
        ok = ts_tree_cursor_goto_next_sibling(&cursor);
    }
    ts_tree_cursor_delete(&cursor);
    return array_list;
}

jstring JNICALL node_field_name_for_child(JNIEnv *env, jobject thiz, jint index) {
    TSNode self = unmarshal_node(env, thiz);
    if (ts_node_child_count(self) <= static_cast<uint32_t>(index)) {
        const char *fmt = "Child index %u is out of bounds";
        char buffer[40] = {0};
        sprintf(buffer, fmt, static_cast<uint32_t>(index));
        THROW(IndexOutOfBoundsException, static_cast<const char*>(buffer));
        return nullptr;
    }

    const char *field_name = ts_node_field_name_for_child(self, static_cast<uint32_t>(index));
    return field_name ? env->NewStringUTF(field_name) : nullptr;
}

jstring JNICALL node_field_name_for_named_child(JNIEnv *env, jobject thiz, jint index) {
    TSNode self = unmarshal_node(env, thiz);
    if (ts_node_child_count(self) <= static_cast<uint32_t>(index)) {
        const char *fmt = "Child index %u is out of bounds";
        char buffer[40] = {0};
        sprintf(buffer, fmt, static_cast<uint32_t>(index));
        THROW(IndexOutOfBoundsException, static_cast<const char*>(buffer));
        return nullptr;
    }

    const char *field_name = ts_node_field_name_for_named_child(self, static_cast<uint32_t>(index));
    return field_name ? env->NewStringUTF(field_name) : nullptr;
}

jobject JNICALL node_child_with_descendant(JNIEnv *env, jobject thiz, jobject descendant) {
    TSNode self = unmarshal_node(env, thiz);
    TSNode other = unmarshal_node(env, descendant);
    TSNode result = ts_node_child_with_descendant(self, other);
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

jobject JNICALL node_descendant_bytes(JNIEnv *env, jobject thiz, jint start, jint end) {
    TSNode self = unmarshal_node(env, thiz);
    TSNode result = ts_node_descendant_for_byte_range(self, (uint32_t)start, (uint32_t)end);
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

jobject JNICALL node_descendant_points(JNIEnv *env, jobject thiz, jobject start, jobject end) {
    TSNode self = unmarshal_node(env, thiz);
    TSPoint start_point = unmarshal_point(env, start);
    TSPoint end_point = unmarshal_point(env, end);
    TSNode result = ts_node_descendant_for_point_range(self, start_point, end_point);
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

jobject JNICALL node_named_descendant_bytes(JNIEnv *env, jobject thiz, jint start, jint end) {
    TSNode self = unmarshal_node(env, thiz);
    TSNode result = ts_node_named_descendant_for_byte_range(self, (uint32_t)start, (uint32_t)end);
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

jobject JNICALL node_named_descendant_points(JNIEnv *env, jobject thiz, jobject start, jobject end) {
    TSNode self = unmarshal_node(env, thiz);
    TSPoint start_point = unmarshal_point(env, start);
    TSPoint end_point = unmarshal_point(env, end);
    TSNode result = ts_node_named_descendant_for_point_range(self, start_point, end_point);
    if (ts_node_is_null(result))
        return nullptr;
    jobject tree = GET_FIELD(Object, thiz, TSNode_tree);
    return marshal_node(env, &result, tree);
}

void JNICALL node_edit(JNIEnv *env, jobject thiz, jobject edit) {
    TSNode self = unmarshal_node(env, thiz);
    TSInputEdit input_edit = unmarshal_input_edit(env, edit);
    ts_node_edit(&self, &input_edit);
    jintArray context = (jintArray)GET_FIELD(Object, thiz, TSNode_context);
    env->SetIntArrayRegion(context, 0, 4, (jint *)self.context);
}

jstring JNICALL node_sexp(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    const char *sexp = ts_node_string(self);
    jstring result = env->NewStringUTF(sexp);
    free((void*)sexp);
    return result;
}

jint JNICALL node_hash_code(JNIEnv *env, jobject thiz) {
    TSNode self = unmarshal_node(env, thiz);
    uintptr_t id = (uintptr_t)self.id;
    uintptr_t tree = (uintptr_t)self.tree;
    return (jint)(id == tree ? id : id ^ tree);
}

jboolean JNICALL node_native_equals(JNIEnv *env, jobject thiz, jobject other) {
    return static_cast<jboolean>(
        ts_node_eq(
            unmarshal_node(env, thiz), 
            unmarshal_node(env, other)
        )
    );
}

extern const JNINativeMethod TSNode_methods[] = {
    {"getString", "()Ljava/lang/String;", (void *)&node_string},
    {"getSymbol", "()S", (void *)&node_symbol},
    {"getGrammarSymbol", "()S", (void *)&node_grammar_symbol},
    {"getType", "()Ljava/lang/String;", (void *)&node_type},
    {"getGrammarType", "()Ljava/lang/String;", (void *)&node_grammar_type},
    {"isNamed", "()Z", (void *)&node_is_named},
    {"isExtra", "()Z", (void *)&node_is_extra},
    {"isError", "()Z", (void *)&node_is_error},
    {"isMissing", "()Z", (void *)&node_is_missing},
    {"hasError", "()Z", (void *)&node_has_error},
    {"hasChanges", "()Z", (void *)&node_has_changes},
    {"getParseState", "()S", (void *)&node_get_parse_state},
    {"getNextParseState", "()S", (void *)&node_get_next_parse_state},
    {"getStartByte", "()I", (void *)&node_get_start_byte},
    {"getEndByte", "()I", (void *)&node_get_end_byte},
    {"getStartPoint", "()L" PACKAGE "TSPoint;", (void *)&node_get_start_point},
    {"getEndPoint", "()L" PACKAGE "TSPoint;", (void *)&node_get_end_point},
    {"getChildCount", "()I", (void *)&node_get_child_count},
    {"getNamedChildCount", "()I", (void *)&node_get_named_child_count},
    {"getDescendantCount", "()I", (void *)&node_get_descendant_count},
    {"getParent", "()L" PACKAGE "TSNode;", (void *)&node_get_parent},
    {"getNextSibling", "()L" PACKAGE "TSNode;", (void *)&node_get_next_sibling},
    {"getNextNamedSibling", "()L" PACKAGE "TSNode;", (void *)&node_get_next_named_sibling},
    {"getPrevSibling", "()L" PACKAGE "TSNode;", (void *)&node_get_prev_sibling},
    {"getPrevNamedSibling", "()L" PACKAGE "TSNode;", (void *)&node_get_prev_named_sibling},
    {"getChildren", "()Ljava/util/List;", (void *)&node_get_children},
    {"child", "(I)L" PACKAGE "TSNode;", (void *)&node_child},
    {"namedChild", "(I)L" PACKAGE "TSNode;", (void *)&node_named_child},
    {"firstChildForByte", "(I)L" PACKAGE "TSNode;", (void *)&node_first_child_for_byte},
    {"firstNamedChildForByte", "(I)L" PACKAGE "TSNode;", (void *)&node_first_named_child_for_byte},
    {"childByFieldId", "(S)L" PACKAGE "TSNode;", (void *)&node_child_by_field_id},
    {"childByFieldName", "(Ljava/lang/String;)L" PACKAGE "TSNode;",
     (void *)&node_child_by_field_name},
    {"childrenByFieldId", "(S)Ljava/util/List;", (void *)&node_children_by_field_id},
    {"fieldNameForChild", "(I)Ljava/lang/String;", (void *)&node_field_name_for_child},
    {"fieldNameForNamedChild", "(I)Ljava/lang/String;", (void *)&node_field_name_for_named_child},
    {"childWithDescendant", "(L" PACKAGE "TSNode;)L" PACKAGE "TSNode;",
     (void *)&node_child_with_descendant},
    {"descendant", "(II)L" PACKAGE "TSNode;", (void *)&node_descendant_bytes},
    {"descendant", "(L" PACKAGE "TSPoint;L" PACKAGE "TSPoint;)L" PACKAGE "TSNode;",
     (void *)&node_descendant_points},
    {"namedDescendant", "(II)L" PACKAGE "TSNode;", (void *)&node_named_descendant_bytes},
    {"namedDescendant", "(L" PACKAGE "TSPoint;L" PACKAGE "TSPoint;)L" PACKAGE "TSNode;",
     (void *)&node_named_descendant_points},
    {"edit", "(L" PACKAGE "TSInputEdit;)V", (void *)&node_edit},
    {"sexp", "()Ljava/lang/String;", (void *)&node_sexp},
    {"hashCode", "()I", (void *)&node_hash_code},
    {"nativeEquals", "(L" PACKAGE "TSNode;)Z", (void *)&node_native_equals},
};

extern const size_t TSNode_methods_size = sizeof TSNode_methods / sizeof(JNINativeMethod);

#ifdef __cplusplus
}
#endif // __cplusplus

