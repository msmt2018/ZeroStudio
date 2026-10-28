#include "ts_utils.h"

#ifdef __cplusplus
extern "C" {
#endif

jlong JNICALL lookahead_iterator_init(jlong language, jshort state) {
    TSLookaheadIterator *self = ts_lookahead_iterator_new(
        reinterpret_cast<TSLanguage*>(language), 
        static_cast<uint16_t>(state)
    );
    return reinterpret_cast<jlong>(self);
}

void JNICALL lookahead_iterator_delete(jlong lookahead) {
    ts_lookahead_iterator_delete(
        reinterpret_cast<TSLookaheadIterator*>(lookahead)
    );
}

jobject JNICALL lookahead_iterator_get_language(JNIEnv *env, jobject thiz) {
    TSLookaheadIterator *self = GET_POINTER(TSLookaheadIterator, thiz);
    const TSLanguage *language = ts_lookahead_iterator_language(self);
    // create java TSLanguage object
    jobject language_object = env->AllocObject(global_class_cache.TSLanguage);
    env->SetLongField(
        language_object, 
        global_field_cache.TSLanguage_self, 
        reinterpret_cast<jlong>(language)
    );
    return language_object;
}

jshort JNICALL lookahead_iterator_get_current_symbol(JNIEnv *env, jobject thiz) {
    TSLookaheadIterator *self = GET_POINTER(TSLookaheadIterator, thiz);
    return static_cast<jshort>(ts_lookahead_iterator_current_symbol(self));
}

jstring JNICALL lookahead_iterator_get_current_symbol_name(JNIEnv *env, jobject thiz) {
    TSLookaheadIterator *self = GET_POINTER(TSLookaheadIterator, thiz);
    const char *name = ts_lookahead_iterator_current_symbol_name(self);
    return env->NewStringUTF(name);
}

jboolean JNICALL lookahead_iterator_reset(JNIEnv *env, jobject thiz, jshort state, jobject language) {
    TSLookaheadIterator *self = GET_POINTER(TSLookaheadIterator, thiz);
    if (language == nullptr) {
        return static_cast<jboolean>(ts_lookahead_iterator_reset_state(
            self, 
            static_cast<uint16_t>(state)
        ));
    }
    
    // native TSLanguage pointer
    TSLanguage *ts_language = GET_POINTER(TSLanguage, language);
    return static_cast<jboolean>(ts_lookahead_iterator_reset(
        self, 
        ts_language, 
        static_cast<uint16_t>(state)
    ));
}

jboolean JNICALL lookahead_iterator_native_next(JNIEnv *env, jobject thiz) {
    TSLookaheadIterator *self = GET_POINTER(TSLookaheadIterator, thiz);
    return static_cast<jboolean>(ts_lookahead_iterator_next(self));
}

extern const JNINativeMethod TSLookaheadIterator_methods[] = {
    {"init", "(JS)J", (void *)&lookahead_iterator_init},
    {"delete", "(J)V", (void *)&lookahead_iterator_delete},
    {"getLanguage", "()L" PACKAGE "TSLanguage;", (void *)&lookahead_iterator_get_language},
    {"getCurrentSymbol", "()S", (void *)&lookahead_iterator_get_current_symbol},
    {"getCurrentSymbolName", "()Ljava/lang/String;",
     (void *)&lookahead_iterator_get_current_symbol_name},
    {"reset", "(SL" PACKAGE "TSLanguage;)Z", (void *)&lookahead_iterator_reset},
    {"nativeNext", "()Z", (void *)&lookahead_iterator_native_next},
};

extern const size_t TSLookaheadIterator_methods_size =
    sizeof TSLookaheadIterator_methods / sizeof(JNINativeMethod);

#ifdef __cplusplus
}
#endif

