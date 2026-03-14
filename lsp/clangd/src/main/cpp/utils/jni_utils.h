// JNI 工具函数头文件
// 提供 JNI 类型转换和辅助函数

#ifndef TINAIDE_JNI_UTILS_H
#define TINAIDE_JNI_UTILS_H

#include <jni.h>
#include <string>

namespace tinaide {
namespace utils {

// 将 jstring 转换为 UTF-8 std::string
std::string jstringToUtf8(JNIEnv* env, jstring jstr);

// 将 UTF-8 std::string 转换为 jstring
jstring utf8ToJstring(JNIEnv* env, const std::string& str);

// 将 jobjectArray 转换为 std::vector<std::string>
std::vector<std::string> jstringArrayToVector(JNIEnv* env, jobjectArray jarr);

} // namespace utils
} // namespace tinaide

#endif // TINAIDE_JNI_UTILS_H
