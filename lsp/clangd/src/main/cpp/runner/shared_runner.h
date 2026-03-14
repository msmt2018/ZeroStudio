// 动态库运行器接口
// 提供加载和执行共享库的功能

#ifndef TINAIDE_SHARED_RUNNER_H
#define TINAIDE_SHARED_RUNNER_H

#include <string>

namespace tinaide {
namespace runner {

// 运行结果结构
struct RunResult {
    int returnCode = -1;                    // 返回码
    std::string output;                     // 标准输出和错误输出
    bool success = false;                   // 是否成功执行
};

// 在当前进程中加载并运行共享库
// @param soPath 共享库路径
// @param symbolName 入口符号名称（如 "main" 或 "run_main"）
// @return 返回码（负数表示错误）
int runInProcess(const std::string& soPath, const std::string& symbolName);

// 在隔离的子进程中运行共享库
// @param soPath 共享库路径
// @param symbolName 入口符号名称
// @param timeoutMs 超时时间（毫秒），默认 15000
// @return 运行结果（包含返回码和输出）
RunResult runIsolated(const std::string& soPath, const std::string& symbolName,
                      int timeoutMs = 15000);

// 初始化异常和信号处理器（内部使用）
void installHandlers();

} // namespace runner
} // namespace tinaide

#endif // TINAIDE_SHARED_RUNNER_H
