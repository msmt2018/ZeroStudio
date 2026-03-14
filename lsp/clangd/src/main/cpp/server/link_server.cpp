// 链接服务器守护进程
//
// 在应用启动最早期 fork 出的守护进程，负责处理所有链接请求。
// 每次链接请求都会 fork 一个子进程执行，确保 LLD 全局状态被完全隔离。
//
// 架构：
// 1. 单线程事件循环，使用 Unix Domain Socket 接收请求
// 2. 每次链接：fork -> dlopen -> 调用链接函数 -> 子进程退出
// 3. 通过 pipe 返回结果给父进程，再通过 socket 返回给客户端
//
// 生命周期：
// 1. 由 TinaApplication.onCreate() 在最早期 fork
// 2. 运行直到收到 SHUTDOWN 消息或主进程退出
// 3. 崩溃时由主进程重新 fork

#include "link_server_protocol.h"
#include "../linker/lld_linker_api.h"

#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
#include <signal.h>
#include <dlfcn.h>
#include <errno.h>
#include <poll.h>

#include <cstdlib>
#include <cstring>
#include <cstdio>
#include <string>
#include <vector>

#include <android/log.h>

#define LOG_TAG "LinkServer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ============================================================================
// 全局变量
// ============================================================================

static volatile sig_atomic_t g_running = 1;
static std::string g_lldLinkerLibPath;
static std::string g_filesDir;

// ============================================================================
// 信号处理
// ============================================================================

static void signalHandler(int sig) {
    if (sig == SIGTERM || sig == SIGINT) {
        g_running = 0;
    }
}

// ============================================================================
// Socket 路径
// ============================================================================

extern "C" int link_server_get_socket_path(char* buffer, size_t buffer_size) {
    // 使用抽象命名空间（以 \0 开头）
    uid_t uid = getuid();
    int len = snprintf(buffer + 1, buffer_size - 1, "tinaide_linkd_%d", uid);
    if (len < 0 || static_cast<size_t>(len) >= buffer_size - 1) {
        return -1;
    }
    buffer[0] = '\0';  // 抽象命名空间标记
    return len + 1;    // 返回总长度（包含前导 \0）
}

// ============================================================================
// JSON 解析（简化版，仅支持需要的字段）
// ============================================================================

namespace {

// 简单的 JSON 字符串提取
std::string extractJsonString(const std::string& json, const std::string& key) {
    std::string searchKey = "\"" + key + "\"";
    size_t keyPos = json.find(searchKey);
    if (keyPos == std::string::npos) return "";

    size_t colonPos = json.find(':', keyPos + searchKey.length());
    if (colonPos == std::string::npos) return "";

    // 跳过空白
    size_t valueStart = colonPos + 1;
    while (valueStart < json.length() && (json[valueStart] == ' ' || json[valueStart] == '\t')) {
        valueStart++;
    }

    if (valueStart >= json.length() || json[valueStart] != '"') return "";
    valueStart++;

    // 找到结束引号
    std::string result;
    for (size_t i = valueStart; i < json.length(); ++i) {
        if (json[i] == '\\' && i + 1 < json.length()) {
            result += json[i + 1];
            i++;
        } else if (json[i] == '"') {
            break;
        } else {
            result += json[i];
        }
    }
    return result;
}

// 提取 JSON 布尔值
bool extractJsonBool(const std::string& json, const std::string& key, bool defaultValue = false) {
    std::string searchKey = "\"" + key + "\"";
    size_t keyPos = json.find(searchKey);
    if (keyPos == std::string::npos) return defaultValue;

    size_t colonPos = json.find(':', keyPos + searchKey.length());
    if (colonPos == std::string::npos) return defaultValue;

    size_t valueStart = colonPos + 1;
    while (valueStart < json.length() && (json[valueStart] == ' ' || json[valueStart] == '\t')) {
        valueStart++;
    }

    if (json.substr(valueStart, 4) == "true") return true;
    if (json.substr(valueStart, 5) == "false") return false;
    return defaultValue;
}

// 提取 JSON 字符串数组
std::vector<std::string> extractJsonStringArray(const std::string& json, const std::string& key) {
    std::vector<std::string> result;
    std::string searchKey = "\"" + key + "\"";
    size_t keyPos = json.find(searchKey);
    if (keyPos == std::string::npos) return result;

    size_t colonPos = json.find(':', keyPos + searchKey.length());
    if (colonPos == std::string::npos) return result;

    size_t arrayStart = json.find('[', colonPos);
    if (arrayStart == std::string::npos) return result;

    size_t arrayEnd = json.find(']', arrayStart);
    if (arrayEnd == std::string::npos) return result;

    std::string arrayContent = json.substr(arrayStart + 1, arrayEnd - arrayStart - 1);

    // 解析数组中的字符串
    size_t pos = 0;
    while (pos < arrayContent.length()) {
        size_t quoteStart = arrayContent.find('"', pos);
        if (quoteStart == std::string::npos) break;

        std::string value;
        for (size_t i = quoteStart + 1; i < arrayContent.length(); ++i) {
            if (arrayContent[i] == '\\' && i + 1 < arrayContent.length()) {
                value += arrayContent[i + 1];
                i++;
            } else if (arrayContent[i] == '"') {
                pos = i + 1;
                break;
            } else {
                value += arrayContent[i];
            }
        }
        if (!value.empty()) {
            result.push_back(value);
        }
    }

    return result;
}

// 构建响应 JSON
std::string buildResponseJson(bool success, int exitCode, const std::string& errorMsg, const std::string& diag) {
    std::string json = "{";
    json += "\"success\":" + std::string(success ? "true" : "false") + ",";
    json += "\"exit_code\":" + std::to_string(exitCode) + ",";

    // 转义字符串
    auto escape = [](const std::string& s) {
        std::string result;
        for (char c : s) {
            switch (c) {
                case '"': result += "\\\""; break;
                case '\\': result += "\\\\"; break;
                case '\n': result += "\\n"; break;
                case '\r': result += "\\r"; break;
                case '\t': result += "\\t"; break;
                default: result += c; break;
            }
        }
        return result;
    };

    json += "\"error_message\":\"" + escape(errorMsg) + "\",";
    json += "\"diagnostics\":\"" + escape(diag) + "\"";
    json += "}";
    return json;
}

} // anonymous namespace

// ============================================================================
// 链接执行
// ============================================================================

namespace {

struct LinkRequest {
    std::vector<std::string> objPaths;
    std::string outputPath;
    std::string sysroot;
    std::string target;
    bool isCxx;
    std::vector<std::string> extraLibDirs;
    std::vector<std::string> extraLibs;
};

bool parseRequest(const std::string& json, LinkRequest& req) {
    req.objPaths = extractJsonStringArray(json, "obj_paths");
    req.outputPath = extractJsonString(json, "output_path");
    req.sysroot = extractJsonString(json, "sysroot");
    req.target = extractJsonString(json, "target");
    req.isCxx = extractJsonBool(json, "is_cxx");
    req.extraLibDirs = extractJsonStringArray(json, "extra_lib_dirs");
    req.extraLibs = extractJsonStringArray(json, "extra_libs");

    if (req.objPaths.empty() || req.outputPath.empty() || req.sysroot.empty()) {
        return false;
    }
    return true;
}

// 在子进程中执行实际的链接操作
// 这个函数在 fork 的子进程中运行，完成后进程退出，确保 LLD 状态被完全清理
void executeLinkInChild(const LinkRequest& req, bool isShared, int resultPipeFd) {
    LOGI("executeLinkInChild: %zu objects -> %s (shared=%d)", req.objPaths.size(), req.outputPath.c_str(), isShared);

    auto writeResult = [resultPipeFd](const std::string& response) {
        uint32_t len = static_cast<uint32_t>(response.size());
        write(resultPipeFd, &len, sizeof(len));
        write(resultPipeFd, response.data(), response.size());
        close(resultPipeFd);
    };

    // 打开 liblld_linker.so
    void* handle = dlopen(g_lldLinkerLibPath.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        std::string error = std::string("dlopen failed: ") + dlerror();
        LOGE("%s", error.c_str());
        writeResult(buildResponseJson(false, -1, error, ""));
        return;
    }

    // 获取函数指针
    auto linkSharedFn = reinterpret_cast<lld_link_shared_fn>(dlsym(handle, "lld_link_shared"));
    auto linkExeFn = reinterpret_cast<lld_link_executable_fn>(dlsym(handle, "lld_link_executable"));
    auto freeResultFn = reinterpret_cast<lld_free_result_fn>(dlsym(handle, "lld_free_result"));

    if (!linkSharedFn || !linkExeFn || !freeResultFn) {
        std::string error = std::string("dlsym failed: ") + dlerror();
        LOGE("%s", error.c_str());
        dlclose(handle);
        writeResult(buildResponseJson(false, -1, error, ""));
        return;
    }

    // 准备参数
    std::vector<const char*> objPathPtrs;
    for (const auto& p : req.objPaths) {
        objPathPtrs.push_back(p.c_str());
    }

    std::vector<const char*> libDirPtrs;
    for (const auto& d : req.extraLibDirs) {
        if (!d.empty()) libDirPtrs.push_back(d.c_str());
    }

    std::vector<const char*> libPtrs;
    for (const auto& l : req.extraLibs) {
        if (!l.empty()) libPtrs.push_back(l.c_str());
    }

    LldLinkOptions options = {};
    options.sysroot = req.sysroot.c_str();
    options.target = req.target.c_str();
    options.is_cxx = req.isCxx ? 1 : 0;
    options.extra_lib_dirs = libDirPtrs.empty() ? nullptr : libDirPtrs.data();
    options.extra_lib_dirs_count = libDirPtrs.size();
    options.extra_libs = libPtrs.empty() ? nullptr : libPtrs.data();
    options.extra_libs_count = libPtrs.size();

    // 执行链接
    LldLinkResult result = {};
    if (isShared) {
        linkSharedFn(objPathPtrs.data(), objPathPtrs.size(), req.outputPath.c_str(), &options, &result);
    } else {
        linkExeFn(objPathPtrs.data(), objPathPtrs.size(), req.outputPath.c_str(), &options, &result);
    }

    // 构建响应
    std::string response = buildResponseJson(
        result.success != 0,
        result.exit_code,
        result.error_message ? result.error_message : "",
        result.diagnostics ? result.diagnostics : ""
    );

    // 清理
    freeResultFn(&result);
    dlclose(handle);

    LOGI("executeLinkInChild done: success=%d", result.success);
    writeResult(response);
}

// 主进程中的 executeLink：fork 子进程执行链接，等待结果
// 这确保每次链接都在全新的进程中执行，LLD 全局状态被完全隔离
std::string executeLink(const LinkRequest& req, bool isShared) {
    LOGI("executeLink: %zu objects -> %s (shared=%d)", req.objPaths.size(), req.outputPath.c_str(), isShared);

    // 创建管道用于接收子进程的结果
    int pipeFds[2];
    if (pipe(pipeFds) < 0) {
        std::string error = std::string("pipe failed: ") + strerror(errno);
        LOGE("%s", error.c_str());
        return buildResponseJson(false, -1, error, "");
    }

    pid_t pid = fork();
    if (pid < 0) {
        std::string error = std::string("fork failed: ") + strerror(errno);
        LOGE("%s", error.c_str());
        close(pipeFds[0]);
        close(pipeFds[1]);
        return buildResponseJson(false, -1, error, "");
    }

    if (pid == 0) {
        // ====== 子进程 ======
        close(pipeFds[0]);  // 关闭读端
        executeLinkInChild(req, isShared, pipeFds[1]);
        _exit(0);
    }

    // ====== 父进程 ======
    close(pipeFds[1]);  // 关闭写端

    // 读取子进程的结果
    std::string response;
    uint32_t len = 0;

    // 设置超时读取
    struct pollfd pfd;
    pfd.fd = pipeFds[0];
    pfd.events = POLLIN;

    int pollResult = poll(&pfd, 1, 120000);  // 2 分钟超时
    if (pollResult <= 0) {
        close(pipeFds[0]);
        kill(pid, SIGKILL);
        waitpid(pid, nullptr, 0);
        std::string error = pollResult == 0 ? "Link timeout" : std::string("poll failed: ") + strerror(errno);
        LOGE("%s", error.c_str());
        return buildResponseJson(false, -1, error, "");
    }

    // 读取长度
    ssize_t n = read(pipeFds[0], &len, sizeof(len));
    if (n != sizeof(len) || len == 0 || len > 10 * 1024 * 1024) {
        close(pipeFds[0]);
        waitpid(pid, nullptr, 0);
        std::string error = "Invalid response from link child";
        LOGE("%s (n=%zd, len=%u)", error.c_str(), n, len);
        return buildResponseJson(false, -1, error, "");
    }

    // 读取响应体
    response.resize(len);
    size_t received = 0;
    while (received < len) {
        n = read(pipeFds[0], &response[received], len - received);
        if (n <= 0) break;
        received += static_cast<size_t>(n);
    }

    close(pipeFds[0]);

    // 等待子进程退出
    int status;
    waitpid(pid, &status, 0);

    if (received != len) {
        std::string error = "Incomplete response from link child";
        LOGE("%s (received=%zu, expected=%u)", error.c_str(), received, len);
        return buildResponseJson(false, -1, error, "");
    }

    LOGI("executeLink done (child exited with status=%d)", WEXITSTATUS(status));
    return response;
}

} // anonymous namespace

// ============================================================================
// 消息处理
// ============================================================================

namespace {

bool sendMessage(int fd, uint16_t type, const std::string& body) {
    auto writeFully = [](int outFd, const void* data, size_t size) -> bool {
        const char* ptr = static_cast<const char*>(data);
        size_t written = 0;
        while (written < size) {
            ssize_t n = write(outFd, ptr + written, size - written);
            if (n < 0) {
                if (errno == EINTR) {
                    continue;
                }
                LOGE("write failed: %s", strerror(errno));
                return false;
            }
            if (n == 0) {
                LOGE("write returned 0 (size=%zu, written=%zu)", size, written);
                return false;
            }
            written += static_cast<size_t>(n);
        }
        return true;
    };

    LinkMessageHeader header;
    header.length = static_cast<uint32_t>(body.size());
    header.type = type;
    header.version = LINK_SERVER_PROTOCOL_VERSION;

    if (!writeFully(fd, &header, sizeof(header))) {
        LOGE("Failed to send message header (type=%u, len=%u)", header.type, header.length);
        return false;
    }

    if (!body.empty() && !writeFully(fd, body.data(), body.size())) {
        LOGE("Failed to send message body (%zu bytes)", body.size());
        return false;
    }

    LOGI("Sent response header type=%u len=%u", header.type, header.length);
    return true;
}

bool receiveMessage(int fd, uint16_t& type, std::string& body, int timeoutMs) {
    auto readFully = [](int inFd, void* data, size_t size) -> bool {
        char* ptr = static_cast<char*>(data);
        size_t total = 0;
        while (total < size) {
            ssize_t n = read(inFd, ptr + total, size - total);
            if (n == 0) {
                LOGE("read returned 0 (expected %zu)", size);
                return false;
            }
            if (n < 0) {
                if (errno == EINTR) {
                    continue;
                }
                LOGE("read failed: %s", strerror(errno));
                return false;
            }
            total += static_cast<size_t>(n);
        }
        return true;
    };

    struct pollfd pfd;
    pfd.fd = fd;
    pfd.events = POLLIN;
    pfd.revents = 0;

    int pollResult = poll(&pfd, 1, timeoutMs);
    if (pollResult <= 0) {
        if (pollResult == 0) {
            LOGW("Server receive timeout after %d ms", timeoutMs);
        } else {
            LOGE("Server poll failed: %s", strerror(errno));
        }
        return false;
    }

    LinkMessageHeader header;
    if (!readFully(fd, &header, sizeof(header))) {
        LOGE("Failed to read request header");
        return false;
    }

    LOGI("Server received header type=%u len=%u", header.type, header.length);
    type = header.type;

    if (header.length > 0) {
        body.resize(header.length);
        if (!readFully(fd, &body[0], header.length)) {
            LOGE("Failed to read request body (%u bytes)", header.length);
            return false;
        }
    } else {
        body.clear();
    }

    return true;
}

void handleClient(int clientFd) {
    LOGI("Client connected");

    while (g_running) {
        bool shouldClose = false;
        uint16_t msgType;
        std::string msgBody;

        if (!receiveMessage(clientFd, msgType, msgBody, 60000)) {
            // 超时或连接断开
            break;
        }

        LOGI("Received message type=%d, body_len=%zu", msgType, msgBody.size());

        switch (msgType) {
            case LINK_MSG_LINK_SHARED: {
                LinkRequest req;
                if (parseRequest(msgBody, req)) {
                    std::string response = executeLink(req, true);
                    if (!sendMessage(clientFd, LINK_MSG_RESPONSE, response)) {
                        LOGE("Failed to send shared link response");
                        shouldClose = true;
                    }
                } else {
                    if (!sendMessage(clientFd, LINK_MSG_ERROR, "{\"error\":\"Invalid request\"}")) {
                        LOGE("Failed to send error response");
                        shouldClose = true;
                    }
                }
                break;
            }

            case LINK_MSG_LINK_EXECUTABLE: {
                LinkRequest req;
                if (parseRequest(msgBody, req)) {
                    std::string response = executeLink(req, false);
                    if (!sendMessage(clientFd, LINK_MSG_RESPONSE, response)) {
                        LOGE("Failed to send executable link response");
                        shouldClose = true;
                    }
                } else {
                    if (!sendMessage(clientFd, LINK_MSG_ERROR, "{\"error\":\"Invalid request\"}")) {
                        LOGE("Failed to send error response");
                        shouldClose = true;
                    }
                }
                break;
            }

            case LINK_MSG_PING:
                if (!sendMessage(clientFd, LINK_MSG_PONG, "{\"alive\":true}")) {
                    LOGE("Failed to send pong");
                    shouldClose = true;
                }
                break;

            case LINK_MSG_SHUTDOWN:
                LOGI("Shutdown requested");
                g_running = 0;
                sendMessage(clientFd, LINK_MSG_RESPONSE, "{\"success\":true}");
                break;

            default:
                LOGW("Unknown message type: %d", msgType);
                if (!sendMessage(clientFd, LINK_MSG_ERROR, "{\"error\":\"Unknown message type\"}")) {
                    shouldClose = true;
                }
                break;
        }

        if (shouldClose) {
            break;
        }
    }

    close(clientFd);
    LOGI("Client disconnected");
}

} // anonymous namespace

// ============================================================================
// 守护进程主入口
// ============================================================================

extern "C" int link_server_main(const char* nativeLibDir, const char* filesDir) {
    LOGI("Link server starting...");
    LOGI("nativeLibDir: %s", nativeLibDir);
    LOGI("filesDir: %s", filesDir);

    // 保存路径
    g_lldLinkerLibPath = std::string(nativeLibDir) + "/liblld_linker.so";
    g_filesDir = filesDir;

    // 设置信号处理
    signal(SIGTERM, signalHandler);
    signal(SIGINT, signalHandler);
    signal(SIGPIPE, SIG_IGN);

    // 创建 socket
    int serverFd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (serverFd < 0) {
        LOGE("Failed to create socket: %s", strerror(errno));
        return 1;
    }

    // 设置 socket 地址（抽象命名空间）
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    int pathLen = link_server_get_socket_path(addr.sun_path, sizeof(addr.sun_path));
    if (pathLen < 0) {
        LOGE("Failed to get socket path");
        close(serverFd);
        return 1;
    }

    // 绑定 socket
    socklen_t addrLen = offsetof(struct sockaddr_un, sun_path) + pathLen;
    if (bind(serverFd, reinterpret_cast<struct sockaddr*>(&addr), addrLen) < 0) {
        LOGE("Failed to bind socket: %s", strerror(errno));
        close(serverFd);
        return 1;
    }

    // 监听连接
    if (listen(serverFd, 5) < 0) {
        LOGE("Failed to listen: %s", strerror(errno));
        close(serverFd);
        return 1;
    }

    LOGI("Link server listening...");

    // 主循环
    while (g_running) {
        struct pollfd pfd;
        pfd.fd = serverFd;
        pfd.events = POLLIN;
        pfd.revents = 0;

        int pollResult = poll(&pfd, 1, 1000);  // 1 秒超时
        if (pollResult < 0) {
            if (errno == EINTR) continue;
            LOGE("poll failed: %s", strerror(errno));
            break;
        }

        if (pollResult == 0) continue;  // 超时

        if (pfd.revents & POLLIN) {
            int clientFd = accept(serverFd, nullptr, nullptr);
            if (clientFd < 0) {
                if (errno == EINTR) continue;
                LOGE("accept failed: %s", strerror(errno));
                continue;
            }

            // 处理客户端（单线程，串行处理）
            handleClient(clientFd);
        }
    }

    close(serverFd);
    LOGI("Link server stopped");
    return 0;
}
