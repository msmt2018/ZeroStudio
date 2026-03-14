// Clangd LSP 服务器接口
// 提供 Clangd LSP 服务器的启动、通信和管理功能

#ifndef TINAIDE_CLANGD_SERVER_H
#define TINAIDE_CLANGD_SERVER_H

#include <string>
#include <vector>
#include <atomic>
#include <mutex>
#include <pthread.h>

namespace tinaide {
namespace lsp {

// Clangd 服务器类
// 管理 clangd 进程的生命周期和通信
class ClangdServer {
public:
    ClangdServer();
    ~ClangdServer();

    // 启动 clangd 服务器
    // @param libPath libclangd.so 的路径
    // @param extraArgs clangd_main 额外参数
    // @return 空字符串表示成功，否则返回错误信息
    std::string start(const std::string& libPath,
                      const std::vector<std::string>& extraArgs = {});

    // 停止 clangd 服务器
    void stop();

    // 检查 clangd 是否正在运行
    // @return true 表示正在运行
    bool isRunning() const;

    // 向 clangd 写入数据（LSP 请求）
    // @param data 要写入的数据
    // @return 写入的字节数，-1 表示失败
    int write(const std::vector<char>& data);

    // 从 clangd 读取数据（LSP 响应）
    // @param maxBytes 最多读取的字节数
    // @return 读取的数据，空表示无数据或失败
    std::vector<char> read(int maxBytes = 8192);

    // 从 clangd 读取数据（带超时）
    // @param maxBytes 最多读取的字节数
    // @param timeoutMs 超时时间（毫秒）
    // @return 读取的数据，空表示超时或失败
    std::vector<char> readWithTimeout(int maxBytes, int timeoutMs);

private:
    // 禁止拷贝和赋值
    ClangdServer(const ClangdServer&) = delete;
    ClangdServer& operator=(const ClangdServer&) = delete;

    // Clangd 线程函数
    static void* clangdThreadFunc(void* arg);

    // 内部状态
    void* handle_;                          // dlopen 句柄
    pthread_t thread_;                      // Clangd 运行线程
    std::atomic<bool> running_;             // 是否正在运行
    int stdinPipe_[2];                      // stdin 管道
    int stdoutPipe_[2];                     // stdout 管道
    std::mutex mutex_;                      // 互斥锁
    std::string error_;                     // 错误信息

    // 函数指针类型
    using ClangdMainFn = int (*)(int argc, char** argv);
    using ClangdRunFn = int (*)();

    ClangdMainFn clangdMain_;               // clangd_main 函数指针
    ClangdRunFn clangdRun_;                 // clangd_run 函数指针
    std::vector<std::string> clangdArgs_;   // clangd_main 参数
};

} // namespace lsp
} // namespace tinaide

#endif // TINAIDE_CLANGD_SERVER_H
