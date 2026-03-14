/**
 * Tree-sitter C++ Language JNI Bindings
 */

#include <jni.h>

// Forward declaration - defined in parser.c
struct TSLanguage;
extern "C" const TSLanguage* tree_sitter_cpp(void);

extern "C" JNIEXPORT jlong JNICALL
Java_com_wuxianggujun_tinaide_treesitter_languages_TSLanguageCpp_nativeLanguage(
    JNIEnv* env,
    jclass clazz
) {
    return reinterpret_cast<jlong>(tree_sitter_cpp());
}
