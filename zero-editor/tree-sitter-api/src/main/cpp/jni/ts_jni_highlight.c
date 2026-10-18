/**
 * @author android_zero
 *
 * 变更说明：
 * 1. 对应 `highlight/highlight.h` 官方语法着色管线支持。
 * 2. 导出 HTML 缓冲着色结果与高频视口扁平化高亮数据。
 */
#include "ts_jni_common.h"

JNIEXPORT jlong JNICALL
Java_com_zerostudio_ide_treesitter_TSHighlighter_nativeNew(
        JNIEnv *env, jclass clazz, jobjectArray j_names, jobjectArray j_attrs, jint count) {
    (void)clazz;
    const char **names = (const char **)malloc(sizeof(char *) * count);
    const char **attrs = (const char **)malloc(sizeof(char *) * count);

    for (int i = 0; i < count; ++i) {
        jstring name_str = (jstring)(*env)->GetObjectArrayElement(env, j_names, i);
        jstring attr_str = (jstring)(*env)->GetObjectArrayElement(env, j_attrs, i);
        names[i] = strdup((*env)->GetStringUTFChars(env, name_str, NULL));
        attrs[i] = strdup((*env)->GetStringUTFChars(env, attr_str, NULL));
        (*env)->DeleteLocalRef(env, name_str);
        (*env)->DeleteLocalRef(env, attr_str);
    }

    TSHighlighter *hl = ts_highlighter_new(names, attrs, (uint32_t)count);

    for (int i = 0; i < count; ++i) {
        free((void *)names[i]);
        free((void *)attrs[i]);
    }
    free(names);
    free(attrs);
    return (jlong)(intptr_t)hl;
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSHighlighter_nativeDelete(
        JNIEnv *env, jclass clazz, jlong hl_ptr) {
    (void)clazz;
    (void)env;
    TSHighlighter *hl = (TSHighlighter *)(intptr_t)hl_ptr;
    if (hl) ts_highlighter_delete(hl);
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TSHighlighter_nativeAddLanguage(
        JNIEnv *env, jclass clazz, jlong hl_ptr,
        jstring lang_name, jstring scope_name, jstring injection_regex,
        jlong lang_ptr, jstring highlight_q, jstring injection_q, jstring locals_q) {
    (void)clazz;
    TSHighlighter *hl = (TSHighlighter *)(intptr_t)hl_ptr;
    const TSLanguage *lang = (const TSLanguage *)(intptr_t)lang_ptr;
    if (!hl || !lang) return -1;

    const char *l_name = (*env)->GetStringUTFChars(env, lang_name, NULL);
    const char *s_name = (*env)->GetStringUTFChars(env, scope_name, NULL);
    const char *inj_reg = injection_regex ? (*env)->GetStringUTFChars(env, injection_regex, NULL) : NULL;
    const char *hl_q = (*env)->GetStringUTFChars(env, highlight_q, NULL);
    const char *inj_q = injection_q ? (*env)->GetStringUTFChars(env, injection_q, NULL) : NULL;
    const char *loc_q = locals_q ? (*env)->GetStringUTFChars(env, locals_q, NULL) : NULL;

    uint32_t hl_q_len = (uint32_t)(*env)->GetStringUTFLength(env, highlight_q);
    uint32_t inj_q_len = injection_q ? (uint32_t)(*env)->GetStringUTFLength(env, injection_q) : 0;
    uint32_t loc_q_len = locals_q ? (uint32_t)(*env)->GetStringUTFLength(env, locals_q) : 0;

    TSHighlightError err = ts_highlighter_add_language(
        hl, l_name, s_name, inj_reg, lang,
        hl_q, inj_q, loc_q,
        hl_q_len, inj_q_len, loc_q_len
    );

    (*env)->ReleaseStringUTFChars(env, lang_name, l_name);
    (*env)->ReleaseStringUTFChars(env, scope_name, s_name);
    if (injection_regex) (*env)->ReleaseStringUTFChars(env, injection_regex, inj_reg);
    (*env)->ReleaseStringUTFChars(env, highlight_q, hl_q);
    if (injection_q) (*env)->ReleaseStringUTFChars(env, injection_q, inj_q);
    if (locals_q) (*env)->ReleaseStringUTFChars(env, locals_q, loc_q);

    return (jint)err;
}

JNIEXPORT jbyteArray JNICALL
Java_com_zerostudio_ide_treesitter_TSHighlighter_nativeHighlightToHtml(
        JNIEnv *env, jclass clazz, jlong hl_ptr, jstring scope_name, jstring source_code) {
    (void)clazz;
    TSHighlighter *hl = (TSHighlighter *)(intptr_t)hl_ptr;
    if (!hl || !source_code) return NULL;

    const char *scope = (*env)->GetStringUTFChars(env, scope_name, NULL);
    const char *source = (*env)->GetStringUTFChars(env, source_code, NULL);
    uint32_t src_len = (uint32_t)(*env)->GetStringUTFLength(env, source_code);

    TSHighlightBuffer *output = ts_highlight_buffer_new();
    TSHighlightError err = ts_highlighter_highlight(hl, scope, source, src_len, output, NULL);

    (*env)->ReleaseStringUTFChars(env, scope_name, scope);
    (*env)->ReleaseStringUTFChars(env, source_code, source);

    if (err != TSHighlightOk) {
        ts_highlight_buffer_delete(output);
        return NULL;
    }

    uint32_t len = ts_highlight_buffer_len(output);
    const uint8_t *content = ts_highlight_buffer_content(output);
    jbyteArray arr = (*env)->NewByteArray(env, (jsize)len);
    if (arr && len > 0) {
        (*env)->SetByteArrayRegion(env, arr, 0, (jsize)len, (const jbyte *)content);
    }

    ts_highlight_buffer_delete(output);
    return arr;
}