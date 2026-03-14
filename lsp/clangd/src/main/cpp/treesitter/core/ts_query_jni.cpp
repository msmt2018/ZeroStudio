/**
 * Tree-sitter Query JNI Bindings
 */

#include <jni.h>
#include <string>
#include <vector>
#include "../include/tree_sitter/api.h"

extern "C" {

// ============================================================================
// TSQuery JNI Methods
// ============================================================================

JNIEXPORT jlongArray JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQuery_nativeCreate(JNIEnv* env, jclass clazz, jlong languagePtr, jstring source) {
    if (languagePtr == 0 || source == nullptr) {
        jlongArray result = env->NewLongArray(3);
        jlong data[3] = {0, 0, 0};
        env->SetLongArrayRegion(result, 0, 3, data);
        return result;
    }
    
    const char* src = env->GetStringUTFChars(source, nullptr);
    if (src == nullptr) {
        jlongArray result = env->NewLongArray(3);
        jlong data[3] = {0, 0, 0};
        env->SetLongArrayRegion(result, 0, 3, data);
        return result;
    }
    
    jsize len = env->GetStringUTFLength(source);
    
    uint32_t errorOffset = 0;
    TSQueryError errorType = TSQueryErrorNone;
    
    TSQuery* query = ts_query_new(
        reinterpret_cast<const TSLanguage*>(languagePtr),
        src,
        static_cast<uint32_t>(len),
        &errorOffset,
        &errorType
    );
    
    env->ReleaseStringUTFChars(source, src);
    
    jlongArray result = env->NewLongArray(3);
    jlong data[3] = {
        reinterpret_cast<jlong>(query),
        static_cast<jlong>(errorOffset),
        static_cast<jlong>(errorType)
    };
    env->SetLongArrayRegion(result, 0, 3, data);
    return result;
}

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQuery_nativeDelete(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr != 0) {
        ts_query_delete(reinterpret_cast<TSQuery*>(ptr));
    }
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQuery_nativePatternCount(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr == 0) return 0;
    return static_cast<jint>(ts_query_pattern_count(reinterpret_cast<const TSQuery*>(ptr)));
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQuery_nativeCaptureCount(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr == 0) return 0;
    return static_cast<jint>(ts_query_capture_count(reinterpret_cast<const TSQuery*>(ptr)));
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQuery_nativeStringCount(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr == 0) return 0;
    return static_cast<jint>(ts_query_string_count(reinterpret_cast<const TSQuery*>(ptr)));
}

JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQuery_nativeGetCaptureName(JNIEnv* env, jobject obj, jlong ptr, jint id) {
    if (ptr == 0) return nullptr;
    uint32_t length = 0;
    const char* name = ts_query_capture_name_for_id(
        reinterpret_cast<const TSQuery*>(ptr),
        static_cast<uint32_t>(id),
        &length
    );
    return name ? env->NewStringUTF(name) : nullptr;
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQuery_nativeStartByteForPattern(JNIEnv* env, jobject obj, jlong ptr, jint patternIndex) {
    if (ptr == 0) return 0;
    return static_cast<jint>(ts_query_start_byte_for_pattern(
        reinterpret_cast<const TSQuery*>(ptr),
        static_cast<uint32_t>(patternIndex)
    ));
}

JNIEXPORT jintArray JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQuery_nativePredicatesForPattern(JNIEnv* env, jobject obj, jlong ptr, jint patternIndex) {
    if (ptr == 0) return nullptr;
    
    uint32_t stepCount = 0;
    const TSQueryPredicateStep* steps = ts_query_predicates_for_pattern(
        reinterpret_cast<const TSQuery*>(ptr),
        static_cast<uint32_t>(patternIndex),
        &stepCount
    );
    
    jintArray result = env->NewIntArray(stepCount * 2);
    std::vector<jint> data(stepCount * 2);
    for (uint32_t i = 0; i < stepCount; i++) {
        data[i * 2] = static_cast<jint>(steps[i].type);
        data[i * 2 + 1] = static_cast<jint>(steps[i].value_id);
    }
    env->SetIntArrayRegion(result, 0, stepCount * 2, data.data());
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQuery_nativeGetStringValue(JNIEnv* env, jobject obj, jlong ptr, jint id) {
    if (ptr == 0) return nullptr;
    uint32_t length = 0;
    const char* value = ts_query_string_value_for_id(
        reinterpret_cast<const TSQuery*>(ptr),
        static_cast<uint32_t>(id),
        &length
    );
    return value ? env->NewStringUTF(value) : nullptr;
}

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQuery_nativeDisableCapture(JNIEnv* env, jobject obj, jlong ptr, jstring name) {
    if (ptr == 0 || name == nullptr) return;
    const char* nameStr = env->GetStringUTFChars(name, nullptr);
    if (nameStr == nullptr) return;
    jsize len = env->GetStringUTFLength(name);
    ts_query_disable_capture(
        reinterpret_cast<TSQuery*>(ptr),
        nameStr,
        static_cast<uint32_t>(len)
    );
    env->ReleaseStringUTFChars(name, nameStr);
}

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQuery_nativeDisablePattern(JNIEnv* env, jobject obj, jlong ptr, jint patternIndex) {
    if (ptr == 0) return;
    ts_query_disable_pattern(
        reinterpret_cast<TSQuery*>(ptr),
        static_cast<uint32_t>(patternIndex)
    );
}

// ============================================================================
// TSQueryCursor JNI Methods
// ============================================================================

JNIEXPORT jlong JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQueryCursor_nativeCreate(JNIEnv* env, jclass clazz) {
    return reinterpret_cast<jlong>(ts_query_cursor_new());
}

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQueryCursor_nativeDelete(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr != 0) {
        ts_query_cursor_delete(reinterpret_cast<TSQueryCursor*>(ptr));
    }
}

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQueryCursor_nativeExec(JNIEnv* env, jobject obj, jlong cursorPtr, jlong queryPtr, jobject nodeObj) {
    if (cursorPtr == 0 || queryPtr == 0 || nodeObj == nullptr) return;
    
    // Get node data from TSNode object
    jclass nodeClass = env->GetObjectClass(nodeObj);
    
    // Get context field
    jfieldID contextField = env->GetFieldID(nodeClass, "context", "[I");
    jfieldID idField = env->GetFieldID(nodeClass, "id", "J");
    jfieldID treeField = env->GetFieldID(nodeClass, "treePointer", "J");
    
    jintArray contextArr = (jintArray)env->GetObjectField(nodeObj, contextField);
    jlong id = env->GetLongField(nodeObj, idField);
    jlong treePtr = env->GetLongField(nodeObj, treeField);
    
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
    
    ts_query_cursor_exec(
        reinterpret_cast<TSQueryCursor*>(cursorPtr),
        reinterpret_cast<const TSQuery*>(queryPtr),
        node
    );
}

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQueryCursor_nativeSetByteRange(JNIEnv* env, jobject obj, jlong ptr, jint startByte, jint endByte) {
    if (ptr == 0) return;
    ts_query_cursor_set_byte_range(
        reinterpret_cast<TSQueryCursor*>(ptr),
        static_cast<uint32_t>(startByte),
        static_cast<uint32_t>(endByte)
    );
}

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQueryCursor_nativeSetPointRange(JNIEnv* env, jobject obj, jlong ptr, 
    jint startRow, jint startCol, jint endRow, jint endCol) {
    if (ptr == 0) return;
    TSPoint startPoint = { static_cast<uint32_t>(startRow), static_cast<uint32_t>(startCol) };
    TSPoint endPoint = { static_cast<uint32_t>(endRow), static_cast<uint32_t>(endCol) };
    ts_query_cursor_set_point_range(
        reinterpret_cast<TSQueryCursor*>(ptr),
        startPoint,
        endPoint
    );
}

JNIEXPORT jlongArray JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQueryCursor_nativeNextMatch(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr == 0) return nullptr;
    
    TSQueryMatch match;
    if (!ts_query_cursor_next_match(reinterpret_cast<TSQueryCursor*>(ptr), &match)) {
        return nullptr;
    }
    
    // Result format: [matchId, patternIndex, captureCount, ...captures]
    // Each capture: [context0, context1, context2, context3, nodeId, treePointer, captureIndex]
    int dataSize = 3 + match.capture_count * 7;
    jlongArray result = env->NewLongArray(dataSize);
    std::vector<jlong> data(dataSize);
    
    data[0] = static_cast<jlong>(match.id);
    data[1] = static_cast<jlong>(match.pattern_index);
    data[2] = static_cast<jlong>(match.capture_count);
    
    for (uint16_t i = 0; i < match.capture_count; i++) {
        const TSQueryCapture& capture = match.captures[i];
        int offset = 3 + i * 7;
        data[offset] = static_cast<jlong>(capture.node.context[0]);
        data[offset + 1] = static_cast<jlong>(capture.node.context[1]);
        data[offset + 2] = static_cast<jlong>(capture.node.context[2]);
        data[offset + 3] = static_cast<jlong>(capture.node.context[3]);
        data[offset + 4] = reinterpret_cast<jlong>(capture.node.id);
        data[offset + 5] = reinterpret_cast<jlong>(capture.node.tree);
        data[offset + 6] = static_cast<jlong>(capture.index);
    }
    
    env->SetLongArrayRegion(result, 0, dataSize, data.data());
    return result;
}

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQueryCursor_nativeRemoveMatch(JNIEnv* env, jobject obj, jlong ptr, jint matchId) {
    if (ptr == 0) return;
    ts_query_cursor_remove_match(
        reinterpret_cast<TSQueryCursor*>(ptr),
        static_cast<uint32_t>(matchId)
    );
}

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQueryCursor_nativeSetMatchLimit(JNIEnv* env, jobject obj, jlong ptr, jint limit) {
    if (ptr == 0) return;
    ts_query_cursor_set_match_limit(
        reinterpret_cast<TSQueryCursor*>(ptr),
        static_cast<uint32_t>(limit)
    );
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQueryCursor_nativeGetMatchLimit(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr == 0) return 0;
    return static_cast<jint>(ts_query_cursor_match_limit(reinterpret_cast<const TSQueryCursor*>(ptr)));
}

JNIEXPORT jboolean JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSQueryCursor_nativeDidExceedMatchLimit(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr == 0) return JNI_FALSE;
    return ts_query_cursor_did_exceed_match_limit(reinterpret_cast<const TSQueryCursor*>(ptr)) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
