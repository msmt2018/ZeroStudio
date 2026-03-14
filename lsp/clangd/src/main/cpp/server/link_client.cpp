// 链接服务器 IPC 客户端
//
// 提供与链接守护进程通信的客户端接口。
// 主进程通过此客户端发送链接请求，接收链接结果。

#include "link_server_protocol.h"
#include "../linker/lld_linker.h"

#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <errno.h>
#include <poll.h>

#include <cstring>
#include <string>
#include <vector>
#include <mutex>

#include <android/log.h>

#define LOG_TAG "LinkClient"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace tinaide {
namespace linker {
namespace client {

// ============================================================================
// 连接管理
// ============================================================================

namespace {

std::mutex g_connectionMutex;
int g_socketFd = -1;

// 转义 JSON 字符串
std::string escapeJson(const std::string& s) {
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
}

// 简单 JSON 字符串提取
std::string extractJsonString(const std::string& json, const std::string& key) {
    std::string searchKey = "\"" + key + "\"";
    size_t keyPos = json.find(searchKey);
    if (keyPos == std::string::npos) return "";

    size_t colonPos = json.find(':', keyPos + searchKey.length());
    if (colonPos == std::string::npos) return "";

    size_t valueStart = colonPos + 1;
    while (valueStart < json.length() && (json[valueStart] == ' ' || json[valueStart] == '\t')) {
        valueStart++;
    }

    if (valueStart >= json.length() || json[valueStart] != '"') return "";
    valueStart++;

    std::string result;
    for (size_t i = valueStart; i < json.length(); ++i) {
        if (json[i] == '\\' && i + 1 < json.length()) {
            char next = json[i + 1];
            switch (next) {
                case 'n': result += '\n'; break;
                case 'r': result += '\r'; break;
                case 't': result += '\t'; break;
                default: result += next; break;
            }
            i++;
        } else if (json[i] == '"') {
            break;
        } else {
            result += json[i];
        }
    }
    return result;
}

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

int extractJsonInt(const std::string& json, const std::string& key, int defaultValue = 0) {
    std::string searchKey = "\"" + key + "\"";
    size_t keyPos = json.find(searchKey);
    if (keyPos == std::string::npos) return defaultValue;

    size_t colonPos = json.find(':', keyPos + searchKey.length());
    if (colonPos == std::string::npos) return defaultValue;

    size_t valueStart = colonPos + 1;
    while (valueStart < json.length() && (json[valueStart] == ' ' || json[valueStart] == '\t')) {
        valueStart++;
    }

    int result = 0;
    bool negative = false;
    if (valueStart < json.length() && json[valueStart] == '-') {
        negative = true;
        valueStart++;
    }

    while (valueStart < json.length() && json[valueStart] >= '0' && json[valueStart] <= '9') {
        result = result * 10 + (json[valueStart] - '0');
        valueStart++;
    }

    return negative ? -result : result;
}

bool connectToServer() {
    if (g_socketFd >= 0) {
        return true;  // 已连接
    }

    g_socketFd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (g_socketFd < 0) {
        LOGE("Failed to create socket: %s", strerror(errno));
        return false;
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    int pathLen = link_server_get_socket_path(addr.sun_path, sizeof(addr.sun_path));
    if (pathLen < 0) {
        LOGE("Failed to get socket path");
        close(g_socketFd);
        g_socketFd = -1;
        return false;
    }

    socklen_t addrLen = offsetof(struct sockaddr_un, sun_path) + pathLen;
    if (connect(g_socketFd, reinterpret_cast<struct sockaddr*>(&addr), addrLen) < 0) {
        LOGE("Failed to connect to link server: %s", strerror(errno));
        close(g_socketFd);
        g_socketFd = -1;
        return false;
    }

    LOGI("Connected to link server");
    return true;
}

void disconnectFromServer() {
    if (g_socketFd >= 0) {
        close(g_socketFd);
        g_socketFd = -1;
        LOGI("Disconnected from link server");
    }
}

bool sendMessage(uint16_t type, const std::string& body) {
    if (g_socketFd < 0) return false;

    LinkMessageHeader header;
    header.length = static_cast<uint32_t>(body.size());
    header.type = type;
    header.version = LINK_SERVER_PROTOCOL_VERSION;

    if (write(g_socketFd, &header, sizeof(header)) != sizeof(header)) {
        LOGE("Failed to send message header (type=%u, len=%u): %s", header.type, header.length, strerror(errno));
        disconnectFromServer();
        return false;
    }

    if (!body.empty()) {
        if (write(g_socketFd, body.c_str(), body.size()) != static_cast<ssize_t>(body.size())) {
            LOGE("Failed to send message body (%zu bytes): %s", body.size(), strerror(errno));
            disconnectFromServer();
            return false;
        }
    }

    return true;
}

bool receiveMessage(uint16_t& type, std::string& body, int timeoutMs) {
    if (g_socketFd < 0) return false;

    auto readFully = [](int fd, void* buffer, size_t size) -> bool {
        char* ptr = static_cast<char*>(buffer);
        size_t received = 0;
        while (received < size) {
            ssize_t n = read(fd, ptr + received, size - received);
            if (n == 0) {
                return false; // peer closed
            }
            if (n < 0) {
                if (errno == EINTR) {
                    continue;
                }
                LOGE("read failed: %s", strerror(errno));
                return false;
            }
            received += static_cast<size_t>(n);
        }
        return true;
    };

    struct pollfd pfd;
    pfd.fd = g_socketFd;
    pfd.events = POLLIN;
    pfd.revents = 0;

    int pollResult = poll(&pfd, 1, timeoutMs);
    if (pollResult <= 0) {
        if (pollResult == 0) {
            LOGE("Receive timeout after %d ms", timeoutMs);
        } else {
            LOGE("poll failed: %s", strerror(errno));
        }
        return false;
    }

    LinkMessageHeader header;
    if (!readFully(g_socketFd, &header, sizeof(header))) {
        LOGE("Failed to read message header");
        disconnectFromServer();
        return false;
    }

    LOGI("Received header: type=%u len=%u", header.type, header.length);
    type = header.type;

    if (header.length == 0) {
        body.clear();
        return true;
    }

    body.resize(header.length);
    if (!readFully(g_socketFd, &body[0], header.length)) {
        LOGE("Failed to read message body (%u bytes expected)", header.length);
        disconnectFromServer();
        return false;
    }

    LOGI("Received body (%zu bytes)", body.size());
    return true;
}

// 构建链接请求 JSON
std::string buildLinkRequestJson(
    const std::vector<std::string>& objPaths,
    const std::string& outputPath,
    const LinkOptions& options) {

    std::string json = "{";

    // obj_paths
    json += "\"obj_paths\":[";
    for (size_t i = 0; i < objPaths.size(); ++i) {
        if (i > 0) json += ",";
        json += "\"" + escapeJson(objPaths[i]) + "\"";
    }
    json += "],";

    // output_path
    json += "\"output_path\":\"" + escapeJson(outputPath) + "\",";

    // sysroot
    json += "\"sysroot\":\"" + escapeJson(options.sysroot) + "\",";

    // target
    json += "\"target\":\"" + escapeJson(options.target) + "\",";

    // is_cxx
    json += "\"is_cxx\":" + std::string(options.isCxx ? "true" : "false") + ",";

    // extra_lib_dirs
    json += "\"extra_lib_dirs\":[";
    bool first = true;
    for (const auto& dir : options.libDirs) {
        if (!dir.empty()) {
            if (!first) json += ",";
            json += "\"" + escapeJson(dir) + "\"";
            first = false;
        }
    }
    json += "],";

    // extra_libs
    json += "\"extra_libs\":[";
    first = true;
    for (const auto& lib : options.libs) {
        if (!lib.empty()) {
            if (!first) json += ",";
            json += "\"" + escapeJson(lib) + "\"";
            first = false;
        }
    }
    json += "]";

    json += "}";
    return json;
}

} // anonymous namespace

// ============================================================================
// 公共接口
// ============================================================================

// 通过 IPC 链接共享库
LinkResult linkSharedViaServer(
    const std::vector<std::string>& objPaths,
    const std::string& soPath,
    const LinkOptions& options) {

    std::lock_guard<std::mutex> lock(g_connectionMutex);
    LinkResult result;

    LOGI("linkSharedViaServer: %zu objects -> %s", objPaths.size(), soPath.c_str());

    // 连接服务器
    if (!connectToServer()) {
        result.errorMessage = "Failed to connect to link server";
        return result;
    }

    // 构建请求
    std::string requestJson = buildLinkRequestJson(objPaths, soPath, options);
    LOGI("Request: %s", requestJson.c_str());

    // 发送请求
    if (!sendMessage(LINK_MSG_LINK_SHARED, requestJson)) {
        result.errorMessage = "Failed to send link request";
        return result;
    }

    // 接收响应
    uint16_t responseType;
    std::string responseBody;
    if (!receiveMessage(responseType, responseBody, options.timeoutMs)) {
        result.errorMessage = "Failed to receive link response";
        return result;
    }

    LOGI("Response type=%d, body=%s", responseType, responseBody.c_str());

    // 解析响应
    if (responseType == LINK_MSG_RESPONSE) {
        result.success = extractJsonBool(responseBody, "success");
        result.exitCode = extractJsonInt(responseBody, "exit_code");
        result.errorMessage = extractJsonString(responseBody, "error_message");
    } else if (responseType == LINK_MSG_ERROR) {
        result.errorMessage = extractJsonString(responseBody, "error");
    } else {
        result.errorMessage = "Unexpected response type: " + std::to_string(responseType);
    }

    return result;
}

// 通过 IPC 链接可执行文件
LinkResult linkExecutableViaServer(
    const std::vector<std::string>& objPaths,
    const std::string& exePath,
    const LinkOptions& options) {

    std::lock_guard<std::mutex> lock(g_connectionMutex);
    LinkResult result;

    LOGI("linkExecutableViaServer: %zu objects -> %s", objPaths.size(), exePath.c_str());

    // 连接服务器
    if (!connectToServer()) {
        result.errorMessage = "Failed to connect to link server";
        return result;
    }

    // 构建请求
    std::string requestJson = buildLinkRequestJson(objPaths, exePath, options);

    // 发送请求
    if (!sendMessage(LINK_MSG_LINK_EXECUTABLE, requestJson)) {
        result.errorMessage = "Failed to send link request";
        return result;
    }

    // 接收响应
    uint16_t responseType;
    std::string responseBody;
    if (!receiveMessage(responseType, responseBody, options.timeoutMs)) {
        result.errorMessage = "Failed to receive link response";
        return result;
    }

    // 解析响应
    if (responseType == LINK_MSG_RESPONSE) {
        result.success = extractJsonBool(responseBody, "success");
        result.exitCode = extractJsonInt(responseBody, "exit_code");
        result.errorMessage = extractJsonString(responseBody, "error_message");
    } else if (responseType == LINK_MSG_ERROR) {
        result.errorMessage = extractJsonString(responseBody, "error");
    } else {
        result.errorMessage = "Unexpected response type: " + std::to_string(responseType);
    }

    return result;
}

// Ping 服务器
bool pingServer() {
    std::lock_guard<std::mutex> lock(g_connectionMutex);

    if (!connectToServer()) {
        return false;
    }

    if (!sendMessage(LINK_MSG_PING, "")) {
        return false;
    }

    uint16_t responseType;
    std::string responseBody;
    if (!receiveMessage(responseType, responseBody, 5000)) {
        return false;
    }

    return responseType == LINK_MSG_PONG;
}

// 关闭服务器
void shutdownServer() {
    std::lock_guard<std::mutex> lock(g_connectionMutex);

    if (!connectToServer()) {
        return;
    }

    sendMessage(LINK_MSG_SHUTDOWN, "");

    uint16_t responseType;
    std::string responseBody;
    receiveMessage(responseType, responseBody, 5000);

    disconnectFromServer();
}

// 关闭客户端连接
void closeConnection() {
    std::lock_guard<std::mutex> lock(g_connectionMutex);
    disconnectFromServer();
}

} // namespace client
} // namespace linker
} // namespace tinaide
