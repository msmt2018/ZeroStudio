// JNI 工具函数实现

#include "jni_utils.h"
#include <vector>

namespace tinaide {
namespace utils {

std::string jstringToUtf8(JNIEnv* env, jstring jstr) {
    if (!jstr) {
        return std::string();
    }
    const char* utf = env->GetStringUTFChars(jstr, nullptr);
    std::string result = utf ? std::string(utf) : std::string();
    if (utf) {
        env->ReleaseStringUTFChars(jstr, utf);
    }
    return result;
}

jstring utf8ToJstring(JNIEnv* env, const std::string& str) {
    return env->NewStringUTF(str.c_str());
}

std::vector<std::string> jstringArrayToVector(JNIEnv* env, jobjectArray jarr) {
    std::vector<std::string> result;
    if (!jarr) {
        return result;
    }

    jsize length = env->GetArrayLength(jarr);
    result.reserve(length);

    for (jsize i = 0; i < length; ++i) {
        jstring jstr = (jstring)env->GetObjectArrayElement(jarr, i);
        if (jstr) {
            result.push_back(jstringToUtf8(env, jstr));
            env->DeleteLocalRef(jstr);
        }
    }

    return result;
}

} // namespace utils
} // namespace tinaide
