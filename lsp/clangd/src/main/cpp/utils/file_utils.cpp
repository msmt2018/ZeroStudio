// 文件系统工具函数实现

#include "file_utils.h"
#include "logging.h"
#include <sys/stat.h>
#include <sys/types.h>
#include <errno.h>
#include <cstring>
#include <cctype>
#include <algorithm>

namespace tinaide {
namespace utils {

bool ensureDirRecursive(const std::string& path) {
    if (path.empty() || path == ".") {
        return true;
    }

    struct stat st;
    if (stat(path.c_str(), &st) == 0) {
        return S_ISDIR(st.st_mode);
    }

    if (errno != ENOENT) {
        LOGE("stat(%s) failed: %s", path.c_str(), strerror(errno));
        return false;
    }

    // 递归创建父目录
    auto slash = path.find_last_of('/');
    if (slash != std::string::npos) {
        std::string parent = path.substr(0, slash);
        if (!parent.empty() && !ensureDirRecursive(parent)) {
            return false;
        }
    }

    // 创建当前目录
    if (mkdir(path.c_str(), 0775) == 0 || errno == EEXIST || errno == EISDIR) {
        return true;
    }

    LOGE("mkdir(%s) failed: %s", path.c_str(), strerror(errno));
    return false;
}

bool ensureParentDir(const std::string& filePath) {
    auto slash = filePath.find_last_of('/');
    if (slash == std::string::npos) {
        return true;
    }

    std::string dir = filePath.substr(0, slash);
    if (dir.empty()) {
        return true;
    }

    return ensureDirRecursive(dir);
}

bool fileExists(const std::string& path) {
    struct stat st;
    return stat(path.c_str(), &st) == 0 && S_ISREG(st.st_mode);
}

bool dirExists(const std::string& path) {
    struct stat st;
    return stat(path.c_str(), &st) == 0 && S_ISDIR(st.st_mode);
}

std::string deriveTripleBase(const std::string& target) {
    if (target.empty()) {
        return std::string();
    }

    std::string result = target;
    // 从末尾移除数字（API 级别）
    while (!result.empty() && std::isdigit(static_cast<unsigned char>(result.back()))) {
        result.pop_back();
    }

    return result;
}

std::string deriveApiLevel(const std::string& target) {
    std::string digits;

    // 从末尾提取连续的数字
    for (auto it = target.rbegin(); it != target.rend(); ++it) {
        if (!std::isdigit(static_cast<unsigned char>(*it))) {
            break;
        }
        digits.push_back(*it);
    }

    // 反转得到正确的顺序
    std::reverse(digits.begin(), digits.end());

    // 如果没有找到数字，返回默认值 "24"
    return digits.empty() ? std::string("24") : digits;
}

} // namespace utils
} // namespace tinaide
