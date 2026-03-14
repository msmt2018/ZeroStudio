// SimpleLspClient - 简化的 LSP 客户端
// 直接通过 pipe 与 clangd 通信，使用 JSON 协议
// 目标：从 9 层架构简化到 5 层

#ifndef TINAIDE_SIMPLE_LSP_CLIENT_H
#define TINAIDE_SIMPLE_LSP_CLIENT_H

#include <string>
#include <optional>
#include <atomic>
#include <thread>
#include <mutex>
#include <map>
#include <functional>
#include <chrono>
#include <vector>
#include <memory>

namespace tinaide {
namespace lsp {

// 前向声明
class ClangdServer;

/**
 * 简化的 LSP 客户端
 * 
 * 设计原则：
 * - 直接使用 JSON 协议，无需 FlatBuffers 转换
 * - 单一 reader 线程从 clangd 读取响应
 * - 简单的 pending_requests_ map 管理请求状态
 * - 所有复杂逻辑（防抖、缓存）放到 Kotlin 层
 */
class SimpleLspClient {
public:
    /**
     * 获取全局单例
     */
    static SimpleLspClient* getInstance();

    /**
     * 销毁单例
     */
    static void destroyInstance();

    // 禁用拷贝和赋值
    SimpleLspClient(const SimpleLspClient&) = delete;
    SimpleLspClient& operator=(const SimpleLspClient&) = delete;

    // ========================================================================
    // 生命周期管理
    // ========================================================================

    /**
     * 初始化客户端
     * @param clangd_path libclangd.so 的路径
     * @param work_dir 工作目录
     * @return 是否成功
     */
    bool initialize(const std::string& clangd_path, const std::string& work_dir, int completion_limit);

    /**
     * 关闭客户端
     */
    void shutdown();

    /**
     * 是否已初始化
     */
    bool isInitialized() const { return initialized_.load(); }

    // ========================================================================
    // LSP 请求接口（异步，返回 request_id）
    // ========================================================================

    /**
     * 请求 Hover 信息
     */
    uint64_t requestHover(const std::string& file_uri, uint32_t line, uint32_t character);

    /**
     * 请求代码补全
     */
    uint64_t requestCompletion(const std::string& file_uri, uint32_t line, uint32_t character,
                               const std::string& trigger_character = "");

    /**
     * 请求定义跳转
     */
    uint64_t requestDefinition(const std::string& file_uri, uint32_t line, uint32_t character);

    /**
     * 请求引用查找
     */
    uint64_t requestReferences(const std::string& file_uri, uint32_t line, uint32_t character,
                               bool include_declaration = true);

    // ========================================================================
    // 结果获取接口（轮询）
    // ========================================================================

    /**
     * 获取请求结果（JSON 字符串）
     * @param request_id 请求 ID
     * @return JSON 结果字符串，nullopt 表示未完成
     */
    std::optional<std::string> getResult(uint64_t request_id);

    // ========================================================================
    // 文档同步
    // ========================================================================

    /**
     * 通知文件打开
     */
    void didOpen(const std::string& file_uri, const std::string& content, const std::string& language_id = "cpp");

    /**
     * 通知文件修改
     */
    void didChange(const std::string& file_uri, const std::string& content, uint32_t version);

    /**
     * 通知文件关闭
     */
    void didClose(const std::string& file_uri);

    // ========================================================================
    // 取消请求
    // ========================================================================

    /**
     * 取消请求
     */
    void cancelRequest(uint64_t request_id);
    
    /**
     * 由 Kotlin 层通知的超时
     */
    void notifyRequestTimeout(uint64_t request_id);

    // ========================================================================
    // 诊断回调
    // ========================================================================

    struct DiagnosticItem {
        uint32_t start_line;
        uint32_t start_character;
        uint32_t end_line;
        uint32_t end_character;
        int severity;  // 1=Error, 2=Warning, 3=Info, 4=Hint
        std::string message;
        std::string source;
        std::string code;
    };

    using DiagnosticsCallback = std::function<void(const std::string& file_uri, 
                                                    const std::vector<DiagnosticItem>& diagnostics)>;
    void setDiagnosticsCallback(DiagnosticsCallback callback);

    // ========================================================================
    // 健康事件回调
    // ========================================================================

    enum class HealthEventType {
        INIT_FAILURE,
        CLANGD_EXIT,
        IO_ERROR
    };

    using HealthCallback = std::function<void(HealthEventType type, const std::string& message)>;
    void setHealthCallback(HealthCallback callback);

private:
    SimpleLspClient() = default;
    ~SimpleLspClient();

    static SimpleLspClient* instance_;
    static std::mutex instance_mutex_;

    // ========================================================================
    // 核心组件
    // ========================================================================

    std::unique_ptr<ClangdServer> server_;
    std::string work_dir_;

    // ========================================================================
    // 请求管理
    // ========================================================================

    struct PendingRequest {
        std::string method;
        std::chrono::steady_clock::time_point created_at;
        std::optional<std::string> result;  // JSON 结果
        bool completed = false;
        bool cancelled = false;
        bool error = false;
        std::string error_message;
    };

    std::mutex pending_mutex_;
    std::map<uint64_t, PendingRequest> pending_requests_;
    std::atomic<uint64_t> next_request_id_{1};
    
    struct MethodStats {
        int total_timeouts = 0;
        int consecutive_timeouts = 0;
    };
    std::mutex stats_mutex_;
    std::map<std::string, MethodStats> method_stats_;
    static constexpr int kTimeoutThreshold = 5;
    static constexpr int kCompletionTimeoutThreshold = 4;

    // ========================================================================
    // 响应读取线程
    // ========================================================================

    std::thread reader_thread_;
    std::atomic<bool> running_{false};
    std::atomic<bool> initialized_{false};
    int completion_limit_ = 50;

    void readerLoop();

    // ========================================================================
    // 内部方法
    // ========================================================================

    bool sendJson(const std::string& json);
    void handleResponse(const std::string& json);
    std::string buildRequest(const std::string& method, uint64_t id, const std::string& params);
    std::string buildNotification(const std::string& method, const std::string& params);
    void sendCancelNotification(uint64_t request_id);

    // LSP 初始化握手
    bool performInitialize();

    // 回调
    DiagnosticsCallback diagnostics_callback_;
    std::mutex diagnostics_mutex_;
    HealthCallback health_callback_;
    std::mutex health_mutex_;
    class RequestSender;
    friend class RequestSender;
    std::unique_ptr<RequestSender> hover_sender_;
    std::unique_ptr<RequestSender> completion_sender_;
    void startSenders();
    void stopSenders();
    bool enqueueOrSend(RequestSender* sender, const std::string& json);
    std::mutex send_mutex_;
    size_t pendingRequestCount();

    void reportHealthEvent(HealthEventType type, const std::string& message);

    // 文件版本追踪
    std::mutex file_mutex_;
    std::map<std::string, uint32_t> file_versions_;

    uint64_t generateRequestId() {
        return next_request_id_.fetch_add(1, std::memory_order_relaxed);
    }
    
    void recordTimeout(const std::string& method);
    void resetTimeoutStats(const std::string& method);
    int timeoutThresholdFor(const std::string& method) const;
};

} // namespace lsp
} // namespace tinaide

#endif // TINAIDE_SIMPLE_LSP_CLIENT_H
