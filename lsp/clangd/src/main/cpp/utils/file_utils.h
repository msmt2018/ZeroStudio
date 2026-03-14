// 文件系统工具函数头文件
// 提供目录创建、文件检查、路径解析等功能

#ifndef TINAIDE_FILE_UTILS_H
#define TINAIDE_FILE_UTILS_H

#include <string>

namespace tinaide {
namespace utils {

// 递归创建目录
// @param path 目录路径
// @return 成功返回 true，失败返回 false
bool ensureDirRecursive(const std::string& path);

// 确保文件的父目录存在
// @param filePath 文件路径
// @return 成功返回 true，失败返回 false
bool ensureParentDir(const std::string& filePath);

// 检查文件是否存在
// @param path 文件路径
// @return 存在返回 true，不存在返回 false
bool fileExists(const std::string& path);

// 检查目录是否存在
// @param path 目录路径
// @return 存在返回 true，不存在返回 false
bool dirExists(const std::string& path);

// 从目标三元组中提取基础架构部分（去除 API 级别）
// 例如：aarch64-linux-android24 -> aarch64-linux-android
// @param target 目标三元组字符串
// @return 基础架构字符串
std::string deriveTripleBase(const std::string& target);

// 从目标三元组中提取 API 级别
// 例如：aarch64-linux-android24 -> "24"
// @param target 目标三元组字符串
// @return API 级别字符串，如果未找到则返回 "24"
std::string deriveApiLevel(const std::string& target);

} // namespace utils
} // namespace tinaide

#endif // TINAIDE_FILE_UTILS_H
