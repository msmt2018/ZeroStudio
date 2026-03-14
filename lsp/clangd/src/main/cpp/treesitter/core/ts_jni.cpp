/**
 * Tree-sitter Core JNI Bindings
 * 
 * This file provides JNI bindings for the core tree-sitter API.
 */

#include <jni.h>
#include <string>
#include <cstring>
#include <android/log.h>
#include "../include/tree_sitter/api.h"

#define LOG_TAG "TreeSitterJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// ============================================================================
// TSLanguage JNI Methods
// ============================================================================

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSLanguage_nativeVersion(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr == 0) return 0;
    return static_cast<jint>(ts_language_abi_version(reinterpret_cast<const TSLanguage*>(ptr)));
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSLanguage_nativeFieldCount(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr == 0) return 0;
    return static_cast<jint>(ts_language_field_count(reinterpret_cast<const TSLanguage*>(ptr)));
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSLanguage_nativeSymbolCount(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr == 0) return 0;
    return static_cast<jint>(ts_language_symbol_count(reinterpret_cast<const TSLanguage*>(ptr)));
}

JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSLanguage_nativeSymbolName(JNIEnv* env, jobject obj, jlong ptr, jint symbol) {
    if (ptr == 0) return nullptr;
    const char* name = ts_language_symbol_name(
        reinterpret_cast<const TSLanguage*>(ptr),
        static_cast<TSSymbol>(symbol)
    );
    return name ? env->NewStringUTF(name) : nullptr;
}

JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSLanguage_nativeSymbolType(JNIEnv* env, jobject obj, jlong ptr, jint symbol) {
    if (ptr == 0) return 0;
    return static_cast<jint>(ts_language_symbol_type(
        reinterpret_cast<const TSLanguage*>(ptr),
        static_cast<TSSymbol>(symbol)
    ));
}

JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSLanguage_nativeFieldName(JNIEnv* env, jobject obj, jlong ptr, jint fieldId) {
    if (ptr == 0) return nullptr;
    const char* name = ts_language_field_name_for_id(
        reinterpret_cast<const TSLanguage*>(ptr),
        static_cast<TSFieldId>(fieldId)
    );
    return name ? env->NewStringUTF(name) : nullptr;
}

// ============================================================================
// TSParser JNI Methods
// ============================================================================

JNIEXPORT jlong JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSParser_nativeCreate(JNIEnv* env, jclass clazz) {
    TSParser* parser = ts_parser_new();
    return reinterpret_cast<jlong>(parser);
}

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSParser_nativeDelete(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr != 0) {
        ts_parser_delete(reinterpret_cast<TSParser*>(ptr));
    }
}

JNIEXPORT jboolean JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSParser_nativeSetLanguage(JNIEnv* env, jobject obj, jlong parserPtr, jlong languagePtr) {
    if (parserPtr == 0 || languagePtr == 0) return JNI_FALSE;
    return ts_parser_set_language(
        reinterpret_cast<TSParser*>(parserPtr),
        reinterpret_cast<const TSLanguage*>(languagePtr)
    ) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSParser_nativeParseString(JNIEnv* env, jobject obj, jlong parserPtr, jstring source, jlong oldTreePtr) {
    if (parserPtr == 0 || source == nullptr) return 0;
    
    const char* src = env->GetStringUTFChars(source, nullptr);
    if (src == nullptr) return 0;
    
    jsize len = env->GetStringUTFLength(source);
    
    TSTree* tree = ts_parser_parse_string(
        reinterpret_cast<TSParser*>(parserPtr),
        reinterpret_cast<TSTree*>(oldTreePtr),
        src,
        static_cast<uint32_t>(len)
    );
    
    env->ReleaseStringUTFChars(source, src);
    return reinterpret_cast<jlong>(tree);
}

JNIEXPORT jlong JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSParser_nativeParseBytes(JNIEnv* env, jobject obj, jlong parserPtr, jbyteArray source, jint length, jlong oldTreePtr) {
    if (parserPtr == 0 || source == nullptr) return 0;
    
    jbyte* src = env->GetByteArrayElements(source, nullptr);
    if (src == nullptr) return 0;
    
    // Parse as UTF-16LE (Android default)
    TSTree* tree = ts_parser_parse_string_encoding(
        reinterpret_cast<TSParser*>(parserPtr),
        reinterpret_cast<TSTree*>(oldTreePtr),
        reinterpret_cast<const char*>(src),
        static_cast<uint32_t>(length),
        TSInputEncodingUTF16LE
    );
    
    env->ReleaseByteArrayElements(source, src, JNI_ABORT);
    return reinterpret_cast<jlong>(tree);
}

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSParser_nativeReset(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr != 0) {
        ts_parser_reset(reinterpret_cast<TSParser*>(ptr));
    }
}

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSParser_nativeSetTimeout(JNIEnv* env, jobject obj, jlong ptr, jlong timeout) {
    // Note: ts_parser_set_timeout_micros was removed in tree-sitter 0.24+
    // Timeout functionality is no longer available in the new API
    (void)ptr;
    (void)timeout;
}

JNIEXPORT jlong JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSParser_nativeGetTimeout(JNIEnv* env, jobject obj, jlong ptr) {
    // Note: ts_parser_timeout_micros was removed in tree-sitter 0.24+
    // Timeout functionality is no longer available in the new API
    (void)ptr;
    return 0;
}

// ============================================================================
// TSTree JNI Methods
// ============================================================================

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSTree_nativeDelete(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr != 0) {
        ts_tree_delete(reinterpret_cast<TSTree*>(ptr));
    }
}

JNIEXPORT jlong JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSTree_nativeCopy(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr == 0) return 0;
    return reinterpret_cast<jlong>(ts_tree_copy(reinterpret_cast<const TSTree*>(ptr)));
}

JNIEXPORT jlong JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSTree_nativeLanguage(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr == 0) return 0;
    return reinterpret_cast<jlong>(ts_tree_language(reinterpret_cast<const TSTree*>(ptr)));
}

JNIEXPORT jlongArray JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSTree_nativeRootNode(JNIEnv* env, jobject obj, jlong ptr) {
    if (ptr == 0) return nullptr;
    
    TSNode node = ts_tree_root_node(reinterpret_cast<const TSTree*>(ptr));
    
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

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSTree_nativeEdit(JNIEnv* env, jobject obj, jlong ptr,
    jint startByte, jint oldEndByte, jint newEndByte,
    jint startRow, jint startCol,
    jint oldEndRow, jint oldEndCol,
    jint newEndRow, jint newEndCol) {
    if (ptr == 0) return;
    
    TSInputEdit edit = {
        .start_byte = static_cast<uint32_t>(startByte),
        .old_end_byte = static_cast<uint32_t>(oldEndByte),
        .new_end_byte = static_cast<uint32_t>(newEndByte),
        .start_point = { static_cast<uint32_t>(startRow), static_cast<uint32_t>(startCol) },
        .old_end_point = { static_cast<uint32_t>(oldEndRow), static_cast<uint32_t>(oldEndCol) },
        .new_end_point = { static_cast<uint32_t>(newEndRow), static_cast<uint32_t>(newEndCol) }
    };
    
    ts_tree_edit(reinterpret_cast<TSTree*>(ptr), &edit);
}

} // extern "C"
