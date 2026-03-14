// SimpleLspClient 实现
// 直接通过 pipe 与 clangd 通信，使用 JSON 协议

#include "simple_lsp_client.h"
#include "../clangd_server.h"

#include <sstream>
#include <cstring>
#include <algorithm>
#include <cctype>
#include <unistd.h>
#include <vector>
#include <sys/stat.h>
#include <condition_variable>
#include <deque>

// 使用 nlohmann/json 库进行 JSON 解析
#include "../../utils/json.hpp"
using json = nlohmann::json;

#define LOG_TAG "SimpleLspClient"
#include "../../utils/logging.h"

namespace tinaide {
namespace lsp {

namespace {

bool fileExists(const std::string& path) {
    struct stat st {};
    return ::stat(path.c_str(), &st) == 0 && S_ISREG(st.st_mode);
}

std::string detectCompileCommandsDir(const std::string& workDir) {
    const char* variants[] = {
        "debug",
        "release",
        "Debug",
        "Release"
    };
    for (const char* variant : variants) {
        std::string dir = workDir + "/build/" + variant;
        std::string candidate = dir + "/compile_commands.json";
        if (fileExists(candidate)) {
            return dir;
        }
    }
    return "";
}

// ============================================================================
// JSON 解析辅助函数 - 使用 nlohmann/json 库解析 publishDiagnostics
// ============================================================================

// 从 JSON 对象解析单个诊断项
SimpleLspClient::DiagnosticItem parseSingleDiagnostic(const json& diagJson) {
    SimpleLspClient::DiagnosticItem item{};
    
    try {
        // 解析 range
        if (diagJson.contains("range") && diagJson["range"].is_object()) {
            const auto& range = diagJson["range"];
            
            // 解析 start 位置
            if (range.contains("start") && range["start"].is_object()) {
                const auto& start = range["start"];
                if (start.contains("line") && start["line"].is_number()) {
                    item.start_line = start["line"].get<uint32_t>();
                }
                if (start.contains("character") && start["character"].is_number()) {
                    item.start_character = start["character"].get<uint32_t>();
                }
            }
            
            // 解析 end 位置
            if (range.contains("end") && range["end"].is_object()) {
                const auto& end = range["end"];
                if (end.contains("line") && end["line"].is_number()) {
                    item.end_line = end["line"].get<uint32_t>();
                }
                if (end.contains("character") && end["character"].is_number()) {
                    item.end_character = end["character"].get<uint32_t>();
                }
            }
        }
        
        // 解析 severity
        if (diagJson.contains("severity") && diagJson["severity"].is_number()) {
            item.severity = diagJson["severity"].get<int>();
            // 确保 severity 在有效范围内 (1=Error, 2=Warning, 3=Info, 4=Hint)
            if (item.severity < 1 || item.severity > 4) {
                item.severity = 4; // 默认为 Hint
            }
        } else {
            item.severity = 4; // 默认为 Hint
        }
        
        // 解析 message
        if (diagJson.contains("message") && diagJson["message"].is_string()) {
            item.message = diagJson["message"].get<std::string>();
        }
        
        // 解析 source (可选)
        if (diagJson.contains("source") && diagJson["source"].is_string()) {
            item.source = diagJson["source"].get<std::string>();
        }
        
        // 解析 code (可选，可能是字符串或数字)
        if (diagJson.contains("code")) {
            if (diagJson["code"].is_string()) {
                item.code = diagJson["code"].get<std::string>();
            } else if (diagJson["code"].is_number()) {
                item.code = std::to_string(diagJson["code"].get<int>());
            }
        }
        
        // 注意：relatedInformation 字段会被自动忽略，不会影响主诊断的解析
        
    } catch (const json::exception& e) {
        LOGW("parseSingleDiagnostic: JSON parse error: %s", e.what());
    }
    
    return item;
}

// 解析 publishDiagnostics 通知中的 diagnostics 数组
std::vector<SimpleLspClient::DiagnosticItem> parseDiagnosticsArray(const std::string& jsonStr) {
    std::vector<SimpleLspClient::DiagnosticItem> result;
    
    try {
        json root = json::parse(jsonStr);
        
        // 查找 params.diagnostics 数组
        json* diagnosticsArray = nullptr;
        
        if (root.contains("params") && root["params"].is_object()) {
            // 标准 LSP 格式: {"jsonrpc":"2.0","method":"...","params":{"uri":"...","diagnostics":[...]}}
            const auto& params = root["params"];
            if (params.contains("diagnostics") && params["diagnostics"].is_array()) {
                diagnosticsArray = const_cast<json*>(&params["diagnostics"]);
            }
        } else if (root.contains("diagnostics") && root["diagnostics"].is_array()) {
            // 简化格式: {"uri":"...","diagnostics":[...]}
            diagnosticsArray = const_cast<json*>(&root["diagnostics"]);
        }
        
        if (diagnosticsArray == nullptr) {
            LOGW("parseDiagnosticsArray: 'diagnostics' array not found");
            return result;
        }
        
        // 遍历并解析每个诊断项
        for (const auto& diagJson : *diagnosticsArray) {
            if (diagJson.is_object()) {
                SimpleLspClient::DiagnosticItem item = parseSingleDiagnostic(diagJson);
                result.push_back(item);
            }
        }
        
        LOGD("parseDiagnosticsArray: parsed %zu diagnostics", result.size());
        
    } catch (const json::exception& e) {
        LOGE("parseDiagnosticsArray: JSON parse error: %s", e.what());
    }
    
    return result;
}

// 从 JSON 字符串中提取 URI（用于 publishDiagnostics）
std::string parseUriFromJson(const std::string& jsonStr) {
    try {
        json root = json::parse(jsonStr);
        
        if (root.contains("params") && root["params"].is_object()) {
            const auto& params = root["params"];
            if (params.contains("uri") && params["uri"].is_string()) {
                return params["uri"].get<std::string>();
            }
        } else if (root.contains("uri") && root["uri"].is_string()) {
            return root["uri"].get<std::string>();
        }
    } catch (const json::exception& e) {
        LOGW("parseUriFromJson: JSON parse error: %s", e.what());
    }
    return "";
}

} // namespace

class SimpleLspClient::RequestSender {
public:
    RequestSender(SimpleLspClient* client, std::string label)
        : client_(client), label_(std::move(label)) {}

    void start() {
        std::lock_guard<std::mutex> lock(mutex_);
        if (running_) {
            return;
        }
        queue_.clear();
        stop_requested_ = false;
        running_ = true;
        worker_ = std::thread(&RequestSender::loop, this);
    }

    void stop() {
        {
            std::lock_guard<std::mutex> lock(mutex_);
            if (!running_) {
                queue_.clear();
                stop_requested_ = false;
                return;
            }
            stop_requested_ = true;
        }
        cv_.notify_all();
        if (worker_.joinable()) {
            worker_.join();
        }
        {
            std::lock_guard<std::mutex> lock(mutex_);
            queue_.clear();
            running_ = false;
            stop_requested_ = false;
        }
    }

    bool enqueue(std::string json) {
        {
            std::lock_guard<std::mutex> lock(mutex_);
            if (!running_) {
                return false;
            }
            queue_.push_back(std::move(json));
        }
        cv_.notify_one();
        return true;
    }

    size_t queue_size() {
        std::lock_guard<std::mutex> lock(mutex_);
        return queue_.size();
    }

private:
    void loop() {
        while (true) {
            std::string payload;
            {
                std::unique_lock<std::mutex> lock(mutex_);
                cv_.wait(lock, [&]() {
                    return stop_requested_ || !queue_.empty();
                });
                if (stop_requested_ && queue_.empty()) {
                    break;
                }
                payload = std::move(queue_.front());
                queue_.pop_front();
            }
            if (!client_->sendJson(payload)) {
                LOGW("%s sender failed to deliver payload", label_.c_str());
            }
        }
    }

    SimpleLspClient* client_;
    std::string label_;
    std::thread worker_;
    std::mutex mutex_;
    std::condition_variable cv_;
    std::deque<std::string> queue_;
    bool running_ = false;
    bool stop_requested_ = false;
};

// ============================================================================
// 单例实现
// ============================================================================

SimpleLspClient* SimpleLspClient::instance_ = nullptr;
std::mutex SimpleLspClient::instance_mutex_;

SimpleLspClient* SimpleLspClient::getInstance() {
    if (instance_ == nullptr) {
        std::lock_guard<std::mutex> lock(instance_mutex_);
        if (instance_ == nullptr) {
            instance_ = new SimpleLspClient();
        }
    }
    return instance_;
}

void SimpleLspClient::destroyInstance() {
    std::lock_guard<std::mutex> lock(instance_mutex_);
    if (instance_ != nullptr) {
        delete instance_;
        instance_ = nullptr;
    }
}

SimpleLspClient::~SimpleLspClient() {
    shutdown();
}

// ============================================================================
// 生命周期管理
// ============================================================================

bool SimpleLspClient::initialize(const std::string& clangd_path, const std::string& work_dir, int completion_limit) {
    if (initialized_.load()) {
        LOGD("SimpleLspClient already initialized");
        return true;
    }

    LOGI("SimpleLspClient initializing...");
    LOGI("clangd_path: %s", clangd_path.c_str());
    LOGI("work_dir: %s", work_dir.c_str());

    work_dir_ = work_dir;
    completion_limit_ = completion_limit > 0 ? completion_limit : 50;
    LOGI("Completion limit: %d items", completion_limit_);

    // 1. 创建并启动 ClangdServer
    std::vector<std::string> extraArgs;
    std::string compileCommandsDir = detectCompileCommandsDir(work_dir_);
    if (!compileCommandsDir.empty()) {
        LOGI("Using compile_commands dir: %s", compileCommandsDir.c_str());
        extraArgs.push_back("--compile-commands-dir=" + compileCommandsDir);
    } else {
        LOGW("compile_commands.json not found under %s/build/<variant>", work_dir_.c_str());
    }
    extraArgs.push_back("--limit-results=" + std::to_string(completion_limit_));

    server_ = std::make_unique<ClangdServer>();
    std::string error = server_->start(clangd_path, extraArgs);
    if (!error.empty()) {
        LOGE("Failed to start clangd: %s", error.c_str());
        reportHealthEvent(HealthEventType::INIT_FAILURE, error);
        server_.reset();
        return false;
    }

    // 等待 clangd 启动
    std::this_thread::sleep_for(std::chrono::milliseconds(500));

    if (!server_->isRunning()) {
        LOGE("Clangd failed to start");
        reportHealthEvent(HealthEventType::CLANGD_EXIT, "Clangd process exited unexpectedly");
        server_.reset();
        return false;
    }

    // 2. 启动响应读取线程
    running_.store(true);
    reader_thread_ = std::thread(&SimpleLspClient::readerLoop, this);

    // 3. 执行 LSP 初始化握手
    if (!performInitialize()) {
        LOGE("LSP initialize handshake failed");
        running_.store(false);
        if (reader_thread_.joinable()) {
            reader_thread_.join();
        }
        server_->stop();
        server_.reset();
        return false;
    }

    startSenders();
    initialized_.store(true);
    LOGI("SimpleLspClient initialized successfully");
    return true;
}

void SimpleLspClient::shutdown() {
    if (!initialized_.load()) {
        return;
    }

    LOGI("SimpleLspClient shutting down...");

    // 1. 停止读取线程
    running_.store(false);
    if (reader_thread_.joinable()) {
        reader_thread_.join();
    }
    stopSenders();

    // 2. 停止 clangd
    if (server_) {
        server_->stop();
        server_.reset();
    }

    // 3. 清理状态
    {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        pending_requests_.clear();
    }
    {
        std::lock_guard<std::mutex> lock(file_mutex_);
        file_versions_.clear();
    }

    initialized_.store(false);
    LOGI("SimpleLspClient shutdown complete");
}

// ============================================================================
// LSP 初始化握手
// ============================================================================

bool SimpleLspClient::performInitialize() {
    LOGD("Performing LSP initialize handshake...");

    // 构建 initialize 请求
    uint64_t init_id = generateRequestId();
    
    std::ostringstream params;
    params << R"({)";
    params << R"("processId":)" << getpid() << ",";
    params << R"("rootUri":"file://)" << work_dir_ << R"(",)";
    params << R"("capabilities":{)";
    params << R"("textDocument":{)";
    params << R"("completion":{"completionItem":{"snippetSupport":false}},)";
    params << R"("hover":{"contentFormat":["plaintext"]},)";
    params << R"("definition":{"linkSupport":false},)";
    params << R"("references":{},)";
    params << R"("publishDiagnostics":{"relatedInformation":true})";
    params << R"(})";  // textDocument
    params << R"(})";  // capabilities
    params << R"(})";

    std::string request = buildRequest("initialize", init_id, params.str());
    
    // 记录 pending 请求
    {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        PendingRequest pr;
        pr.method = "initialize";
        pr.created_at = std::chrono::steady_clock::now();
        pending_requests_[init_id] = pr;
    }

    if (!sendJson(request)) {
        LOGE("Failed to send initialize request");
        return false;
    }

    // 等待初始化响应（最多 10 秒）
    for (int i = 0; i < 100; ++i) {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        
        std::lock_guard<std::mutex> lock(pending_mutex_);
        auto it = pending_requests_.find(init_id);
        if (it != pending_requests_.end() && it->second.completed) {
            LOGD("Initialize response received");
            pending_requests_.erase(it);
            
            // 发送 initialized 通知
            std::string initialized_notif = buildNotification("initialized", "{}");
            sendJson(initialized_notif);
            
            return true;
        }
    }

    LOGE("Initialize request timed out");
    return false;
}

// ============================================================================
// LSP 请求接口
// ============================================================================

uint64_t SimpleLspClient::requestHover(const std::string& file_uri, uint32_t line, uint32_t character) {
    if (!initialized_.load()) {
        LOGE("Client not initialized");
        return 0;
    }

    // 清理旧 hover 请求并通知 clangd 取消
    std::vector<uint64_t> cancelled_ids;
    {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        for (auto& entry : pending_requests_) {
            if (entry.second.method == "textDocument/hover" && !entry.second.completed && !entry.second.cancelled) {
                LOGD("Cancelling old hover request %llu", (unsigned long long)entry.first);
                entry.second.cancelled = true;
                cancelled_ids.push_back(entry.first);
            }
        }
    }
    for (auto request_id : cancelled_ids) {
        sendCancelNotification(request_id);
    }

    uint64_t request_id = generateRequestId();
    LOGD("requestHover: id=%llu, file=%s, line=%u, char=%u",
         (unsigned long long)request_id, file_uri.c_str(), line, character);

    std::ostringstream params;
    params << R"({"textDocument":{"uri":")" << file_uri << R"("},)";
    params << R"("position":{"line":)" << line << R"(,"character":)" << character << "}}";

    std::string request = buildRequest("textDocument/hover", request_id, params.str());

    {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        PendingRequest pr;
        pr.method = "textDocument/hover";
        pr.created_at = std::chrono::steady_clock::now();
        pending_requests_[request_id] = pr;
    }

    if (!enqueueOrSend(hover_sender_.get(), request)) {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        pending_requests_.erase(request_id);
        return 0;
    }

    return request_id;
}

uint64_t SimpleLspClient::requestCompletion(const std::string& file_uri, uint32_t line, uint32_t character,
                                            const std::string& trigger_character) {
    if (!initialized_.load()) {
        LOGE("Client not initialized");
        return 0;
    }

    // 清理旧 completion 请求并通知 clangd 取消
    std::vector<uint64_t> cancelled_ids;
    {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        for (auto& entry : pending_requests_) {
            if (entry.second.method == "textDocument/completion" && !entry.second.completed && !entry.second.cancelled) {
                LOGD("Cancelling old completion request %llu", (unsigned long long)entry.first);
                entry.second.cancelled = true;
                cancelled_ids.push_back(entry.first);
            }
        }
    }
    for (auto request_id : cancelled_ids) {
        sendCancelNotification(request_id);
    }

    uint64_t request_id = generateRequestId();
    LOGD("requestCompletion: id=%llu, file=%s, line=%u, char=%u",
         (unsigned long long)request_id, file_uri.c_str(), line, character);

    std::ostringstream params;
    params << R"({"textDocument":{"uri":")" << file_uri << R"("},)";
    params << R"("position":{"line":)" << line << R"(,"character":)" << character << "}";
    
    // 添加 completion context
    if (!trigger_character.empty()) {
        params << R"(,"context":{"triggerKind":2,"triggerCharacter":")" << trigger_character << R"("})";
    } else {
        params << R"(,"context":{"triggerKind":1})";  // Invoked
    }
    params << "}";

    std::string request = buildRequest("textDocument/completion", request_id, params.str());

    {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        PendingRequest pr;
        pr.method = "textDocument/completion";
        pr.created_at = std::chrono::steady_clock::now();
        pending_requests_[request_id] = pr;
    }

    if (!enqueueOrSend(completion_sender_.get(), request)) {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        pending_requests_.erase(request_id);
        return 0;
    }

    return request_id;
}

uint64_t SimpleLspClient::requestDefinition(const std::string& file_uri, uint32_t line, uint32_t character) {
    if (!initialized_.load()) {
        LOGE("Client not initialized");
        return 0;
    }

    uint64_t request_id = generateRequestId();
    LOGD("requestDefinition: id=%llu, file=%s, line=%u, char=%u",
         (unsigned long long)request_id, file_uri.c_str(), line, character);

    std::ostringstream params;
    params << R"({"textDocument":{"uri":")" << file_uri << R"("},)";
    params << R"("position":{"line":)" << line << R"(,"character":)" << character << "}}";

    std::string request = buildRequest("textDocument/definition", request_id, params.str());

    {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        PendingRequest pr;
        pr.method = "textDocument/definition";
        pr.created_at = std::chrono::steady_clock::now();
        pending_requests_[request_id] = pr;
    }

    if (!sendJson(request)) {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        pending_requests_.erase(request_id);
        return 0;
    }

    return request_id;
}

uint64_t SimpleLspClient::requestReferences(const std::string& file_uri, uint32_t line, uint32_t character,
                                            bool include_declaration) {
    if (!initialized_.load()) {
        LOGE("Client not initialized");
        return 0;
    }

    uint64_t request_id = generateRequestId();
    LOGD("requestReferences: id=%llu, file=%s, line=%u, char=%u",
         (unsigned long long)request_id, file_uri.c_str(), line, character);

    std::ostringstream params;
    params << R"({"textDocument":{"uri":")" << file_uri << R"("},)";
    params << R"("position":{"line":)" << line << R"(,"character":)" << character << "},";
    params << R"("context":{"includeDeclaration":)" << (include_declaration ? "true" : "false") << "}}";

    std::string request = buildRequest("textDocument/references", request_id, params.str());

    {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        PendingRequest pr;
        pr.method = "textDocument/references";
        pr.created_at = std::chrono::steady_clock::now();
        pending_requests_[request_id] = pr;
    }

    if (!sendJson(request)) {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        pending_requests_.erase(request_id);
        return 0;
    }

    return request_id;
}

// ============================================================================
// 结果获取
// ============================================================================

std::optional<std::string> SimpleLspClient::getResult(uint64_t request_id) {
    std::lock_guard<std::mutex> lock(pending_mutex_);
    
    auto it = pending_requests_.find(request_id);
    if (it == pending_requests_.end()) {
        LOGD("getResult: request %llu not found in pending_requests (size=%zu)", 
             (unsigned long long)request_id, pending_requests_.size());
        return std::nullopt;
    }

    if (!it->second.completed) {
        // 检查是否超时（超过 30 秒的请求直接清理）
        auto now = std::chrono::steady_clock::now();
        auto age = std::chrono::duration_cast<std::chrono::seconds>(now - it->second.created_at).count();
        if (age > 30) {
            LOGW("getResult: request %llu timed out after %lld seconds, removing", 
                 (unsigned long long)request_id, (long long)age);
            pending_requests_.erase(it);
            return std::nullopt;
        }
        return std::nullopt;
    }

    // 获取结果并移除请求
    std::optional<std::string> result = it->second.result;
    pending_requests_.erase(it);
    LOGD("getResult: returning result for request %llu, pending_requests size=%zu", 
         (unsigned long long)request_id, pending_requests_.size());
    return result;
}

// ============================================================================
// 文档同步
// ============================================================================

void SimpleLspClient::didOpen(const std::string& file_uri, const std::string& content, 
                              const std::string& language_id) {
    if (!initialized_.load()) {
        LOGW("didOpen called before initialization");
        return;
    }

    LOGD("didOpen: %s", file_uri.c_str());

    {
        std::lock_guard<std::mutex> lock(file_mutex_);
        file_versions_[file_uri] = 1;
    }

    // 转义 content 中的特殊字符
    std::string escaped_content;
    escaped_content.reserve(content.size() * 1.1);
    for (char c : content) {
        switch (c) {
            case '"': escaped_content += "\\\""; break;
            case '\\': escaped_content += "\\\\"; break;
            case '\n': escaped_content += "\\n"; break;
            case '\r': escaped_content += "\\r"; break;
            case '\t': escaped_content += "\\t"; break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    // 控制字符用 \uXXXX 转义
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", static_cast<unsigned char>(c));
                    escaped_content += buf;
                } else {
                    escaped_content += c;
                }
        }
    }

    std::ostringstream params;
    params << R"({"textDocument":{)";
    params << R"("uri":")" << file_uri << R"(",)";
    params << R"("languageId":")" << language_id << R"(",)";
    params << R"("version":1,)";
    params << R"("text":")" << escaped_content << R"("})";
    params << "}";

    std::string notification = buildNotification("textDocument/didOpen", params.str());
    sendJson(notification);
}


void SimpleLspClient::didChange(const std::string& file_uri, const std::string& content, uint32_t version) {
    if (!initialized_.load()) {
        LOGW("didChange called before initialization");
        return;
    }

    LOGD("didChange: %s, version=%u", file_uri.c_str(), version);

    {
        std::lock_guard<std::mutex> lock(file_mutex_);
        file_versions_[file_uri] = version;
    }

    // 转义 content
    std::string escaped_content;
    escaped_content.reserve(content.size() * 1.1);
    for (char c : content) {
        switch (c) {
            case '"': escaped_content += "\\\""; break;
            case '\\': escaped_content += "\\\\"; break;
            case '\n': escaped_content += "\\n"; break;
            case '\r': escaped_content += "\\r"; break;
            case '\t': escaped_content += "\\t"; break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", static_cast<unsigned char>(c));
                    escaped_content += buf;
                } else {
                    escaped_content += c;
                }
        }
    }

    std::ostringstream params;
    params << R"({"textDocument":{"uri":")" << file_uri << R"(","version":)" << version << "},";
    params << R"("contentChanges":[{"text":")" << escaped_content << R"("}]})";

    std::string notification = buildNotification("textDocument/didChange", params.str());
    sendJson(notification);
}

void SimpleLspClient::didClose(const std::string& file_uri) {
    if (!initialized_.load()) {
        LOGW("didClose called before initialization");
        return;
    }

    LOGD("didClose: %s", file_uri.c_str());

    {
        std::lock_guard<std::mutex> lock(file_mutex_);
        file_versions_.erase(file_uri);
    }

    std::ostringstream params;
    params << R"({"textDocument":{"uri":")" << file_uri << R"("}})";

    std::string notification = buildNotification("textDocument/didClose", params.str());
    sendJson(notification);
}

// ============================================================================
// 取消请求
// ============================================================================

void SimpleLspClient::cancelRequest(uint64_t request_id) {
    LOGD("cancelRequest: id=%llu", (unsigned long long)request_id);

    bool should_send = false;
    {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        auto it = pending_requests_.find(request_id);
        if (it != pending_requests_.end() && !it->second.completed) {
            it->second.cancelled = true;
            should_send = true;
        }
    }

    if (should_send) {
        sendCancelNotification(request_id);
    }
}

void SimpleLspClient::notifyRequestTimeout(uint64_t request_id) {
    std::string method;
    {
        std::lock_guard<std::mutex> lock(pending_mutex_);
        auto it = pending_requests_.find(request_id);
        if (it != pending_requests_.end()) {
            method = it->second.method;
            pending_requests_.erase(it);
        }
    }
    if (!method.empty()) {
        LOGW("Request %llu (%s) reported timeout by Kotlin layer",
             static_cast<unsigned long long>(request_id), method.c_str());
        recordTimeout(method);
    } else {
        LOGW("Timeout notification for unknown request %llu",
             static_cast<unsigned long long>(request_id));
    }
}

// ============================================================================
// 回调设置
// ============================================================================

void SimpleLspClient::setDiagnosticsCallback(DiagnosticsCallback callback) {
    std::lock_guard<std::mutex> lock(diagnostics_mutex_);
    diagnostics_callback_ = std::move(callback);
}

void SimpleLspClient::setHealthCallback(HealthCallback callback) {
    std::lock_guard<std::mutex> lock(health_mutex_);
    health_callback_ = std::move(callback);
}

void SimpleLspClient::reportHealthEvent(HealthEventType type, const std::string& message) {
    HealthCallback callback_copy;
    {
        std::lock_guard<std::mutex> lock(health_mutex_);
        callback_copy = health_callback_;
    }
    if (callback_copy) {
        callback_copy(type, message);
    }
}

// ============================================================================
// 内部方法：JSON 构建
// ============================================================================

std::string SimpleLspClient::buildRequest(const std::string& method, uint64_t id, const std::string& params) {
    std::ostringstream json;
    json << R"({"jsonrpc":"2.0","id":)" << id;
    json << R"(,"method":")" << method << R"(")";
    json << R"(,"params":)" << params;
    json << "}";
    return json.str();
}

std::string SimpleLspClient::buildNotification(const std::string& method, const std::string& params) {
    std::ostringstream json;
    json << R"({"jsonrpc":"2.0","method":")" << method << R"(")";
    json << R"(,"params":)" << params;
    json << "}";
    return json.str();
}

void SimpleLspClient::sendCancelNotification(uint64_t request_id) {
    std::ostringstream params;
    params << R"({"id":)" << request_id << "}";
    std::string notification = buildNotification("$/cancelRequest", params.str());
    sendJson(notification);
}

void SimpleLspClient::recordTimeout(const std::string& method) {
    if (method.empty()) {
        return;
    }
    int streak = 0;
    {
        std::lock_guard<std::mutex> lock(stats_mutex_);
        auto& stats = method_stats_[method];
        stats.total_timeouts++;
        stats.consecutive_timeouts++;
        streak = stats.consecutive_timeouts;
    }
    size_t pending = pendingRequestCount();
    size_t hoverQueued = hover_sender_ ? hover_sender_->queue_size() : 0;
    size_t completionQueued = completion_sender_ ? completion_sender_->queue_size() : 0;
    LOGW("Timeout recorded for %s (streak=%d) pending=%zu hoverQ=%zu completionQ=%zu",
         method.c_str(), streak, pending, hoverQueued, completionQueued);
    const int threshold = timeoutThresholdFor(method);
    if (streak >= threshold) {
        {
            std::lock_guard<std::mutex> lock(stats_mutex_);
            method_stats_[method].consecutive_timeouts = 0;
        }
        std::string message = method + " timed out " + std::to_string(streak) + " times consecutively";
        reportHealthEvent(HealthEventType::IO_ERROR, message);
    }
}

void SimpleLspClient::resetTimeoutStats(const std::string& method) {
    if (method.empty()) {
        return;
    }
    std::lock_guard<std::mutex> lock(stats_mutex_);
    auto it = method_stats_.find(method);
    if (it != method_stats_.end()) {
        it->second.consecutive_timeouts = 0;
    }
}

int SimpleLspClient::timeoutThresholdFor(const std::string& method) const {
    if (method == "textDocument/completion") {
        return kCompletionTimeoutThreshold;
    }
    return kTimeoutThreshold;
}

void SimpleLspClient::startSenders() {
    stopSenders();
    hover_sender_ = std::make_unique<RequestSender>(this, "hover");
    completion_sender_ = std::make_unique<RequestSender>(this, "completion");
    if (hover_sender_) {
        hover_sender_->start();
    }
    if (completion_sender_) {
        completion_sender_->start();
    }
}

void SimpleLspClient::stopSenders() {
    if (hover_sender_) {
        hover_sender_->stop();
        hover_sender_.reset();
    }
    if (completion_sender_) {
        completion_sender_->stop();
        completion_sender_.reset();
    }
}

bool SimpleLspClient::enqueueOrSend(RequestSender* sender, const std::string& json) {
    if (sender != nullptr && sender->enqueue(json)) {
        return true;
    }
    return sendJson(json);
}

size_t SimpleLspClient::pendingRequestCount() {
    std::lock_guard<std::mutex> lock(pending_mutex_);
    return pending_requests_.size();
}

// ============================================================================
// 内部方法：发送 JSON
// ============================================================================

bool SimpleLspClient::sendJson(const std::string& json) {
    if (!server_ || !server_->isRunning()) {
        LOGE("Cannot send: server not running");
        return false;
    }

    // 构建 LSP 消息（带 Content-Length header）
    std::ostringstream message;
    message << "Content-Length: " << json.size() << "\r\n\r\n" << json;
    std::string msg_str = message.str();

    LOGD("Sending: %s", json.c_str());

    std::vector<char> data(msg_str.begin(), msg_str.end());
    int written = 0;
    {
        std::lock_guard<std::mutex> lock(send_mutex_);
        written = server_->write(data);
    }
    
    if (written < 0 || static_cast<size_t>(written) != data.size()) {
        LOGE("Failed to write to clangd pipe");
        return false;
    }

    return true;
}

// ============================================================================
// 响应读取线程
// ============================================================================

void SimpleLspClient::readerLoop() {
    LOGI("Reader thread started");

    std::string buffer;
    buffer.reserve(64 * 1024);
    int empty_read_count = 0;

    while (running_.load()) {
        if (!server_ || !server_->isRunning()) {
            LOGW("Server not running, reader thread exiting");
            reportHealthEvent(HealthEventType::CLANGD_EXIT, "Clangd process exited");
            break;
        }

        // 从 clangd 读取数据（带超时）
        auto data = server_->readWithTimeout(8192, 100);
        if (data.empty()) {
            empty_read_count++;
            // 每 50 次空读（约 5 秒）打印一次状态
            if (empty_read_count % 50 == 0) {
                std::lock_guard<std::mutex> lock(pending_mutex_);
                if (!pending_requests_.empty()) {
                    LOGD("Reader: %d empty reads, %zu pending requests, buffer size=%zu", 
                         empty_read_count, pending_requests_.size(), buffer.size());
                }
            }
            continue;
        }
        empty_read_count = 0;

        buffer.append(data.begin(), data.end());
        LOGD("Reader: received %zu bytes, buffer now %zu bytes", data.size(), buffer.size());

        // 解析 LSP 消息
        while (true) {
            // 查找 header 结束位置
            size_t header_end = buffer.find("\r\n\r\n");
            if (header_end == std::string::npos) {
                break;  // 等待更多数据
            }

            // 解析 Content-Length
            std::string header = buffer.substr(0, header_end);
            size_t content_length = 0;
            
            size_t cl_pos = header.find("Content-Length:");
            if (cl_pos == std::string::npos) {
                cl_pos = header.find("content-length:");
            }
            if (cl_pos != std::string::npos) {
                size_t value_start = cl_pos + 15;  // strlen("Content-Length:")
                while (value_start < header.size() && 
                       (header[value_start] == ' ' || header[value_start] == '\t')) {
                    ++value_start;
                }
                size_t value_end = header.find_first_of("\r\n", value_start);
                if (value_end == std::string::npos) {
                    value_end = header.size();
                }
                content_length = std::stoull(header.substr(value_start, value_end - value_start));
            }

            if (content_length == 0) {
                LOGW("Invalid LSP message: no Content-Length");
                buffer.erase(0, header_end + 4);
                continue;
            }

            // 检查是否有完整的消息体
            size_t message_start = header_end + 4;
            if (buffer.size() < message_start + content_length) {
                break;  // 等待更多数据
            }

            // 提取 JSON 消息
            std::string json = buffer.substr(message_start, content_length);
            buffer.erase(0, message_start + content_length);

            LOGD("Received: %s", json.substr(0, 500).c_str());

            // 处理响应
            handleResponse(json);
        }
    }

    LOGI("Reader thread stopped");
}

// ============================================================================
// 响应处理
// ============================================================================

void SimpleLspClient::handleResponse(const std::string& json) {
    // 先检查是否是通知（有 method 字段但没有 id 或 id 为 null）
    size_t method_pos = json.find("\"method\"");
    size_t id_pos = json.find("\"id\"");
    
    // 如果有 method 字段，先检查是否是通知
    if (method_pos != std::string::npos) {
        // 检查 id 是否为 null 或不存在
        bool is_notification = (id_pos == std::string::npos);
        if (!is_notification && id_pos != std::string::npos) {
            // 检查 id 值是否为 null
            size_t colon_pos = json.find(':', id_pos);
            if (colon_pos != std::string::npos) {
                size_t value_start = colon_pos + 1;
                while (value_start < json.size() && 
                       (json[value_start] == ' ' || json[value_start] == '\t')) {
                    ++value_start;
                }
                if (json.substr(value_start, 4) == "null") {
                    is_notification = true;
                }
            }
        }
        
        if (is_notification) {
            // 这是一个通知，跳到通知处理逻辑
            goto handle_notification;
        }
    }
    
    // 处理响应（有 id 字段且不为 null）
    if (id_pos != std::string::npos) {
        size_t colon_pos = json.find(':', id_pos);
        if (colon_pos != std::string::npos) {
            size_t value_start = colon_pos + 1;
            while (value_start < json.size() && 
                   (json[value_start] == ' ' || json[value_start] == '\t')) {
                ++value_start;
            }
            
            // 解析 ID（可能是数字或字符串）
            uint64_t request_id = 0;
            if (json[value_start] == '"') {
                // 字符串 ID
                size_t end_quote = json.find('"', value_start + 1);
                if (end_quote != std::string::npos) {
                    try {
                        request_id = std::stoull(json.substr(value_start + 1, end_quote - value_start - 1));
                    } catch (...) {
                        LOGW("Failed to parse string ID");
                    }
                }
            } else if (std::isdigit(json[value_start])) {
                // 数字 ID
                size_t value_end = value_start;
                while (value_end < json.size() && std::isdigit(json[value_end])) {
                    ++value_end;
                }
                try {
                    request_id = std::stoull(json.substr(value_start, value_end - value_start));
                } catch (...) {
                    LOGW("Failed to parse numeric ID");
                }
            } else {
                LOGD("ID value is not a number or string, might be null");
            }

            if (request_id > 0) {
                std::lock_guard<std::mutex> lock(pending_mutex_);
                auto it = pending_requests_.find(request_id);
                if (it != pending_requests_.end()) {
                    if (!it->second.cancelled) {
                        it->second.result = json;
                        it->second.completed = true;
                        resetTimeoutStats(it->second.method);
                        LOGD("Response stored for request %llu", (unsigned long long)request_id);
                    } else {
                        LOGD("Response for cancelled request %llu, ignoring", (unsigned long long)request_id);
                        pending_requests_.erase(it);
                    }
                } else {
                    LOGW("Response for unknown request %llu", (unsigned long long)request_id);
                }
                return;
            }
        }
    }

handle_notification:
    // 处理通知
    if (method_pos != std::string::npos) {
        // 查找方法名
        size_t colon_pos = json.find(':', method_pos);
        if (colon_pos != std::string::npos) {
            size_t quote_start = json.find('"', colon_pos);
            if (quote_start != std::string::npos) {
                size_t quote_end = json.find('"', quote_start + 1);
                if (quote_end != std::string::npos) {
                    std::string method = json.substr(quote_start + 1, quote_end - quote_start - 1);
                    
                    if (method == "textDocument/publishDiagnostics") {
                        // 处理诊断通知（使用 nlohmann/json 库解析）
                        LOGD("Received diagnostics notification");
                        
                        DiagnosticsCallback callback_copy;
                        {
                            std::lock_guard<std::mutex> lock(diagnostics_mutex_);
                            callback_copy = diagnostics_callback_;
                        }
                        
                        if (callback_copy) {
                            // 使用 nlohmann/json 解析 URI 和诊断数组
                            std::string file_uri = parseUriFromJson(json);
                            if (!file_uri.empty()) {
                                std::vector<DiagnosticItem> diagnostics = parseDiagnosticsArray(json);
                                
                                LOGI("Dispatching %zu diagnostics for %s", 
                                     diagnostics.size(), file_uri.c_str());
                                
                                callback_copy(file_uri, diagnostics);
                            } else {
                                LOGW("publishDiagnostics: URI not found in notification");
                            }
                        }
                    }
                }
            }
        }
    }
}

} // namespace lsp
} // namespace tinaide
