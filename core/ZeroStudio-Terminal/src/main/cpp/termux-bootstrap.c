#include <jni.h>

extern jbyte blob[];
extern int blob_size;

JNIEXPORT jbyteArray JNICALL Java_android_zero_studio_termux_TerminalBootstrapInstaller_getZip(JNIEnv *env, __attribute__((__unused__)) jobject This)
{
    jbyteArray ret = (*env)->NewByteArray(env, blob_size);
    (*env)->SetByteArrayRegion(env, ret, 0, blob_size, blob);
    return ret;
}
