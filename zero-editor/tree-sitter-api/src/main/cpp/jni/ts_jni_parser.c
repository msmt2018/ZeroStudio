/**
 * @author android_zero
 *
 * 变更说明：
 * 1. 映射 TSParser 实例化、销毁、语言设置、重置操作。
 * 2. 实现基于纯内存的快速全量/增量解析接口。
 * 3. 对接 DirectByteBuffer 与分块回调。
 *
 * 流程脑图：
 * TSParser* -> ts_parser_set_language() -> ts_parser_parse_string() -> TSTree*
 */
#include "ts_jni_common.h"

JNIEXPORT jlong JNICALL
Java_com_zerostudio_ide_treesitter_TSParser_nativeNew(JNIEnv *env, jclass clazz) {
    (void)clazz;
    (void)env;
    return (jlong)(intptr_t)ts_parser_new();
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSParser_nativeDelete(JNIEnv *env, jclass clazz, jlong parser_ptr) {
    (void)clazz;
    (void)env;
    TSParser *parser = (TSParser *)(intptr_t)parser_ptr;
    if (parser) {
        ts_parser_delete(parser);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_zerostudio_ide_treesitter_TSParser_nativeSetLanguage(
        JNIEnv *env, jclass clazz, jlong parser_ptr, jlong lang_ptr) {
    (void)clazz;
    (void)env;
    TSParser *parser = (TSParser *)(intptr_t)parser_ptr;
    const TSLanguage *lang = (const TSLanguage *)(intptr_t)lang_ptr;
    if (!parser) return JNI_FALSE;
    return ts_parser_set_language(parser, lang) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSParser_nativeReset(JNIEnv *env, jclass clazz, jlong parser_ptr) {
    (void)clazz;
    (void)env;
    TSParser *parser = (TSParser *)(intptr_t)parser_ptr;
    if (parser) {
        ts_parser_reset(parser);
    }
}

JNIEXPORT jlong JNICALL
Java_com_zerostudio_ide_treesitter_TSParser_nativeParseString(
        JNIEnv *env, jclass clazz, jlong parser_ptr, jlong old_tree_ptr, jstring source_code) {
    (void)clazz;
    TSParser *parser = (TSParser *)(intptr_t)parser_ptr;
    const TSTree *old_tree = (const TSTree *)(intptr_t)old_tree_ptr;
    if (!parser || !source_code) return 0;

    const char *source = (*env)->GetStringUTFChars(env, source_code, NULL);
    uint32_t len = (uint32_t)(*env)->GetStringUTFLength(env, source_code);

    TSTree *tree = ts_parser_parse_string(parser, old_tree, source, len);
    (*env)->ReleaseStringUTFChars(env, source_code, source);
    return (jlong)(intptr_t)tree;
}

JNIEXPORT jlong JNICALL
Java_com_zerostudio_ide_treesitter_TSParser_nativeParseDirectBuffer(
        JNIEnv *env, jclass clazz, jlong parser_ptr, jlong old_tree_ptr, jobject direct_buf, jint length) {
    (void)clazz;
    TSParser *parser = (TSParser *)(intptr_t)parser_ptr;
    const TSTree *old_tree = (const TSTree *)(intptr_t)old_tree_ptr;
    if (!parser || !direct_buf) return 0;

    const char *buffer = (const char *)(*env)->GetDirectBufferAddress(env, direct_buf);
    if (!buffer) return 0;

    TSTree *tree = ts_parser_parse_string(parser, old_tree, buffer, (uint32_t)length);
    return (jlong)(intptr_t)tree;
}