/**
 * @author android_zero
 *
 * 变更说明：
 * 1. 负责 TSLanguage 指针的动态链接库加载（dlopen/dlsym）。
 * 2. 映射符号名称、状态数、字段名、超类型（Supertypes）与子类型（Subtypes）。
 *
 * 流程脑图：
 * dlopen(soPath) ──> dlsym(funcName) ──> TSLanguage* ──> ts_language_symbol_name/type
 */
#include "ts_jni_common.h"
#include <dlfcn.h>

JNIEXPORT jlong JNICALL
Java_com_zerostudio_ide_treesitter_TSLanguage_nativeLoadFromSharedLib(
        JNIEnv *env, jclass clazz, jstring lib_path, jstring func_name) {
    (void)clazz;
    const char *path = (*env)->GetStringUTFChars(env, lib_path, NULL);
    const char *func = (*env)->GetStringUTFChars(env, func_name, NULL);

    void *handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        ts_jni_throw_exception(env, dlerror());
        (*env)->ReleaseStringUTFChars(env, lib_path, path);
        (*env)->ReleaseStringUTFChars(env, func_name, func);
        return 0;
    }

    typedef const TSLanguage *(*LanguageConstructor)(void);
    LanguageConstructor constructor = (LanguageConstructor)dlsym(handle, func);
    if (!constructor) {
        ts_jni_throw_exception(env, dlerror());
        (*env)->ReleaseStringUTFChars(env, lib_path, path);
        (*env)->ReleaseStringUTFChars(env, func_name, func);
        return 0;
    }

    const TSLanguage *lang = constructor();
    (*env)->ReleaseStringUTFChars(env, lib_path, path);
    (*env)->ReleaseStringUTFChars(env, func_name, func);
    return (jlong)(intptr_t)lang;
}

JNIEXPORT jstring JNICALL
Java_com_zerostudio_ide_treesitter_TSLanguage_nativeGetSymbolName(
        JNIEnv *env, jclass clazz, jlong lang_ptr, jint symbol) {
    (void)clazz;
    const TSLanguage *lang = (const TSLanguage *)(intptr_t)lang_ptr;
    if (!lang) return NULL;
    const char *name = ts_language_symbol_name(lang, (TSSymbol)symbol);
    return name ? (*env)->NewStringUTF(env, name) : NULL;
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TSLanguage_nativeGetSymbolForName(
        JNIEnv *env, jclass clazz, jlong lang_ptr, jstring name, jboolean is_named) {
    (void)clazz;
    const TSLanguage *lang = (const TSLanguage *)(intptr_t)lang_ptr;
    if (!lang || !name) return 0;
    const char *str = (*env)->GetStringUTFChars(env, name, NULL);
    uint32_t len = (uint32_t)(*env)->GetStringUTFLength(env, name);
    TSSymbol sym = ts_language_symbol_for_name(lang, str, len, (bool)is_named);
    (*env)->ReleaseStringUTFChars(env, name, str);
    return (jint)sym;
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TSLanguage_nativeGetSymbolCount(JNIEnv *env, jclass clazz, jlong lang_ptr) {
    (void)clazz;
    (void)env;
    const TSLanguage *lang = (const TSLanguage *)(intptr_t)lang_ptr;
    return lang ? (jint)ts_language_symbol_count(lang) : 0;
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TSLanguage_nativeGetStateCount(JNIEnv *env, jclass clazz, jlong lang_ptr) {
    (void)clazz;
    (void)env;
    const TSLanguage *lang = (const TSLanguage *)(intptr_t)lang_ptr;
    return lang ? (jint)ts_language_state_count(lang) : 0;
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TSLanguage_nativeGetFieldCount(JNIEnv *env, jclass clazz, jlong lang_ptr) {
    (void)clazz;
    (void)env;
    const TSLanguage *lang = (const TSLanguage *)(intptr_t)lang_ptr;
    return lang ? (jint)ts_language_field_count(lang) : 0;
}

JNIEXPORT jstring JNICALL
Java_com_zerostudio_ide_treesitter_TSLanguage_nativeGetFieldNameForId(
        JNIEnv *env, jclass clazz, jlong lang_ptr, jint id) {
    (void)clazz;
    const TSLanguage *lang = (const TSLanguage *)(intptr_t)lang_ptr;
    if (!lang) return NULL;
    const char *name = ts_language_field_name_for_id(lang, (TSFieldId)id);
    return name ? (*env)->NewStringUTF(env, name) : NULL;
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TSLanguage_nativeNextState(
        JNIEnv *env, jclass clazz, jlong lang_ptr, jint state, jint symbol) {
    (void)clazz;
    (void)env;
    const TSLanguage *lang = (const TSLanguage *)(intptr_t)lang_ptr;
    return lang ? (jint)ts_language_next_state(lang, (TSStateId)state, (TSSymbol)symbol) : 0;
}