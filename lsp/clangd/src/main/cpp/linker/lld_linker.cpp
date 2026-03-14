// LLD 链接器实现（IPC 版本）
//
// 所有链接操作都会通过 Unix Socket 将请求发送到独立的 link server 进程。
// link server 在受控的单线程子进程内 dlopen/dlclose liblld_linker.so，
// 每次链接后都会清理 LLD 的全局状态，从而规避 duplicate symbol 和 fork 死锁问题。

#include "lld_linker.h"
#include "../server/link_client.h"
#include "../utils/logging.h"

#include <sys/stat.h>

namespace tinaide {
namespace linker {

namespace {

// 基础 sysroot 校验（主要用于在 JNI 层尽早提示错误）
bool validateSysrootBasic(const LinkOptions& options, std::string& errorOut) {
    if (options.sysroot.empty()) {
        errorOut = "Sysroot not specified";
        return false;
    }

    struct stat st;
    if (stat(options.sysroot.c_str(), &st) != 0 || !S_ISDIR(st.st_mode)) {
        errorOut = "Sysroot directory does not exist: " + options.sysroot;
        return false;
    }

    return true;
}

LinkResult forwardToServer(const std::vector<std::string>& objPaths,
                           const std::string& outputPath,
                           const LinkOptions& options,
                           bool isShared) {
    if (objPaths.empty()) {
        LinkResult result;
        result.errorMessage = "No object files to link";
        return result;
    }

    if (isShared) {
        LOGI("IPC link (shared): %zu objects -> %s", objPaths.size(), outputPath.c_str());
        return client::linkSharedViaServer(objPaths, outputPath, options);
    }

    LOGI("IPC link (executable): %zu objects -> %s", objPaths.size(), outputPath.c_str());
    return client::linkExecutableViaServer(objPaths, outputPath, options);
}

} // anonymous namespace

LinkResult linkExecutable(const std::string& objPath,
                          const std::string& exePath,
                          const LinkOptions& options) {
    LOGI("linkExecutable: %s -> %s", objPath.c_str(), exePath.c_str());

    std::string validationError;
    if (!validateSysrootBasic(options, validationError)) {
        LinkResult result;
        result.errorMessage = validationError;
        return result;
    }

    std::vector<std::string> objPaths = {objPath};
    return forwardToServer(objPaths, exePath, options, /*isShared=*/false);
}

LinkResult linkExecutableMany(const std::vector<std::string>& objPaths,
                              const std::string& exePath,
                              const LinkOptions& options) {
    LOGI("linkExecutableMany: %zu objects -> %s", objPaths.size(), exePath.c_str());

    std::string validationError;
    if (!validateSysrootBasic(options, validationError)) {
        LinkResult result;
        result.errorMessage = validationError;
        return result;
    }

    return forwardToServer(objPaths, exePath, options, /*isShared=*/false);
}

LinkResult linkSharedLibrary(const std::string& objPath,
                             const std::string& soPath,
                             const LinkOptions& options) {
    LOGI("linkSharedLibrary: %s -> %s", objPath.c_str(), soPath.c_str());

    std::string validationError;
    if (!validateSysrootBasic(options, validationError)) {
        LinkResult result;
        result.errorMessage = validationError;
        return result;
    }

    std::vector<std::string> objPaths = {objPath};
    return forwardToServer(objPaths, soPath, options, /*isShared=*/true);
}

LinkResult linkSharedLibraryMany(const std::vector<std::string>& objPaths,
                                 const std::string& soPath,
                                 const LinkOptions& options) {
    LOGI("linkSharedLibraryMany: %zu objects -> %s", objPaths.size(), soPath.c_str());

    std::string validationError;
    if (!validateSysrootBasic(options, validationError)) {
        LinkResult result;
        result.errorMessage = validationError;
        return result;
    }

    return forwardToServer(objPaths, soPath, options, /*isShared=*/true);
}

} // namespace linker
} // namespace tinaide
