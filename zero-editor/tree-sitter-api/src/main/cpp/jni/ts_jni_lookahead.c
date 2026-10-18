/**
 * @author android_zero
 *
 * 变更说明：
 * 映射 TSLookaheadIterator：用于在解析状态/语法错误处探测下一个合法语法 Token（LSP 智能自动补全核心）。
 *
 * 流程脑图：
 * TSLanguage* + StateId ──> ts_lookahead_iterator_new() ──> ts_lookahead_iterator_next() ──> Candidate Symbols
 */
#include "ts_jni_common.h"

JNIEXPORT jlong JNICALL
Java_com_zerostudio_ide_treesitter_TSLookaheadIterator_nativeNew(
        JNIEnv *env, jclass clazz, jlong lang_ptr, jint state) {
    (void)clazz;
    (void)env;
    const TSLanguage *lang = (const TSLanguage *)(intptr_t)lang_ptr;
    if (!lang) return 0;
    TSLookaheadIterator *iter = ts_lookahead_iterator_new(lang, (TSStateId)state);
    return (jlong)(intptr_t)iter;
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSLookaheadIterator_nativeDelete(
        JNIEnv *env, jclass clazz, jlong iter_ptr) {
    (void)clazz;
    (void)env;
    TSLookaheadIterator *iter = (TSLookaheadIterator *)(intptr_t)iter_ptr;
    if (iter) {
        ts_lookahead_iterator_delete(iter);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_zerostudio_ide_treesitter_TSLookaheadIterator_nativeNext(
        JNIEnv *env, jclass clazz, jlong iter_ptr) {
    (void)clazz;
    (void)env;
    TSLookaheadIterator *iter = (TSLookaheadIterator *)(intptr_t)iter_ptr;
    return iter && ts_lookahead_iterator_next(iter) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TSLookaheadIterator_nativeCurrentSymbol(
        JNIEnv *env, jclass clazz, jlong iter_ptr) {
    (void)clazz;
    (void)env;
    TSLookaheadIterator *iter = (TSLookaheadIterator *)(intptr_t)iter_ptr;
    return iter ? (jint)ts_lookahead_iterator_current_symbol(iter) : 0;
}

JNIEXPORT jstring JNICALL
Java_com_zerostudio_ide_treesitter_TSLookaheadIterator_nativeCurrentSymbolName(
        JNIEnv *env, jclass clazz, jlong iter_ptr) {
    (void)clazz;
    TSLookaheadIterator *iter = (TSLookaheadIterator *)(intptr_t)iter_ptr;
    if (!iter) return NULL;
    const char *name = ts_lookahead_iterator_current_symbol_name(iter);
    return name ? (*env)->NewStringUTF(env, name) : NULL;
}