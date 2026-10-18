/**
 * @author android_zero
 *
 * 变更说明：
 * 完整适配 `tags/tags.h`：支持 LSP 语义符号导航（DocumentSymbol, Definition, Declaration, Reference）。
 * 将文档内的符号定义一次性打包为扁平的 `int[]` 传回 JVM。
 *
 * 扁平输出规范（每 10 个 int 表示一个 TSTag）：
 * [0: start_byte, 1: end_byte, 2: name_start_byte, 3: name_end_byte,
 *  4: line_start_byte, 5: line_end_byte, 6: start_row, 7: start_col,
 *  8: syntax_type_id, 9: is_definition]
 */
#include "ts_jni_common.h"

JNIEXPORT jlong JNICALL
Java_com_zerostudio_ide_treesitter_TSTagger_nativeNew(JNIEnv *env, jclass clazz) {
    (void)clazz;
    (void)env;
    return (jlong)(intptr_t)ts_tagger_new();
}

JNIEXPORT void JNICALL
Java_com_zerostudio_ide_treesitter_TSTagger_nativeDelete(JNIEnv *env, jclass clazz, jlong tagger_ptr) {
    (void)clazz;
    (void)env;
    TSTagger *tagger = (TSTagger *)(intptr_t)tagger_ptr;
    if (tagger) ts_tagger_delete(tagger);
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TSTagger_nativeAddLanguage(
        JNIEnv *env, jclass clazz, jlong tagger_ptr, jstring scope_name, jlong lang_ptr,
        jstring tags_query, jstring locals_query) {
    (void)clazz;
    TSTagger *tagger = (TSTagger *)(intptr_t)tagger_ptr;
    const TSLanguage *lang = (const TSLanguage *)(intptr_t)lang_ptr;
    if (!tagger || !lang) return -1;

    const char *scope = (*env)->GetStringUTFChars(env, scope_name, NULL);
    const char *t_q = (*env)->GetStringUTFChars(env, tags_query, NULL);
    const char *l_q = locals_query ? (*env)->GetStringUTFChars(env, locals_query, NULL) : NULL;

    uint32_t t_q_len = (uint32_t)(*env)->GetStringUTFLength(env, tags_query);
    uint32_t l_q_len = locals_query ? (uint32_t)(*env)->GetStringUTFLength(env, locals_query) : 0;

    TSTagsError err = ts_tagger_add_language(tagger, scope, lang, t_q, l_q, t_q_len, l_q_len);

    (*env)->ReleaseStringUTFChars(env, scope_name, scope);
    (*env)->ReleaseStringUTFChars(env, tags_query, t_q);
    if (locals_query) (*env)->ReleaseStringUTFChars(env, locals_query, l_q);

    return (jint)err;
}

JNIEXPORT jintArray JNICALL
Java_com_zerostudio_ide_treesitter_TSTagger_nativeTag(
        JNIEnv *env, jclass clazz, jlong tagger_ptr, jstring scope_name, jstring source_code) {
    (void)clazz;
    TSTagger *tagger = (TSTagger *)(intptr_t)tagger_ptr;
    if (!tagger || !source_code) return NULL;

    const char *scope = (*env)->GetStringUTFChars(env, scope_name, NULL);
    const char *source = (*env)->GetStringUTFChars(env, source_code, NULL);
    uint32_t src_len = (uint32_t)(*env)->GetStringUTFLength(env, source_code);

    TSTagsBuffer *buf = ts_tags_buffer_new();
    TSTagsError err = ts_tagger_tag(tagger, scope, source, src_len, buf, NULL);

    (*env)->ReleaseStringUTFChars(env, scope_name, scope);
    (*env)->ReleaseStringUTFChars(env, source_code, source);

    if (err != TSTagsOk) {
        ts_tags_buffer_delete(buf);
        return NULL;
    }

    uint32_t tag_count = ts_tags_buffer_tags_len(buf);
    const TSTag *tags = ts_tags_buffer_tags(buf);

    jsize stride = 10;
    jsize element_count = (jsize)(tag_count * stride);
    jintArray result = (*env)->NewIntArray(env, element_count);
    jint *raw = (jint *)malloc(sizeof(jint) * element_count);

    for (uint32_t i = 0; i < tag_count; ++i) {
        uint32_t base = i * stride;
        raw[base + 0] = (jint)tags[i].start_byte;
        raw[base + 1] = (jint)tags[i].end_byte;
        raw[base + 2] = (jint)tags[i].name_start_byte;
        raw[base + 3] = (jint)tags[i].name_end_byte;
        raw[base + 4] = (jint)tags[i].line_start_byte;
        raw[base + 5] = (jint)tags[i].line_end_byte;
        raw[base + 6] = (jint)tags[i].start_point.row;
        raw[base + 7] = (jint)tags[i].start_point.column;
        raw[base + 8] = (jint)tags[i].syntax_type_id;
        raw[base + 9] = tags[i].is_definition ? 1 : 0;
    }

    (*env)->SetIntArrayRegion(env, result, 0, element_count, raw);
    free(raw);
    ts_tags_buffer_delete(buf);
    return result;
}