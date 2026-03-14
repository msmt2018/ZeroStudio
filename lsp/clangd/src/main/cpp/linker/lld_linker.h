// LLD 链接器接口
// 提供可执行文件和共享库的链接功能
//
// 实际链接逻辑委托给独立的 link server 进程（参见 docs/LLD-Process-Isolation.md）。
// 每次链接通过 IPC 请求 link server，在隔离进程内 dlopen/liblld_linker.so 并清理
// 其全局状态，避免 duplicate symbol 与 fork 死锁问题。

#ifndef TINAIDE_LLD_LINKER_H
#define TINAIDE_LLD_LINKER_H

#include <string>
#include <vector>

namespace tinaide {
namespace linker {

// 链接选项结构
struct LinkOptions {
    std::string sysroot;                    // Sysroot 路径
    std::string target;                     // 目标三元组（如 aarch64-linux-android24）
    bool isCxx = false;                     // 是否为 C++ 代码
    std::vector<std::string> libDirs;       // 额外的库搜索目录
    std::vector<std::string> libs;          // 额外的链接库（不带 -l 前缀）
    int timeoutMs = 30000;                  // 链接超时时间（毫秒），默认 30 秒
};

// 链接结果结构
struct LinkResult {
    bool success = false;                   // 是否成功
    std::string errorMessage;               // 错误信息（如果失败）
    int exitCode = -1;                      // 子进程退出码
};

// 链接单个目标文件为可执行文件
// @param objPath 目标文件路径
// @param exePath 输出的可执行文件路径
// @param options 链接选项
// @return 链接结果
LinkResult linkExecutable(const std::string& objPath, const std::string& exePath,
                          const LinkOptions& options);

// 链接多个目标文件为可执行文件
// @param objPaths 目标文件路径列表
// @param exePath 输出的可执行文件路径
// @param options 链接选项
// @return 链接结果
LinkResult linkExecutableMany(const std::vector<std::string>& objPaths,
                              const std::string& exePath,
                              const LinkOptions& options);

// 链接单个目标文件为共享库
// @param objPath 目标文件路径
// @param soPath 输出的共享库路径
// @param options 链接选项
// @return 链接结果
LinkResult linkSharedLibrary(const std::string& objPath, const std::string& soPath,
                             const LinkOptions& options);

// 链接多个目标文件为共享库
// @param objPaths 目标文件路径列表
// @param soPath 输出的共享库路径
// @param options 链接选项
// @return 链接结果
LinkResult linkSharedLibraryMany(const std::vector<std::string>& objPaths,
                                 const std::string& soPath,
                                 const LinkOptions& options);

} // namespace linker
} // namespace tinaide

#endif // TINAIDE_LLD_LINKER_H
