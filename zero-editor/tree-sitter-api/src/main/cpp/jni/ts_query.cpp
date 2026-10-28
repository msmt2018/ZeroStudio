#include <ctype.h>
#include <malloc.h>

#include "ts_utils.h"
#include "ts_query_filter.h"

#ifdef __cplusplus
extern "C" {
#endif

static inline bool is_valid_identifier_char(char ch) { return isalnum(ch) || ch == '_'; }

static inline bool is_valid_predicate_char(char ch) {
    return isalnum(ch) || ch == '-' || ch == '_' || ch == '?' || ch == '.' || ch == '!';
}

jlong JNICALL query_init(JNIEnv *env, jclass clazz, jlong language, jstring pattern) {
    TSQueryError error_type;
    const char *pattern_chars = env->GetStringUTFChars(pattern, nullptr);
    uint32_t error_offset, length = env->GetStringUTFLength(pattern);
    // native TSLanguage
    TSLanguage *ts_language = reinterpret_cast<TSLanguage*>(language);
    TSQuery *self = ts_query_new(ts_language, pattern_chars, length, &error_offset, &error_type);
    if (self != nullptr) {
        env->ReleaseStringUTFChars(pattern, pattern_chars);
        // return the TSQuery pointer
        return reinterpret_cast<jlong>(self);
    }
    
    // query init failure, thow exception
    uint32_t start = 0, end = 0, row = 0, column;
#ifndef _MSC_VER
    char *line = strtok((char *)pattern_chars, "\n");
#else
    char *next_token = NULL;
    char *line = strtok_s((char *)pattern_chars, "\n", &next_token);
#endif
    while (line != NULL) {
        end = start + (uint32_t)strlen(line) + 1;
        if (end > error_offset)
            break;
        start = end;
        row += 1;
#ifndef _MSC_VER
        line = strtok(NULL, "\n");
#else
        line = strtok_s(NULL, "\n", &next_token);
#endif
    }
    column = error_offset - start, end = 0;

    switch (error_type) {
        case TSQueryErrorSyntax: {
            jobject exception = nullptr;
            if (error_offset < length) {
                exception = NEW_OBJECT(TSQueryError$Syntax, (jlong)row, (jlong)column);
            } else {
                exception = NEW_OBJECT(TSQueryError$Syntax, (jlong)-1, (jlong)-1);
            }
            env->Throw((jthrowable)exception);
            break;
        }
        case TSQueryErrorCapture: {
            while (is_valid_predicate_char(pattern_chars[error_offset + end])) {
                end += 1;
            }

            char *capture_chars = (char*)calloc(end + 1, sizeof(char));
            memcpy(capture_chars, &pattern_chars[error_offset], end);
            jstring capture = env->NewStringUTF(capture_chars);
            jobject exception = NEW_OBJECT(TSQueryError$Capture, (jint)row, (jint)column, capture);
            env->Throw((jthrowable)exception);
            free((void*)capture_chars);
            break;
        }
        case TSQueryErrorNodeType: {
            while (is_valid_identifier_char(pattern_chars[error_offset + end])) {
                end += 1;
            }

            char *node_chars = (char*)calloc(end + 1, sizeof(char));
            memcpy(node_chars, &pattern_chars[error_offset], end);
            jstring node = env->NewStringUTF(node_chars);
            jobject exception = NEW_OBJECT(TSQueryError$NodeType, (jint)row, (jint)column, node);
            env->Throw((jthrowable)exception);
            free((void*)node_chars);
            break;
        }
        case TSQueryErrorField: {
            while (is_valid_identifier_char(pattern_chars[error_offset + end])) {
                end += 1;
            }

            char *field_chars = (char*)calloc(end + 1, sizeof(char));
            memcpy(field_chars, &pattern_chars[error_offset], end);
            jstring field = env->NewStringUTF(field_chars);
            jobject exception = NEW_OBJECT(TSQueryError$Field, (jint)row, (jint)column, field);
            env->Throw((jthrowable)exception);
            free((void*)field_chars);
            break;
        }
        case TSQueryErrorStructure: {
            jobject exception = NEW_OBJECT(TSQueryError$Structure, (jint)row, (jint)column);
            env->Throw((jthrowable)exception);
            break;
        }
        default:
            UNREACHABLE();
    }

    env->ReleaseStringUTFChars(pattern, pattern_chars);
    return reinterpret_cast<jlong>(nullptr);
}

void JNICALL query_delete(jlong query) {
    query_filter::forget_query(reinterpret_cast<TSQuery*>(query));
    ts_query_delete(reinterpret_cast<TSQuery*>(query));
}

jint JNICALL query_native_pattern_count(JNIEnv *env, jobject thiz) {
    TSQuery *self = GET_POINTER(TSQuery, thiz);
    return static_cast<jint>(ts_query_pattern_count(self));
}

jint JNICALL query_native_capture_count(JNIEnv *env, jobject thiz) {
    TSQuery *self = GET_POINTER(TSQuery, thiz);
    return static_cast<jint>(ts_query_capture_count(self));
}

void JNICALL query_disable_pattern(JNIEnv *env, jobject thiz, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, thiz);
    if (ts_query_pattern_count(self) > static_cast<uint32_t>(index)) {
        ts_query_disable_pattern(self, static_cast<uint32_t>(index));
    } else {
        const char *fmt = "Pattern index %u is out of bounds";
        char buffer[45] = {0};
        sprintf(buffer, fmt, static_cast<uint32_t>(index));
        THROW(IndexOutOfBoundsException, static_cast<const char*>(buffer));
    }
}

jint JNICALL query_start_byte_for_pattern(JNIEnv *env, jobject thiz, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, thiz);
    if (ts_query_pattern_count(self) > static_cast<uint32_t>(index)) {
        return static_cast<jint>(
            ts_query_start_byte_for_pattern(self, static_cast<uint32_t>(index))
        );
    } 
    const char *fmt = "Pattern index %u is out of bounds";
    char buffer[45] = {0};
    sprintf(buffer, fmt, static_cast<uint32_t>(index));
    THROW(IndexOutOfBoundsException, static_cast<const char*>(buffer));
    // return invalid offset
    return -1;
}

jint JNICALL query_end_byte_for_pattern(JNIEnv *env, jobject thiz, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, thiz);
    if (ts_query_pattern_count(self) > static_cast<uint32_t>(index)) {
        return static_cast<jint>(
            ts_query_end_byte_for_pattern(self, static_cast<uint32_t>(index))
        );
    } 
    const char *fmt = "Pattern index %u is out of bounds";
    char buffer[45] = {0};
    sprintf(buffer, fmt, static_cast<uint32_t>(index));
    THROW(IndexOutOfBoundsException, static_cast<const char*>(buffer));
    // return invalid offset
    return -1;
}

jboolean JNICALL query_is_pattern_rooted(JNIEnv *env, jobject thiz, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, thiz);
    if (ts_query_pattern_count(self) > static_cast<uint32_t>(index)) {
        return static_cast<jboolean>(
            ts_query_is_pattern_rooted(self, static_cast<uint32_t>(index))
        );
    } 
    const char *fmt = "Pattern index %u is out of bounds";
    char buffer[45] = {0};
    sprintf(buffer, fmt, static_cast<uint32_t>(index));
    THROW(IndexOutOfBoundsException, static_cast<const char*>(buffer));
    // return invalid state
    return JNI_FALSE;
}

jboolean JNICALL query_is_pattern_non_local(JNIEnv *env, jobject thiz, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, thiz);
    if (ts_query_pattern_count(self) > static_cast<uint32_t>(index)) {
        return static_cast<jboolean>(
            ts_query_is_pattern_non_local(self, static_cast<uint32_t>(index))
        );
    } 
    const char *fmt = "Pattern index %u is out of bounds";
    char buffer[45] = {0};
    sprintf(buffer, fmt, static_cast<uint32_t>(index));
    THROW(IndexOutOfBoundsException, static_cast<const char*>(buffer));
    // return invalid state
    return JNI_FALSE;
}

// how often capture [capture] occurs in pattern [pattern] (TSQuantifier: 0 not at all,
// 1 zero or one, 2 zero or more, 3 one, 4 one or more); 0 for indexes out of bounds
jint JNICALL query_native_capture_quantifier(JNIEnv *env, jobject thiz, jint pattern, jint capture) {
    TSQuery *self = GET_POINTER(TSQuery, thiz);
    if (pattern < 0 || capture < 0 || static_cast<uint32_t>(pattern) >= ts_query_pattern_count(self) ||
        static_cast<uint32_t>(capture) >= ts_query_capture_count(self)) {
        return 0;
    }
    return static_cast<jint>(ts_query_capture_quantifier_for_id(
        self, static_cast<uint32_t>(pattern), static_cast<uint32_t>(capture)));
}

jint JNICALL query_string_count(JNIEnv *env, jobject thiz) {
    TSQuery *self = GET_POINTER(TSQuery, thiz);
    return static_cast<jint>(ts_query_string_count(self));
}

jstring JNICALL query_capture_name_for_id(JNIEnv *env, jobject thiz, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, thiz);
    uint32_t length;
    const char *name = ts_query_capture_name_for_id(
        self, static_cast<uint32_t>(index), &length
    );
    return name ? env->NewStringUTF(name) : nullptr;
}

jstring JNICALL query_string_value_for_id(JNIEnv *env, jobject thiz, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, thiz);
    uint32_t length;
    const char *name = ts_query_string_value_for_id(
        self, static_cast<uint32_t>(index), &length
    );
    return name ? env->NewStringUTF(name) : nullptr;
}

jboolean JNICALL query_native_is_pattern_guaranteed_at_step(JNIEnv *env, jobject thiz, jint offset) {
    TSQuery *self = GET_POINTER(TSQuery, thiz);
    return static_cast<jboolean>(
        ts_query_is_pattern_guaranteed_at_step(self, static_cast<uint32_t>(offset))
    );
}

void JNICALL query_disable_capture(JNIEnv *env, jobject thiz, jstring capture) {
    TSQuery *self = GET_POINTER(TSQuery, thiz);
    const char *capture_chars = env->GetStringUTFChars(capture, nullptr);
    uint32_t length = static_cast<uint32_t>(env->GetStringUTFLength(capture));
    ts_query_disable_capture(self, capture_chars, length);
    env->ReleaseStringUTFChars(capture, capture_chars);
}

jobject JNICALL query_predicates_for_pattern(JNIEnv *env, jobject thiz, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, thiz);
    uint32_t step_count;
    const TSQueryPredicateStep *steps = ts_query_predicates_for_pattern(
        self, static_cast<uint32_t>(index), &step_count
    );
    if (step_count == 0)
        return nullptr;

    jobject predicates = NEW_OBJECT(ArrayList, static_cast<jint>(step_count));
    for (uint32_t i = 0; i < step_count; ++i) {
        const jint values[2] = {(jint)steps[i].value_id, (jint)steps[i].type};
        jintArray predicate = (jintArray)env->NewIntArray(2);
        env->SetIntArrayRegion(predicate, 0, 2, values);
        CALL_METHOD(Boolean, predicates, ArrayList_add, predicate);
        env->DeleteLocalRef(predicate);
    }
    return predicates;
}

extern const JNINativeMethod TSQuery_methods[] = {
    {"init", "(JLjava/lang/String;)J", (void *)&query_init},
    {"delete", "(J)V", (void *)&query_delete},
    {"nativePatternCount", "()I", (void *)&query_native_pattern_count},
    {"nativeCaptureCount", "()I", (void *)&query_native_capture_count},
    {"disablePattern", "(I)V", (void *)&query_disable_pattern},    
    {"disableCapture", "(Ljava/lang/String;)V", (void *)&query_disable_capture},
    {"startByteForPattern", "(I)I", (void *)&query_start_byte_for_pattern},
    {"endByteForPattern", "(I)I", (void *)&query_end_byte_for_pattern},
    {"isPatternRooted", "(I)Z", (void *)&query_is_pattern_rooted},
    {"isPatternNonLocal", "(I)Z", (void *)&query_is_pattern_non_local},
    {"stringCount", "()I", (void *)&query_string_count},
    {"nativeCaptureQuantifier", "(II)I", (void *)&query_native_capture_quantifier},
    {"captureNameForId", "(I)Ljava/lang/String;", (void *)&query_capture_name_for_id},
    {"stringValueForId", "(I)Ljava/lang/String;", (void *)&query_string_value_for_id},
    {"nativeIsPatternGuaranteedAtStep", "(I)Z",
     (void *)&query_native_is_pattern_guaranteed_at_step},
    {"predicatesForPattern", "(I)Ljava/util/List;", (void *)&query_predicates_for_pattern},
};

extern const size_t TSQuery_methods_size = sizeof TSQuery_methods / sizeof(JNINativeMethod);

#ifdef __cplusplus
}
#endif // __cplusplus
