// LLD 链接器实现（独立库版本）
//
// 这个文件编译为独立的 liblld_linker.so，供主进程通过 dlopen/dlclose 动态加载。
// 每次链接完成后 dlclose() 可以清理 LLD 的全局状态，解决 duplicate symbol 问题。

#include "lld_linker_api.h"
#include "../utils/file_utils.h"

#include <cstdlib>
#include <cstring>
#include <sstream>
#include <vector>
#include <string>
#include <sys/stat.h>

#if LLVM_HEADERS_AVAILABLE
#include "llvm/ADT/ArrayRef.h"
#include "llvm/Support/raw_ostream.h"
#endif

// LLD 前向声明
#if LLVM_HEADERS_AVAILABLE && defined(LLD_LINK_ENABLED)
namespace llvm {
    template <typename T> class ArrayRef;
    class raw_ostream;
}
namespace lld {
namespace elf {
    bool link(llvm::ArrayRef<const char*>, llvm::raw_ostream&,
              llvm::raw_ostream&, bool, bool);
}
}
#endif

// Android 日志
#include <android/log.h>
#define LOG_TAG "LldLinkerImpl"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ============================================================================
// 内部辅助函数
// ============================================================================

namespace {

// 复制字符串（调用者需要 free）
char* strdup_safe(const std::string& str) {
    if (str.empty()) return nullptr;
    char* result = static_cast<char*>(malloc(str.size() + 1));
    if (result) {
        memcpy(result, str.c_str(), str.size() + 1);
    }
    return result;
}

// 从 target 三元组提取基础部分（如 aarch64-linux-android）
std::string deriveTripleBase(const std::string& target) {
    // target 格式: aarch64-linux-android24 或 x86_64-linux-android24
    std::string base = target;
    // 移除末尾的数字（API level）
    while (!base.empty() && std::isdigit(base.back())) {
        base.pop_back();
    }
    return base;
}

// 从 target 三元组提取 API level
std::string deriveApiLevel(const std::string& target) {
    std::string level;
    size_t i = target.size();
    while (i > 0 && std::isdigit(target[i - 1])) {
        --i;
    }
    if (i < target.size()) {
        level = target.substr(i);
    }
    return level.empty() ? "21" : level;  // 默认 API 21
}

// 检查文件是否存在
bool fileExists(const std::string& path) {
    struct stat st;
    return stat(path.c_str(), &st) == 0 && S_ISREG(st.st_mode);
}

// 检查目录是否存在
bool dirExists(const std::string& path) {
    struct stat st;
    return stat(path.c_str(), &st) == 0 && S_ISDIR(st.st_mode);
}

// 验证 sysroot 和必需文件
bool validateSysroot(const std::string& sysroot, const std::string& target,
                     bool isCxx, bool isShared, std::string& errorOut) {
    std::string tripleBase = deriveTripleBase(target);
    std::string apiLevel = deriveApiLevel(target);
    std::string libDir = sysroot + "/usr/lib/" + tripleBase + "/" + apiLevel;

    if (!dirExists(libDir)) {
        std::ostringstream oss;
        oss << "[LLD] Sysroot library directory missing: " << libDir;
        errorOut = oss.str();
        return false;
    }

    std::vector<std::string> missing;
    std::string crtBegin = libDir + (isShared ? "/crtbegin_so.o" : "/crtbegin_dynamic.o");
    std::string crtEnd = libDir + (isShared ? "/crtend_so.o" : "/crtend_android.o");

    if (!fileExists(crtBegin)) {
        missing.push_back(isShared ? "crtbegin_so.o" : "crtbegin_dynamic.o");
    }
    if (!fileExists(crtEnd)) {
        missing.push_back(isShared ? "crtend_so.o" : "crtend_android.o");
    }

    if (!missing.empty()) {
        std::ostringstream oss;
        oss << "[LLD] Missing CRT files in " << libDir << ": ";
        for (size_t i = 0; i < missing.size(); ++i) {
            if (i > 0) oss << ", ";
            oss << missing[i];
        }
        errorOut = oss.str();
        return false;
    }

    return true;
}

// 构建共享库链接参数
std::vector<std::string> buildSharedLibraryArgs(
    const char** objPaths, size_t objCount,
    const char* outputPath,
    const LldLinkOptions* options) {

    std::vector<std::string> args;
    std::string sysroot = options->sysroot ? options->sysroot : "";
    std::string target = options->target ? options->target : "aarch64-linux-android24";
    std::string tripleBase = deriveTripleBase(target);
    std::string apiLevel = deriveApiLevel(target);
    std::string libDir = sysroot + "/usr/lib/" + tripleBase + "/" + apiLevel;
    std::string libDirRoot = sysroot + "/usr/lib/" + tripleBase;

    // 基础参数
    args.push_back("ld.lld");
    args.push_back("-shared");

    // 库搜索路径
    args.push_back("-L");
    args.push_back(libDir);
    args.push_back("-L");
    args.push_back(libDirRoot);

    // CRT 启动对象
    args.push_back(libDir + "/crtbegin_so.o");

    // 输入的目标文件
    for (size_t i = 0; i < objCount; ++i) {
        if (objPaths[i]) {
            args.push_back(objPaths[i]);
        }
    }

    // 输出文件
    args.push_back("-o");
    args.push_back(outputPath);

    // C++ 运行时
    // 注意：libc++ 的异常处理依赖 libunwind，必须同时链接
    if (options->is_cxx) {
        args.push_back("-lc++");
        args.push_back("-lunwind");
    }

    // 系统库
    args.push_back("-lc");
    args.push_back("-lm");
    args.push_back("-llog");
    args.push_back("-landroid");

    // 额外的库搜索路径
    if (options->extra_lib_dirs && options->extra_lib_dirs_count > 0) {
        for (size_t i = 0; i < options->extra_lib_dirs_count; ++i) {
            if (options->extra_lib_dirs[i]) {
                args.push_back("-L");
                args.push_back(options->extra_lib_dirs[i]);
            }
        }
    }

    // 额外的链接库
    if (options->extra_libs && options->extra_libs_count > 0) {
        for (size_t i = 0; i < options->extra_libs_count; ++i) {
            if (options->extra_libs[i]) {
                args.push_back(std::string("-l") + options->extra_libs[i]);
            }
        }
    }

    // CRT 结束对象
    args.push_back(libDir + "/crtend_so.o");

    return args;
}

// 构建可执行文件链接参数
std::vector<std::string> buildExecutableArgs(
    const char** objPaths, size_t objCount,
    const char* outputPath,
    const LldLinkOptions* options) {

    std::vector<std::string> args;
    std::string sysroot = options->sysroot ? options->sysroot : "";
    std::string target = options->target ? options->target : "aarch64-linux-android24";
    std::string tripleBase = deriveTripleBase(target);
    std::string apiLevel = deriveApiLevel(target);
    std::string libDir = sysroot + "/usr/lib/" + tripleBase + "/" + apiLevel;
    std::string libDirRoot = sysroot + "/usr/lib/" + tripleBase;

    // 基础参数
    args.push_back("ld.lld");
    args.push_back("-pie");
    args.push_back("-z");
    args.push_back("now");
    args.push_back("-z");
    args.push_back("relro");

    // 库搜索路径
    args.push_back("-L");
    args.push_back(libDir);
    args.push_back("-L");
    args.push_back(libDirRoot);

    // 动态链接器
    const char* dynLinker = (tripleBase.find("64") != std::string::npos)
        ? "/system/bin/linker64"
        : "/system/bin/linker";
    args.push_back("-dynamic-linker");
    args.push_back(dynLinker);

    // CRT 启动对象
    args.push_back(libDir + "/crtbegin_dynamic.o");

    // 输入的目标文件
    for (size_t i = 0; i < objCount; ++i) {
        if (objPaths[i]) {
            args.push_back(objPaths[i]);
        }
    }

    // 输出文件
    args.push_back("-o");
    args.push_back(outputPath);

    // C++ 运行时
    // 注意：libc++ 的异常处理依赖 libunwind，必须同时链接
    if (options->is_cxx) {
        args.push_back("-lc++");
        args.push_back("-lunwind");
    }

    // 系统库
    args.push_back("-lc");
    args.push_back("-lm");
    args.push_back("-llog");
    args.push_back("-landroid");

    // 额外的库搜索路径
    if (options->extra_lib_dirs && options->extra_lib_dirs_count > 0) {
        for (size_t i = 0; i < options->extra_lib_dirs_count; ++i) {
            if (options->extra_lib_dirs[i]) {
                args.push_back("-L");
                args.push_back(options->extra_lib_dirs[i]);
            }
        }
    }

    // 额外的链接库
    if (options->extra_libs && options->extra_libs_count > 0) {
        for (size_t i = 0; i < options->extra_libs_count; ++i) {
            if (options->extra_libs[i]) {
                args.push_back(std::string("-l") + options->extra_libs[i]);
            }
        }
    }

    // CRT 结束对象
    args.push_back(libDir + "/crtend_android.o");

    return args;
}

// 执行链接
void executeLldLink(const std::vector<std::string>& argStrings,
                    const char* outputPath,
                    LldLinkResult* result) {
#if LLVM_HEADERS_AVAILABLE && defined(LLD_LINK_ENABLED)
    // 转换为 C 风格参数数组
    std::vector<const char*> args;
    args.reserve(argStrings.size());
    for (const auto& arg : argStrings) {
        args.push_back(arg.c_str());
    }

    // 捕获诊断输出
    std::string diagStr;
    llvm::raw_string_ostream diagStream(diagStr);

    LOGI("Executing LLD with %zu args", args.size());
    for (size_t i = 0; i < args.size(); ++i) {
        LOGI("  arg[%zu]: %s", i, args[i]);
    }

    // 调用 LLD
    bool success = lld::elf::link(args, diagStream, diagStream, false, false);
    diagStream.flush();

    if (success) {
        // 设置可执行权限
        chmod(outputPath, 0755);
        result->success = 1;
        result->exit_code = 0;
        LOGI("LLD link succeeded: %s", outputPath);
    } else {
        result->success = 0;
        result->exit_code = 1;
        LOGE("LLD link failed");
    }

    if (!diagStr.empty()) {
        result->diagnostics = strdup_safe(diagStr);
        if (!success) {
            result->error_message = strdup_safe(diagStr);
        }
        LOGI("LLD diagnostics: %s", diagStr.c_str());
    }
#else
    result->success = 0;
    result->exit_code = -1;
    result->error_message = strdup_safe("LLD not available (LLVM headers not found)");
#endif
}

} // anonymous namespace

// ============================================================================
// 公共接口实现
// ============================================================================

extern "C" {

int lld_api_version(void) {
    return LLD_LINKER_API_VERSION;
}

void lld_link_shared(
    const char** obj_paths,
    size_t obj_count,
    const char* output_path,
    const LldLinkOptions* options,
    LldLinkResult* result) {

    // 初始化结果
    memset(result, 0, sizeof(LldLinkResult));

    if (!obj_paths || obj_count == 0) {
        result->error_message = strdup_safe("No input object files");
        return;
    }

    if (!output_path) {
        result->error_message = strdup_safe("No output path specified");
        return;
    }

    if (!options || !options->sysroot) {
        result->error_message = strdup_safe("Sysroot not specified");
        return;
    }

    LOGI("lld_link_shared: %zu objects -> %s", obj_count, output_path);

    // 验证 sysroot
    std::string target = options->target ? options->target : "aarch64-linux-android24";
    std::string validationError;
    if (!validateSysroot(options->sysroot, target, options->is_cxx, true, validationError)) {
        result->error_message = strdup_safe(validationError);
        return;
    }

    // 构建参数并执行链接
    auto args = buildSharedLibraryArgs(obj_paths, obj_count, output_path, options);
    executeLldLink(args, output_path, result);
}

void lld_link_executable(
    const char** obj_paths,
    size_t obj_count,
    const char* output_path,
    const LldLinkOptions* options,
    LldLinkResult* result) {

    // 初始化结果
    memset(result, 0, sizeof(LldLinkResult));

    if (!obj_paths || obj_count == 0) {
        result->error_message = strdup_safe("No input object files");
        return;
    }

    if (!output_path) {
        result->error_message = strdup_safe("No output path specified");
        return;
    }

    if (!options || !options->sysroot) {
        result->error_message = strdup_safe("Sysroot not specified");
        return;
    }

    LOGI("lld_link_executable: %zu objects -> %s", obj_count, output_path);

    // 验证 sysroot
    std::string target = options->target ? options->target : "aarch64-linux-android24";
    std::string validationError;
    if (!validateSysroot(options->sysroot, target, options->is_cxx, false, validationError)) {
        result->error_message = strdup_safe(validationError);
        return;
    }

    // 构建参数并执行链接
    auto args = buildExecutableArgs(obj_paths, obj_count, output_path, options);
    executeLldLink(args, output_path, result);
}

void lld_free_result(LldLinkResult* result) {
    if (!result) return;

    if (result->error_message) {
        free(result->error_message);
        result->error_message = nullptr;
    }
    if (result->diagnostics) {
        free(result->diagnostics);
        result->diagnostics = nullptr;
    }
}

} // extern "C"
