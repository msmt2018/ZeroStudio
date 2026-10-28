#include <ctype.h>
#include <malloc.h>

#include "ts_utils.h"
#include "ts_query_filter.h"

#ifdef __cplusplus
extern "C" {
#endif

jlong JNICALL query_cursor_init() { 
    return reinterpret_cast<jlong>(ts_query_cursor_new()); 
}

void JNICALL query_cursor_delete(jlong cursor) {
    query_filter::forget_cursor(reinterpret_cast<TSQueryCursor*>(cursor));
    ts_query_cursor_delete(reinterpret_cast<TSQueryCursor*>(cursor));
}

jint JNICALL query_cursor_get_match_limit(JNIEnv *env, jobject thiz) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, thiz);
    return static_cast<jint>(ts_query_cursor_match_limit(cursor));
}

void JNICALL query_cursor_set_match_limit(JNIEnv *env, jobject thiz, jint value) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, thiz);
    
    ts_query_cursor_set_match_limit(cursor, static_cast<uint32_t>(value));
    env->SetIntField(thiz, global_field_cache.TSQueryCursor_matchLimit, value);
}

void JNICALL query_cursor_set_max_start_depth(JNIEnv *env, jobject thiz, jint value) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, thiz);
    ts_query_cursor_set_max_start_depth(cursor, static_cast<uint32_t>(value));
    env->SetIntField(thiz, global_field_cache.TSQueryCursor_maxStartDepth, value);
}

jboolean JNICALL query_cursor_did_exceed_match_limit(JNIEnv *env, jobject thiz) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, thiz);
    return static_cast<jboolean>(ts_query_cursor_did_exceed_match_limit(cursor));
}

// Registered as (II)Z: the result has to be returned, or the Kotlin side reads
// whatever happens to be in the return register.
jboolean JNICALL query_cursor_native_set_byte_range(JNIEnv *env, jobject thiz, jint start, jint end) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, thiz);
    return static_cast<jboolean>(
        ts_query_cursor_set_byte_range(cursor, static_cast<uint32_t>(start), static_cast<uint32_t>(end))
    );
}

// Registered as (II)Z, like the byte range. An end of 0 is no end (tree-sitter's).
jboolean JNICALL query_cursor_native_set_containing_byte_range(JNIEnv *env, jobject thiz, jint start, jint end) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, thiz);
    return static_cast<jboolean>(
        ts_query_cursor_set_containing_byte_range(cursor, static_cast<uint32_t>(start), static_cast<uint32_t>(end))
    );
}

// The text of the document from start_byte on (UTF-16): the matches whose text
// predicates certainly fail are left out here, not handed to Kotlin (ts_query_filter.h).
void JNICALL query_cursor_native_set_text(JNIEnv *env, jobject thiz, jint start_byte, jstring text) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, thiz);
    jsize length = env->GetStringLength(text);
    std::u16string chars(static_cast<size_t>(length), u'\0');
    if (length > 0) env->GetStringRegion(text, 0, length, reinterpret_cast<jchar *>(&chars[0]));
    query_filter::set_text(cursor, static_cast<uint32_t>(start_byte), std::move(chars));
}

jboolean JNICALL query_cursor_native_set_point_range(JNIEnv *env, jobject thiz, jobject start, jobject end) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, thiz);
    TSPoint start_point = unmarshal_point(env, start), end_point = unmarshal_point(env, end);
    return static_cast<jboolean>(ts_query_cursor_set_point_range(cursor, start_point, end_point));
}

static bool query_progress_callback(TSQueryCursorState *state) {
    JNIEnv *env = ::getEnv();
    jobject offset = env->AllocObject(global_class_cache.UInt);
    env->SetIntField(offset, global_field_cache.UInt_data,
                        (jint)state->current_byte_offset);
    jobject result = CALL_METHOD(Object, (jobject)state->payload, Function1_invoke, offset);
    env->DeleteLocalRef(offset);
    return (bool)env->GetBooleanField(result, global_field_cache.Boolean_value);
}

void JNICALL query_cursor_exec(JNIEnv *env, jobject thiz, jlong query, jobject node, jobject progress_callback) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, thiz);
    TSNode ts_node = unmarshal_node(env, node);
    // for the predicates decided here (see ts_query_filter.h)
    query_filter::set_query(cursor, (TSQuery *)query);
    if (progress_callback != nullptr) {
        TSQueryCursorOptions options = {
            .payload = (void *)progress_callback,
            .progress_callback = query_progress_callback,
        };
        ts_query_cursor_exec_with_options(cursor, (TSQuery *)query, ts_node, &options);        
    } else {
        ts_query_cursor_exec(cursor, (TSQuery *)query, ts_node);
    }
}

// The pattern index of [match] for TSQueryMatch, with the top bit set when its predicates
// were all decided natively and hold (TSQueryMatch.predicatesDecided).
static inline jint pattern_of(const TSQueryMatch &match, query_filter::Decision decision) {
    uint32_t raw = match.pattern_index & 0x7FFFFFFFu;
    if (decision == query_filter::Decision::ACCEPT) raw |= 0x80000000u;
    return (jint)raw;
}

jobject JNICALL query_cursor_next_capture(JNIEnv *env, jobject thiz, jobject capture_names, jobject tree) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, thiz);
    uint32_t capture_index;
    TSQueryMatch match;
    query_filter::Decision decision;
    for (;;) {
        if (!ts_query_cursor_next_capture(cursor, &match, &capture_index))
            return nullptr;
        decision = query_filter::decide(cursor, match);
        if (decision != query_filter::Decision::REJECT) break;
        // its other captures will not come either, as Neovim does
        ts_query_cursor_remove_match(cursor, match.id);
    }

    jobject captures = NEW_OBJECT(ArrayList, (jint)match.capture_count);
    for (uint16_t i = 0; i < match.capture_count; ++i) {
        TSQueryCapture capture = match.captures[i];
        jobject node = marshal_node(env, &capture.node, tree);
        jobject name = CALL_METHOD(Object, capture_names, List_get, capture.index);
        if (env->ExceptionCheck())
            return nullptr;

        jobject capture_obj = NEW_OBJECT(TSQueryCapture, node, name);
        CALL_METHOD(Boolean, captures, ArrayList_add, capture_obj);
        env->DeleteLocalRef(capture_obj);
        env->DeleteLocalRef(node);
        env->DeleteLocalRef(name);
        if (env->ExceptionCheck())
            return nullptr;
    }
    jobject match_obj = NEW_OBJECT(TSQueryMatch, pattern_of(match, decision), captures);
    jobject index = env->AllocObject(global_class_cache.UInt);
    env->SetIntField(index, global_field_cache.UInt_data, (jint)capture_index);
    return NEW_OBJECT(Pair, index, match_obj);
}

jobject JNICALL query_cursor_next_match(JNIEnv *env, jobject thiz, jobject capture_names, jobject tree) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, thiz);
    TSQueryMatch match;
    query_filter::Decision decision;
    do {
        if (!ts_query_cursor_next_match(cursor, &match))
            return nullptr;
        decision = query_filter::decide(cursor, match);
    } while (decision == query_filter::Decision::REJECT);

    jobject captures = NEW_OBJECT(ArrayList, (jint)match.capture_count);
    for (uint16_t i = 0; i < match.capture_count; ++i) {
        TSQueryCapture capture = match.captures[i];
        jobject node = marshal_node(env, &capture.node, tree);
        jobject name = CALL_METHOD(Object, capture_names, List_get, capture.index);
        if (env->ExceptionCheck())
            return nullptr;

        jobject capture_obj = NEW_OBJECT(TSQueryCapture, node, name);
        CALL_METHOD(Boolean, captures, ArrayList_add, capture_obj);
        env->DeleteLocalRef(capture_obj);
        env->DeleteLocalRef(node);
        env->DeleteLocalRef(name);
        if (env->ExceptionCheck())
            return nullptr;
    }
    return NEW_OBJECT(TSQueryMatch, pattern_of(match, decision), captures);
}

extern const JNINativeMethod TSQueryCursor_methods[] = {
    {"init", "()J", (void *)&query_cursor_init},
    {"delete", "(J)V", (void *)&query_cursor_delete},
    {"getMatchLimit", "()I", (void *)&query_cursor_get_match_limit},
    {"setMatchLimit", "(I)V", (void *)&query_cursor_set_match_limit},
    {"setMaxStartDepth", "(I)V", (void *)&query_cursor_set_max_start_depth},
    {"didExceedMatchLimit", "()Z", (void *)&query_cursor_did_exceed_match_limit},
    {"nativeSetByteRange", "(II)Z", (void *)&query_cursor_native_set_byte_range},
    {"nativeSetContainingByteRange", "(II)Z", (void *)&query_cursor_native_set_containing_byte_range},
    {"nativeSetText", "(ILjava/lang/String;)V", (void *)&query_cursor_native_set_text},
    {"nativeSetPointRange", "(L" PACKAGE "TSPoint;L" PACKAGE "TSPoint;)Z",
     (void *)&query_cursor_native_set_point_range},
    {"nextMatch", "(Ljava/util/List;L" PACKAGE "TSTree;)L" PACKAGE "TSQueryMatch;",
     (void *)&query_cursor_next_match},
    {"nextCapture", "(Ljava/util/List;L" PACKAGE "TSTree;)Lkotlin/Pair;",
     (void *)&query_cursor_next_capture},
    {"exec", "(JL" PACKAGE "TSNode;Lkotlin/jvm/functions/Function1;)V", (void *)&query_cursor_exec}
};

extern const size_t TSQueryCursor_methods_size = sizeof TSQueryCursor_methods / sizeof(JNINativeMethod);

#ifdef __cplusplus
}
#endif // __cplusplus

