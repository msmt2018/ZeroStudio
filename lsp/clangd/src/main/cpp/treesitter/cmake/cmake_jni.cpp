// Tree-sitter CMake JNI bindings

#include <jni.h>

// Forward declaration - defined in parser.c
// TSLanguage is an opaque struct from tree-sitter
struct TSLanguage;
extern "C" const TSLanguage* tree_sitter_cmake(void);

extern "C" JNIEXPORT jlong JNICALL
Java_com_wuxianggujun_tinaide_treesitter_TSLanguageCMake_nativeLanguage(
    JNIEnv* env,
    jclass clazz
) {
    return reinterpret_cast<jlong>(tree_sitter_cmake());
}
