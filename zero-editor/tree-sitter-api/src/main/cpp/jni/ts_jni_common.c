/**
 * @author android_zero
 *
 * 变更说明：
 * 实现了版本获取、通用异常向上抛出逻辑。
 */
#include "ts_jni_common.h"

void ts_jni_throw_exception(JNIEnv *env, const char *message) {
    jclass exClass = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (exClass != NULL) {
        (*env)->ThrowNew(env, exClass, message);
    }
}

JNIEXPORT jstring JNICALL
Java_com_zerostudio_ide_treesitter_TreeSitter_nativeGetEngineVersion(JNIEnv *env, jclass clazz) {
    (void)clazz;
    return (*env)->NewStringUTF(env, TS_ENGINE_VERSION_STR);
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TreeSitter_nativeGetLanguageVersion(JNIEnv *env, jclass clazz) {
    (void)clazz;
    return (jint)TREE_SITTER_LANGUAGE_VERSION;
}

JNIEXPORT jint JNICALL
Java_com_zerostudio_ide_treesitter_TreeSitter_nativeGetMinCompatibleLanguageVersion(JNIEnv *env, jclass clazz) {
    (void)clazz;
    return (jint)TREE_SITTER_MIN_COMPATIBLE_LANGUAGE_VERSION;
}