// Clangd LSP 服务器实现

#include "clangd_server.h"
#include "../utils/logging.h"

#include <dlfcn.h>
#include <unistd.h>
#include <fcntl.h>
#include <poll.h>
#include <sys/stat.h>
#include <errno.h>
#include <cstring>
#include <dirent.h>
#include <cctype>
#include <linux/fcntl.h>

// F_SETPIPE_SZ 可能在某些 Android 版本中未定义
#ifndef F_SETPIPE_SZ
#define F_SETPIPE_SZ 1031
#endif

namespace {

std::string trimTrailingSlash(std::string path) {
    while (!path.empty() && path.back() == '/') {
        path.pop_back();
    }
    return path;
}

std::string detectSysrootDir(const std::string& libPath) {
    const std::string marker = "/sysroot/";
    auto marker_pos = libPath.find(marker);
    if (marker_pos != std::string::npos) {
        std::string sysroot = libPath.substr(0, marker_pos + marker.size());
        return trimTrailingSlash(sysroot);
    }

    size_t pos = libPath.rfind('/');
    int levels = 0;
    while (pos != std::string::npos && levels < 5) {
        pos = libPath.rfind('/', pos == 0 ? 0 : pos - 1);
        ++levels;
    }
    if (pos != std::string::npos) {
        return trimTrailingSlash(libPath.substr(0, pos));
    }
    return "";
}

int compareVersionStrings(const std::string& lhs, const std::string& rhs) {
    size_t i = 0;
    size_t j = 0;
    while (i < lhs.size() || j < rhs.size()) {
        int lv = 0;
        int rv = 0;
        while (i < lhs.size() && lhs[i] != '.') {
            if (std::isdigit(static_cast<unsigned char>(lhs[i]))) {
                lv = lv * 10 + (lhs[i] - '0');
            }
            ++i;
        }
        while (j < rhs.size() && rhs[j] != '.') {
            if (std::isdigit(static_cast<unsigned char>(rhs[j]))) {
                rv = rv * 10 + (rhs[j] - '0');
            }
            ++j;
        }
        if (lv != rv) {
            return lv < rv ? -1 : 1;
        }
        if (i < lhs.size() && lhs[i] == '.') {
            ++i;
        }
        if (j < rhs.size() && rhs[j] == '.') {
            ++j;
        }
    }
    return 0;
}

std::string findClangResourceDir(const std::string& sysrootDir) {
    if (sysrootDir.empty()) {
        return "";
    }
    std::string clangRoot = sysrootDir + "/lib/clang";
    DIR* dir = opendir(clangRoot.c_str());
    if (!dir) {
        return "";
    }
    std::string bestVersion;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        std::string name = entry->d_name;
        if (name.empty() || name == "." || name == "..") {
            continue;
        }
        if (!std::isdigit(static_cast<unsigned char>(name[0]))) {
            continue;
        }
        if (bestVersion.empty() || compareVersionStrings(name, bestVersion) > 0) {
            bestVersion = name;
        }
    }
    closedir(dir);
    if (bestVersion.empty()) {
        return "";
    }
    return clangRoot + "/" + bestVersion;
}

std::vector<std::string> buildDefaultClangdArgs(const std::string& resourceDir) {
    std::vector<std::string> args = {
        "clangd",
        "--background-index=false",  // 禁用后台索引，减少资源占用
        "--clang-tidy=false",
        "--completion-style=bundled",  // 使用简化的补全样式
        "--pch-storage=memory",
        "--log=error",  // 只记录错误，减少日志输出
        "--header-insertion=never",  // 不自动插入头文件
        "-j=2"  // 限制并发任务数
    };
    if (!resourceDir.empty()) {
        args.push_back("--resource-dir=" + resourceDir);
    }
    return args;
}

} // namespace

namespace tinaide {
namespace lsp {

ClangdServer::ClangdServer()
    : handle_(nullptr)
    , thread_(0)
    , running_(false)
    , clangdMain_(nullptr)
    , clangdRun_(nullptr) {
    stdinPipe_[0] = stdinPipe_[1] = -1;
    stdoutPipe_[0] = stdoutPipe_[1] = -1;
}

ClangdServer::~ClangdServer() {
    stop();
}

void* ClangdServer::clangdThreadFunc(void* arg) {
    ClangdServer* server = static_cast<ClangdServer*>(arg);

    // 保存原始的 stdin/stdout/stderr
    int savedStdin = dup(STDIN_FILENO);
    int savedStdout = dup(STDOUT_FILENO);
    int savedStderr = dup(STDERR_FILENO);

    // 创建 stderr 管道用于捕获错误信息
    int stderrPipe[2] = {-1, -1};
    if (pipe(stderrPipe) == 0) {
        fcntl(stderrPipe[0], F_SETFL, O_NONBLOCK);
        dup2(stderrPipe[1], STDERR_FILENO);
    } else {
        LOGW("clangd_thread: failed to create stderr pipe: %s", strerror(errno));
    }

    // 重定向 stdin/stdout 到管道
    dup2(server->stdinPipe_[0], STDIN_FILENO);   // 从 stdin 管道读取
    dup2(server->stdoutPipe_[1], STDOUT_FILENO); // 写入 stdout 管道

    // 关闭线程中不需要的管道端点（但不能关闭 Java 侧使用的端点！）
    // stdinPipe_[0] 已经被 dup2 复制到 STDIN，可以关闭
    // stdoutPipe_[1] 已经被 dup2 复制到 STDOUT，可以关闭
    // 但是要在本地变量中保存，不要修改 server 的成员变量
    int localStdinRead = server->stdinPipe_[0];
    int localStdoutWrite = server->stdoutPipe_[1];

    // 关闭本地副本（不影响 Java 侧的 stdinPipe_[1] 和 stdoutPipe_[0]）
    close(localStdinRead);
    close(localStdoutWrite);

    LOGI("clangd_thread: starting clangd");

    int rc = -1;
    if (server->clangdMain_) {
        if (server->clangdArgs_.empty()) {
            server->clangdArgs_ = buildDefaultClangdArgs("");
        }
        std::vector<char*> argv;
        argv.reserve(server->clangdArgs_.size());
        for (auto& arg : server->clangdArgs_) {
            argv.push_back(const_cast<char*>(arg.c_str()));
        }
        rc = server->clangdMain_(static_cast<int>(argv.size()), argv.data());
    } else if (server->clangdRun_) {
        // 使用简化入口
        rc = server->clangdRun_();
    }

    LOGI("clangd_thread: clangd exited with rc=%d", rc);

    // 读取 stderr 输出
    if (stderrPipe[0] >= 0) {
        char buffer[4096];
        ssize_t n = ::read(stderrPipe[0], buffer, sizeof(buffer) - 1);
        if (n > 0) {
            buffer[n] = '\0';
            LOGE("clangd stderr: %s", buffer);
        }
        close(stderrPipe[0]);
        close(stderrPipe[1]);
    }

    // 恢复 stdin/stdout/stderr
    dup2(savedStdin, STDIN_FILENO);
    dup2(savedStdout, STDOUT_FILENO);
    dup2(savedStderr, STDERR_FILENO);
    close(savedStdin);
    close(savedStdout);
    close(savedStderr);

    server->running_.store(false);
    return reinterpret_cast<void*>(static_cast<intptr_t>(rc));
}

std::string ClangdServer::start(const std::string& libPath,
                                const std::vector<std::string>& extraArgs) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (running_.load()) {
        return "clangd is already running";
    }

    if (libPath.empty()) {
        return "libclangd.so path is empty";
    }

    // 检查库文件是否存在
    struct stat st;
    if (stat(libPath.c_str(), &st) != 0) {
        return "libclangd.so not found: " + libPath;
    }

    // ========== 预加载依赖库（修复 clangd 启动失败问题）==========
    // 获取 libclangd.so 所在目录
    std::string libDir = libPath.substr(0, libPath.rfind('/'));
    LOGI("startClangd: preloading dependencies from: %s", libDir.c_str());

    // 1. 预加载 libLLVM-*.so
    std::string llvmLib;
    DIR* dir = opendir(libDir.c_str());
    if (dir) {
        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            std::string name = entry->d_name;
            if (name.find("libLLVM-") == 0 && name.find(".so") != std::string::npos) {
                llvmLib = libDir + "/" + name;
                break;
            }
        }
        closedir(dir);
    }

    if (!llvmLib.empty()) {
        void* llvmHandle = dlopen(llvmLib.c_str(), RTLD_NOW | RTLD_GLOBAL);
        if (!llvmHandle) {
            LOGW("Failed to preload %s: %s", llvmLib.c_str(), dlerror());
        } else {
            LOGI("Preloaded LLVM library: %s", llvmLib.c_str());
        }
    } else {
        LOGW("libLLVM-*.so not found in %s", libDir.c_str());
    }

    // 2. 预加载 libclang-cpp.so
    std::string clangCppLib = libDir + "/libclang-cpp.so";
    if (stat(clangCppLib.c_str(), &st) == 0) {
        void* clangCppHandle = dlopen(clangCppLib.c_str(), RTLD_NOW | RTLD_GLOBAL);
        if (!clangCppHandle) {
            LOGW("Failed to preload libclang-cpp.so: %s", dlerror());
        } else {
            LOGI("Preloaded libclang-cpp.so");
        }
    } else {
        LOGW("libclang-cpp.so not found at %s", clangCppLib.c_str());
    }

    // 3. 预加载 libc++_shared.so（可能需要）
    std::string libcxxLib = libDir + "/libc++_shared.so";
    if (stat(libcxxLib.c_str(), &st) == 0) {
        void* libcxxHandle = dlopen(libcxxLib.c_str(), RTLD_NOW | RTLD_GLOBAL);
        if (!libcxxHandle) {
            LOGW("Failed to preload libc++_shared.so: %s", dlerror());
        } else {
            LOGI("Preloaded libc++_shared.so");
        }
    }
    // ========== 依赖库预加载结束 ==========

    // 创建管道
    if (pipe(stdinPipe_) != 0) {
        return std::string("Failed to create stdin pipe: ") + strerror(errno);
    }
    if (pipe(stdoutPipe_) != 0) {
        close(stdinPipe_[0]);
        close(stdinPipe_[1]);
        return std::string("Failed to create stdout pipe: ") + strerror(errno);
    }

    // 增加管道缓冲区大小（避免 clangd 在发送大量数据时阻塞）
    // F_SETPIPE_SZ 需要 Linux 2.6.35+，Android 支持
    int pipeSize = 1024 * 1024; // 1MB
    if (fcntl(stdoutPipe_[0], F_SETPIPE_SZ, pipeSize) < 0) {
        LOGW("Failed to increase stdout pipe size: %s", strerror(errno));
    } else {
        LOGI("Increased stdout pipe size to %d bytes", pipeSize);
    }
    if (fcntl(stdinPipe_[1], F_SETPIPE_SZ, pipeSize) < 0) {
        LOGW("Failed to increase stdin pipe size: %s", strerror(errno));
    }

    // 设置非阻塞模式（Java 端使用）
    fcntl(stdinPipe_[1], F_SETFL, O_NONBLOCK);  // 写端
    fcntl(stdoutPipe_[0], F_SETFL, O_NONBLOCK); // 读端

    // 加载 libclangd.so
    LOGI("startClangd: loading libclangd.so from: %s", libPath.c_str());
    handle_ = dlopen(libPath.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!handle_) {
        const char* error = dlerror();
        std::string errorMsg = std::string("dlopen failed: ") + (error ? error : "unknown");
        LOGE("%s", errorMsg.c_str());
        close(stdinPipe_[0]);
        close(stdinPipe_[1]);
        close(stdoutPipe_[0]);
        close(stdoutPipe_[1]);
        return errorMsg;
    }

    LOGI("startClangd: libclangd.so loaded successfully");

    // 清除之前的 dlerror
    dlerror();

    // 查找入口点
    clangdMain_ = reinterpret_cast<ClangdMainFn>(dlsym(handle_, "clangd_main"));
    const char* dlsymError1 = dlerror();
    if (dlsymError1) {
        LOGW("dlsym(clangd_main) warning: %s", dlsymError1);
    }

    clangdRun_ = reinterpret_cast<ClangdRunFn>(dlsym(handle_, "clangd_run"));
    const char* dlsymError2 = dlerror();
    if (dlsymError2) {
        LOGW("dlsym(clangd_run) warning: %s", dlsymError2);
    }

    if (!clangdMain_ && !clangdRun_) {
        std::string errorMsg = "Both clangd_main and clangd_run symbols not found";
        LOGE("%s", errorMsg.c_str());
        dlclose(handle_);
        handle_ = nullptr;
        close(stdinPipe_[0]);
        close(stdinPipe_[1]);
        close(stdoutPipe_[0]);
        close(stdoutPipe_[1]);
        return errorMsg;
    }

    LOGI("startClangd: found symbols - clangd_main=%p, clangd_run=%p",
         (void*)clangdMain_, (void*)clangdRun_);

    std::string sysrootDir = detectSysrootDir(libPath);
    if (sysrootDir.empty()) {
        LOGW("startClangd: failed to detect sysroot from %s", libPath.c_str());
    } else {
        LOGI("startClangd: detected sysroot at %s", sysrootDir.c_str());
    }
    std::string resourceDir;
    if (!sysrootDir.empty()) {
        resourceDir = findClangResourceDir(sysrootDir);
        if (!resourceDir.empty()) {
            LOGI("startClangd: using clang resource dir %s", resourceDir.c_str());
        } else {
            LOGW("startClangd: clang resource dir missing under %s/lib/clang", sysrootDir.c_str());
        }
    }

    // 构建最终参数（默认参数 + 额外参数）
    clangdArgs_ = buildDefaultClangdArgs(resourceDir);
    clangdArgs_.insert(clangdArgs_.end(), extraArgs.begin(), extraArgs.end());

    // 启动 clangd 线程
    running_.store(true);
    int rc = pthread_create(&thread_, nullptr, clangdThreadFunc, this);
    if (rc != 0) {
        running_.store(false);
        dlclose(handle_);
        handle_ = nullptr;
        close(stdinPipe_[0]);
        close(stdinPipe_[1]);
        close(stdoutPipe_[0]);
        close(stdoutPipe_[1]);
        return std::string("pthread_create failed: ") + strerror(rc);
    }

    LOGI("startClangd: clangd thread started");
    return ""; // 成功
}

void ClangdServer::stop() {
    std::lock_guard<std::mutex> lock(mutex_);

    if (!running_.load()) {
        LOGI("stopClangd: clangd is not running");
        return;
    }

    LOGI("stopClangd: stopping clangd");

    // 关闭管道以发送 EOF 信号
    if (stdinPipe_[1] >= 0) {
        close(stdinPipe_[1]);
        stdinPipe_[1] = -1;
    }

    // 等待线程结束
    int rc = pthread_join(thread_, nullptr);
    if (rc != 0) {
        LOGW("stopClangd: pthread_join failed: %s", strerror(rc));
    }

    // 关闭剩余的管道
    if (stdinPipe_[0] >= 0) {
        close(stdinPipe_[0]);
        stdinPipe_[0] = -1;
    }
    if (stdoutPipe_[0] >= 0) {
        close(stdoutPipe_[0]);
        stdoutPipe_[0] = -1;
    }
    if (stdoutPipe_[1] >= 0) {
        close(stdoutPipe_[1]);
        stdoutPipe_[1] = -1;
    }

    // 卸载库
    if (handle_) {
        dlclose(handle_);
        handle_ = nullptr;
    }

    clangdMain_ = nullptr;
    clangdRun_ = nullptr;
    running_.store(false);

    LOGI("stopClangd: clangd stopped");
}

bool ClangdServer::isRunning() const {
    if (!running_.load()) {
        return false;
    }
    // 检查管道是否仍然有效
    if (stdinPipe_[1] < 0 || stdoutPipe_[0] < 0) {
        return false;
    }
    return true;
}

int ClangdServer::write(const std::vector<char>& data) {
    if (!running_.load() || stdinPipe_[1] < 0) {
        LOGE("writeToClangd: not running or pipe closed");
        return -1;
    }

    if (data.empty()) {
        return 0;
    }

    // 使用 poll 检查管道是否可写
    struct pollfd pfd;
    pfd.fd = stdinPipe_[1];
    pfd.events = POLLOUT;
    pfd.revents = 0;
    
    int pollResult = poll(&pfd, 1, 1000); // 等待最多 1 秒
    if (pollResult <= 0) {
        LOGE("writeToClangd: pipe not writable (poll=%d, errno=%s)", pollResult, strerror(errno));
        return -1;
    }
    if (pfd.revents & (POLLERR | POLLHUP | POLLNVAL)) {
        LOGE("writeToClangd: pipe error (revents=0x%x)", pfd.revents);
        return -1;
    }

    ssize_t written = ::write(stdinPipe_[1], data.data(), data.size());
    if (written < 0) {
        LOGE("writeToClangd: write failed: %s", strerror(errno));
        return -1;
    }

    return static_cast<int>(written);
}

std::vector<char> ClangdServer::read(int maxBytes) {
    std::vector<char> result;

    if (!running_.load() || stdoutPipe_[0] < 0) {
        return result;
    }

    if (maxBytes <= 0) {
        maxBytes = 8192;
    }

    // 检查是否有数据可读（非阻塞）
    struct pollfd pfd;
    pfd.fd = stdoutPipe_[0];
    pfd.events = POLLIN;
    pfd.revents = 0;

    int pollResult = poll(&pfd, 1, 0); // 非阻塞
    if (pollResult <= 0 || !(pfd.revents & POLLIN)) {
        return result; // 无数据
    }

    // 读取数据
    std::vector<char> buffer(maxBytes);
    ssize_t n = ::read(stdoutPipe_[0], buffer.data(), buffer.size());

    if (n <= 0) {
        if (n < 0 && errno != EAGAIN && errno != EWOULDBLOCK) {
            LOGW("readFromClangd: read failed: %s", strerror(errno));
        }
        return result;
    }

    result.assign(buffer.begin(), buffer.begin() + n);
    return result;
}

std::vector<char> ClangdServer::readWithTimeout(int maxBytes, int timeoutMs) {
    std::vector<char> result;

    if (!running_.load() || stdoutPipe_[0] < 0) {
        return result;
    }

    if (maxBytes <= 0) {
        maxBytes = 8192;
    }
    if (timeoutMs < 0) {
        timeoutMs = 0;
    }

    // 等待数据（带超时）
    struct pollfd pfd;
    pfd.fd = stdoutPipe_[0];
    pfd.events = POLLIN;
    pfd.revents = 0;

    int pollResult = poll(&pfd, 1, timeoutMs);
    if (pollResult <= 0 || !(pfd.revents & POLLIN)) {
        return result; // 超时或错误
    }

    // 读取数据
    std::vector<char> buffer(maxBytes);
    ssize_t n = ::read(stdoutPipe_[0], buffer.data(), buffer.size());

    if (n <= 0) {
        return result;
    }

    result.assign(buffer.begin(), buffer.begin() + n);
    return result;
}

} // namespace lsp
} // namespace tinaide
