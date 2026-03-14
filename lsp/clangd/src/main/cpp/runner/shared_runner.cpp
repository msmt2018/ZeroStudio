// 动态库运行器实现

#include "shared_runner.h"
#include "../utils/logging.h"

#include <dlfcn.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <signal.h>
#include <poll.h>
#include <errno.h>
#include <cstring>
#include <typeinfo>
#include <cxxabi.h>
#include <cstdlib>
#include <sstream>

namespace tinaide {
namespace runner {

// 异常和信号处理器
void installHandlers() {
    static bool installed = false;
    if (installed) {
        return;
    }
    installed = true;

    // 自定义 std::terminate 处理器
    auto terminateHandler = []() noexcept {
        const std::type_info* typeInfo = abi::__cxa_current_exception_type();
        const char* typeName = typeInfo ? typeInfo->name() : nullptr;
        int status = 0;
        char* demangled = typeName ? abi::__cxa_demangle(typeName, nullptr, nullptr, &status) : nullptr;
        const char* displayName = demangled ? demangled : (typeName ? typeName : "<unknown>");
        
        // 记录到 logcat
        LOGE("std::terminate: uncaught exception: %s", displayName);
        // 同时写入 stderr 供 runIsolated 捕获
        fprintf(stderr, "std::terminate: uncaught exception: %s\n", displayName);
        
        if (demangled) {
            free(demangled);
        }
        _Exit(134);
    };
    std::set_terminate(terminateHandler);

    // 信号处理器
    auto signalHandler = [](int sig) {
        LOGE("caught signal %d", sig);
        _Exit(128 + sig);
    };

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = signalHandler;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGABRT, &sa, nullptr);
    sigaction(SIGSEGV, &sa, nullptr);
    sigaction(SIGBUS, &sa, nullptr);
    sigaction(SIGILL, &sa, nullptr);
}

int runInProcess(const std::string& soPath, const std::string& symbolName) {
    LOGI("runInProcess: %s, symbol=%s", soPath.c_str(), symbolName.c_str());

    // 安装异常和信号处理器
    installHandlers();

    // 加载共享库
    void* handle = dlopen(soPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        const char* error = dlerror();
        std::string errorMsg = std::string("dlopen failed: ") + (error ? error : "unknown");
        LOGW("%s", errorMsg.c_str());
        return -127;
    }

    // 检查符号名称
    if (symbolName.empty()) {
        LOGW("runInProcess: empty symbol name");
        dlclose(handle);
        return -125;
    }

    // 查找入口符号
    void* symbol = dlsym(handle, symbolName.c_str());
    if (!symbol) {
        const char* error = dlerror();
        std::string errorMsg = std::string("dlsym failed: ") + (error ? error : "unknown");
        LOGW("%s", errorMsg.c_str());
        dlclose(handle);
        return -126;
    }

    // 执行入口函数
    using EntryNoArg = int (*)();
    int returnCode = -125;
    try {
        returnCode = reinterpret_cast<EntryNoArg>(symbol)();
    } catch (const std::bad_cast& e) {
        LOGW("unhandled std::bad_cast: %s", e.what());
        returnCode = -101;
    } catch (const std::exception& e) {
        LOGW("unhandled std::exception: %s", e.what());
        returnCode = -102;
    } catch (...) {
        LOGW("unhandled non-std exception");
        returnCode = -103;
    }

    dlclose(handle);
    return returnCode;
}

RunResult runIsolated(const std::string& soPath, const std::string& symbolName,
                      int timeoutMs) {
    LOGI("runIsolated: %s, symbol=%s, timeout=%dms", 
         soPath.c_str(), symbolName.c_str(), timeoutMs);

    RunResult result;
    if (timeoutMs <= 0) {
        timeoutMs = 15000;
    }

    // 创建管道用于捕获子进程输出
    int pipefd[2];
    if (pipe(pipefd) != 0) {
        result.output = std::string("pipe failed: ") + strerror(errno);
        return result;
    }

    // Fork 子进程
    pid_t pid = fork();
    if (pid < 0) {
        result.output = std::string("fork failed: ") + strerror(errno);
        close(pipefd[0]);
        close(pipefd[1]);
        return result;
    }

    if (pid == 0) {
        // 子进程：重定向标准输出和错误到管道
        dup2(pipefd[1], STDOUT_FILENO);
        dup2(pipefd[1], STDERR_FILENO);
        close(pipefd[0]);
        close(pipefd[1]);

        // 安装异常和信号处理器
        installHandlers();

        // 加载共享库
        void* handle = dlopen(soPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
        if (!handle) {
            const char* error = dlerror();
            fprintf(stderr, "dlopen failed: %s\n", error ? error : "unknown");
            _exit(127);
        }

        // 检查符号名称
        if (symbolName.empty()) {
            fprintf(stderr, "runIsolated: empty symbol name\n");
            dlclose(handle);
            _exit(125);
        }

        // 查找入口符号
        void* symbol = dlsym(handle, symbolName.c_str());
        // 如果找不到符号且是 "main"，尝试 C++ mangled 名称
        if (!symbol && symbolName == "main") {
            const char* mangledNames[] = {
                "_Z4mainv",           // int main()
                "_Z4mainiPPc",        // int main(int, char**)
                "main",               // C linkage fallback
                nullptr
            };
            for (int i = 0; mangledNames[i] && !symbol; ++i) {
                dlerror(); // 清除错误
                symbol = dlsym(handle, mangledNames[i]);
                if (symbol) {
                    LOGI("runIsolated: resolved main as %s", mangledNames[i]);
                    break;
                }
            }
        }

        if (!symbol) {
            const char* error = dlerror();
            fprintf(stderr, "dlsym failed: %s\n", error ? error : "unknown");
            dlclose(handle);
            _exit(126);
        }

        // 执行入口函数
        using EntryNoArg = int (*)();
        int rc = -1;
        try {
            LOGI("runIsolated: invoking entry");
            rc = reinterpret_cast<EntryNoArg>(symbol)();
            LOGI("runIsolated: entry returned rc=%d", rc);
        } catch (const std::bad_cast& e) {
            fprintf(stderr, "unhandled std::bad_cast: %s\n", e.what());
            rc = 101;
        } catch (const std::exception& e) {
            fprintf(stderr, "unhandled std::exception: %s\n", e.what());
            rc = 102;
        } catch (...) {
            fprintf(stderr, "unhandled non-std exception\n");
            rc = 103;
        }

        dlclose(handle);
        _exit(rc);
    }

    // 父进程：关闭写端
    close(pipefd[1]);

    // 读取子进程输出
    std::string output;
    output.reserve(4096);

    int returnCode = -1;
    const int readFd = pipefd[0];
    int elapsed = 0;
    char buffer[1024];

    bool reachedEof = false;
    while (true) {
        // 使用 poll 检查是否有数据可读
        struct pollfd pfd;
        pfd.fd = readFd;
        pfd.events = POLLIN;
        pfd.revents = 0;

        int pollTimeout = 100; // 100ms 步长
        int pollResult = poll(&pfd, 1, pollTimeout);

        if (pollResult > 0 && (pfd.revents & (POLLIN | POLLHUP | POLLERR))) {
            ssize_t n = read(readFd, buffer, sizeof(buffer));
            if (n > 0) {
                output.append(buffer, buffer + n);
                continue;
            }
            if (n == 0) {
                reachedEof = true;
                break; // EOF
            }
            continue;
        } else if (pollResult < 0) {
            if (errno == EINTR) {
                continue;
            }
            // poll 错误视为一次超时步长，继续循环
            elapsed += pollTimeout;
        } else {
            // pollResult == 0 表示真正等待了 pollTimeout
            elapsed += pollTimeout;
        }

        // 检查子进程状态
        int status = 0;
        pid_t waitResult = waitpid(pid, &status, WNOHANG);
        if (waitResult == pid) {
            if (WIFEXITED(status)) {
                returnCode = WEXITSTATUS(status);
            } else if (WIFSIGNALED(status)) {
                returnCode = 128 + WTERMSIG(status);
            }
            break;
        }

        // 超时检查
        if (elapsed >= timeoutMs) {
            kill(pid, SIGKILL);
            int killStatus = 0;
            waitpid(pid, &killStatus, 0);
            returnCode = 124; // 超时返回码
            break;
        }
    }

    close(readFd);

    // 仍未获取退出码时阻塞等待一次，避免误报
    if (returnCode < 0) {
        int status = 0;
        waitpid(pid, &status, 0);
        if (WIFEXITED(status)) {
            returnCode = WEXITSTATUS(status);
        } else if (WIFSIGNALED(status)) {
            returnCode = 128 + WTERMSIG(status);
        } else if (reachedEof) {
            // EOF 但未能解析状态，视为正常退出
            returnCode = 0;
        }
    }

    // 构建结果
    result.returnCode = returnCode;
    result.output = output;
    result.success = (returnCode == 0);

    return result;
}

} // namespace runner
} // namespace tinaide
